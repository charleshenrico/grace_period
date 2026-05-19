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

    static final String[] SKIN_NAMES = { "P1", "P2", "P3", "P4" };
    static final int NUM_SKINS = 4;

    // ── State machine ─────────────────────────────────────────────────────────
    enum State { MAIN_MENU, MP_CHOICE, MP_NAME_ENTRY, MP_HOST_ENTRY,
        MP_LOBBY_HOST, MP_LOBBY_CLIENT,
        CHAR_SELECT, SHOW_MAP, ZOOMING, PLAYING, FINISHED, GAMEOVER }
    State currentState = State.MAIN_MENU;

    // ── Menu ──────────────────────────────────────────────────────────────────
    int mainSel = 0;
    int mpSel   = 0;

    // ── Text input ────────────────────────────────────────────────────────────
    StringBuilder nameInput = new StringBuilder();
    StringBuilder hostInput = new StringBuilder();
    String errorMsg = "";

    // ── Game objects ──────────────────────────────────────────────────────────
    MapManager     mapM;
    AbilityManager abilityM;
    Player         player;
    javax.swing.Timer timer;

    // ── Camera / animation ────────────────────────────────────────────────────
    int    frameCount = 0;
    double scaleX = 1, scaleY = 1, camX = 0, camY = 0;

    // ── Countdown and UPLB Effects ─────────────────────────────────────────────
    long timeMillisLeft = 60_000;
    long lastUpdateTime = -1;
    public long carillonZoomEndTime = 0;
    public long obleFreezeEndTime = 0;

    // ── Assets ────────────────────────────────────────────────────────────────
    BufferedImage bgImage;
    private BufferedImage[][] ghostSprites;

    // ── Multiplayer ───────────────────────────────────────────────────────────
    boolean isMultiplayer    = false;
    boolean isCreatingServer = false;
    NetworkClient netClient;
    GameServer    hostedServer;

    // ── UPDATED: List of LobbyPlayer objects instead of Strings ──
    List<NetworkClient.LobbyPlayer> lobbyList = new ArrayList<>();

    Map<String,Color> remoteColors = new LinkedHashMap<>();
    int colorIndex = 1;

    int chosenSkinIndex = 0;

    volatile long    pendingMapSeed   = -1;
    volatile boolean pendingGameStart = false;

    // ── Chat and Scoreboard ───────────────────────────────────────────────────
    public boolean isTypingChat = false;
    public StringBuilder chatInput = new StringBuilder();
    class ChatMessage {
        String text; long timestamp;
        ChatMessage(String t) { text = t; timestamp = System.currentTimeMillis(); }
    }
    public List<ChatMessage> chatLog = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    public GamePanel() {
        setPreferredSize(new Dimension(1280, 800));
        setBackground(new Color(34, 139, 34));
        setFocusable(true);
        try { bgImage = loadImage("background.jpg"); } catch (Exception ignored) {}

        loadGhostSprites();

        mapM = new MapManager(MazeGenerator.generateMap());
        player = new Player((50*40)+5, (49*40)+5, PLAYER_COLORS[0], Color.BLACK, "P1");
        player.setSkin(0);
        abilityM = new AbilityManager(mapM.mapLayout, this, System.currentTimeMillis(), false);

        setupKeys();
        timer = new javax.swing.Timer(16, this);
        timer.start();
    }

    private void loadGhostSprites() {
        ghostSprites = new BufferedImage[NUM_SKINS][4];
        String[] dirs = { "Down", "Up", "Left", "Right" };
        for (int s = 0; s < NUM_SKINS; s++) {
            for (int d = 0; d < 4; d++) {
                ghostSprites[s][d] = loadImage(SKIN_NAMES[s] + dirs[d] + ".png");
            }
        }
    }

    private void setupKeys() {
        InputMap  im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        bindKey(im, am, KeyEvent.VK_W,     false, "wOn",  e -> { if (player!=null && !isTypingChat) player.upPressed    = true;  });
        bindKey(im, am, KeyEvent.VK_S,     false, "sOn",  e -> { if (player!=null && !isTypingChat) player.downPressed  = true;  });
        bindKey(im, am, KeyEvent.VK_A,     false, "aOn",  e -> { if (player!=null && !isTypingChat) player.leftPressed  = true;  });
        bindKey(im, am, KeyEvent.VK_D,     false, "dOn",  e -> { if (player!=null && !isTypingChat) player.rightPressed = true;  });
        bindKey(im, am, KeyEvent.VK_UP,    false, "uOn",  e -> { if (player!=null && !isTypingChat) player.upPressed    = true;  });
        bindKey(im, am, KeyEvent.VK_DOWN,  false, "dn0",  e -> { if (player!=null && !isTypingChat) player.downPressed  = true;  });
        bindKey(im, am, KeyEvent.VK_LEFT,  false, "lt0",  e -> { if (player!=null && !isTypingChat) player.leftPressed  = true;  });
        bindKey(im, am, KeyEvent.VK_RIGHT, false, "rt0",  e -> { if (player!=null && !isTypingChat) player.rightPressed = true;  });
        bindKey(im, am, KeyEvent.VK_SPACE, false, "sp",   e -> { if (player!=null && !isTypingChat) player.dashPressed  = true;  });

        bindKey(im, am, KeyEvent.VK_W,     true,  "wOf", e -> { if (player!=null) player.upPressed    = false; });
        bindKey(im, am, KeyEvent.VK_S,     true,  "sOf", e -> { if (player!=null) player.downPressed  = false; });
        bindKey(im, am, KeyEvent.VK_A,     true,  "aOf", e -> { if (player!=null) player.leftPressed  = false; });
        bindKey(im, am, KeyEvent.VK_D,     true,  "dOf", e -> { if (player!=null) player.rightPressed = false; });
        bindKey(im, am, KeyEvent.VK_UP,    true,  "uOf", e -> { if (player!=null) player.upPressed    = false; });
        bindKey(im, am, KeyEvent.VK_DOWN,  true,  "dnf", e -> { if (player!=null) player.downPressed  = false; });
        bindKey(im, am, KeyEvent.VK_LEFT,  true,  "ltf", e -> { if (player!=null) player.leftPressed  = false; });
        bindKey(im, am, KeyEvent.VK_RIGHT, true,  "rtf", e -> { if (player!=null) player.rightPressed = false; });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, 0, false), "chat");
        am.put("chat", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (currentState == State.PLAYING && !isTypingChat) {
                SwingUtilities.invokeLater(() -> {
                    isTypingChat = true;
                    if (player != null) {
                        player.upPressed = player.downPressed = player.leftPressed = player.rightPressed = player.dashPressed = false;
                    }
                });
            }
        }});

        // ── ADDED: "R" to toggle Ready state ──
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0, false), "ready");
        am.put("ready", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if ((currentState == State.MP_LOBBY_HOST || currentState == State.MP_LOBBY_CLIENT) && netClient != null) {
                netClient.toggleReady();
            }
        }});

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,     0, false), "enter");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,    0, false), "escape");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE,0, false), "bs");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q,         0, false), "skinPrev");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_E,         0, false), "skinNext");

        am.put("enter",  new AbstractAction() { public void actionPerformed(ActionEvent e) { onEnter();     } });
        am.put("escape", new AbstractAction() { public void actionPerformed(ActionEvent e) { onEscape();    } });
        am.put("bs",     new AbstractAction() { public void actionPerformed(ActionEvent e) { onBackspace(); } });

        am.put("skinPrev", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (isCharPickState(currentState))
                chosenSkinIndex = (chosenSkinIndex + NUM_SKINS - 1) % NUM_SKINS;
        }});
        am.put("skinNext", new AbstractAction() { public void actionPerformed(ActionEvent e) {
            if (isCharPickState(currentState))
                chosenSkinIndex = (chosenSkinIndex + 1) % NUM_SKINS;
        }});
    }

    private boolean isCharPickState(State s) {
        return s == State.CHAR_SELECT
                || s == State.MP_LOBBY_HOST
                || s == State.MP_LOBBY_CLIENT;
    }

    private void bindKey(InputMap im, ActionMap am, int vk, boolean rel, String id, ActionListener al) {
        im.put(KeyStroke.getKeyStroke(vk, 0, rel), id);
        am.put(id, new AbstractAction() { public void actionPerformed(ActionEvent e) { al.actionPerformed(e); } });
    }

    @Override public void addNotify() {
        super.addNotify();
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (currentState == State.MAIN_MENU) {
                    if (e.getKeyCode()==KeyEvent.VK_UP || e.getKeyCode()==KeyEvent.VK_DOWN) mainSel = 1 - mainSel;
                } else if (currentState == State.MP_CHOICE) {
                    if (e.getKeyCode()==KeyEvent.VK_UP || e.getKeyCode()==KeyEvent.VK_DOWN) mpSel = 1 - mpSel;
                }
            }
            @Override public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (c == '\b' || c == '\n' || c == '\r' || c < 32) return;

                if (isTypingChat) {
                    if (chatInput.length() < 40) chatInput.append(c);
                    return;
                }

                if (currentState == State.MP_NAME_ENTRY && nameInput.length() < 20) {
                    nameInput.append(c); errorMsg = "";
                } else if (currentState == State.MP_HOST_ENTRY && hostInput.length() < 40) {
                    hostInput.append(c); errorMsg = "";
                }
            }
        });
    }

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
                // ── UPDATED: Host can only start if ALL players are ready ──
                boolean allReady = true;
                for (NetworkClient.LobbyPlayer p : lobbyList) { if (!p.isReady) allReady = false; }

                if (netClient != null && netClient.isHost() && lobbyList.size() >= 1 && allReady) {
                    netClient.requestStartGame();
                } else {
                    errorMsg = "Everyone must press [ R ] to Ready Up first!";
                }
                break;
            case CHAR_SELECT:
                initGame("P1", System.currentTimeMillis());
                currentState = State.SHOW_MAP; frameCount = 0;
                break;
            case PLAYING:
                if (isTypingChat) {
                    if (chatInput.length() > 0) {
                        if (netClient != null) {
                            netClient.sendChat(chatInput.toString());
                        } else {
                            chatLog.add(new ChatMessage(player.name + ": " + chatInput.toString()));
                            if (chatLog.size() > 5) chatLog.remove(0);
                        }
                    }
                    chatInput.setLength(0);
                    isTypingChat = false;
                } else {
                    isTypingChat = true;
                    if (player != null) {
                        player.upPressed = player.downPressed = player.leftPressed = player.rightPressed = player.dashPressed = false;
                    }
                }
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
            case CHAR_SELECT: currentState = State.MAIN_MENU; break;
            case PLAYING:
                if (isTypingChat) { isTypingChat = false; chatInput.setLength(0); }
                else { returnToMenu(); }
                break;
            case FINISHED: case GAMEOVER: returnToMenu(); break;
            default: disconnectNetwork(); currentState = State.MAIN_MENU; break;
        }
    }

    private void onBackspace() {
        if (isTypingChat && chatInput.length() > 0) {
            chatInput.deleteCharAt(chatInput.length()-1);
            return;
        }
        if (currentState == State.MP_NAME_ENTRY && nameInput.length() > 0)
            nameInput.deleteCharAt(nameInput.length()-1);
        else if (currentState == State.MP_HOST_ENTRY && hostInput.length() > 0)
            hostInput.deleteCharAt(hostInput.length()-1);
    }

    private void startSinglePlayer() {
        isMultiplayer = false;
        currentState = State.CHAR_SELECT;
        frameCount = 0;
    }

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

        nc.setOnLobbyUpdate(players -> SwingUtilities.invokeLater(() -> {
            lobbyList = new ArrayList<>(players);
            currentState = netClient.isHost() ? State.MP_LOBBY_HOST : State.MP_LOBBY_CLIENT;
        }));

        nc.setOnGameStart(seed -> { pendingMapSeed = seed; pendingGameStart = true; });

        nc.setOnAbilityRemoved(idx -> SwingUtilities.invokeLater(() -> {
            if (abilityM != null) abilityM.remoteRemove(idx);
        }));

        nc.setOnTrapReceived(trapType -> SwingUtilities.invokeLater(() -> {
            if (player == null) return;
            switch (trapType) {
                case "SLOW_DOWN":        player.applySlowDown();        break;
                case "REVERSE_CONTROLS": player.applyReverseControls(); break;
            }
        }));

        nc.setOnDisconnect(() -> SwingUtilities.invokeLater(() -> {
            if (currentState != State.MAIN_MENU) { errorMsg = "Disconnected."; returnToMenu(); }
        }));

        nc.setOnChatReceived(msg -> SwingUtilities.invokeLater(() -> {
            chatLog.add(new ChatMessage(msg));
            if (chatLog.size() > 5) chatLog.remove(0);
        }));

        return nc;
    }

    private void initGame(String myName, long seed) {
        mapM     = new MapManager(MazeGenerator.generateMap(seed));
        player   = new Player((50*40)+5, (57*40)+5, PLAYER_COLORS[chosenSkinIndex % PLAYER_COLORS.length], Color.BLACK, myName);
        player.setSkin(chosenSkinIndex);
        abilityM = new AbilityManager(mapM.mapLayout, this, seed, isMultiplayer);
        timeMillisLeft = 60_000; lastUpdateTime = -1;
        frameCount = 0; chatLog.clear();
    }

    private void returnToMenu() {
        disconnectNetwork();
        currentState = State.MAIN_MENU;
        isMultiplayer = false; pendingGameStart = false; pendingMapSeed = -1;
        lobbyList.clear(); frameCount = 0; errorMsg = ""; chatLog.clear();
    }

    private void disconnectNetwork() {
        if (netClient    != null) { netClient.stop();    netClient    = null; }
        if (hostedServer != null) { hostedServer.stop(); hostedServer = null; }
    }

    @Override public void actionPerformed(ActionEvent e) { update(); repaint(); }

    private void update() {
        frameCount++;

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

        if (now > obleFreezeEndTime) { timeMillisLeft -= delta; }
        if (timeMillisLeft <= 0) { timeMillisLeft = 0; currentState = State.GAMEOVER; return; }

        int steps = (int) Math.round(delta / 16.67);
        steps = Math.max(1, Math.min(steps, 5));

        for (int i = 0; i < steps; i++) {
            player.update(mapM.mapLayout);
            int pickedUp = abilityM.update(player);
            if (pickedUp >= 0 && isMultiplayer && netClient != null) {
                netClient.sendPickup(pickedUp);
                Ability.Type t = abilityM.getAbilityType(pickedUp);
                if (t == Ability.Type.SLOW_DOWN || t == Ability.Type.REVERSE_CONTROLS) {
                    netClient.sendTrapOthers(t.name());
                }
            }
        }

        if (isMultiplayer && netClient != null) {
            netClient.sendPosition(player.x, player.y, chosenSkinIndex,
                    player.getAbilityFlags(), player.getDirCode());
        }

        double activeZoom = (now < carillonZoomEndTime) ? 0.8 : TARGET_ZOOM;
        scaleX = activeZoom; scaleY = activeZoom;
        camX = (getWidth()  / 2.0) - (player.x + player.size/2.0) * activeZoom;
        camY = (getHeight() / 2.0) - (player.y + player.size/2.0) * activeZoom;

        int cc = player.getCenterCol(), cr = player.getCenterRow();
        if (cr >= 0 && cr < mapM.mapLayout.length && cc >= 0 && cc < mapM.mapLayout[0].length
                && mapM.mapLayout[cr][cc] == 2) {
            player.hasFinished = true;
            currentState = State.FINISHED;
        }
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        switch (currentState) {
            case MAIN_MENU:       drawMainMenu(g2);         return;
            case MP_CHOICE:       drawMpChoice(g2);         return;
            case MP_NAME_ENTRY:   drawNameEntry(g2);        return;
            case MP_HOST_ENTRY:   drawHostEntry(g2);        return;
            case MP_LOBBY_HOST:   drawLobby(g2, true);      return;
            case MP_LOBBY_CLIENT: drawLobby(g2, false);     return;
            case CHAR_SELECT:     drawCharSelect(g2);       return;
            default: break;
        }

        AffineTransform old = g2.getTransform();
        AffineTransform cam = new AffineTransform();
        if (currentState == State.SHOW_MAP) { cam.scale(0.7, 0.7); }
        else { cam.translate(camX, camY); cam.scale(scaleX, scaleY); }
        g2.setTransform(cam);

        mapM.draw(g2);
        abilityM.draw(g2);

        if (isMultiplayer && netClient != null && currentState == State.PLAYING) {
            for (Map.Entry<String, int[]> en : netClient.getRemotePositions().entrySet()) {
                int[] data   = en.getValue();
                int skinIdx  = data.length >= 3 ? data[2] : 0;
                int flags    = data.length >= 4 ? data[3] : 0;
                int dirCode  = data.length >= 5 ? data[4] : 0;
                drawGhost(g2, en.getKey(), data[0], data[1], skinIdx, flags, dirCode);
            }
        }

        player.draw(g2);
        g2.setTransform(old);

        if (currentState == State.PLAYING) {
            drawHUD(g2);
            drawChat(g2);
            drawScoreboard(g2);
        }

        if (currentState == State.FINISHED) drawOverlay(g2, "YOU REACHED THE GOAL!", "Final Grade: 1.0", Color.YELLOW);
        if (currentState == State.GAMEOVER) drawOverlay(g2, "YOU LOSE.", "Final Grade: 5.0", Color.RED);
    }

    private void drawGhost(Graphics2D g2, String name, int x, int y,
                           int skinIdx, int flags, int dirCode) {
        boolean invisible = (flags & 2) != 0;
        if (invisible) return;

        boolean shielded = (flags & 1) != 0;
        boolean slowed   = (flags & 4) != 0;
        boolean reversed = (flags & 8) != 0;

        int si = Math.max(0, Math.min(NUM_SKINS - 1, skinIdx));
        int dc = Math.max(0, Math.min(3, dirCode));

        BufferedImage sprite = (ghostSprites != null) ? ghostSprites[si][dc] : null;

        if (sprite != null) {
            g2.drawImage(sprite, x, y, 30, 30, null);
        } else {
            Color col  = PLAYER_COLORS[si % PLAYER_COLORS.length];
            Color body = slowed ? new Color(120, 160, 220) : col;
            g2.setColor(body);
            g2.fillRect(x, y, 30, 30);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(x, y, 30, 30);
        }

        if (slowed) {
            g2.setColor(new Color(120, 160, 220, 110));
            g2.fillRect(x, y, 30, 30);
        }

        if (shielded) {
            g2.setColor(new Color(120, 200, 230));
            g2.setStroke(new BasicStroke(3));
            g2.drawOval(x - 5, y - 5, 40, 40);
            g2.setStroke(new BasicStroke(1));
        }

        g2.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        int lx = x + (30 - fm.stringWidth(name)) / 2;
        g2.setColor(Color.BLACK); g2.drawString(name, lx + 1, y - 4);
        g2.setColor(Color.WHITE); g2.drawString(name, lx, y - 5);

        if (reversed) {
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            String tag = "REV";
            FontMetrics fm2 = g2.getFontMetrics();
            int tx = x + (30 - fm2.stringWidth(tag)) / 2;
            g2.setColor(Color.BLACK);             g2.drawString(tag, tx + 1, y - 16);
            g2.setColor(new Color(200, 100, 220)); g2.drawString(tag, tx,     y - 17);
        }
    }

    private void drawCharSelect(Graphics2D g2) {
        drawBg(g2);

        g2.setFont(new Font("Arial", Font.BOLD, 58));
        centered(g2, "Choose Your Character", 160);

        int sprW   = 100, sprH = 100;
        int gap    = 40;
        int totalW = NUM_SKINS * sprW + (NUM_SKINS - 1) * gap;
        int startX = (getWidth() - totalW) / 2;
        int sprY   = 215;

        for (int i = 0; i < NUM_SKINS; i++) {
            int sx  = startX + i * (sprW + gap);
            boolean sel = chosenSkinIndex == i;

            if (sel) {
                g2.setColor(frameCount % 60 < 30 ? Color.YELLOW : Color.ORANGE);
                g2.setStroke(new BasicStroke(5));
                g2.drawRoundRect(sx - 8, sprY - 8, sprW + 16, sprH + 16, 18, 18);
                g2.setStroke(new BasicStroke(1));
            } else {
                g2.setColor(new Color(90, 90, 90));
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(sx - 4, sprY - 4, sprW + 8, sprH + 8, 12, 12);
                g2.setStroke(new BasicStroke(1));
            }

            BufferedImage preview = (ghostSprites != null) ? ghostSprites[i][0] : null;
            if (preview != null) {
                g2.drawImage(preview, sx, sprY, sprW, sprH, null);
            } else {
                g2.setColor(PLAYER_COLORS[i % PLAYER_COLORS.length]);
                g2.fillRect(sx, sprY, sprW, sprH);
            }

            g2.setFont(new Font("Arial", Font.BOLD, 22));
            FontMetrics fm = g2.getFontMetrics();
            int lx = sx + (sprW - fm.stringWidth(SKIN_NAMES[i])) / 2;
            g2.setColor(sel ? Color.YELLOW : new Color(170, 170, 170));
            g2.drawString(SKIN_NAMES[i], lx, sprY + sprH + 28);
        }

        if (ghostSprites != null) {
            String[] dirLabels = { "Down", "Up", "Left", "Right" };
            int dSize = 55, dGap = 24;
            int dTotalW = 4 * dSize + 3 * dGap;
            int dStartX = (getWidth() - dTotalW) / 2;
            int dY      = sprY + sprH + 55;

            g2.setFont(new Font("Arial", Font.PLAIN, 15));
            g2.setColor(new Color(190, 190, 190));
            centered(g2, SKIN_NAMES[chosenSkinIndex] + "  —  directional sprites", dY - 6);

            for (int d = 0; d < 4; d++) {
                int px = dStartX + d * (dSize + dGap);
                int py = dY + 10;
                BufferedImage dir = ghostSprites[chosenSkinIndex][d];
                if (dir != null) {
                    g2.drawImage(dir, px, py, dSize, dSize, null);
                } else {
                    g2.setColor(PLAYER_COLORS[chosenSkinIndex % PLAYER_COLORS.length]);
                    g2.fillRect(px, py, dSize, dSize);
                }
                g2.setFont(new Font("Arial", Font.PLAIN, 12));
                g2.setColor(new Color(210, 210, 210));
                int lx2 = px + (dSize - g2.getFontMetrics().stringWidth(dirLabels[d])) / 2;
                g2.drawString(dirLabels[d], lx2, py + dSize + 15);
            }
        }

        g2.setFont(new Font("Arial", Font.PLAIN, 21));
        centered(g2, "[ Q ] / [ E ] switch    [ ENTER ] confirm    [ ESC ] back", 640);
        drawError(g2, 678);
    }

    private void drawChat(Graphics2D g2) {
        int y = getHeight() - 60;

        if (isTypingChat) {
            g2.setColor(new Color(0,0,0,150));
            g2.fillRect(20, y-25, 400, 30);
            g2.setColor(Color.WHITE);
            g2.drawRect(20, y-25, 400, 30);
            g2.setFont(new Font("Arial", Font.PLAIN, 18));
            g2.drawString("Say: " + chatInput.toString() + (frameCount%40<20?"|":""), 25, y-5);
            y -= 40;
        } else {
            g2.setColor(new Color(255, 255, 255, 120));
            g2.setFont(new Font("Arial", Font.ITALIC, 14));
            g2.drawString("Press [T] to chat", 25, y - 5);
            y -= 25;
        }

        long now = System.currentTimeMillis();
        for (int i = chatLog.size() - 1; i >= 0; i--) {
            ChatMessage cm = chatLog.get(i);
            long age = now - cm.timestamp;
            if (age > 10000) continue;

            int alpha = 255;
            if (age > 8000) alpha = (int)(255 * (10000 - age) / 2000.0);

            g2.setColor(new Color(0,0,0, (int)(alpha*0.6)));
            g2.setFont(new Font("Arial", Font.BOLD, 18));
            FontMetrics fm = g2.getFontMetrics();
            g2.fillRect(20, y-20, fm.stringWidth(cm.text)+10, 24);
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.drawString(cm.text, 25, y - 2);
            y -= 30;
        }
    }

    private void drawScoreboard(Graphics2D g2) {
        if (!isMultiplayer || netClient == null) return;

        int startX = getWidth() - 250;
        int startY = 100;

        g2.setColor(new Color(0,0,0,180));
        g2.fillRoundRect(startX, startY, 230, 40 + (netClient.getRemotePositions().size() + 1)*30, 15, 15);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 18));
        g2.drawString("RACE STATUS", startX + 50, startY + 25);

        int y = startY + 55;

        g2.setFont(new Font("Arial", Font.BOLD, 16));
        g2.setColor(PLAYER_COLORS[chosenSkinIndex % PLAYER_COLORS.length]);
        g2.drawString(netClient.getPlayerName(), startX + 15, y);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.PLAIN, 14));
        g2.drawString(getStatusText(player.getAbilityFlags()), startX + 100, y);
        y += 30;

        for (Map.Entry<String, int[]> entry : netClient.getRemotePositions().entrySet()) {
            int[] data = entry.getValue();
            int sIdx  = data.length >= 3 ? data[2] : 0;
            int flags = data.length >= 4 ? data[3] : 0;

            g2.setFont(new Font("Arial", Font.BOLD, 16));
            g2.setColor(PLAYER_COLORS[sIdx % PLAYER_COLORS.length]);
            g2.drawString(entry.getKey(), startX + 15, y);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.PLAIN, 14));
            g2.drawString(getStatusText(flags), startX + 100, y);
            y += 30;
        }
    }

    private String getStatusText(int flags) {
        if ((flags & 32) != 0) return "Finished! \uD83D\uDC51";
        if ((flags &  8) != 0) return "Reversed \uD83D\uDE35\u200D\uD83D\uDCAB";
        if ((flags &  4) != 0) return "Slowed \uD83D\uDC0C";
        if ((flags & 16) != 0) return "Fast \u26A1";
        if ((flags &  2) != 0) return "Hidden \uD83D\uDC7B";
        if ((flags &  1) != 0) return "Shielded \uD83D\uDEE1\uFE0F";
        return "Running \uD83C\uDFC3";
    }

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
        else { g2.setColor(Color.DARK_GRAY); g2.fillRect(0, 0, getWidth(), getHeight()); }
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRect(0, 0, getWidth(), getHeight());
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
        g2.setFont(new Font("Arial", Font.BOLD, 72));   centered(g2, "Welcome to Grace Period", 220);
        g2.setFont(new Font("Arial", Font.ITALIC, 38)); centered(g2, "don't be late...", 285);

        String[] opts = { "> 1 Player <", "> Multiplayer <" };
        int[] ys = { 430, 510 };
        for (int i = 0; i < 2; i++) {
            g2.setFont(new Font("Arial", Font.BOLD, 40));
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(opts[i])) / 2;
            boolean sel = mainSel == i;
            if (sel && frameCount%60<30) g2.setColor(Color.YELLOW);
            else g2.setColor(sel ? Color.ORANGE : new Color(180, 180, 180));
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
        for (int i = 0; i < 2; i++) {
            g2.setFont(new Font("Arial", Font.BOLD, 40));
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(opts[i])) / 2;
            boolean sel = mpSel == i;
            if (sel && frameCount%60<30) g2.setColor(Color.YELLOW);
            else g2.setColor(sel ? Color.ORANGE : new Color(180, 180, 180));
            g2.drawString(opts[i], x, ys[i]);
        }
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        centered(g2, "[ \u2191 / \u2193 ] select    [ ENTER ] confirm    [ ESC ] back", 630);
    }

    private void drawNameEntry(Graphics2D g2) {
        drawBg(g2);
        String title = isCreatingServer ? "Create Server" : "Join Server - Step 1/2";
        g2.setFont(new Font("Arial", Font.BOLD, 58));   centered(g2, title, 210);
        g2.setFont(new Font("Arial", Font.PLAIN, 30));  centered(g2, "Enter your player name:", 340);
        drawInputBox(g2, nameInput.toString(), 390);
        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        centered(g2, "[ ENTER ] confirm    [ ESC ] back", 630);
        drawError(g2, 680);
    }

    private void drawHostEntry(Graphics2D g2) {
        drawBg(g2);
        g2.setFont(new Font("Arial", Font.BOLD, 58));  centered(g2, "Join Server - Step 2/2", 200);
        g2.setFont(new Font("Arial", Font.BOLD, 26));  centered(g2, "Name: " + nameInput, 310);
        g2.setFont(new Font("Arial", Font.PLAIN, 30)); centered(g2, "Enter server IP address:", 370);
        drawInputBox(g2, hostInput.toString(), 420);
        g2.setFont(new Font("Arial", Font.ITALIC, 22)); centered(g2, "(leave blank for localhost)", 510);
        g2.setFont(new Font("Arial", Font.PLAIN, 20));  centered(g2, "[ ENTER ] connect    [ ESC ] back", 630);
        drawError(g2, 680);
    }

    private void drawInputBox(Graphics2D g2, String text, int y) {
        int bw=500, bh=50, bx=(getWidth()-bw)/2;
        g2.setColor(new Color(0,0,0,160)); g2.fillRoundRect(bx, y-35, bw, bh, 10, 10);
        g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(2)); g2.drawRoundRect(bx, y-35, bw, bh, 10, 10);
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

    // ── LOBBY UPDATE: Renders Ready states ────────────────────────────────────
    private void drawLobby(Graphics2D g2, boolean isHost) {
        drawBg(g2);
        g2.setFont(new Font("Arial", Font.BOLD, 58)); centered(g2, "Multiplayer Lobby", 150);

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

        g2.setFont(new Font("Arial", Font.BOLD, 28)); centered(g2, "Players:", 250);

        int ly = 285;
        boolean allReady = true;

        for (int i = 0; i < lobbyList.size(); i++) {
            NetworkClient.LobbyPlayer lp = lobbyList.get(i);
            String pn = lp.name;
            if (!lp.isReady) allReady = false;

            boolean isH = netClient != null && pn.equals(netClient.getHostName());

            // ── ADDED: Display Ready Status ──
            String readyText = lp.isReady ? "  [READY]" : "  [NOT READY]";
            String label = "  " + pn + (isH ? "  [HOST]" : "") + readyText;

            g2.setFont(new Font("Arial", Font.BOLD, 26));
            FontMetrics fm = g2.getFontMetrics();
            int lx = (getWidth() - fm.stringWidth(label)) / 2;

            // Color the text Green if Ready, Red if not ready
            Color c = lp.isReady ? new Color(100, 255, 100) : new Color(255, 100, 100);

            g2.setColor(Color.BLACK); g2.drawString(label, lx+2, ly+2);
            g2.setColor(c);           g2.drawString(label, lx, ly);
            ly += 40;
        }

        g2.setFont(new Font("Arial", Font.BOLD, 22));
        centered(g2, "Your character:  [ Q ] prev    [ E ] next", 535);

        int spW = 64, spH = 64;
        int spX = getWidth() / 2 - spW / 2;
        int spY = 548;

        BufferedImage preview = (ghostSprites != null) ? ghostSprites[chosenSkinIndex][0] : null;
        if (preview != null) {
            g2.drawImage(preview, spX, spY, spW, spH, null);
        } else {
            g2.setColor(PLAYER_COLORS[chosenSkinIndex]);
            g2.fillRect(spX, spY, spW, spH);
        }

        g2.setColor(frameCount % 60 < 30 ? Color.YELLOW : Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(spX, spY, spW, spH);
        g2.setStroke(new BasicStroke(1));

        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        g2.setColor(Color.WHITE);
        centered(g2, SKIN_NAMES[chosenSkinIndex], 628);

        // ── ADDED: "Press R to Ready" instruction ──
        g2.setFont(new Font("Arial", Font.BOLD, 24));
        g2.setColor(new Color(150, 200, 255));
        centered(g2, "Press [ R ] to Toggle Ready", 665);

        if (isHost) {
            boolean canStart = lobbyList.size() >= 1 && allReady; // Must be ready!
            String btn = canStart ? "[ ENTER ] Start Game" : "Waiting for all players to be READY...";
            g2.setFont(new Font("Arial", Font.BOLD, 34));
            FontMetrics fm = g2.getFontMetrics();
            int bx = (getWidth() - fm.stringWidth(btn)) / 2;
            g2.setColor(canStart && frameCount%60<40 ? Color.YELLOW : new Color(180,180,180));
            g2.drawString(btn, bx, 715);
        } else {
            g2.setFont(new Font("Arial", Font.ITALIC, 28));
            centered(g2, "Waiting for host to start the game...", 715);
        }

        g2.setFont(new Font("Arial", Font.PLAIN, 20));
        centered(g2, "[ ESC ] disconnect & return to menu", 750);

        drawError(g2, 780);
    }

    private void drawHUD(Graphics2D g2) {
        long s = timeMillisLeft / 1000;
        String ts = String.format("%d:%02d", s/60, s%60);
        boolean urg = s < 30;
        g2.setFont(new Font("Arial", Font.BOLD, 48));
        FontMetrics fm = g2.getFontMetrics();
        int tw=fm.stringWidth(ts), th=fm.getHeight();
        int bw=tw+40, bh=th+16, bx=(getWidth()-bw)/2, by=20;

        if (System.currentTimeMillis() < obleFreezeEndTime) {
            g2.setColor(new Color(0, 100, 255, 180));
        } else {
            g2.setColor(urg ? new Color(160,0,0,220) : new Color(0,0,0,180));
        }

        g2.fillRoundRect(bx,by,bw,bh,20,20);
        g2.setStroke(new BasicStroke(2));
        g2.setColor(urg ? Color.RED : new Color(255,255,255,80));
        g2.drawRoundRect(bx, by, bw, bh, 20, 20);
        g2.setColor(urg ? Color.YELLOW : Color.WHITE);
        g2.drawString(ts, bx+20, by+th);
    }

    private void drawOverlay(Graphics2D g2, String l1, String l2, Color col) {
        g2.setColor(new Color(0,0,0,190)); g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setFont(new Font("Arial", Font.BOLD, 68)); g2.setColor(col);
        centered(g2, l1, getHeight()/2 - 40);
        g2.setFont(new Font("Arial", Font.BOLD, 44)); g2.setColor(Color.WHITE);
        centered(g2, l2, getHeight()/2 + 30);
        g2.setFont(new Font("Arial", Font.PLAIN, 22)); g2.setColor(new Color(180,180,180));
        centered(g2, "Press [ ENTER ] to return to menu", getHeight()/2 + 90);
    }
}