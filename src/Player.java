import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

public class Player {

    // Position and size
    public int x, y;
    public final int size = 30;

    // Speed (mutable so the SLOW_DOWN trap can change it temporarily)
    private static final int BASE_SPEED = 5;
    private static final int SLOW_SPEED = 2;
    public int speed = BASE_SPEED;

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

    // --- Ability state ---
    private static final int INVISIBILITY_TICKS = 300; // 5s @ 60fps
    private static final int SLOW_TICKS         = 300; // 5s @ 60fps
    private static final int SHIELD_TICKS       = 600; // 10s @ 60fps

    private int invisibleTicks = 0;   // > 0 = invisible
    private int slowedTicks    = 0;   // > 0 = slowed
    private int shieldTicks    = 0;   // > 0 = shield up

    public Player(int startX, int startY, Color fillColor, Color borderColor, String name) {
        this.x           = startX;
        this.y           = startY;
        this.fillColor   = fillColor;
        this.borderColor = borderColor;
        this.name        = name;
    }

    public void update(int[][] mapLayout) {

        // --- Tick ability timers ---
        if (invisibleTicks > 0) invisibleTicks--;
        if (slowedTicks    > 0) slowedTicks--;
        if (shieldTicks    > 0) shieldTicks--;

        // Speed reflects current slow status
        speed = (slowedTicks > 0) ? SLOW_SPEED : BASE_SPEED;

        // While slowed, dashes do NOT recharge.
        // Once slow ends, recharge resumes from where it left off so dashing
        // continues to work normally for the rest of the game.
        if (slowedTicks == 0 && !rechargeQueue.isEmpty()) {
            rechargeQueue.sort(Collections.reverseOrder());
            rechargeQueue.set(0, rechargeQueue.get(0) + 1);
            if (rechargeQueue.get(0) >= DASH_RECHARGE_TICKS) {
                rechargeQueue.remove(0);
                dashBars++;
            }
        }

        // Initiate dash on space press (disabled while slowed)
        if (dashPressed && !isDashing && dashBars > 0 && slowedTicks == 0) {
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
        }
        // Always consume the dash key press so it doesn't queue up
        dashPressed = false;

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

    // -------- Ability effects (called by AbilityManager) --------

    // Power-up: STAMINA BOOST
    // instantly refill dash meter and clear active recharges.
    public void applyStaminaBoost() {
        dashBars = MAX_DASH_BARS;
        rechargeQueue.clear();
    }

    // Power-up: INVISIBILITY
    // become hidden for 5 seconds
    public void applyInvisibility() {
        invisibleTicks = INVISIBILITY_TICKS;
    }

    // DEFENSIVE: SHIELD
    // A 10-second shield. The shield ONLY interacts with traps (currently SLOW_DOWN only).
    // Other power-ups (Stamina Boost, Invisibility) apply normally and leave the shield untouched.

    // If the player is currently slowed when they pick up the
    // shield, the shield instantly removes the slow-down and is consumed.
    public void applyShield() {
        if (slowedTicks > 0) {
            // Shield meets active slow-down trap — both are consumed,
            // player is back to normal immediately.
            slowedTicks = 0;
            shieldTicks = 0;
            return;
        }
        shieldTicks = SHIELD_TICKS;
    }


    // TRAP: SLOW DOWN
    // slow player for 5 seconds (and disable dashing during that time).
    // If the shield is up, the shield absorbs the trap and is consumed:
    // the player is NOT slowed, and the dash meter is unaffected.
    public void applySlowDown() {
        if (shieldTicks > 0) {
            // Shield blocks the trap; both are consumed
            shieldTicks = 0;
            return;
        }
        slowedTicks = SLOW_TICKS;
    }

    public boolean isInvisible()    { return invisibleTicks > 0; }
    public boolean isShieldActive() { return shieldTicks > 0; }
    public boolean isSlowed()       { return slowedTicks > 0; }

    // -------- Drawing --------
    public void draw(Graphics2D g2) {
        // If invisible, draw the player translucent so the local player
        // can still see themselves, pure invisiblity will be implemented in Milestone 2
        java.awt.Composite oldComposite = g2.getComposite();
        if (isInvisible()) {
            g2.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.30f));
        }

        // Player body
        Color body = fillColor;
        if (isDashing)      body = body.brighter();
        if (isSlowed())     body = new Color(120, 160, 220); // bluish while slowed

        g2.setColor(body);
        g2.fillRect(x, y, size, size);
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(x, y, size, size);

        // Restore composite before drawing UI (name + dash bar + shield ring)
        g2.setComposite(oldComposite);

        // Shield ring around the player + countdown label
        if (isShieldActive()) {
            g2.setColor(new Color(120, 200, 230));
            g2.setStroke(new BasicStroke(3));
            int pad = 5;
            g2.drawOval(x - pad, y - pad, size + pad * 2, size + pad * 2);
            g2.setStroke(new BasicStroke(1));

            g2.setFont(new Font("Arial", Font.BOLD, 10));
            String tag = "SHIELD " + ((shieldTicks + 59) / 60) + "s";
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + (size - fm.stringWidth(tag)) / 2;
            int ty = y - 18;
            g2.setColor(Color.BLACK);
            g2.drawString(tag, tx + 1, ty + 1);
            g2.setColor(new Color(120, 200, 230));
            g2.drawString(tag, tx, ty);
        }

        // Slow indicator — small text under the dash bar
        if (isSlowed()) {
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            String tag = "SLOW " + ((slowedTicks + 59) / 60) + "s";
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + (size - fm.stringWidth(tag)) / 2;
            int ty = y - 18;
            g2.setColor(Color.BLACK);
            g2.drawString(tag, tx + 1, ty + 1);
            g2.setColor(new Color(120, 160, 220));
            g2.drawString(tag, tx, ty);
        }

        // Invisibility countdown — small text above name
        if (isInvisible()) {
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            String tag = "HIDDEN " + ((invisibleTicks + 59) / 60) + "s";
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + (size - fm.stringWidth(tag)) / 2;
            int ty = y - 30;
            g2.setColor(Color.BLACK);
            g2.drawString(tag, tx + 1, ty + 1);
            g2.setColor(new Color(180, 230, 0));
            g2.drawString(tag, tx, ty);
        }

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
        int barW  = size + 10;
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

        // Partial recharge on the next empty segment (uses the highest queued timer)
        if (!rechargeQueue.isEmpty() && slowedTicks == 0) {
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