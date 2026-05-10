import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

public class Player {

    // ── Position & size ────────────────────────────────────────────────────────
    public int x, y;
    public final int size = 30;

    // ── Speed ─────────────────────────────────────────────────────────────────
    private static final int BASE_SPEED = 5;
    private static final int SLOW_SPEED = 2;
    public int speed = BASE_SPEED;

    // ── Input flags (set by GamePanel key bindings) ────────────────────────────
    public boolean upPressed, downPressed, leftPressed, rightPressed;
    public boolean dashPressed;

    // ── Visual identity ────────────────────────────────────────────────────────
    private Color fillColor;
    private Color borderColor;
    public String name;

    // --- UPLB Powerup Timers (From Main) ---
    public long towerSpeedEndTime = 0;
    public long libraryGhostEndTime = 0;

    // ── Dash system ───────────────────────────────────────────────────────────
    private static final int MAX_DASH_BARS       = 3;
    private static final int DASH_DISTANCE       = 120;
    private static final int DASH_SPEED          = 20;
    private static final int DASH_RECHARGE_TICKS = 180;
    private int dashBars = MAX_DASH_BARS;
    private ArrayList<Integer> rechargeQueue = new ArrayList<>();
    private boolean isDashing     = false;
    private int     dashRemaining = 0;
    private int     dashDirX = 0, dashDirY = 0;

    // Last actual movement direction (used as dash fallback). Default: down.
    private int lastDirX = 0, lastDirY = 1;

    // ── Ability timers (From Multiplayer) ──────────────────────────────────────
    private static final int INVISIBILITY_TICKS = 300; // 5s  @ 60fps
    private static final int SHIELD_TICKS_MAX   = 600; // 10s @ 60fps
    private static final int PUDDLE_TICKS_MAX   = 300; // 5s  — ground trap (shield does NOT block)
    private static final int SLOW_TICKS_MAX     = 300; // 5s  — enemy-applied slow (shield DOES block)
    private static final int REVERSE_TICKS_MAX  = 300; // 5s  — enemy-applied reverse (shield DOES block)

    private int invisibleTicks       = 0;
    private int shieldTicks          = 0;
    private int puddleTicks          = 0;  // from stepping on a Puddle ground pickup
    private int slowedTicks          = 0;  // from an enemy using Slow-Down against you
    private int reverseControlsTicks = 0;  // from an enemy using Reverse Controls against you

    public Player(int startX, int startY, Color fillColor, Color borderColor, String name) {
        this.x = startX;
        this.y = startY;
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        this.name = name;
    }

    // ── Main update (called once per game tick) ────────────────────────────────
    public void update(int[][] mapLayout) {
        long now = System.currentTimeMillis();

        // ==========================================
        // SMART GHOST ESCAPE LOGIC (Main branch feature)
        // If the library timer is officially over, but we are still inside a wall...
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

        // ── Tick all timers ────────────────────────────────────────────────────
        if (invisibleTicks       > 0) invisibleTicks--;
        if (shieldTicks          > 0) shieldTicks--;
        if (reverseControlsTicks > 0) reverseControlsTicks--;

        boolean wasPuddle = puddleTicks > 0;
        boolean wasSlowed = slowedTicks > 0;
        if (puddleTicks > 0) puddleTicks--;
        if (slowedTicks > 0) slowedTicks--;

        boolean isSlowNow = puddleTicks > 0 || slowedTicks > 0;

        // When ALL slow effects just expired → restore full dashes
        if ((wasPuddle || wasSlowed) && !isSlowNow) {
            dashBars = MAX_DASH_BARS;
            rechargeQueue.clear();
        }

        // ── Speed (Merged Logic) ───────────────────────────────────────────────
        if (isSlowNow) {
            speed = SLOW_SPEED; // Traps take highest priority
        } else if (now < towerSpeedEndTime) {
            speed = BASE_SPEED * 2; // Tower Speed takes second priority
        } else {
            speed = BASE_SPEED;
        }

        // ── Dash recharge (paused while any slow is active) ────────────────────
        if (!isSlowNow && !rechargeQueue.isEmpty()) {
            rechargeQueue.sort(Collections.reverseOrder());
            rechargeQueue.set(0, rechargeQueue.get(0) + 1);
            if (rechargeQueue.get(0) >= DASH_RECHARGE_TICKS) {
                rechargeQueue.remove(0);
                dashBars++;
            }
        }

        // ── Resolve effective movement directions (reversed controls) ─────────
        boolean rev = reverseControlsTicks > 0;
        boolean mu = rev ? downPressed  : upPressed;
        boolean md = rev ? upPressed    : downPressed;
        boolean ml = rev ? rightPressed : leftPressed;
        boolean mr = rev ? leftPressed  : rightPressed;

        // ── Dash initiation (disabled while any slow is active) ───────────────
        if (dashPressed && !isDashing && dashBars > 0 && !isSlowNow) {
            isDashing     = true;
            dashRemaining = DASH_DISTANCE;
            dashBars--;

            // Dash direction from current effective keys
            dashDirX = 0; dashDirY = 0;
            if (mu) dashDirY = -1;
            if (md) dashDirY =  1;
            if (ml) dashDirX = -1;
            if (mr) dashDirX =  1;
            // Fallback: last actual movement direction
            if (dashDirX == 0 && dashDirY == 0) {
                dashDirX = lastDirX;
                dashDirY = lastDirY;
            }

            rechargeQueue.add(0);
        }
        dashPressed = false;

        // ── Execute dash ──────────────────────────────────────────────────────
        if (isDashing) {
            int step  = Math.min(DASH_SPEED, dashRemaining);
            int moved = moveDash(step, mapLayout);
            dashRemaining -= moved;
            if (dashRemaining <= 0 || moved == 0) {
                isDashing = false; dashRemaining = 0;
            }
            return;
        }

        // ── Normal movement using effective directions ────────────────────────
        if (mu) {
            y -= speed;
            if (isColliding(x, y, mapLayout)) y += speed;
            else { lastDirX = 0; lastDirY = -1; }
        }
        if (md) {
            y += speed;
            if (isColliding(x, y, mapLayout)) y -= speed;
            else { lastDirX = 0; lastDirY = 1; }
        }
        if (ml) {
            x -= speed;
            if (isColliding(x, y, mapLayout)) x += speed;
            else { lastDirX = -1; lastDirY = 0; }
        }
        if (mr) {
            x += speed;
            if (isColliding(x, y, mapLayout)) x -= speed;
            else { lastDirX = 1; lastDirY = 0; }
        }
    }

