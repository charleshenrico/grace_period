import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import javax.imageio.ImageIO;

public class Player {

    public int x, y;
    public final int size = 30;

    private static final int BASE_SPEED = 5;
    private static final int SLOW_SPEED = 2;
    public int speed = BASE_SPEED;

    public boolean upPressed, downPressed, leftPressed, rightPressed, dashPressed;
    private Color fillColor, borderColor;
    public String name;

    public boolean hasFinished = false;

    // UPLB Powerup Timers
    public long towerSpeedEndTime = 0;
    public long libraryGhostEndTime = 0;

    // Dash system
    private static final int MAX_DASH_BARS       = 3;
    private static final int DASH_DISTANCE       = 120;
    private static final int DASH_SPEED          = 20;
    private static final int DASH_RECHARGE_TICKS = 180;
    private int dashBars = MAX_DASH_BARS;
    private ArrayList<Integer> rechargeQueue = new ArrayList<>();
    private boolean isDashing     = false;
    private int     dashRemaining = 0;
    private int     dashDirX = 0, dashDirY = 0;
    private int lastDirX = 0, lastDirY = 1;

    // Ability timers
    private static final int INVISIBILITY_TICKS = 300;
    private static final int SHIELD_TICKS_MAX   = 600;
    private static final int PUDDLE_TICKS_MAX   = 300;
    private static final int SLOW_TICKS_MAX     = 300;
    private static final int REVERSE_TICKS_MAX  = 300;

    private int invisibleTicks       = 0;
    private int shieldTicks          = 0;
    private int puddleTicks          = 0;
    private int slowedTicks          = 0;
    private int reverseControlsTicks = 0;

    // ── SPRITE SYSTEM ──────────────────────────────────────────────────────────
    private int skinIndex = 0;
    private BufferedImage imgUp, imgDown, imgLeft, imgRight;

    public Player(int startX, int startY, Color fillColor, Color borderColor, String name) {
        this.x = startX;
        this.y = startY;
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        this.name = name;
        setSkin(0);
    }

    public void setSkin(int idx) {
        skinIndex = idx;
        String[] prefixes = { "P1", "P2", "P3", "P4" };
        String prefix = (idx >= 0 && idx < prefixes.length) ? prefixes[idx] : "P1";
        imgUp    = loadImage(prefix + "Up.png");
        imgDown  = loadImage(prefix + "Down.png");
        imgLeft  = loadImage(prefix + "Left.png");
        imgRight = loadImage(prefix + "Right.png");
    }

    public int getSkinIndex() { return skinIndex; }

    // Returns 0=down, 1=up, 2=left, 3=right  (used to encode direction in network packets)
    public int getDirCode() {
        if (lastDirY == -1) return 1;
        if (lastDirY ==  1) return 0;
        if (lastDirX == -1) return 2;
        if (lastDirX ==  1) return 3;
        return 0;
    }

    private BufferedImage getCurrentSprite() {
        if (lastDirY == -1) return imgUp;
        if (lastDirY ==  1) return imgDown;
        if (lastDirX == -1) return imgLeft;
        if (lastDirX ==  1) return imgRight;
        return imgDown;
    }

    private static BufferedImage loadImage(String name) {
        try {
            java.io.InputStream is = Player.class.getResourceAsStream("/" + name);
            if (is != null) return ImageIO.read(is);
        } catch (Exception ignored) {}
        try { return ImageIO.read(new java.io.File("res/" + name)); }
        catch (Exception ignored) {}
        return null;
    }
    // ───────────────────────────────────────────────────────────────────────────

    public void update(int[][] mapLayout) {
        long now = System.currentTimeMillis();

        if (now > libraryGhostEndTime && libraryGhostEndTime != 0) {
            if (isPhysicallyInWall(this.x, this.y, mapLayout)) libraryGhostEndTime = now + 100;
            else libraryGhostEndTime = 0;
        }

        if (invisibleTicks       > 0) invisibleTicks--;
        if (shieldTicks          > 0) shieldTicks--;
        if (reverseControlsTicks > 0) reverseControlsTicks--;

        boolean wasPuddle = puddleTicks > 0;
        boolean wasSlowed = slowedTicks > 0;
        if (puddleTicks > 0) puddleTicks--;
        if (slowedTicks > 0) slowedTicks--;

        boolean isSlowNow = puddleTicks > 0 || slowedTicks > 0;

        if ((wasPuddle || wasSlowed) && !isSlowNow) {
            dashBars = MAX_DASH_BARS;
            rechargeQueue.clear();
        }

        if (isSlowNow) speed = SLOW_SPEED;
        else if (now < towerSpeedEndTime) speed = BASE_SPEED * 2;
        else speed = BASE_SPEED;

        if (!isSlowNow && !rechargeQueue.isEmpty()) {
            rechargeQueue.sort(Collections.reverseOrder());
            rechargeQueue.set(0, rechargeQueue.get(0) + 1);
            if (rechargeQueue.get(0) >= DASH_RECHARGE_TICKS) {
                rechargeQueue.remove(0);
                dashBars++;
            }
        }

        boolean rev = reverseControlsTicks > 0;
        boolean mu = rev ? downPressed  : upPressed;
        boolean md = rev ? upPressed    : downPressed;
        boolean ml = rev ? rightPressed : leftPressed;
        boolean mr = rev ? leftPressed  : rightPressed;

        if (dashPressed && !isDashing && dashBars > 0 && !isSlowNow) {
            isDashing     = true;
            dashRemaining = DASH_DISTANCE;
            dashBars--;

            dashDirX = 0; dashDirY = 0;
            if (mu) dashDirY = -1;
            if (md) dashDirY =  1;
            if (ml) dashDirX = -1;
            if (mr) dashDirX =  1;
            if (dashDirX == 0 && dashDirY == 0) {
                dashDirX = lastDirX; dashDirY = lastDirY;
            }
            rechargeQueue.add(0);
        }
        dashPressed = false;

        if (isDashing) {
            int step  = Math.min(DASH_SPEED, dashRemaining);
            int moved = moveDash(step, mapLayout);
            dashRemaining -= moved;
            if (dashRemaining <= 0 || moved == 0) {
                isDashing = false; dashRemaining = 0;
            }
            return;
        }

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

        return tl == 6 || tl == 1 || tr == 6 || tr == 1 ||
                bl == 6 || bl == 1 || br == 6 || br == 1;
    }

