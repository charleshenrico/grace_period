import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.util.*;
import java.util.List;

public class GamePanel extends JPanel implements ActionListener {

    // ── Constants ─────────────────────────────────────────────────────────────
    final double LOGICAL_WIDTH  = 101 * 40.0;
    final double LOGICAL_HEIGHT = 61  * 40.0;
    final double TARGET_ZOOM    = 2.5;

    static final Color[] PLAYER_COLORS = {
            new Color(255, 80,  80),
            new Color(80,  180, 255),
            new Color(80,  255, 130),
            new Color(255, 220, 50),
            new Color(220, 80,  255),
            new Color(255, 150, 50),
    };

    // ── State machine ─────────────────────────────────────────────────────────
    enum State { MAIN_MENU, MP_CHOICE, MP_NAME_ENTRY, MP_HOST_ENTRY,
        MP_LOBBY_HOST, MP_LOBBY_CLIENT,
        SHOW_MAP, ZOOMING, PLAYING, FINISHED, GAMEOVER }
    State currentState = State.MAIN_MENU;

    // ── Menu ──────────────────────────────────────────────────────────────────
    int mainSel = 0;   // 0=1Player  1=Multiplayer
    int mpSel   = 0;   // 0=Create   1=Join

    // ── Text input ────────────────────────────────────────────────────────────
    StringBuilder nameInput = new StringBuilder();
    StringBuilder hostInput = new StringBuilder();
    String errorMsg = "";

    // ── Game objects ──────────────────────────────────────────────────────────
    MapManager     mapM;
    AbilityManager abilityM;
    Player         player;
    javax.swing.Timer          timer;

    // ── Camera / animation ────────────────────────────────────────────────────
    int    frameCount = 0;
    double scaleX = 1, scaleY = 1, camX = 0, camY = 0;

    // ── Countdown ─────────────────────────────────────────────────────────────
    long timeMillisLeft = 60_000;
    long lastUpdateTime = -1;

    // ── Assets ────────────────────────────────────────────────────────────────
    BufferedImage bgImage;

    // ── Multiplayer ───────────────────────────────────────────────────────────
    boolean isMultiplayer    = false;
    boolean isCreatingServer = false;
    NetworkClient netClient;
    GameServer    hostedServer;
    List<String>  lobbyList = new ArrayList<>();
    Map<String,Color> remoteColors = new LinkedHashMap<>();
    int colorIndex = 1;
    int chosenColorIndex = 0; // player's chosen color

    volatile long    pendingMapSeed   = -1;
    volatile boolean pendingGameStart = false;

    // ─────────────────────────────────────────────────────────────────────────
    public GamePanel() {
        setPreferredSize(new Dimension(1280, 800));
        setBackground(new Color(34, 139, 34));
        setFocusable(true);
        try { bgImage = loadImage("background.jpg"); }
        catch (Exception ignored) {}
        setupKeys();
        timer = new javax.swing.Timer(16, this);
        timer.start();
    }

