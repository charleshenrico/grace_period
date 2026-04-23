import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

public class Player {

    // Position and size
    public int x, y;
    public final int size = 30;
    public final int speed = 5;

    // Input flags
    public boolean upPressed, downPressed, leftPressed, rightPressed;
    public boolean dashPressed;

    // Visual identity
    private Color fillColor;
    private Color borderColor;
    public String name;

    // --- Dash System ---
    private static final int MAX_DASH_BARS       = 3;
    private static final int DASH_DISTANCE       = 120;
    private static final int DASH_SPEED          = 20;
    private static final int DASH_RECHARGE_TICKS = 180;

    private int dashBars = MAX_DASH_BARS;

    // Each entry is an independent timer for one spent dash.
    // Highest timer = spent earliest = recharges first.
    private ArrayList<Integer> rechargeQueue = new ArrayList<>();

    private boolean isDashing = false;
    private int dashRemaining = 0;
    private int dashDirX = 0;
    private int dashDirY = 0;

    // Last known movement direction — default: facing down
    private int lastDirX = 0;
    private int lastDirY = 1;

    public Player(int startX, int startY, Color fillColor, Color borderColor, String name) {
        this.x           = startX;
        this.y           = startY;
        this.fillColor   = fillColor;
        this.borderColor = borderColor;
        this.name        = name;
    }

    public void update(int[][] mapLayout) {

        // Tick only the furthest-along recharge timer (highest value)
        if (!rechargeQueue.isEmpty()) {
            rechargeQueue.sort(Collections.reverseOrder());
            rechargeQueue.set(0, rechargeQueue.get(0) + 1);
            if (rechargeQueue.get(0) >= DASH_RECHARGE_TICKS) {
                rechargeQueue.remove(0);
                dashBars++;
            }
        }

        // Initiate dash on space press
        if (dashPressed && !isDashing && dashBars > 0) {
            isDashing     = true;
            dashRemaining = DASH_DISTANCE;
            dashBars--;

            dashDirX = 0;
            dashDirY = 0;
            if (upPressed)    dashDirY = -1;
            if (downPressed)  dashDirY =  1;
            if (leftPressed)  dashDirX = -1;
            if (rightPressed) dashDirX =  1;
            if (dashDirX == 0 && dashDirY == 0) {
                dashDirX = lastDirX;
                dashDirY = lastDirY;
            }

            rechargeQueue.add(0);
            dashPressed = false;
        }

        // Execute dash
        if (isDashing) {
            int step  = Math.min(DASH_SPEED, dashRemaining);
            int moved = moveDash(step, mapLayout);
            dashRemaining -= moved;
            if (dashRemaining <= 0 || moved == 0) {
                isDashing     = false;
                dashRemaining = 0;
            }
            return;
        }

        // Normal movement + track last direction
        if (upPressed) {
            y -= speed;
            if (isColliding(x, y, mapLayout)) y += speed;
            else { lastDirX = 0; lastDirY = -1; }
        }
        if (downPressed) {
            y += speed;
            if (isColliding(x, y, mapLayout)) y -= speed;
            else { lastDirX = 0; lastDirY = 1; }
        }
        if (leftPressed) {
            x -= speed;
            if (isColliding(x, y, mapLayout)) x += speed;
            else { lastDirX = -1; lastDirY = 0; }
        }
        if (rightPressed) {
            x += speed;
            if (isColliding(x, y, mapLayout)) x -= speed;
            else { lastDirX = 1; lastDirY = 0; }
        }
    }

    private int moveDash(int step, int[][] mapLayout) {
        int moved = 0;
        for (int i = 0; i < step; i++) {
            int nextX = x + dashDirX;
            int nextY = y + dashDirY;
            if (isColliding(nextX, nextY, mapLayout)) break;
            x = nextX;
            y = nextY;
            moved++;
        }
        return moved;
    }

    public int getCenterCol() { return (x + size / 2) / 40; }
    public int getCenterRow() { return (y + size / 2) / 40; }

    private boolean isColliding(int px, int py, int[][] mapLayout) {
        int leftCol   = px / 40;
        int rightCol  = (px + size - 1) / 40;
        int topRow    = py / 40;
        int bottomRow = (py + size - 1) / 40;

        if (leftCol < 0 || rightCol >= mapLayout[0].length ||
            topRow  < 0 || bottomRow >= mapLayout.length) return true;

        int tl = mapLayout[topRow][leftCol];
        int tr = mapLayout[topRow][rightCol];
        int bl = mapLayout[bottomRow][leftCol];
        int br = mapLayout[bottomRow][rightCol];

        return tl == 6 || tl == 1 || tr == 6 || tr == 1 ||
               bl == 6 || bl == 1 || br == 6 || br == 1;
    }

    public void draw(Graphics2D g2) {
        // Player body
        g2.setColor(isDashing ? fillColor.brighter() : fillColor);
        g2.fillRect(x, y, size, size);
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(x, y, size, size);

        // Name label above player
        if (name != null && !name.isEmpty()) {
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int lx = x + (size - fm.stringWidth(name)) / 2;
            int ly = y - 5;
            g2.setColor(Color.BLACK);
            g2.drawString(name, lx + 1, ly + 1);
            g2.setColor(Color.WHITE);
            g2.drawString(name, lx, ly);
        }

        // --- Single unified dash bar ---
        // The bar is split into thirds by divider lines.
        // Each third represents one dash charge.
        // Filled (bright blue) = charged, partial (dark blue) = recharging, dark = empty.
        int barW  = size + 10;  // slightly wider than player
        int barH  = 6;
        int barX  = x + (size - barW) / 2;
        int barY  = y + size + 6;
        int segW  = barW / MAX_DASH_BARS;

        // Background
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(barX, barY, barW, barH);

        // Fully charged segments
        for (int i = 0; i < dashBars; i++) {
            g2.setColor(new Color(80, 180, 255));
            g2.fillRect(barX + i * segW, barY, segW, barH);
        }

        // Partial recharge on the next empty segment
        // Use the highest timer in the queue (closest to done)
        if (!rechargeQueue.isEmpty()) {
            int highest  = Collections.max(rechargeQueue);
            int partialW = (int)((double) highest / DASH_RECHARGE_TICKS * segW);
            g2.setColor(new Color(40, 100, 180));
            g2.fillRect(barX + dashBars * segW, barY, partialW, barH);
        }

        // Divider lines between thirds
        g2.setColor(new Color(30, 120, 200));
        for (int i = 1; i < MAX_DASH_BARS; i++) {
            g2.fillRect(barX + i * segW, barY, 1, barH);
        }

        // Outline
        g2.setColor(new Color(30, 120, 200));
        g2.setStroke(new BasicStroke(1));
        g2.drawRect(barX, barY, barW, barH);
    }
}