    private int moveDash(int step, int[][] mapLayout) {
        int moved = 0;
        for (int i = 0; i < step; i++) {
            int nx = x + dashDirX, ny = y + dashDirY;
            if (isColliding(nx, ny, mapLayout)) break;
            x = nx; y = ny; moved++;
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

    // ── Ability effects ───────────────────────────────────────────────────────

    /** Power-up: instantly refills dash meter. */
    public void applyStaminaBoost() {
        dashBars = MAX_DASH_BARS;
        rechargeQueue.clear();
    }

    /** Power-up: makes this player invisible to enemies for 5s. */
    public void applyInvisibility() {
        invisibleTicks = INVISIBILITY_TICKS;
    }

    /**
     * Defensive: arms a 10-second shield.
     * Also immediately cancels any ENEMY traps currently active (slow or reverse).
     * Does NOT cancel Puddle (ground trap — not from an enemy).
     */
    public void applyShield() {
        if (slowedTicks > 0) {
            slowedTicks = 0;
            if (puddleTicks == 0) { dashBars = MAX_DASH_BARS; rechargeQueue.clear(); }
        }
        if (reverseControlsTicks > 0) reverseControlsTicks = 0;
        shieldTicks = SHIELD_TICKS_MAX;
    }

    /**
     * Self-trap (PUDDLE ground pickup): slows this player + removes dashes for 5s.
     * Shield does NOT block this — Puddle is a ground-level hazard, not an enemy ability.
     */
    public void applyPuddle() {
        puddleTicks = PUDDLE_TICKS_MAX;
        dashBars = 0;
        rechargeQueue.clear();
    }

    /**
     * Enemy trap (SLOW DOWN): applied when an enemy uses the Slow-Down icon against you.
     * Slows this player + removes dashes for 5s. Shield blocks and is consumed.
     */
    public void applySlowDown() {
        if (shieldTicks > 0) { shieldTicks = 0; return; }
        slowedTicks = SLOW_TICKS_MAX;
        dashBars = 0;
        rechargeQueue.clear();
    }

    /**
     * Enemy trap (REVERSE CONTROLS): applied when an enemy uses the Reverse Controls icon.
     * Reverses this player's movement for 5s. Shield blocks and is consumed.
     */
    public void applyReverseControls() {
        if (shieldTicks > 0) { shieldTicks = 0; return; }
        reverseControlsTicks = REVERSE_TICKS_MAX;
    }

    // ── State queries ─────────────────────────────────────────────────────────

    public boolean isInvisible()    { return invisibleTicks > 0; }
    public boolean isShieldActive() { return shieldTicks > 0; }
    public boolean isSlowed()       { return puddleTicks > 0 || slowedTicks > 0; }
    public boolean isPuddled()      { return puddleTicks > 0; }
    public boolean isEnemySlowed()  { return slowedTicks > 0; }
    public boolean isReversed()     { return reverseControlsTicks > 0; }

    /**
     * Compact ability-state bitmask for network transmission (POS/STATE packets).
     *   Bit 0 (1): shield active
     *   Bit 1 (2): invisible
     *   Bit 2 (4): slowed (puddle and/or enemy slow)
     *   Bit 3 (8): reversed controls
     */
    public int getAbilityFlags() {
        int f = 0;
        if (isShieldActive()) f |= 1;
        if (isInvisible())    f |= 2;
        if (isSlowed())       f |= 4;
        if (isReversed())     f |= 8;
        return f;
    }

    // ── Draw (local player only — ghosts are drawn in GamePanel.drawGhost) ──────
    public void draw(Graphics2D g2) {

        java.awt.Composite oldC = g2.getComposite();

        // Ghost rendering priority: Library Ghost Mode overrides normal invisibility opacity
        if (System.currentTimeMillis() < libraryGhostEndTime) {
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.40f));
        } else if (isInvisible()) {
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.30f));
        }

        // Body
        Color body = fillColor;
        if (isDashing)  body = body.brighter();
        if (isSlowed()) body = new Color(120, 160, 220);

        g2.setColor(body);
        g2.fillRect(x, y, size, size);
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(x, y, size, size);

        g2.setComposite(oldC);

        // ── Status tags above the player ──────────────────────────────────────
        int tagY = y - 18;

        if (isShieldActive()) {
            g2.setColor(new Color(120, 200, 230));
            g2.setStroke(new BasicStroke(3));
            g2.drawOval(x - 5, y - 5, size + 10, size + 10);
            g2.setStroke(new BasicStroke(1));
            drawTag(g2, "SHIELD " + ((shieldTicks + 59) / 60) + "s",
                    new Color(120, 200, 230), x, tagY);
            tagY -= 12;
        }
        if (isPuddled()) {
            drawTag(g2, "PUDDLE " + ((puddleTicks + 59) / 60) + "s",
                    new Color(100, 180, 80), x, tagY);
            tagY -= 12;
        }
        if (isEnemySlowed()) {
            drawTag(g2, "SLOW " + ((slowedTicks + 59) / 60) + "s",
                    new Color(120, 160, 220), x, tagY);
            tagY -= 12;
        }
        if (isReversed()) {
            drawTag(g2, "REV " + ((reverseControlsTicks + 59) / 60) + "s",
                    new Color(200, 100, 220), x, tagY);
            tagY -= 12;
        }
        if (isInvisible()) {
            drawTag(g2, "HIDDEN " + ((invisibleTicks + 59) / 60) + "s",
                    new Color(180, 230, 0), x, tagY);
        }

        // Name label
        if (name != null && !name.isEmpty()) {
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int lx = x + (size - fm.stringWidth(name)) / 2;
            g2.setColor(Color.BLACK); g2.drawString(name, lx + 1, y - 4);
            g2.setColor(Color.WHITE); g2.drawString(name, lx, y - 5);
        }

        // ── Dash bar ──────────────────────────────────────────────────────────
        int barW = size + 10;
        int barH = 6;
        int barX = x + (size - barW) / 2;
        int barY = y + size + 6;
        int segW = barW / MAX_DASH_BARS;

        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(barX, barY, barW, 6);

        for (int i = 0; i < dashBars; i++) {
            g2.setColor(new Color(80, 180, 255));
            g2.fillRect(barX + i * segW, barY, segW, 6);
        }

        if (!rechargeQueue.isEmpty() && !isSlowed()) {
            int highest  = Collections.max(rechargeQueue);
            int partialW = (int)((double) highest / DASH_RECHARGE_TICKS * segW);
            g2.setColor(new Color(40, 100, 180));
            g2.fillRect(barX + dashBars * segW, barY, partialW, barH);
        }

        g2.setColor(new Color(30, 120, 200));
        for (int i = 1; i < MAX_DASH_BARS; i++)
            g2.fillRect(barX + i * segW, barY, 1, barH);

        g2.setStroke(new BasicStroke(1));
        g2.drawRect(barX, barY, barW, 6);
    }

    /** Small shadow-backed colored label above the player. */
    private void drawTag(Graphics2D g2, String text, Color col, int px, int py) {
        g2.setFont(new Font("Arial", Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        int tx = px + (size - fm.stringWidth(text)) / 2;
        g2.setColor(Color.BLACK);  g2.drawString(text, tx + 1, py + 1);
        g2.setColor(col);          g2.drawString(text, tx,     py);
    }
}