import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * UDP client that communicates with GameServer.
 * All callbacks arrive on the listener thread — callers must be thread-safe.
 */
public class NetworkClient implements Runnable {

    public static final int PORT = GameServer.PORT;
    private static final int TIMEOUT_MS = 100;
    private static final int PING_INTERVAL_MS = 1000;

    // ── State ────────────────────────────────────────────────────────────────
    public enum Phase { CONNECTING, LOBBY, PLAYING, DISCONNECTED }

    private volatile Phase phase = Phase.CONNECTING;
    private final String playerName;
    private final String serverHost;

    private DatagramSocket socket;
    private InetAddress serverAddress;

    // Lobby info
    private volatile List<String> lobbyPlayers = new ArrayList<>();
    private volatile String hostName = "";
    private volatile boolean isHost = false;

    // Remote players: name → (x, y, colorIndex)
    private final Map<String, int[]> remotePositions = new LinkedHashMap<>();

    // Callbacks (called on network thread — use SwingUtilities.invokeLater internally)
    private Consumer<List<String>> onLobbyUpdate;
    private Consumer<Long>         onGameStart;
    private Consumer<Integer>      onAbilityRemoved; // receives ability index
    private Runnable               onDisconnect;

    private Thread thread;
    private volatile boolean running = true;
    private long lastPing = 0;

    // ── Constructor ──────────────────────────────────────────────────────────
    public NetworkClient(String serverHost, String playerName) throws IOException {
        this.serverHost  = serverHost;
        this.playerName  = playerName;
        this.serverAddress = InetAddress.getByName(serverHost);
        socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT_MS);
        thread = new Thread(this, "NetworkClient");
        thread.setDaemon(true);
    }

    // ── Callback setters ─────────────────────────────────────────────────────
    public void setOnLobbyUpdate(Consumer<List<String>> cb) { this.onLobbyUpdate = cb; }
    public void setOnGameStart(Consumer<Long> cb)           { this.onGameStart = cb; }
    public void setOnAbilityRemoved(Consumer<Integer> cb)   { this.onAbilityRemoved = cb; }
    public void setOnDisconnect(Runnable cb)                { this.onDisconnect = cb; }

    // ── Start ─────────────────────────────────────────────────────────────────
    public void connect() {
        thread.start();
        send("CONNECT " + playerName);
    }

    // ── Stop ─────────────────────────────────────────────────────────────────
    public void stop() {
        running = false;
        phase = Phase.DISCONNECTED;
        if (socket != null && !socket.isClosed()) socket.close();
    }

    // ── Send position + color update ──────────────────────────────────────────
    public void sendPosition(int x, int y, int colorIndex) {
        if (phase == Phase.PLAYING) {
            send("POS " + playerName + " " + x + " " + y + " " + colorIndex);
        }
    }

    // ── Notify server of ability pickup ───────────────────────────────────────
    public void sendPickup(int abilityIndex) {
        if (phase == Phase.PLAYING) {
            send("PICKUP " + abilityIndex);
        }
    }
    public void requestStartGame() {
        if (isHost && phase == Phase.LOBBY) {
            send("START_GAME");
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public Phase getPhase()                    { return phase; }
    public boolean isHost()                    { return isHost; }
    public List<String> getLobbyPlayers()      { return Collections.unmodifiableList(lobbyPlayers); }
    public String getHostName()                { return hostName; }
    public String getPlayerName()              { return playerName; }
    public String getServerHost()              { return serverHost; }

    /** Returns a snapshot of remote player positions. Thread-safe. */
    public synchronized Map<String, int[]> getRemotePositions() {
        return new LinkedHashMap<>(remotePositions);
    }

    // ── Main receive loop ─────────────────────────────────────────────────────
    @Override
    public void run() {
        while (running) {
            // Receive
            byte[] buf = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
                String data = new String(buf, 0, packet.getLength()).trim();
                if (!data.isEmpty()) handleMessage(data);
            } catch (SocketTimeoutException e) {
                // nothing — fall through to keep-alive logic
            } catch (IOException e) {
                if (running) System.err.println("[Client] IO error: " + e.getMessage());
                break;
            }

            // Keep-alive pings while in LOBBY phase
            long now = System.currentTimeMillis();
            if (phase == Phase.LOBBY && now - lastPing >= PING_INTERVAL_MS) {
                send("PING");
                lastPing = now;
            }

            // While still connecting, keep re-sending CONNECT
            if (phase == Phase.CONNECTING && now - lastPing >= 500) {
                send("CONNECT " + playerName);
                lastPing = now;
            }
        }
        phase = Phase.DISCONNECTED;
        if (onDisconnect != null) onDisconnect.run();
    }

    // ── Message handler ───────────────────────────────────────────────────────
    private void handleMessage(String data) {
        if (data.startsWith("CONNECTED ")) {
            phase = Phase.LOBBY;
            System.out.println("[Client] Connected to server as " + playerName);

        } else if (data.startsWith("LOBBY ")) {
            // LOBBY <n1>,<n2>,... <hostName>
            String rest = data.substring(6).trim();
            String[] parts = rest.split(" ");
            String[] names = parts[0].split(",");
            lobbyPlayers = new ArrayList<>(Arrays.asList(names));
            if (parts.length > 1) {
                hostName = parts[1];
                isHost = hostName.equals(playerName);
            }
            if (onLobbyUpdate != null) onLobbyUpdate.accept(Collections.unmodifiableList(lobbyPlayers));

        } else if (data.startsWith("START ")) {
            long seed = Long.parseLong(data.substring(6).trim());
            phase = Phase.PLAYING;
            System.out.println("[Client] Game starting! seed=" + seed);
            if (onGameStart != null) onGameStart.accept(seed);

        } else if (data.startsWith("STATE ")) {
            // STATE n1:x1:y1,n2:x2:y2,...
            parseState(data.substring(6).trim());

        } else if (data.startsWith("REMOVE ")) {
            try {
                int idx = Integer.parseInt(data.substring(7).trim());
                if (onAbilityRemoved != null) onAbilityRemoved.accept(idx);
            } catch (NumberFormatException ignored) {}

        } else if (data.equals("PONG")) {
            // server is alive — reset timeout counter
        } else if (data.startsWith("ERR ")) {
            System.err.println("[Client] Server error: " + data.substring(4));
        }
    }

    private synchronized void parseState(String stateStr) {
        if (stateStr.isEmpty()) return;
        remotePositions.clear();
        String[] entries = stateStr.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length < 3) continue;
            try {
                String name = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int colorIndex = parts.length >= 4 ? Integer.parseInt(parts[3]) : 0;
                if (!name.equals(playerName)) {
                    remotePositions.put(name, new int[]{x, y, colorIndex});
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // ── Send helper ───────────────────────────────────────────────────────────
    private void send(String msg) {
        byte[] buf = msg.getBytes();
        try {
            socket.send(new DatagramPacket(buf, buf.length, serverAddress, PORT));
        } catch (IOException e) {
            // ignore transient errors
        }
    }
}