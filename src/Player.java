import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

public class Player {

    public int x, y;
    public final int size = 30;

    private static final int BASE_SPEED = 5;
    private static final int SLOW_SPEED = 2;
    public int speed = BASE_SPEED;

    public boolean upPressed, downPressed, leftPressed, rightPressed, dashPressed;
    private Color fillColor, borderColor;
    public String name;

    // --- UPLB Powerup Timers ---
    public long towerSpeedEndTime = 0;
    public long libraryGhostEndTime = 0;

    // --- Dash System ---
    private static final int MAX_DASH_BARS       = 3;
    private static final int DASH_DISTANCE       = 120;
    private static final int DASH_SPEED          = 20;
    private static final int DASH_RECHARGE_TICKS = 180;
    private int dashBars = MAX_DASH_BARS;
    private ArrayList<Integer> rechargeQueue = new ArrayList<>();
    private boolean isDashing = false;
    private int dashRemaining = 0;
    private int dashDirX = 0, dashDirY = 0;
    private int lastDirX = 0, lastDirY = 1;

    // --- Ability state ---
    private static final int INVISIBILITY_TICKS = 300;
    private static final int SLOW_TICKS         = 300;
    private static final int SHIELD_TICKS       = 600;

    private int invisibleTicks = 0;
    private int slowedTicks    = 0;
    private int shieldTicks    = 0;

    public Player(int startX, int startY, Color fillColor, Color borderColor, String name) {
        this.x = startX;
        this.y = startY;
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        this.name = name;
    }

