import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;

public class NetworkClient implements Runnable {

    public static final int PORT = GameServer.PORT;
    private static final int TIMEOUT_MS       = 100;
    private static final int PING_INTERVAL_MS = 1000;

    public enum Phase { CONNECTING, LOBBY, PLAYING, DISCONNECTED }

    // ── ADDED: Custom class to track name and ready state ──
    public static class LobbyPlayer {
        public String name;
        public boolean isReady;
        public LobbyPlayer(String name, boolean isReady) {
            this.name = name; this.isReady = isReady;
        }
    }

    private volatile Phase phase = Phase.CONNECTING;
    private final String playerName;
    private final String serverHost;

    private DatagramSocket socket;
    private InetAddress serverAddress;

    private volatile List<LobbyPlayer> lobbyPlayers = new ArrayList<>(); // ── UPDATED ──
    private volatile String  hostName = "";
    private volatile boolean isHost   = false;

    private final Map<String, int[]> remotePositions = new LinkedHashMap<>();

    private Consumer<List<LobbyPlayer>> onLobbyUpdate; // ── UPDATED ──
    private Consumer<Long>         onGameStart;
    private Consumer<Integer>      onAbilityRemoved;
    private Consumer<String>       onTrapReceived;
    private Consumer<String>       onChatReceived;
    private Runnable               onDisconnect;

    private Thread thread;
    private volatile boolean running = true;
    private long lastPing = 0;

    public NetworkClient(String serverHost, String playerName) throws IOException {
        this.serverHost    = serverHost;
        this.playerName    = playerName;
        this.serverAddress = InetAddress.getByName(serverHost);
        socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT_MS);
        thread = new Thread(this, "NetworkClient");
        thread.setDaemon(true);
    }

    public void setOnLobbyUpdate(Consumer<List<LobbyPlayer>> cb)  { this.onLobbyUpdate = cb; }
    public void setOnGameStart(Consumer<Long> cb)                 { this.onGameStart      = cb; }
    public void setOnAbilityRemoved(Consumer<Integer> cb)         { this.onAbilityRemoved = cb; }
    public void setOnTrapReceived(Consumer<String> cb)            { this.onTrapReceived   = cb; }
    public void setOnChatReceived(Consumer<String> cb)            { this.onChatReceived   = cb; }
    public void setOnDisconnect(Runnable cb)                      { this.onDisconnect     = cb; }

    public void connect() {
        thread.start();
        send("CONNECT " + playerName);
    }

    public void stop() {
        running = false;
        phase   = Phase.DISCONNECTED;
        if (socket != null && !socket.isClosed()) socket.close();
    }

    public void sendPosition(int x, int y, int skinIndex, int abilityFlags, int dirCode) {
        if (phase == Phase.PLAYING) {
            send("POS " + playerName + " " + x + " " + y + " "
                    + skinIndex + " " + abilityFlags + " " + dirCode);
        }
    }

    public void sendPickup(int abilityIndex) {
        if (phase == Phase.PLAYING) send("PICKUP " + abilityIndex);
    }

    public void sendTrapOthers(String trapType) {
        if (phase == Phase.PLAYING) send("TRAP_OTHERS " + trapType);
    }

    public void sendChat(String msg) {
        if (phase == Phase.PLAYING) send("CHAT " + playerName + ": " + msg);
    }

    public void requestStartGame() {
        if (isHost && phase == Phase.LOBBY) send("START_GAME");
    }

    // ── ADDED: Toggle Ready Method ──
    public void toggleReady() {
        if (phase == Phase.LOBBY) send("TOGGLE_READY");
    }

    public Phase             getPhase()         { return phase; }
    public boolean           isHost()           { return isHost; }
    public List<LobbyPlayer> getLobbyPlayers()  { return Collections.unmodifiableList(lobbyPlayers); }
    public String            getHostName()      { return hostName; }
    public String            getPlayerName()    { return playerName; }
    public String            getServerHost()    { return serverHost; }

    public synchronized Map<String, int[]> getRemotePositions() {
        return new LinkedHashMap<>(remotePositions);
    }

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

    private void handleMessage(String data) {
        if (data.startsWith("CONNECTED ")) {
            phase = Phase.LOBBY;
            System.out.println("[Client] Connected to server as " + playerName);
        } else if (data.startsWith("LOBBY ")) {
            // ── UPDATED: Parses name:ready ──
            String rest = data.substring(6).trim();
            String[] parts = rest.split(" ");
            String[] entries = parts[0].split(",");

            List<LobbyPlayer> tempLobby = new ArrayList<>();
            for (String entry : entries) {
                String[] nameAndReady = entry.split(":");
                if (nameAndReady.length == 2) {
                    tempLobby.add(new LobbyPlayer(nameAndReady[0], Boolean.parseBoolean(nameAndReady[1])));
                }
            }
            lobbyPlayers = tempLobby;

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
            String trapType = data.substring(9).trim();
            if (onTrapReceived != null) onTrapReceived.accept(trapType);

        } else if (data.startsWith("CHAT ")) {
            if (onChatReceived != null) onChatReceived.accept(data.substring(5).trim());

        } else if (data.equals("PONG")) {
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
                String name       = parts[0];
                int x             = Integer.parseInt(parts[1]);
                int y             = Integer.parseInt(parts[2]);
                int skinIndex     = parts.length >= 4 ? Integer.parseInt(parts[3]) : 0;
                int abilityFlags  = parts.length >= 5 ? Integer.parseInt(parts[4]) : 0;
                int dirCode       = parts.length >= 6 ? Integer.parseInt(parts[5]) : 0;
                if (!name.equals(playerName)) {
                    remotePositions.put(name, new int[]{x, y, skinIndex, abilityFlags, dirCode});
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private void send(String msg) {
        byte[] buf = msg.getBytes();
        try { socket.send(new DatagramPacket(buf, buf.length, serverAddress, PORT)); }
        catch (IOException e) { }
    }
}