import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AbilityManager {
    private static final int TILE_SIZE = 40;

    private static final int N_STAMINA      = 4;
    private static final int N_INVISIBILITY = 3;
    private static final int N_SHIELD       = 3;
    private static final int N_SLOW_DOWN    = 5;
    private static final int MIN_SPACING_TILES = 3;

    private final int[][] mapLayout;
    private final List<Ability> abilities = new ArrayList<>();
    private final Random rng = new Random();

    private BufferedImage staminaImage, invisibilityImage, shieldImage, slowDownImage;
    private GamePanel panel;

    // Cooldowns for the UPLB Landmarks so they don't trigger 60x a second
    private long towerCD, libraryCD, obleCD, carillonCD;

    public AbilityManager(int[][] mapLayout, GamePanel panel) {
        this.mapLayout = mapLayout;
        this.panel = panel;
        loadImages();
        spawnInitial();
    }

    private void loadImages() {
        try {
            staminaImage      = ImageIO.read(getClass().getResourceAsStream("/Stamina-Boost.png"));
            invisibilityImage = ImageIO.read(getClass().getResourceAsStream("/Invisibility.png"));
            shieldImage       = ImageIO.read(getClass().getResourceAsStream("/Shield.png"));
            slowDownImage     = ImageIO.read(getClass().getResourceAsStream("/Slow-Down.png"));
        } catch (Exception e) {}
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

        for (int attempt = 0; attempt < 200; attempt++) {
            int row = rng.nextInt(rows);
            int col = rng.nextInt(cols);
            if (mapLayout[row][col] == 0) return new int[]{col * TILE_SIZE + offset, row * TILE_SIZE + offset};
        }
        return null;
    }

    private void respawn(Ability a) {
        int[] pos = randomPathPosition();
        if (pos != null) { a.x = pos[0]; a.y = pos[1]; }
        a.active = true;
        a.respawnTimer = 0;
    }

    public void update(Player player) {
        // 1. Floating Abilities Logic
        for (Ability a : abilities) {
            if (a.active) {
                if (a.intersects(player.x, player.y, player.size)) {
                    applyEffect(a, player);
                    a.active = false;
                    a.respawnTimer = 0;
                }
            } else {
                a.respawnTimer++;
                if (a.respawnTimer >= Ability.RESPAWN_TICKS) respawn(a);
            }
        }

        // 2. UPLB Landmarks Logic
        int r = player.getCenterRow();
        int c = player.getCenterCol();
        if (r < 0 || r >= mapLayout.length || c < 0 || c >= mapLayout[0].length) return;

        int tile = mapLayout[r][c];
        long now = System.currentTimeMillis();

        if (tile == 7 && now > towerCD) { // TOWER
            player.towerSpeedEndTime = now + 5000;
            towerCD = now + 15000;
            System.out.println("TOWER: Speed Up!");
        }
        else if (tile == 10 && now > libraryCD) { // LIBRARY
            player.libraryGhostEndTime = now + 5000;
            libraryCD = now + 15000;
            System.out.println("LIBRARY: Ghost Mode!");
        }
        else if (tile == 9 && now > carillonCD) { // CARILLON
            panel.carillonZoomEndTime = now + 3000;
            carillonCD = now + 15000;
            System.out.println("CARILLON: Zoom Out!");
        }
        else if (tile == 8 && now > obleCD) { // OBLE
            panel.obleFreezeEndTime = now + 5000;
            obleCD = now + 20000;
            System.out.println("OBLE: Time Freeze!");
            // [MULTIPLAYER] panel.serverOut.println("ABILITY:OBLE");
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