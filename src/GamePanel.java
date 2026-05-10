import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

// [MULTIPLAYER]
// import java.io.*;
// import java.net.Socket;
// import java.util.HashMap;
// import java.util.Map;

public class GamePanel extends JPanel implements ActionListener {
    MapManager mapM;
    AbilityManager abilityM;
    Timer timer;
    Player player;

    final double LOGICAL_WIDTH = 101 * 40.0;
    final double LOGICAL_HEIGHT = 61 * 40.0;

    long timeMillisLeft = 60_000;
    long lastUpdateTime = -1;

    // Panel-level UPLB Powerup Timers
    public long carillonZoomEndTime = 0;
    public long obleFreezeEndTime = 0;

    enum State { MAIN_MENU, SHOW_MAP, ZOOMING, PLAYING, FINISHED, GAMEOVER }
    State currentState = State.MAIN_MENU;

    int frameCount = 0;
    double currentScaleX = 1.0;
    double currentScaleY = 1.0;
    double camX = 0, camY = 0;
    final double TARGET_ZOOM = 2.5;

    private BufferedImage bgImage;

    // [MULTIPLAYER]
    // Socket socket;
    // PrintWriter serverOut;
    // BufferedReader serverIn;
    // int myPlayerID = -1;
    // Map<Integer, Player> remotePlayers = new HashMap<>();

    public GamePanel() {
        this.setPreferredSize(new Dimension(1280, 800));
        this.setBackground(new Color(34, 139, 34));
        this.setFocusable(true);

        mapM = new MapManager(MazeGenerator.generateMap());
        player = new Player((50 * 40) + 5, (49 * 40) + 5, Color.RED, Color.BLACK, "P1");

        // Pass 'this' so AbilityManager can access Panel timers
        abilityM = new AbilityManager(mapM.mapLayout, this);

        try { bgImage = ImageIO.read(getClass().getResourceAsStream("/background.jpg")); }
        catch (Exception e) {}

        setupKeyBindings();

        timer = new Timer(16, this);
        timer.start();

        // [MULTIPLAYER] connectToServer();
    }

    /*
    // [MULTIPLAYER]
    private void connectToServer() {
        try {
            socket = new Socket("localhost", 4444);
            serverOut = new PrintWriter(socket.getOutputStream(), true);
            serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // Thread to listen for incoming packets...
        } catch (Exception e) { System.out.println("Running Offline"); }
    }
    */