    public void update(int[][] mapLayout) {
        long now = System.currentTimeMillis();

        // ==========================================
        // SMART GHOST ESCAPE LOGIC
        // If the powerup timer is officially over, but we are still inside a wall...
        // ==========================================
        if (now > libraryGhostEndTime && libraryGhostEndTime != 0) {
            if (isPhysicallyInWall(this.x, this.y, mapLayout)) {
                // We are stuck! Secretly keep ghost mode alive for 0.1s so we can walk out
                libraryGhostEndTime = now + 100;
            } else {
                // We are safely standing on a path. Turn off ghost mode completely.
                libraryGhostEndTime = 0;
            }
        }

        // --- Standard Timers ---
        if (invisibleTicks > 0) invisibleTicks--;
        if (slowedTicks    > 0) slowedTicks--;
        if (shieldTicks    > 0) shieldTicks--;

        // Determine active speed (Priority: Slow Trap -> Tower Boost -> Base)
        if (slowedTicks > 0) speed = SLOW_SPEED;
        else if (now < towerSpeedEndTime) speed = BASE_SPEED * 2;
        else speed = BASE_SPEED;

        // Dash Recharge
        if (slowedTicks == 0 && !rechargeQueue.isEmpty()) {
            rechargeQueue.sort(Collections.reverseOrder());
            rechargeQueue.set(0, rechargeQueue.get(0) + 1);
            if (rechargeQueue.get(0) >= DASH_RECHARGE_TICKS) {
                rechargeQueue.remove(0);
                dashBars++;
            }
        }

        // Execute Dash
        if (dashPressed && !isDashing && dashBars > 0 && slowedTicks == 0) {
            isDashing     = true;
            dashRemaining = DASH_DISTANCE;
            dashBars--;

            dashDirX = 0; dashDirY = 0;
            if (upPressed)    dashDirY = -1;
            if (downPressed)  dashDirY =  1;
            if (leftPressed)  dashDirX = -1;
            if (rightPressed) dashDirX =  1;
            if (dashDirX == 0 && dashDirY == 0) { dashDirX = lastDirX; dashDirY = lastDirY; }

            rechargeQueue.add(0);
        }
        dashPressed = false;

        if (isDashing) {
            int step  = Math.min(DASH_SPEED, dashRemaining);
            int moved = moveDash(step, mapLayout);
            dashRemaining -= moved;
            if (dashRemaining <= 0 || moved == 0) {
                isDashing = false;
                dashRemaining = 0;
            }
            return;
        }

        // Normal Movement
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

    // STRICT PHYSICAL CHECK: Are we touching a wall tile?
    private boolean isPhysicallyInWall(int px, int py, int[][] mapLayout) {
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

        // 1 = Tree, 6 = Wall
        return tl == 6 || tl == 1 || tr == 6 || tr == 1 ||
                bl == 6 || bl == 1 || br == 6 || br == 1;
    }

    // MAIN COLLISION CHECK
    private boolean isColliding(int px, int py, int[][] mapLayout) {
        // If the Library Ghost timer is active (or being kept alive so we can escape), ignore walls!
        if (System.currentTimeMillis() < libraryGhostEndTime) {
            return false;
        }

        // Otherwise, act like a normal physical object
        return isPhysicallyInWall(px, py, mapLayout);
    }

    public void applyStaminaBoost() { dashBars = MAX_DASH_BARS; rechargeQueue.clear(); }
    public void applyInvisibility() { invisibleTicks = INVISIBILITY_TICKS; }
    public void applyShield() {
        if (slowedTicks > 0) { slowedTicks = 0; shieldTicks = 0; return; }
        shieldTicks = SHIELD_TICKS;
    }
    public void applySlowDown() {
        if (shieldTicks > 0) { shieldTicks = 0; return; }
        slowedTicks = SLOW_TICKS;
    }
    public boolean isInvisible()    { return invisibleTicks > 0; }
    public boolean isShieldActive() { return shieldTicks > 0; }
    public boolean isSlowed()       { return slowedTicks > 0; }

    public void draw(Graphics2D g2) {
        java.awt.Composite oldComposite = g2.getComposite();

        // Ghost rendering priority: Library Ghost Mode overrides normal invisibility opacity
        if (System.currentTimeMillis() < libraryGhostEndTime) {
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.40f));
        } else if (isInvisible()) {
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.30f));
        }

        Color body = fillColor;
        if (isDashing) body = body.brighter();
        if (isSlowed()) body = new Color(120, 160, 220);

        g2.setColor(body);
        g2.fillRect(x, y, size, size);
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(x, y, size, size);

        g2.setComposite(oldComposite);

        if (isShieldActive()) {
            g2.setColor(new Color(120, 200, 230));
            g2.setStroke(new BasicStroke(3));
            g2.drawOval(x - 5, y - 5, size + 10, size + 10);
            g2.setStroke(new BasicStroke(1));
            drawLabel(g2, "SHIELD " + ((shieldTicks + 59) / 60) + "s", y - 18, new Color(120, 200, 230));
        }

        if (isSlowed()) drawLabel(g2, "SLOW " + ((slowedTicks + 59) / 60) + "s", y - 18, new Color(120, 160, 220));
        if (isInvisible()) drawLabel(g2, "HIDDEN " + ((invisibleTicks + 59) / 60) + "s", y - 30, new Color(180, 230, 0));

        if (name != null && !name.isEmpty()) drawLabel(g2, name, y - 5, Color.WHITE);

        drawDashBar(g2);
    }

    private void drawLabel(Graphics2D g2, String text, int ty, Color c) {
        g2.setFont(new Font("Arial", Font.BOLD, 10));
        int tx = x + (size - g2.getFontMetrics().stringWidth(text)) / 2;
        g2.setColor(Color.BLACK); g2.drawString(text, tx + 1, ty + 1);
        g2.setColor(c); g2.drawString(text, tx, ty);
    }

    private void drawDashBar(Graphics2D g2) {
        int barW  = size + 10;
        int barX  = x + (size - barW) / 2;
        int barY  = y + size + 6;
        int segW  = barW / MAX_DASH_BARS;

        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(barX, barY, barW, 6);

        for (int i = 0; i < dashBars; i++) {
            g2.setColor(new Color(80, 180, 255));
            g2.fillRect(barX + i * segW, barY, segW, 6);
        }

        if (!rechargeQueue.isEmpty() && slowedTicks == 0) {
            int partialW = (int)((double) Collections.max(rechargeQueue) / DASH_RECHARGE_TICKS * segW);
            g2.setColor(new Color(40, 100, 180));
            g2.fillRect(barX + dashBars * segW, barY, partialW, 6);
        }

        g2.setColor(new Color(30, 120, 200));
        for (int i = 1; i < MAX_DASH_BARS; i++) g2.fillRect(barX + i * segW, barY, 1, 6);
        g2.setStroke(new BasicStroke(1));
        g2.drawRect(barX, barY, barW, 6);
    }
}