    // ── Key bindings ──────────────────────────────────────────────────────────
    private void setupKeys() {
        InputMap  im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        // movement on/off
        bindKey(im, am, KeyEvent.VK_W,     false, "wOn",  e -> { if (player!=null) player.upPressed    = true;  });
        bindKey(im, am, KeyEvent.VK_S,     false, "sOn",  e -> { if (player!=null) player.downPressed  = true;  });
        bindKey(im, am, KeyEvent.VK_A,     false, "aOn",  e -> { if (player!=null) player.leftPressed  = true;  });
        bindKey(im, am, KeyEvent.VK_D,     false, "dOn",  e -> { if (player!=null) player.rightPressed = true;  });
        bindKey(im, am, KeyEvent.VK_UP,    false, "uOn",  e -> { if (player!=null) player.upPressed    = true;  });
        bindKey(im, am, KeyEvent.VK_DOWN,  false, "dn0",  e -> { if (player!=null) player.downPressed  = true;  });
        bindKey(im, am, KeyEvent.VK_LEFT,  false, "lt0",  e -> { if (player!=null) player.leftPressed  = true;  });
        bindKey(im, am, KeyEvent.VK_RIGHT, false, "rt0",  e -> { if (player!=null) player.rightPressed = true;  });
        bindKey(im, am, KeyEvent.VK_SPACE, false, "sp",   e -> { if (player!=null) player.dashPressed  = true;  });

        bindKey(im, am, KeyEvent.VK_W,     true,  "wOf", e -> { if (player!=null) player.upPressed    = false; });
        bindKey(im, am, KeyEvent.VK_S,     true,  "sOf", e -> { if (player!=null) player.downPressed  = false; });
        bindKey(im, am, KeyEvent.VK_A,     true,  "aOf", e -> { if (player!=null) player.leftPressed  = false; });
        bindKey(im, am, KeyEvent.VK_D,     true,  "dOf", e -> { if (player!=null) player.rightPressed = false; });
        bindKey(im, am, KeyEvent.VK_UP,    true,  "uOf", e -> { if (player!=null) player.upPressed    = false; });
        bindKey(im, am, KeyEvent.VK_DOWN,  true,  "dnf", e -> { if (player!=null) player.downPressed  = false; });
        bindKey(im, am, KeyEvent.VK_LEFT,  true,  "ltf", e -> { if (player!=null) player.leftPressed  = false; });
        bindKey(im, am, KeyEvent.VK_RIGHT, true,  "rtf", e -> { if (player!=null) player.rightPressed = false; });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,     0, false), "enter");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,    0, false), "escape");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE,0, false), "bs");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q,         0, false), "colorPrev");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_E,         0, false), "colorNext");

        am.put("enter",  new AbstractAction() { public void actionPerformed(ActionEvent e) { onEnter();     } });
        am.put("escape", new AbstractAction() { public void actionPerformed(ActionEvent e) { onEscape();    } });
        am.put("bs",     new AbstractAction() { public void actionPerformed(ActionEvent e) { onBackspace(); } });
        am.put("colorPrev", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (currentState == State.MP_LOBBY_HOST || currentState == State.MP_LOBBY_CLIENT)
                chosenColorIndex = (chosenColorIndex + PLAYER_COLORS.length - 1) % PLAYER_COLORS.length;
        }});
        am.put("colorNext", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (currentState == State.MP_LOBBY_HOST || currentState == State.MP_LOBBY_CLIENT)
                chosenColorIndex = (chosenColorIndex + 1) % PLAYER_COLORS.length;
        }});
    }

    private void bindKey(InputMap im, ActionMap am, int vk, boolean rel, String id, ActionListener al) {
        im.put(KeyStroke.getKeyStroke(vk, 0, rel), id);
        am.put(id, new AbstractAction() { public void actionPerformed(ActionEvent e) { al.actionPerformed(e); } });
    }

    @Override public void addNotify() {
        super.addNotify();
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                // arrow keys drive menu selection
                if (currentState == State.MAIN_MENU) {
                    if (e.getKeyCode()==KeyEvent.VK_UP || e.getKeyCode()==KeyEvent.VK_DOWN)
                        mainSel = 1 - mainSel;
                } else if (currentState == State.MP_CHOICE) {
                    if (e.getKeyCode()==KeyEvent.VK_UP || e.getKeyCode()==KeyEvent.VK_DOWN)
                        mpSel = 1 - mpSel;
                }
            }
            @Override public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (c == '\b' || c == '\n' || c == '\r') return;
                if (c < 32) return;
                if (currentState == State.MP_NAME_ENTRY && nameInput.length() < 20) {
                    nameInput.append(c); errorMsg = "";
                } else if (currentState == State.MP_HOST_ENTRY && hostInput.length() < 40) {
                    hostInput.append(c); errorMsg = "";
                }
            }
        });
    }

    // ── Enter / Escape / Backspace ────────────────────────────────────────────
    private void onEnter() {
        switch (currentState) {
            case MAIN_MENU:
                if (mainSel == 0) startSinglePlayer();
                else { currentState = State.MP_CHOICE; mpSel = 0; }
                break;
            case MP_CHOICE:
                isCreatingServer = (mpSel == 0);
                nameInput.setLength(0); hostInput.setLength(0); errorMsg = "";
                currentState = State.MP_NAME_ENTRY;
                break;
            case MP_NAME_ENTRY:
                String nm = nameInput.toString().trim();
                if (nm.isEmpty()) { errorMsg = "Please enter a name."; return; }
                if (isCreatingServer) tryCreateServer(nm);
                else { currentState = State.MP_HOST_ENTRY; errorMsg = ""; }
                break;
            case MP_HOST_ENTRY:
                String h = hostInput.toString().trim();
                if (h.isEmpty()) h = "localhost";
                tryJoinServer(nameInput.toString().trim(), h);
                break;
            case MP_LOBBY_HOST:
                if (netClient != null && netClient.isHost() && lobbyList.size() >= 1)
                    netClient.requestStartGame();
                break;
            case FINISHED: case GAMEOVER:
                returnToMenu();
                break;
            default: break;
        }
    }

    private void onEscape() {
        switch (currentState) {
            case MAIN_MENU: System.exit(0); break;
            case PLAYING: case FINISHED: case GAMEOVER: returnToMenu(); break;
            default: disconnectNetwork(); currentState = State.MAIN_MENU; break;
        }
    }

    private void onBackspace() {
        if (currentState == State.MP_NAME_ENTRY && nameInput.length() > 0)
            nameInput.deleteCharAt(nameInput.length()-1);
        else if (currentState == State.MP_HOST_ENTRY && hostInput.length() > 0)
            hostInput.deleteCharAt(hostInput.length()-1);
    }

    // ── Single-player ─────────────────────────────────────────────────────────
    private void startSinglePlayer() {
        isMultiplayer = false;
        initGame("P1", System.currentTimeMillis());
        currentState = State.SHOW_MAP; frameCount = 0;
    }

    // ── Multiplayer: host ─────────────────────────────────────────────────────
    private void tryCreateServer(String name) {
        try { hostedServer = new GameServer(); }
        catch (Exception ex) { errorMsg = "Cannot start server: " + ex.getMessage(); return; }
        try {
            netClient = buildClient("localhost", name);
            netClient.connect();
            currentState = State.MP_LOBBY_HOST;
        } catch (Exception ex) {
            errorMsg = "Connect failed: " + ex.getMessage();
            if (hostedServer != null) { hostedServer.stop(); hostedServer = null; }
        }
    }

    // ── Multiplayer: join ─────────────────────────────────────────────────────
    private void tryJoinServer(String name, String host) {
        try {
            netClient = buildClient(host, name);
            netClient.connect();
            currentState = State.MP_LOBBY_CLIENT;
        } catch (Exception ex) {
            errorMsg = "Cannot connect to " + host + ": " + ex.getMessage();
        }
    }

    private NetworkClient buildClient(String host, String name) throws Exception {
        NetworkClient nc = new NetworkClient(host, name);
        nc.setOnLobbyUpdate(names -> SwingUtilities.invokeLater(() -> {
            lobbyList = new ArrayList<>(names);
            currentState = netClient.isHost() ? State.MP_LOBBY_HOST : State.MP_LOBBY_CLIENT;
        }));
        nc.setOnGameStart(seed -> { pendingMapSeed = seed; pendingGameStart = true; });
        nc.setOnDisconnect(() -> SwingUtilities.invokeLater(() -> {
            if (currentState != State.MAIN_MENU) { errorMsg = "Disconnected."; returnToMenu(); }
        }));
        return nc;
    }

    // ── Init game world ───────────────────────────────────────────────────────
    private void initGame(String myName, long seed) {
        mapM     = new MapManager(MazeGenerator.generateMap(seed));
        player   = new Player((50*40)+5, (57*40)+5, PLAYER_COLORS[chosenColorIndex], Color.BLACK, myName);
        abilityM = new AbilityManager(mapM.mapLayout, seed);
        timeMillisLeft = 60_000; lastUpdateTime = -1;
        remoteColors.clear(); colorIndex = 1; frameCount = 0;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    private void returnToMenu() {
        disconnectNetwork();
        currentState = State.MAIN_MENU;
        isMultiplayer = false; pendingGameStart = false; pendingMapSeed = -1;
        lobbyList.clear(); remoteColors.clear(); colorIndex = 1;
        frameCount = 0; errorMsg = "";
    }

    private void disconnectNetwork() {
        if (netClient != null)    { netClient.stop();    netClient    = null; }
        if (hostedServer != null) { hostedServer.stop(); hostedServer = null; }
    }

    // ── Game loop ─────────────────────────────────────────────────────────────
    @Override public void actionPerformed(ActionEvent e) { update(); repaint(); }

    private void update() {
        frameCount++;

        // Network-triggered game start
        if (pendingGameStart) {
            pendingGameStart = false;
            isMultiplayer = true;
            String myName = netClient != null ? netClient.getPlayerName() : "P1";
            initGame(myName, pendingMapSeed);
            currentState = State.SHOW_MAP; frameCount = 0;
            return;
        }

        if (currentState == State.SHOW_MAP) {
            if (frameCount > 180) { currentState = State.ZOOMING; frameCount = 0; }
        } else if (currentState == State.ZOOMING) {
            updateZooming();
        } else if (currentState == State.PLAYING) {
            updatePlaying();
        }
    }

    private void updateZooming() {
        int dur = 120;
        double t  = (double) frameCount / dur;
        double pr = Math.sin(t * Math.PI / 2);
        double sx = getWidth()  / LOGICAL_WIDTH;
        double sy = getHeight() / LOGICAL_HEIGHT;
        scaleX = sx + (TARGET_ZOOM - sx) * pr;
        scaleY = sy + (TARGET_ZOOM - sy) * pr;
        camX = ((getWidth()  / 2.0) - (player.x + player.size/2.0) * TARGET_ZOOM) * pr;
        camY = ((getHeight() / 2.0) - (player.y + player.size/2.0) * TARGET_ZOOM) * pr;
        if (frameCount >= dur) { currentState = State.PLAYING; lastUpdateTime = System.currentTimeMillis(); }
    }

    private void updatePlaying() {
        long now = System.currentTimeMillis();
        long delta = now - lastUpdateTime;
        lastUpdateTime = now;
        timeMillisLeft -= delta;
        if (timeMillisLeft <= 0) { timeMillisLeft = 0; currentState = State.GAMEOVER; return; }

        // Run update steps proportional to elapsed time to keep speed consistent across lag
        int steps = (int) Math.round(delta / 16.67);
        steps = Math.max(1, Math.min(steps, 5));
        for (int i = 0; i < steps; i++) {
            player.update(mapM.mapLayout);
            abilityM.update(player);
        }

        if (isMultiplayer && netClient != null)
            netClient.sendPosition(player.x, player.y);

        scaleX = TARGET_ZOOM; scaleY = TARGET_ZOOM;
        camX = (getWidth()  / 2.0) - (player.x + player.size/2.0) * TARGET_ZOOM;
        camY = (getHeight() / 2.0) - (player.y + player.size/2.0) * TARGET_ZOOM;

        // Assign colours to new remote players
        if (isMultiplayer && netClient != null) {
            for (String rn : netClient.getRemotePositions().keySet()) {
                if (!remoteColors.containsKey(rn)) {
                    remoteColors.put(rn, PLAYER_COLORS[colorIndex % PLAYER_COLORS.length]);
                    colorIndex++;
                }
            }
        }

        // Win check
        int cc = player.getCenterCol(), cr = player.getCenterRow();
        if (cr>=0 && cr<mapM.mapLayout.length && cc>=0 && cc<mapM.mapLayout[0].length
                && mapM.mapLayout[cr][cc] == 2)
            currentState = State.FINISHED;
    }

    // ── Paint ─────────────────────────────────────────────────────────────────
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        switch (currentState) {
            case MAIN_MENU:       drawMainMenu(g2);       return;
            case MP_CHOICE:       drawMpChoice(g2);       return;
            case MP_NAME_ENTRY:   drawNameEntry(g2);      return;
            case MP_HOST_ENTRY:   drawHostEntry(g2);      return;
            case MP_LOBBY_HOST:   drawLobby(g2, true);    return;
            case MP_LOBBY_CLIENT: drawLobby(g2, false);   return;
            default: break;
        }

        // World transform
        AffineTransform old = g2.getTransform();
        AffineTransform cam = new AffineTransform();
        if (currentState == State.SHOW_MAP) { cam.scale(0.7, 0.7); }
        else { cam.translate(camX, camY); cam.scale(scaleX, scaleY); }
        g2.setTransform(cam);

        mapM.draw(g2);
        abilityM.draw(g2);

        // Remote ghosts
        if (isMultiplayer && netClient != null && currentState == State.PLAYING) {
            for (Map.Entry<String,int[]> en : netClient.getRemotePositions().entrySet()) {
                Color col = remoteColors.getOrDefault(en.getKey(), Color.CYAN);
                drawGhost(g2, en.getKey(), en.getValue()[0], en.getValue()[1], col);
            }
        }

        player.draw(g2);
        g2.setTransform(old);

        if (currentState == State.PLAYING)  drawHUD(g2);
        if (currentState == State.FINISHED) drawOverlay(g2, "YOU REACHED THE GOAL!", "Final Grade: 1.0", Color.YELLOW);
        if (currentState == State.GAMEOVER) drawOverlay(g2, "YOU LOSE.", "Final Grade: 5.0", Color.RED);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private BufferedImage loadImage(String name) {
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/" + name);
            if (is != null) return ImageIO.read(is);
        } catch (Exception ignored) {}
        try { return ImageIO.read(new java.io.File("res/" + name)); }
        catch (Exception ignored) {}
        return null;
    }

    private void drawBg(Graphics2D g2) {
        if (bgImage != null) g2.drawImage(bgImage, 0, 0, getWidth(), getHeight(), null);
        else { g2.setColor(Color.DARK_GRAY); g2.fillRect(0,0,getWidth(),getHeight()); }
        g2.setColor(new Color(0,0,0,120)); g2.fillRect(0,0,getWidth(),getHeight());
    }

    private void shadow(Graphics2D g2, String t, int x, int y) {
        g2.setColor(Color.BLACK); g2.drawString(t, x+2, y+2);
        g2.setColor(Color.WHITE); g2.drawString(t, x, y);
    }

    private void centered(Graphics2D g2, String t, int y) {
        int x = (getWidth() - g2.getFontMetrics().stringWidth(t)) / 2;
        shadow(g2, t, x, y);
    }

    private void drawMainMenu(Graphics2D g2) {
        drawBg(g2);
        g2.setFont(new Font("Arial", Font.BOLD, 72)); centered(g2, "Welcome to Grace Period", 220);
        g2.setFont(new Font("Arial", Font.ITALIC, 38)); centered(g2, "don't be late...", 285);

        String[] opts = { "> 1 Player <", "> Multiplayer <" };
        int[] ys = { 430, 510 };
        for (int i=0; i<2; i++) {
            g2.setFont(new Font("Arial", Font.BOLD, 40));
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(opts[i])) / 2;
            boolean sel = mainSel == i;
            if (sel && frameCount%60<30) g2.setColor(Color.YELLOW);
            else g2.setColor(sel ? Color.ORANGE : new Color(180,180,180));
            g2.drawString(opts[i], x, ys[i]);
        }
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        centered(g2, "[ \u2191 / \u2193 ] select    [ ENTER ] confirm    [ ESC ] quit", 640);
        drawError(g2, 690);
    }

    private void drawMpChoice(Graphics2D g2) {
        drawBg(g2);
        g2.setFont(new Font("Arial", Font.BOLD, 65)); centered(g2, "Multiplayer", 200);
        String[] opts = { "> Create Server <", "> Join Server <" };
        int[] ys = { 390, 470 };
        for (int i=0; i<2; i++) {
            g2.setFont(new Font("Arial", Font.BOLD, 40));
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(opts[i])) / 2;
            boolean sel = mpSel == i;
            if (sel && frameCount%60<30) g2.setColor(Color.YELLOW);
            else g2.setColor(sel ? Color.ORANGE : new Color(180,180,180));
            g2.drawString(opts[i], x, ys[i]);
        }
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        centered(g2, "[ \u2191 / \u2193 ] select    [ ENTER ] confirm    [ ESC ] back", 630);
    }

    private void drawNameEntry(Graphics2D g2) {
        drawBg(g2);
        String title = isCreatingServer ? "Create Server" : "Join Server - Step 1/2";
        g2.setFont(new Font("Arial", Font.BOLD, 58)); centered(g2, title, 210);
        g2.setFont(new Font("Arial", Font.PLAIN, 30)); centered(g2, "Enter your player name:", 340);
        drawInputBox(g2, nameInput.toString(), 390);
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        centered(g2, "[ ENTER ] confirm    [ ESC ] back", 630);
        drawError(g2, 680);
    }

    private void drawHostEntry(Graphics2D g2) {
        drawBg(g2);
        g2.setFont(new Font("Arial", Font.BOLD, 58)); centered(g2, "Join Server - Step 2/2", 200);
        g2.setFont(new Font("Arial", Font.BOLD, 26));
        centered(g2, "Name: " + nameInput, 310);
        g2.setFont(new Font("Arial", Font.PLAIN, 30)); centered(g2, "Enter server IP address:", 370);
        drawInputBox(g2, hostInput.toString(), 420);
        g2.setFont(new Font("Arial", Font.ITALIC, 22)); centered(g2, "(leave blank for localhost)", 510);
        g2.setFont(new Font("Arial", Font.PLAIN, 20)); centered(g2, "[ ENTER ] connect    [ ESC ] back", 630);
        drawError(g2, 680);
    }

    private void drawInputBox(Graphics2D g2, String text, int y) {
        int bw=500, bh=50, bx=(getWidth()-bw)/2;
        g2.setColor(new Color(0,0,0,160)); g2.fillRoundRect(bx,y-35,bw,bh,10,10);
        g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(2)); g2.drawRoundRect(bx,y-35,bw,bh,10,10);
        String display = text + (frameCount%40<20 ? "|" : "");
        g2.setFont(new Font("Monospaced", Font.PLAIN, 26));
        g2.setColor(Color.WHITE); g2.drawString(display, bx+15, y);
    }

    private void drawError(Graphics2D g2, int y) {
        if (!errorMsg.isEmpty()) {
            g2.setFont(new Font("Arial", Font.BOLD, 22));
            g2.setColor(Color.RED);
            int x = (getWidth() - g2.getFontMetrics().stringWidth(errorMsg)) / 2;
            g2.drawString(errorMsg, x, y);
        }
    }

    private void drawLobby(Graphics2D g2, boolean isHost) {
        drawBg(g2);
        g2.setFont(new Font("Arial", Font.BOLD, 58)); centered(g2, "Multiplayer Lobby", 150);

        // Host shows their own IP (so others know what to connect to).
        // Joining clients show the server's address they connected to.
        try {
            String ip = isHost
                    ? InetAddress.getLocalHost().getHostAddress()
                    : (netClient != null ? netClient.getServerHost() : "?");
            String label = isHost
                    ? "Your IP (share this): " + ip + "   Port: " + GameServer.PORT
                    : "Connected to: " + ip + "   Port: " + GameServer.PORT;
            g2.setFont(new Font("Arial", Font.PLAIN, 22));
            centered(g2, label, 200);
        } catch (Exception ignored) {}

        g2.setFont(new Font("Arial", Font.BOLD, 28)); centered(g2, "Players:", 270);

        int ly = 310;
        for (int i=0; i<lobbyList.size(); i++) {
            String pn = lobbyList.get(i);
            boolean isH = netClient != null && pn.equals(netClient.getHostName());
            String label = "  " + pn + (isH ? "  [HOST]" : "");
            g2.setFont(new Font("Arial", Font.BOLD, 26));
            FontMetrics fm = g2.getFontMetrics();
            int lx = (getWidth() - fm.stringWidth(label)) / 2;
            Color c = PLAYER_COLORS[i % PLAYER_COLORS.length];
            g2.setColor(Color.BLACK); g2.drawString(label, lx+2, ly+2);
            g2.setColor(c);           g2.drawString(label, lx, ly);
            ly += 40;
        }

        if (isHost) {
            boolean canStart = lobbyList.size() >= 1;
            String btn = canStart ? "[ ENTER ] Start Game" : "Waiting for players to join...";
            g2.setFont(new Font("Arial", Font.BOLD, 34));
            FontMetrics fm = g2.getFontMetrics();
            int bx = (getWidth() - fm.stringWidth(btn)) / 2;
            g2.setColor(canStart && frameCount%60<40 ? Color.YELLOW : new Color(180,180,180));
            g2.drawString(btn, bx, 620);
        } else {
            g2.setFont(new Font("Arial", Font.ITALIC, 28));
            centered(g2, "Waiting for host to start the game...", 620);
        }
        // Color picker
        String[] colorNames = {"Red","Blue","Green","Yellow","Purple","Orange"};
        g2.setFont(new Font("Arial", Font.BOLD, 22));
        centered(g2, "Your color:  [ Q ] prev    [ E ] next", 590);
        int swatchX = getWidth()/2 - 20;
        g2.setColor(PLAYER_COLORS[chosenColorIndex]);
        g2.fillRect(swatchX, 600, 40, 20);
        g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(1));
        g2.drawRect(swatchX, 600, 40, 20);
        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        centered(g2, colorNames[chosenColorIndex], 638);

        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        centered(g2, "[ ESC ] disconnect & return to menu", 680);
    }

    private void drawGhost(Graphics2D g2, String name, int x, int y, Color col) {
        g2.setColor(col);           g2.fillRect(x, y, 30, 30);
        g2.setColor(Color.WHITE);   g2.setStroke(new BasicStroke(2)); g2.drawRect(x, y, 30, 30);
        g2.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        int lx = x + (30 - fm.stringWidth(name)) / 2;
        g2.setColor(Color.BLACK); g2.drawString(name, lx+1, y-4);
        g2.setColor(Color.WHITE); g2.drawString(name, lx, y-5);
    }

    private void drawHUD(Graphics2D g2) {
        long s = timeMillisLeft/1000;
        String ts = String.format("%d:%02d", s/60, s%60);
        boolean urg = s < 30;
        g2.setFont(new Font("Arial", Font.BOLD, 48));
        FontMetrics fm = g2.getFontMetrics();
        int tw=fm.stringWidth(ts), th=fm.getHeight();
        int bw=tw+40, bh=th+16, bx=(getWidth()-bw)/2, by=20;
        g2.setColor(urg ? new Color(160,0,0,220) : new Color(0,0,0,180));
        g2.fillRoundRect(bx,by,bw,bh,20,20);
        g2.setStroke(new BasicStroke(2));
        g2.setColor(urg ? Color.RED : new Color(255,255,255,80));
        g2.drawRoundRect(bx,by,bw,bh,20,20);
        g2.setColor(urg ? Color.YELLOW : Color.WHITE);
        g2.drawString(ts, bx+20, by+th);

        if (isMultiplayer && netClient != null) {
            int total = netClient.getRemotePositions().size()+1;
            String badge = "Players: " + total;
            g2.setFont(new Font("Arial", Font.BOLD, 22));
            FontMetrics fm2 = g2.getFontMetrics();
            int px = getWidth()-fm2.stringWidth(badge)-30;
            g2.setColor(new Color(0,0,0,160));
            g2.fillRoundRect(px-8,20,fm2.stringWidth(badge)+16,fm2.getHeight()+8,10,10);
            g2.setColor(Color.WHITE); g2.drawString(badge, px, 20+fm2.getAscent());
        }
    }

    private void drawOverlay(Graphics2D g2, String l1, String l2, Color col) {
        g2.setColor(new Color(0,0,0,190)); g2.fillRect(0,0,getWidth(),getHeight());
        g2.setFont(new Font("Arial", Font.BOLD, 68)); g2.setColor(col);
        centered(g2, l1, getHeight()/2 - 40);
        g2.setFont(new Font("Arial", Font.BOLD, 44)); g2.setColor(Color.WHITE);
        centered(g2, l2, getHeight()/2 + 30);
        g2.setFont(new Font("Arial", Font.PLAIN, 22)); g2.setColor(new Color(180,180,180));
        centered(g2, "Press [ ENTER ] to return to menu", getHeight()/2 + 90);
    }
}