    private void setupKeyBindings() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, false), "upOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false), "upOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false), "downOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false), "downOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, false), "leftOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "leftOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, false), "rightOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "rightOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "dashOn");

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, true), "upOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true), "upOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true), "downOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, true), "downOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true), "leftOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true), "leftOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "rightOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true), "rightOff");

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "enter");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "escape");

        am.put("upOn", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.upPressed = true; }});
        am.put("downOn", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.downPressed = true; }});
        am.put("leftOn", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.leftPressed = true; }});
        am.put("rightOn", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.rightPressed = true; }});
        am.put("dashOn", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.dashPressed = true; }});

        am.put("upOff", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.upPressed = false; }});
        am.put("downOff", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.downPressed = false; }});
        am.put("leftOff", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.leftPressed = false; }});
        am.put("rightOff", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.rightPressed = false; }});

        am.put("enter", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (currentState == State.MAIN_MENU) {
                    currentState = State.SHOW_MAP;
                    frameCount = 0;
                }
            }
        });
        am.put("escape", new AbstractAction() { public void actionPerformed(ActionEvent e) { System.exit(0); }});
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        update();
        repaint();
    }

    private void update() {
        if (currentState == State.MAIN_MENU) frameCount++;
        else if (currentState == State.SHOW_MAP) {
            frameCount++;
            if (frameCount > 180) { currentState = State.ZOOMING; frameCount = 0; }
        }
        else if (currentState == State.ZOOMING) {
            frameCount++;
            double progress = Math.sin(((double) frameCount / 120) * Math.PI / 2);
            double startScaleX = getWidth() / LOGICAL_WIDTH;
            double startScaleY = getHeight() / LOGICAL_HEIGHT;

            currentScaleX = startScaleX + (TARGET_ZOOM - startScaleX) * progress;
            currentScaleY = startScaleY + (TARGET_ZOOM - startScaleY) * progress;
            camX = (getWidth() / 2.0) - (player.x + player.size / 2.0) * TARGET_ZOOM * progress;
            camY = (getHeight() / 2.0) - (player.y + player.size / 2.0) * TARGET_ZOOM * progress;

            if (frameCount >= 120) {
                currentState = State.PLAYING;
                lastUpdateTime = System.currentTimeMillis();
            }
        }
        else if (currentState == State.PLAYING) {
            long now = System.currentTimeMillis();
            long delta = now - lastUpdateTime;
            lastUpdateTime = now;

            // OBLE POWERUP: Stop the timer
            if (now > obleFreezeEndTime) {
                timeMillisLeft -= delta;
            }

            if (timeMillisLeft <= 0) {
                timeMillisLeft = 0;
                currentState = State.GAMEOVER;
                return;
            }

            // [MULTIPLAYER] Send inputs to server instead of moving locally
            // if (player.upPressed) serverOut.println("MOVE:UP");
            player.update(mapM.mapLayout);
            abilityM.update(player);

            // CARILLON POWERUP: Zoom out
            double activeZoom = (now < carillonZoomEndTime) ? 0.8 : TARGET_ZOOM;

            currentScaleX = activeZoom;
            currentScaleY = activeZoom;
            camX = (getWidth() / 2.0) - (player.x + player.size / 2.0) * activeZoom;
            camY = (getHeight() / 2.0) - (player.y + player.size / 2.0) * activeZoom;

            if (mapM.mapLayout[player.getCenterRow()][player.getCenterCol()] == 2) {
                currentState = State.FINISHED;
            }
        }
    }

    private void drawTextWithShadow(Graphics2D g2, String text, int x, int y) {
        g2.setColor(Color.BLACK);
        g2.drawString(text, x + 3, y + 3);
        g2.setColor(Color.WHITE);
        g2.drawString(text, x, y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (currentState == State.MAIN_MENU) {
            if (bgImage != null) g2.drawImage(bgImage, 0, 0, getWidth(), getHeight(), null);
            else { g2.setColor(Color.DARK_GRAY); g2.fillRect(0, 0, getWidth(), getHeight()); }
            g2.setColor(new Color(0, 0, 0, 100)); g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setFont(new Font("Arial", Font.BOLD, 75));
            drawTextWithShadow(g2, "Welcome to Grace Period", (getWidth() - g2.getFontMetrics().stringWidth("Welcome to Grace Period")) / 2, 250);

            g2.setFont(new Font("Arial", Font.ITALIC, 45));
            drawTextWithShadow(g2, "don't be late...", (getWidth() - g2.getFontMetrics().stringWidth("don't be late...")) / 2, 320);

            g2.setFont(new Font("Arial", Font.BOLD, 40));
            if (frameCount % 60 < 30) {
                g2.setColor(Color.YELLOW);
                g2.drawString("> 1 Player <", (getWidth() - g2.getFontMetrics().stringWidth("> 1 Player <")) / 2, 550);
            }
            return;
        }

        AffineTransform oldTransform = g2.getTransform();
        AffineTransform cameraTransform = new AffineTransform();

        if (currentState == State.SHOW_MAP) {
            cameraTransform.scale(0.7, 0.7);
        } else {
            cameraTransform.translate(camX, camY);
            cameraTransform.scale(currentScaleX, currentScaleY);
        }

        g2.setTransform(cameraTransform);

        mapM.draw(g2);
        abilityM.draw(g2);
        player.draw(g2);

        // [MULTIPLAYER]
        // for(Player remotePlayer : remotePlayers.values()) remotePlayer.draw(g2);

        g2.setTransform(oldTransform);

        if (currentState == State.PLAYING) {
            long secondsLeft = timeMillisLeft / 1000;
            String timeStr = String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60);
            boolean urgent = secondsLeft < 30;

            g2.setFont(new Font("Arial", Font.BOLD, 48));
            int tw = g2.getFontMetrics().stringWidth(timeStr);
            int boxX = (getWidth() - (tw + 40)) / 2;

            if (System.currentTimeMillis() < obleFreezeEndTime) g2.setColor(new Color(0, 100, 255, 180));
            else g2.setColor(urgent ? new Color(160, 0, 0, 220) : new Color(0, 0, 0, 180));

            g2.fillRoundRect(boxX, 20, tw + 40, g2.getFontMetrics().getHeight() + 16, 20, 20);
            g2.setStroke(new BasicStroke(2));
            g2.setColor(urgent ? Color.RED : new Color(255, 255, 255, 80));
            g2.drawRoundRect(boxX, 20, tw + 40, g2.getFontMetrics().getHeight() + 16, 20, 20);
            g2.setColor(urgent ? Color.YELLOW : Color.WHITE);
            g2.drawString(timeStr, boxX + 20, 20 + g2.getFontMetrics().getHeight());
        }

        if (currentState == State.FINISHED || currentState == State.GAMEOVER) {
            g2.setColor(new Color(0, 0, 0, 200));
            g2.fillRect(0, 0, getWidth(), getHeight());

            boolean won = (currentState == State.FINISHED);
            g2.setColor(won ? Color.YELLOW : Color.RED);
            g2.setFont(new Font("Arial", Font.BOLD, 65));
            String msg1 = won ? "YOU REACHED THE GOAL!" : "YOU LOSE.";
            g2.drawString(msg1, (getWidth() - g2.getFontMetrics().stringWidth(msg1)) / 2, getHeight() / 2 - 40);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 45));
            String msg2 = won ? "Final Grade: 1.0" : "Final Grade: 5.0";
            g2.drawString(msg2, (getWidth() - g2.getFontMetrics().stringWidth(msg2)) / 2, getHeight() / 2 + 30);
        }
    }
}