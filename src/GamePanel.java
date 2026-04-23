import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class GamePanel extends JPanel implements ActionListener {
    MapManager mapM;
    Timer timer;

    // Maze size
    final double LOGICAL_WIDTH = 101 * 40.0;
    final double LOGICAL_HEIGHT = 61 * 40.0;

    // Spawn in the middle of the GATE
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

        // Spawn player in the center of the Gate tile (tile 3)
        player = new Player(
            (50 * 40) + 5,   // x: middle column, nudged 5px
            (58 * 40) + 5,   // y: bottom area gate row, nudged 5px
            Color.RED,
            Color.BLACK,
            "P1"
        );

        try {
            bgImage = ImageIO.read(getClass().getResourceAsStream("/background.jpg"));
        } catch (Exception e) {
            System.out.println("Notice: background.jpg not found in 'res' folder.");
        }

        //Movement
        this.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();

                if (currentState == State.MAIN_MENU) {
                    if (code == KeyEvent.VK_ENTER) {
                        currentState = State.SHOW_MAP;
                        frameCount = 0;
                    }
                    if (code == KeyEvent.VK_ESCAPE) System.exit(0);
                    return;
                }
                // Route key inputs to the player
                if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP)    player.upPressed    = true;
                if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN)  player.downPressed  = true;
                if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT)  player.leftPressed  = true;
                if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) player.rightPressed = true;
                // DASH
                if (code == KeyEvent.VK_SPACE) player.dashPressed = true;
                if (code == KeyEvent.VK_ESCAPE) System.exit(0);
            }
            public void keyReleased(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP)    player.upPressed    = false;
                if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN)  player.downPressed  = false;
                if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT)  player.leftPressed  = false;
                if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) player.rightPressed = false;
            }
        });

        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        update();
        repaint();
    }

    //User View
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
            // Delegate movement + collision to the Player class
            player.update(mapM.mapLayout);
 
            // Camera follows player
            currentScaleX = TARGET_ZOOM;
            currentScaleY = TARGET_ZOOM;
            camX = (getWidth()  / 2.0) - (player.x + player.size / 2.0) * TARGET_ZOOM;
            camY = (getHeight() / 2.0) - (player.y + player.size / 2.0) * TARGET_ZOOM;
 
            // Win condition: player center is on Physci tile (2)
            int col = player.getCenterCol();
            int row = player.getCenterRow();
            if (row >= 0 && row < mapM.mapLayout.length &&
                col >= 0 && col < mapM.mapLayout[0].length) {
                if (mapM.mapLayout[row][col] == 2) {
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

        // Draw Maze
        mapM.draw(g2);

        // Draw Player
        player.draw(g2);

        g2.setTransform(oldTransform);

        if (currentState == State.FINISHED) {
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("Arial", Font.BOLD, 65));
            FontMetrics fm = g2.getFontMetrics();
            String text = "Final Grade:1.0";
            int txtX = (getWidth() - fm.stringWidth(text)) / 2;
            g2.drawString(text, txtX, getHeight() / 2);
        }
    }
}