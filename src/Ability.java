import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

// A single ability pickup placed on the map.
// The icon is inside one 40x40 path tile.
public class Ability {

    public enum Type {
        STAMINA_BOOST,   // Power-up: instantly refills dash meter
        INVISIBILITY,    // Power-up: hides player for a few seconds
        SHIELD,          // Defensive: blocks the next trap
        SLOW_DOWN        // Trap: slows player and removes dashes
    }

    // Icon size in world pixels
    public static final int SIZE = 40;

    // How long an ability stays gone before respawning (5s @ 60fps)
    // EDIT THIS based on the specs
    public static final int RESPAWN_TICKS = 300;

    public final Type type;
    public int x, y; // top-left in world coords
    public boolean active = true;
    public int respawnTimer = 0;
    public final BufferedImage image;

    public Ability(Type type, int x, int y, BufferedImage image) {
        this.type  = type;
        this.x     = x;
        this.y     = y;
        this.image = image;
    }

    // hit test against the player rectangle
    public boolean intersects(int px, int py, int psize) {
        if (!active) return false;
        return px < x + SIZE && px + psize > x &&
                py < y + SIZE && py + psize > y;
    }

    public void draw(Graphics2D g2) {
        if (!active) return;
        if (image != null) {
            g2.drawImage(image, x, y, SIZE, SIZE, null);
        } else {
            // Fallback: colored circle if PNG didn't load
            Color c;
            switch (type) {
                case STAMINA_BOOST: c = new Color(230, 220, 0);   break;
                case INVISIBILITY:  c = new Color(180, 230, 0);   break;
                case SHIELD:        c = new Color(120, 200, 230); break;
                case SLOW_DOWN:     c = new Color(230, 100, 100); break;
                default:            c = Color.WHITE;
            }
            g2.setColor(c);
            g2.fillOval(x, y, SIZE, SIZE);
            g2.setColor(Color.BLACK);
            g2.drawOval(x, y, SIZE, SIZE);
        }
    }
}