    private boolean isColliding(int px, int py, int[][] mapLayout) {
        if (System.currentTimeMillis() < libraryGhostEndTime) return false;
        return isPhysicallyInWall(px, py, mapLayout);
    }

    public void applyStaminaBoost() { dashBars = MAX_DASH_BARS; rechargeQueue.clear(); }
    public void applyInvisibility() { invisibleTicks = INVISIBILITY_TICKS; }
    public void applyShield() {
        if (slowedTicks > 0) {
            slowedTicks = 0;
            if (puddleTicks == 0) { dashBars = MAX_DASH_BARS; rechargeQueue.clear(); }
        }
        if (reverseControlsTicks > 0) reverseControlsTicks = 0;
        shieldTicks = SHIELD_TICKS_MAX;
    }
    public void applyPuddle() { puddleTicks = PUDDLE_TICKS_MAX; dashBars = 0; rechargeQueue.clear(); }
    public void applySlowDown() {
        if (shieldTicks > 0) { shieldTicks = 0; return; }
        slowedTicks = SLOW_TICKS_MAX; dashBars = 0; rechargeQueue.clear();
    }
    public void applyReverseControls() {
        if (shieldTicks > 0) { shieldTicks = 0; return; }
        reverseControlsTicks = REVERSE_TICKS_MAX;
    }

    public boolean isInvisible()    { return invisibleTicks > 0; }
    public boolean isShieldActive() { return shieldTicks > 0; }
    public boolean isSlowed()       { return puddleTicks > 0 || slowedTicks > 0; }
    public boolean isPuddled()      { return puddleTicks > 0; }
    public boolean isEnemySlowed()  { return slowedTicks > 0; }
    public boolean isReversed()     { return reverseControlsTicks > 0; }

    public int getAbilityFlags() {
        int f = 0;
        if (isShieldActive()) f |= 1;
        if (isInvisible())    f |= 2;
        if (isSlowed())       f |= 4;
        if (isReversed())     f |= 8;
        if (System.currentTimeMillis() < towerSpeedEndTime) f |= 16;
        if (hasFinished)      f |= 32;
        return f;
    }

    public void draw(Graphics2D g2) {
        java.awt.Composite oldC = g2.getComposite();

        float alpha = 1.0f;
        if (System.currentTimeMillis() < libraryGhostEndTime) {
            alpha = 0.40f;
        } else if (isInvisible()) {
            alpha = 0.30f;
        }
        if (alpha < 1.0f) {
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        }

        BufferedImage sprite = getCurrentSprite();

        if (sprite != null) {
            g2.drawImage(sprite, x, y, size, size, null);
            if (isDashing) {
                g2.setColor(new Color(255, 255, 255, 90));
                g2.fillRect(x, y, size, size);
            }
            if (isSlowed()) {
                g2.setColor(new Color(120, 160, 220, 120));
                g2.fillRect(x, y, size, size);
            }
        } else {
            Color body = fillColor;
            if (isDashing)  body = body.brighter();
            if (isSlowed()) body = new Color(120, 160, 220);
            g2.setColor(body);
            g2.fillRect(x, y, size, size);
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(x, y, size, size);
        }

        g2.setComposite(oldC);

        int tagY = y - 18;

        if (isShieldActive()) {
            g2.setColor(new Color(120, 200, 230));
            g2.setStroke(new BasicStroke(3));
            g2.drawOval(x - 5, y - 5, size + 10, size + 10);
            g2.setStroke(new BasicStroke(1));
            drawTag(g2, "SHIELD " + ((shieldTicks + 59) / 60) + "s", new Color(120, 200, 230), x, tagY);
            tagY -= 12;
        }
        if (isPuddled()) {
            drawTag(g2, "PUDDLE " + ((puddleTicks + 59) / 60) + "s", new Color(100, 180, 80), x, tagY);
            tagY -= 12;
        }
        if (isEnemySlowed()) {
            drawTag(g2, "SLOW " + ((slowedTicks + 59) / 60) + "s", new Color(120, 160, 220), x, tagY);
            tagY -= 12;
        }
        if (isReversed()) {
            drawTag(g2, "REV " + ((reverseControlsTicks + 59) / 60) + "s", new Color(200, 100, 220), x, tagY);
            tagY -= 12;
        }
        if (isInvisible()) {
            drawTag(g2, "HIDDEN " + ((invisibleTicks + 59) / 60) + "s", new Color(180, 230, 0), x, tagY);
        }

        if (name != null && !name.isEmpty()) {
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int lx = x + (size - fm.stringWidth(name)) / 2;
            g2.setColor(Color.BLACK); g2.drawString(name, lx + 1, y - 4);
            g2.setColor(Color.WHITE); g2.drawString(name, lx, y - 5);
        }

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

    private void drawTag(Graphics2D g2, String text, Color col, int px, int py) {
        g2.setFont(new Font("Arial", Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        int tx = px + (size - fm.stringWidth(text)) / 2;
        g2.setColor(Color.BLACK);  g2.drawString(text, tx + 1, py + 1);
        g2.setColor(col);          g2.drawString(text, tx,     py);
    }
}
