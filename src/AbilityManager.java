import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//  Spawns "Ability" on random walkable path tiles
//  Handles the collision of abiliteies with player
//  Applies effects, and respawns each consumed ability after RESPAWN_TICKS at a new random tile

public class AbilityManager {
    private static final int TILE_SIZE = 40;

    // How many of each ability type live on the map at once
    // EDIT THIS depends on our specs
    private static final int N_STAMINA      = 4;
    private static final int N_INVISIBILITY = 3;
    private static final int N_SHIELD       = 3;
    private static final int N_SLOW_DOWN    = 5;

    // Minimum spacing (in tiles) between any two abilities to avoid clusters
    private static final int MIN_SPACING_TILES = 3;

    private final int[][] mapLayout;
    private final List<Ability> abilities = new ArrayList<>();
    private final Random rng = new Random();

    private BufferedImage staminaImage;
    private BufferedImage invisibilityImage;
    private BufferedImage shieldImage;
    private BufferedImage slowDownImage;

    public AbilityManager(int[][] mapLayout) {
        this.mapLayout = mapLayout;
        loadImages();
        spawnInitial();
    }

    private void loadImages() {
        try {
            staminaImage      = ImageIO.read(getClass().getResourceAsStream("/Stamina-Boost.png"));
            invisibilityImage = ImageIO.read(getClass().getResourceAsStream("/Invisibility.png"));
            shieldImage       = ImageIO.read(getClass().getResourceAsStream("/Shield.png"));
            slowDownImage     = ImageIO.read(getClass().getResourceAsStream("/Slow-Down.png"));
        } catch (Exception e) {
            System.out.println("Notice: Some ability icons missing in 'res' folder. Using colored circles.");
        }
    }

    private BufferedImage imageFor(Ability.Type t) {
        switch (t) {
            case STAMINA_BOOST: return staminaImage;
            case INVISIBILITY:  return invisibilityImage;
            case SHIELD:        return shieldImage;
            case SLOW_DOWN:     return slowDownImage;
            default:            return null;
        }
    }

    private void spawnInitial() {
        for (int i = 0; i < N_STAMINA;      i++) spawnNew(Ability.Type.STAMINA_BOOST);
        for (int i = 0; i < N_INVISIBILITY; i++) spawnNew(Ability.Type.INVISIBILITY);
        for (int i = 0; i < N_SHIELD;       i++) spawnNew(Ability.Type.SHIELD);
        for (int i = 0; i < N_SLOW_DOWN;    i++) spawnNew(Ability.Type.SLOW_DOWN);
    }

    private void spawnNew(Ability.Type type) {
        int[] pos = randomPathPosition();
        if (pos == null) return;
        abilities.add(new Ability(type, pos[0], pos[1], imageFor(type)));
    }

    // Returns a top-left pixel coordinates of an icon centered
    // inside a randomly chosen walkable path tile
    // Avoid walls/trees/landmarks and tries to keep abilities spaced in the map
    private int[] randomPathPosition() {
        int rows = mapLayout.length;
        int cols = mapLayout[0].length;
        int offset = (TILE_SIZE - Ability.SIZE) / 2;
        int minDistSq = (MIN_SPACING_TILES * TILE_SIZE) * (MIN_SPACING_TILES * TILE_SIZE);

        for (int attempt = 0; attempt < 400; attempt++) {
            int row = rng.nextInt(rows);
            int col = rng.nextInt(cols);

            // Only spawn on a walkable PATH tile
            if (mapLayout[row][col] != 0) continue;

            int x = col * TILE_SIZE + offset;
            int y = row * TILE_SIZE + offset;

            // Spacing check vs all currently-placed (active OR awaiting-respawn) abilities
            boolean tooClose = false;
            for (Ability other : abilities) {
                int dx = other.x - x;
                int dy = other.y - y;
                if (dx * dx + dy * dy < minDistSq) {
                    tooClose = true;
                    break;
                }
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
        if (pos != null) {
            a.x = pos[0];
            a.y = pos[1];
        }
        a.active = true;
        a.respawnTimer = 0;
    }

    // Tick all abilities — handle pickup against player and respawn timers
    public void update(Player player) {
        for (Ability a : abilities) {
            if (a.active) {
                if (a.intersects(player.x, player.y, player.size)) {
                    applyEffect(a, player);
                    a.active = false;
                    a.respawnTimer = 0;
                }
            } else {
                a.respawnTimer++;
                if (a.respawnTimer >= Ability.RESPAWN_TICKS) {
                    respawn(a);
                }
            }
        }
    }

    private void applyEffect(Ability a, Player player) {
        switch (a.type) {
            case STAMINA_BOOST: player.applyStaminaBoost(); break;
            case INVISIBILITY:  player.applyInvisibility();  break;
            case SHIELD:        player.applyShield();        break;
            case SLOW_DOWN:     player.applySlowDown();      break;
        }
    }

    public void draw(Graphics2D g2) {
        for (Ability a : abilities) a.draw(g2);
    }
}
