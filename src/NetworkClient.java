import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * UDP client that communicates with GameServer.
 * All callbacks arrive on the listener thread — callers must invoke Swing work
 * through SwingUtilities.invokeLater.
 *
 * Remote positions map: name -> int[]{ x, y, colorIndex, abilityFlags }
 *   abilityFlags bits:
 *     1 = shield active
 *     2 = invisible
 *     4 = slowed (puddle or enemy slow)
 *     8 = reversed controls
 */
public class NetworkClient implements Runnable {

    public static final int PORT = GameServer.PORT;
    private static final int TIMEOUT_MS       = 100;
    private static final int PING_INTERVAL_MS = 1000;

    // ── State ─────────────────────────────────────────────────────────────────
    public enum Phase { CONNECTING, LOBBY, PLAYING, DISCONNECTED }

    private volatile Phase phase = Phase.CONNECTING;
    private final String playerName;
    private final String serverHost;

    private DatagramSocket socket;
    private InetAddress serverAddress;

    private volatile List<String> lobbyPlayers = new ArrayList<>();
    private volatile String  hostName = "";
    private volatile boolean isHost   = false;

    // Remote players: name -> { x, y, colorIndex, abilityFlags }
    private final Map<String, int[]> remotePositions = new LinkedHashMap<>();

    // ── Callbacks ─────────────────────────────────────────────────────────────
    private Consumer<List<String>> onLobbyUpdate;
    private Consumer<Long>         onGameStart;
    private Consumer<Integer>      onAbilityRemoved;
    private Consumer<String>       onTrapReceived;   // receives "SLOW_DOWN" or "REVERSE_CONTROLS"
    private Runnable               onDisconnect;

    private Thread thread;
    private volatile boolean running = true;
    private long lastPing = 0;

    // ── Constructor ───────────────────────────────────────────────────────────
    public NetworkClient(String serverHost, String playerName) throws IOException {
        this.serverHost    = serverHost;
        this.playerName    = playerName;
        this.serverAddress = InetAddress.getByName(serverHost);
        socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT_MS);
        thread = new Thread(this, "NetworkClient");
        thread.setDaemon(true);
    }

    // ── Callback setters ──────────────────────────────────────────────────────
    public void setOnLobbyUpdate(Consumer<List<String>> cb)  { this.onLobbyUpdate    = cb; }
    public void setOnGameStart(Consumer<Long> cb)            { this.onGameStart       = cb; }
    public void setOnAbilityRemoved(Consumer<Integer> cb)    { this.onAbilityRemoved  = cb; }
    public void setOnTrapReceived(Consumer<String> cb)       { this.onTrapReceived    = cb; }
    public void setOnDisconnect(Runnable cb)                 { this.onDisconnect      = cb; }

    // ── Start / stop ──────────────────────────────────────────────────────────
    public void connect() {
        thread.start();
        send("CONNECT " + playerName);
    }

    public void stop() {
        running = false;
        phase   = Phase.DISCONNECTED;
        if (socket != null && !socket.isClosed()) socket.close();
    }

    // ── Send methods ──────────────────────────────────────────────────────────

    /**
     * Broadcasts the local player's position, color choice, and current ability
     * state to the server. The server fans this out to all other clients via STATE.
     */
    public void sendPosition(int x, int y, int colorIndex, int abilityFlags) {
        if (phase == Phase.PLAYING) {
            send("POS " + playerName + " " + x + " " + y + " " + colorIndex + " " + abilityFlags);
        }
    }

    /** Notifies server that the local player picked up the ability at the given index. */
    public void sendPickup(int abilityIndex) {
        if (phase == Phase.PLAYING) send("PICKUP " + abilityIndex);
    }

    /**
     * Notifies the server that the local player picked up an enemy-trap ability.
     * The server will forward TRAP_YOU <trapType> to every OTHER connected client.
     *
     * @param trapType "SLOW_DOWN" or "REVERSE_CONTROLS"
     */
    public void sendTrapOthers(String trapType) {
        if (phase == Phase.PLAYING) send("TRAP_OTHERS " + trapType);
    }

    public void requestStartGame() {
        if (isHost && phase == Phase.LOBBY) send("START_GAME");
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public Phase         getPhase()         { return phase; }
    public boolean       isHost()           { return isHost; }
    public List<String>  getLobbyPlayers()  { return Collections.unmodifiableList(lobbyPlayers); }
    public String        getHostName()      { return hostName; }
    public String        getPlayerName()    { return playerName; }
    public String        getServerHost()    { return serverHost; }

    /**
     * Returns a snapshot of remote player data.
     * Each int[] = { x, y, colorIndex, abilityFlags }
     */
    public synchronized Map<String, int[]> getRemotePositions() {
        return new LinkedHashMap<>(remotePositions);
    }

    // ── Main receive loop ─────────────────────────────────────────────────────
    @Override
    public void run() {
        while (running) {
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

            long now = System.currentTimeMillis();
            if (phase == Phase.LOBBY && now - lastPing >= PING_INTERVAL_MS) {
                send("PING"); lastPing = now;
            }
            if (phase == Phase.CONNECTING && now - lastPing >= 500) {
                send("CONNECT " + playerName); lastPing = now;
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
                isHost   = hostName.equals(playerName);
            }
            if (onLobbyUpdate != null) onLobbyUpdate.accept(Collections.unmodifiableList(lobbyPlayers));

        } else if (data.startsWith("START ")) {
            long seed = Long.parseLong(data.substring(6).trim());
            phase = Phase.PLAYING;
            System.out.println("[Client] Game starting! seed=" + seed);
            if (onGameStart != null) onGameStart.accept(seed);

        } else if (data.startsWith("STATE ")) {
            parseState(data.substring(6).trim());

        } else if (data.startsWith("REMOVE ")) {
            try {
                int idx = Integer.parseInt(data.substring(7).trim());
                if (onAbilityRemoved != null) onAbilityRemoved.accept(idx);
            } catch (NumberFormatException ignored) {}

        } else if (data.startsWith("TRAP_YOU ")) {
            // Server is telling us that an enemy used a trap against us.
            String trapType = data.substring(9).trim();
            if (onTrapReceived != null) onTrapReceived.accept(trapType);

        } else if (data.equals("PONG")) {
            // server alive
        } else if (data.startsWith("ERR ")) {
            System.err.println("[Client] Server error: " + data.substring(4));
        }
    }

    /**
     * Parses STATE message and updates the remotePositions map.
     * Format: n1:x1:y1:ci1:f1,n2:x2:y2:ci2:f2,...
     * Stores { x, y, colorIndex, abilityFlags } per player.
     */
    private synchronized void parseState(String stateStr) {
        if (stateStr.isEmpty()) return;
        remotePositions.clear();
        String[] entries = stateStr.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length < 3) continue;
            try {
                String name       = parts[0];
                int x             = Integer.parseInt(parts[1]);
                int y             = Integer.parseInt(parts[2]);
                int colorIndex    = parts.length >= 4 ? Integer.parseInt(parts[3]) : 0;
                int abilityFlags  = parts.length >= 5 ? Integer.parseInt(parts[4]) : 0;
                if (!name.equals(playerName)) {
                    remotePositions.put(name, new int[]{x, y, colorIndex, abilityFlags});
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
