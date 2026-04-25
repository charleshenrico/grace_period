import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class GamePanel extends JPanel implements ActionListener {
    MapManager mapM;
    AbilityManager abilityM;
    Timer timer;

    final double LOGICAL_WIDTH = 101 * 40.0;
    final double LOGICAL_HEIGHT = 61 * 40.0;

    // Player is now delegated to its own class (with built-in dash mechanic)
    Player player;

    enum State { MAIN_MENU, SHOW_MAP, ZOOMING, PLAYING, FINISHED }
    State currentState = State.MAIN_MENU;

    int frameCount = 0;
    double currentScaleX = 1.0;
    double currentScaleY = 1.0;
    double camX = 0, camY = 0;
    final double TARGET_ZOOM = 2.5;

    private BufferedImage bgImage;

    public GamePanel() {
        this.setPreferredSize(new Dimension(1280, 800));
        this.setBackground(new Color(34, 139, 34));
        this.setFocusable(true);

        mapM = new MapManager(MazeGenerator.generateMap());

        // Spawn player inside the GATE (tile 3) at bottom-middle of the map
        player = new Player(
                (50 * 40) + 5,
                (57 * 40) + 5,
                Color.RED,
                Color.BLACK,
                "P1"
        );

        // Spawn ability pickups on random walkable path tiles
        abilityM = new AbilityManager(mapM.mapLayout);

        try {
            bgImage = ImageIO.read(getClass().getResourceAsStream("/background.jpg"));
        } catch (Exception e) {
            System.out.println("Notice: background.jpg not found in 'res' folder.");
        }

        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, false),      "upOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false),     "upOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false),      "downOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false),   "downOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, false),      "leftOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false),   "leftOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, false),      "rightOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false),  "rightOn");

        // DASH key
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false),  "dashOn");

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, true),       "upOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true),      "upOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true),       "downOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, true),    "downOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true),       "leftOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true),    "leftOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true),       "rightOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true),   "rightOff");

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false),  "enter");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "escape");

        am.put("upOn",    new AbstractAction() { public void actionPerformed(ActionEvent e) { player.upPressed = true; } });
        am.put("upOff",   new AbstractAction() { public void actionPerformed(ActionEvent e) { player.upPressed = false; } });
        am.put("downOn",  new AbstractAction() { public void actionPerformed(ActionEvent e) { player.downPressed = true; } });
        am.put("downOff", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.downPressed = false; } });
        am.put("leftOn",  new AbstractAction() { public void actionPerformed(ActionEvent e) { player.leftPressed = true; } });
        am.put("leftOff", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.leftPressed = false; } });
        am.put("rightOn", new AbstractAction() { public void actionPerformed(ActionEvent e) { player.rightPressed = true; } });
        am.put("rightOff",new AbstractAction() { public void actionPerformed(ActionEvent e) { player.rightPressed = false; } });
        am.put("dashOn",  new AbstractAction() { public void actionPerformed(ActionEvent e) { player.dashPressed = true; } });

        am.put("enter", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (currentState == State.MAIN_MENU) {
                    currentState = State.SHOW_MAP;
                    frameCount = 0;
                }
            }
        });
        am.put("escape", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        timer = new Timer(16, this);
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        update();
        repaint();
    }

    private void update() {
        if (currentState == State.MAIN_MENU) {
            frameCount++;
        }
        else if (currentState == State.SHOW_MAP) {
            frameCount++;
            if (frameCount > 180) {
                currentState = State.ZOOMING;
                frameCount = 0;
            }
        }
        else if (currentState == State.ZOOMING) {
            frameCount++;
            int zoomDuration = 120;

            double t = (double) frameCount / zoomDuration;
            double progress = Math.sin(t * Math.PI / 2);

            double startScaleX = getWidth() / LOGICAL_WIDTH;
            double startScaleY = getHeight() / LOGICAL_HEIGHT;

            currentScaleX = startScaleX + (TARGET_ZOOM - startScaleX) * progress;
            currentScaleY = startScaleY + (TARGET_ZOOM - startScaleY) * progress;

            double targetCamX = (getWidth() / 2.0) - (player.x + player.size / 2.0) * TARGET_ZOOM;
            double targetCamY = (getHeight() / 2.0) - (player.y + player.size / 2.0) * TARGET_ZOOM;

            camX = targetCamX * progress;
            camY = targetCamY * progress;

            if (frameCount >= zoomDuration) {
                currentState = State.PLAYING;
            }
        }
        else if (currentState == State.PLAYING) {
            // Delegate movement, dash, and collision to the Player class
            player.update(mapM.mapLayout);

            // Tick ability pickups (handles collision-with-player + respawn timers)
            abilityM.update(player);

            // Camera follows player
            currentScaleX = TARGET_ZOOM;
            currentScaleY = TARGET_ZOOM;
            camX = (getWidth() / 2.0) - (player.x + player.size / 2.0) * TARGET_ZOOM;
            camY = (getHeight() / 2.0) - (player.y + player.size / 2.0) * TARGET_ZOOM;

            // Win condition — player overlaps any PhySci tile (type 2)
            int centerCol = player.getCenterCol();
            int centerRow = player.getCenterRow();
            if (centerRow >= 0 && centerRow < mapM.mapLayout.length &&
                    centerCol >= 0 && centerCol < mapM.mapLayout[0].length) {
                if (mapM.mapLayout[centerRow][centerCol] == 2) {
                    currentState = State.FINISHED;
                }
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
            if (bgImage != null) {
                g2.drawImage(bgImage, 0, 0, getWidth(), getHeight(), null);
            } else {
                g2.setColor(Color.DARK_GRAY);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRect(0, 0, getWidth(), getHeight());

            FontMetrics fm;

            g2.setFont(new Font("Arial", Font.BOLD, 75));
            fm = g2.getFontMetrics();
            String title = "Welcome to Grace Period";
            int titleX = (getWidth() - fm.stringWidth(title)) / 2;
            drawTextWithShadow(g2, title, titleX, 250);

            g2.setFont(new Font("Arial", Font.ITALIC, 45));
            fm = g2.getFontMetrics();
            String subtitle = "don't be late...";
            int subX = (getWidth() - fm.stringWidth(subtitle)) / 2;
            drawTextWithShadow(g2, subtitle, subX, 320);

            g2.setFont(new Font("Arial", Font.BOLD, 40));
            fm = g2.getFontMetrics();
            String option = "> 1 Player <";
            int optX = (getWidth() - fm.stringWidth(option)) / 2;

            if (frameCount % 60 < 30) {
                g2.setColor(Color.YELLOW);
                g2.drawString(option, optX, 550);
            }

            g2.setFont(new Font("Arial", Font.PLAIN, 20));
            fm = g2.getFontMetrics();
            String hint = "Press [ ENTER ] to start";
            int hintX = (getWidth() - fm.stringWidth(hint)) / 2;
            drawTextWithShadow(g2, hint, hintX, 600);

            return;
        }

        AffineTransform oldTransform = g2.getTransform();
        AffineTransform cameraTransform = new AffineTransform();

        if (currentState == State.SHOW_MAP) {
            double myCustomZoom = 0.7;
            cameraTransform.scale(myCustomZoom, myCustomZoom);
        } else {
            cameraTransform.translate(camX, camY);
            cameraTransform.scale(currentScaleX, currentScaleY);
        }

        g2.setTransform(cameraTransform);

        mapM.draw(g2);

        // Ability pickups in the world (under the player)
        abilityM.draw(g2);

        // Player draws itself + its dash bar UI
        player.draw(g2);

        g2.setTransform(oldTransform);

        if (currentState == State.FINISHED) {
            // Dark overlay
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, getWidth(), getHeight());

            FontMetrics fm;

            // "YOU REACHED THE GOAL!"
            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("Arial", Font.BOLD, 65));
            fm = g2.getFontMetrics();
            String line1 = "YOU REACHED THE GOAL!";
            int x1 = (getWidth() - fm.stringWidth(line1)) / 2;
            g2.drawString(line1, x1, getHeight() / 2 - 40);

            // "Final Grade: 1.0"
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 45));
            fm = g2.getFontMetrics();
            String line2 = "Final Grade: 1.0";
            int x2 = (getWidth() - fm.stringWidth(line2)) / 2;
            g2.drawString(line2, x2, getHeight() / 2 + 30);
        }
    }
}
