import java.io.IOException;
import java.net.*;
import java.util.*;
import java.net.InetAddress;

/**
 * UDP Game Server for Grace Period multiplayer.
 * Handles player connections, lobby management, and game state broadcasting.
 * Protocol (UDP datagrams, text-based):
 *   Client → Server:  CONNECT <name>
 *                     PING
 *                     START_GAME          (host only)
 *                     POS <name> <x> <y>  (during game)
 *   Server → Client:  LOBBY <name1>,<name2>,...
 *                     CONNECTED <name>
 *                     START <seed>
 *                     STATE <n1>:<x1>:<y1>,<n2>:<x2>:<y2>,...
 *                     PONG
 */
public class GameServer implements Runnable {

    public static final int PORT = 9877;
    public static final int MAX_PLAYERS = 6;
    private static final int TIMEOUT_MS = 100;
    private static final int HEARTBEAT_INTERVAL_MS = 2000;
    private static final int PLAYER_TIMEOUT_MS = 300000;

    private DatagramSocket serverSocket;
    private final Map<String, NetPlayerInfo> players = new LinkedHashMap<>();
    private volatile boolean gameStarted = false;
    private long mapSeed;
    private String hostName = null; // first player to connect is host

    private Thread thread;
    private volatile boolean running = true;

    // ── Constructor ──────────────────────────────────────────────────────────
    public GameServer() throws IOException {
        serverSocket = new DatagramSocket(PORT, InetAddress.getByName("0.0.0.0"));
        serverSocket.setSoTimeout(TIMEOUT_MS);
        mapSeed = System.currentTimeMillis();
        thread = new Thread(this, "GameServer");
        thread.setDaemon(true);
        thread.start();
        System.out.println("[Server] Started on port " + PORT + " | seed=" + mapSeed);
    }

    // ── Stop ─────────────────────────────────────────────────────────────────
    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    // ── Main receive loop ────────────────────────────────────────────────────
    @Override
    public void run() {
        long lastHeartbeat = System.currentTimeMillis();
        while (running) {
            // Receive packet
            byte[] buf = new byte[512];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                serverSocket.receive(packet);
            } catch (SocketTimeoutException e) {
                // no data — fall through to heartbeat / timeout check
            } catch (IOException e) {
                if (running) e.printStackTrace();
                break;
            }

            String data = new String(buf, 0, packet.getLength()).trim();
            if (!data.isEmpty()) {
                handleMessage(data, packet.getAddress(), packet.getPort());
            }

            // Periodic heartbeat + timeout eviction
            long now = System.currentTimeMillis();
            if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                lastHeartbeat = now;
                evictTimedOut(now);
                if (!gameStarted) broadcastLobby();
            }
        }
        System.out.println("[Server] Stopped.");
    }

    // ── Message dispatcher ───────────────────────────────────────────────────
    private synchronized void handleMessage(String data, InetAddress addr, int port) {
        // Reset timeout for any message from a known player
        NetPlayerInfo known = findPlayer(addr, port);
        if (known != null) known.lastSeen = System.currentTimeMillis();

        if (data.startsWith("CONNECT ")) {
            handleConnect(data.substring(8).trim(), addr, port);
        } else if (data.equals("PING")) {
            send("PONG", addr, port);
        } else if (data.equals("START_GAME")) {
            handleStartGame(addr, port);
        } else if (data.startsWith("POS ")) {
            handlePosition(data, addr, port);
        }
    }

    private void handleConnect(String name, InetAddress addr, int port) {
        if (gameStarted) {
            send("ERR Game already started", addr, port);
            return;
        }
        if (players.size() >= MAX_PLAYERS) {
            send("ERR Server full", addr, port);
            return;
        }
        // Reject duplicate names
        if (players.containsKey(name)) {
            send("ERR Name taken", addr, port);
            return;
        }

        NetPlayerInfo p = new NetPlayerInfo(name, addr, port);
        players.put(name, p);

        if (hostName == null) hostName = name;

        System.out.println("[Server] " + name + " connected from " + addr + ":" + port
                + " | players=" + players.size());

        send("CONNECTED " + name, addr, port);
        broadcastLobby();
    }

    private void handleStartGame(InetAddress addr, int port) {
        if (gameStarted) return;
        // Only the host can start
        NetPlayerInfo sender = findPlayer(addr, port);
        if (sender == null || !sender.name.equals(hostName)) {
            send("ERR Only the host can start", addr, port);
            return;
        }
        if (players.size() < 1) {
            send("ERR Need at least 1 player", addr, port);
            return;
        }
        gameStarted = true;
        System.out.println("[Server] Game starting! seed=" + mapSeed);
        broadcast("START " + mapSeed);
    }

    private void handlePosition(String data, InetAddress addr, int port) {
        if (!gameStarted) return;
        // POS <name> <x> <y>
        String[] parts = data.split(" ");
        if (parts.length < 4) return;
        String name = parts[1];
        try {
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            NetPlayerInfo p = players.get(name);
            if (p != null) {
                p.x = x;
                p.y = y;
                p.lastSeen = System.currentTimeMillis();
            }
        } catch (NumberFormatException ignored) { return; }

        // Build and broadcast full state
        broadcast(buildStateString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private String buildStateString() {
        StringBuilder sb = new StringBuilder("STATE ");
        boolean first = true;
        for (NetPlayerInfo p : players.values()) {
            if (!first) sb.append(',');
            sb.append(p.name).append(':').append(p.x).append(':').append(p.y);
            first = false;
        }
        return sb.toString();
    }

    private void broadcastLobby() {
        if (players.isEmpty()) return;
        StringBuilder sb = new StringBuilder("LOBBY ");
        boolean first = true;
        for (String name : players.keySet()) {
            if (!first) sb.append(',');
            sb.append(name);
            first = false;
        }
        sb.append(' ').append(hostName);
        broadcast(sb.toString());
    }

    private void evictTimedOut(long now) {
        if (!gameStarted) {
            Iterator<Map.Entry<String, NetPlayerInfo>> it = players.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, NetPlayerInfo> e = it.next();
                if (now - e.getValue().lastSeen > PLAYER_TIMEOUT_MS) {
                    System.out.println("[Server] Evicting timed-out player: " + e.getKey());
                    it.remove();
                }
            }
        }
    }

    private NetPlayerInfo findPlayer(InetAddress addr, int port) {
        for (NetPlayerInfo p : players.values()) {
            if (p.address.equals(addr) && p.port == port) return p;
        }
        return null;
    }

    void broadcast(String msg) {
        for (NetPlayerInfo p : players.values()) {
            send(msg, p.address, p.port);
        }
    }

    void send(String msg, InetAddress addr, int port) {
        byte[] buf = msg.getBytes();
        try {
            serverSocket.send(new DatagramPacket(buf, buf.length, addr, port));
        } catch (IOException e) {
            // ignore transient send errors
        }
    }

    // ── Inner class ──────────────────────────────────────────────────────────
    static class NetPlayerInfo {
        final String name;
        final InetAddress address;
        final int port;
        int x, y;
        long lastSeen = System.currentTimeMillis();

        NetPlayerInfo(String name, InetAddress address, int port) {
            this.name = name;
            this.address = address;
            this.port = port;
        }
    }

    // ── Stand-alone server entry point ────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        new GameServer();
        System.out.println("[Server] Running. Press Ctrl+C to stop.");
        Thread.currentThread().join(); // keep alive
    }
}