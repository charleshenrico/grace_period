import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Spawns Ability pickups on random walkable path tiles.
// Handles collision with the local player, applies effects, and respawns
// each consumed ability after RESPAWN_TICKS at a new random tile.
public class AbilityManager {

    private static final int TILE_SIZE = 40;

    // Counts per ability type on the map at once
    private static final int N_STAMINA          = 4;
    private static final int N_INVISIBILITY     = 3;
    private static final int N_SHIELD           = 3;
    private static final int N_PUDDLE           = 5;   // self-trap (ground)
    private static final int N_SLOW_DOWN        = 3;   // enemy trap
    private static final int N_REVERSE_CONTROLS = 3;  // enemy trap

    // Minimum spacing between any two abilities (in tiles)
    private static final int MIN_SPACING_TILES = 3;

    private final int[][] mapLayout;
    private final List<Ability> abilities = new ArrayList<>();
    private final Random rng;

    // When false, enemy-trap abilities (SLOW_DOWN, REVERSE_CONTROLS) are not
    // spawned because there are no opponents to affect.
    private final boolean multiplayer;

    private BufferedImage staminaImage;
    private BufferedImage invisibilityImage;
    private BufferedImage shieldImage;
    private BufferedImage puddleImage;
    private BufferedImage slowDownImage;
    private BufferedImage reverseImage;

    public AbilityManager(int[][] mapLayout, boolean multiplayer) {
        this(mapLayout, System.currentTimeMillis(), multiplayer);
    }

    public AbilityManager(int[][] mapLayout, long seed, boolean multiplayer) {
        this.mapLayout   = mapLayout;
        this.multiplayer = multiplayer;
        this.rng = new Random(seed);
        loadImages();
        spawnInitial();
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

    private void loadImages() {
        staminaImage      = loadImage("Stamina-Boost.png");
        invisibilityImage = loadImage("Invisibility.png");
        shieldImage       = loadImage("Shield.png");
        puddleImage       = loadImage("Puddle.png");
        slowDownImage     = loadImage("Slow-Down.png");
        reverseImage      = loadImage("Reverse.png");
    }

    private BufferedImage imageFor(Ability.Type t) {
        switch (t) {
            case STAMINA_BOOST:    return staminaImage;
            case INVISIBILITY:     return invisibilityImage;
            case SHIELD:           return shieldImage;
            case PUDDLE:           return puddleImage;
            case SLOW_DOWN:        return slowDownImage;
            case REVERSE_CONTROLS: return reverseImage;
            default:               return null;
        }
    }

    private void spawnInitial() {
        for (int i = 0; i < N_STAMINA;      i++) spawnNew(Ability.Type.STAMINA_BOOST);
        for (int i = 0; i < N_INVISIBILITY; i++) spawnNew(Ability.Type.INVISIBILITY);
        for (int i = 0; i < N_SHIELD;       i++) spawnNew(Ability.Type.SHIELD);
        for (int i = 0; i < N_PUDDLE;       i++) spawnNew(Ability.Type.PUDDLE);
        // Enemy-trap abilities are useless (and misleading) in single-player
        if (multiplayer) {
            for (int i = 0; i < N_SLOW_DOWN;        i++) spawnNew(Ability.Type.SLOW_DOWN);
            for (int i = 0; i < N_REVERSE_CONTROLS; i++) spawnNew(Ability.Type.REVERSE_CONTROLS);
        }
    }

    private void spawnNew(Ability.Type type) {
        int[] pos = randomPathPosition();
        if (pos == null) return;
        abilities.add(new Ability(type, pos[0], pos[1], imageFor(type)));
    }

    private int[] randomPathPosition() {
        int rows = mapLayout.length;
        int cols = mapLayout[0].length;
        int offset = (TILE_SIZE - Ability.SIZE) / 2;
        int minDistSq = (MIN_SPACING_TILES * TILE_SIZE) * (MIN_SPACING_TILES * TILE_SIZE);

        for (int attempt = 0; attempt < 400; attempt++) {
            int row = rng.nextInt(rows);
            int col = rng.nextInt(cols);
            if (mapLayout[row][col] != 0) continue;

            int x = col * TILE_SIZE + offset;
            int y = row * TILE_SIZE + offset;

            boolean tooClose = false;
            for (Ability other : abilities) {
                int dx = other.x - x;
                int dy = other.y - y;
                if (dx * dx + dy * dy < minDistSq) { tooClose = true; break; }
            }
            if (tooClose) continue;
            return new int[]{x, y};
        }

        // Last resort: any path tile, ignore spacing
        for (int attempt = 0; attempt < 200; attempt++) {
            int row = rng.nextInt(rows);
            int col = rng.nextInt(cols);
            if (mapLayout[row][col] == 0) {
                return new int[]{col * TILE_SIZE + offset, row * TILE_SIZE + offset};
            }
        }
        return null;
    }

    private void respawn(Ability a) {
        int[] pos = randomPathPosition();
        if (pos != null) { a.x = pos[0]; a.y = pos[1]; }
        a.active = true;
        a.respawnTimer = 0;
    }

    /**
     * Tick all abilities.
     * Returns the list index of the ability picked up this tick, or -1.
     * Self-effect abilities (STAMINA, INVISIBILITY, SHIELD, PUDDLE) are applied
     * immediately to the local player.
     * Enemy-effect abilities (SLOW_DOWN, REVERSE_CONTROLS) are removed from the
     * map but NOT applied locally — GamePanel reads the type via getAbilityType()
     * and sends it over the network so other players are affected.
     */
    public int update(Player player) {
        for (int i = 0; i < abilities.size(); i++) {
            Ability a = abilities.get(i);
            if (a.active) {
                if (a.intersects(player.x, player.y, player.size)) {
                    applyLocalEffect(a, player);
                    a.active = false;
                    a.respawnTimer = 0;
                    return i;
                }
            } else {
                a.respawnTimer++;
                if (a.respawnTimer >= Ability.RESPAWN_TICKS) respawn(a);
            }
        }
        return -1;
    }

    /** Apply the ability effect to the LOCAL player (only for self-targeting abilities). */
    private void applyLocalEffect(Ability a, Player player) {
        switch (a.type) {
            case STAMINA_BOOST:    player.applyStaminaBoost(); break;
            case INVISIBILITY:     player.applyInvisibility(); break;
            case SHIELD:           player.applyShield();       break;
            case PUDDLE:           player.applyPuddle();       break;
            case SLOW_DOWN:        /* handled via network — no self-effect */ break;
            case REVERSE_CONTROLS: /* handled via network — no self-effect */ break;
        }
    }

    /** Returns the type of the ability at the given index (valid even after pickup). */
    public Ability.Type getAbilityType(int index) {
        if (index >= 0 && index < abilities.size()) return abilities.get(index).type;
        return null;
    }

    /** Called when the server tells us another player picked up ability at index i. */
    public void remoteRemove(int index) {
        if (index >= 0 && index < abilities.size()) {
            Ability a = abilities.get(index);
            a.active = false;
            a.respawnTimer = 0;
        }
    }

    public void draw(Graphics2D g2) {
        for (Ability a : abilities) a.draw(g2);
    }
}
