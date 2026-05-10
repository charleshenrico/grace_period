import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AbilityManager {

    private static final int TILE_SIZE = 40;

    // Counts per ability type on the map at once
    private static final int N_STAMINA          = 4;
    private static final int N_INVISIBILITY     = 3;
    private static final int N_SHIELD           = 3;
    private static final int N_PUDDLE           = 5;   // self-trap (ground)
    private static final int N_SLOW_DOWN        = 3;   // enemy trap
    private static final int N_REVERSE_CONTROLS = 3;   // enemy trap

    private static final int MIN_SPACING_TILES = 3;

    private final int[][] mapLayout;
    private final List<Ability> abilities = new ArrayList<>();
    private final Random rng;

    // When false, enemy-trap abilities are not spawned
    private final boolean multiplayer;

    private BufferedImage staminaImage, invisibilityImage, shieldImage, puddleImage, slowDownImage, reverseImage;
    private GamePanel panel;

    // Cooldowns for the UPLB Landmarks so they don't trigger 60x a second
    private long towerCD, libraryCD, obleCD, carillonCD;

    // --- MERGED CONSTRUCTORS ---
    
    // Fallback constructor if GamePanel doesn't pass the multiplayer boolean
    public AbilityManager(int[][] mapLayout, GamePanel panel, long seed) {
        this(mapLayout, panel, seed, false);
    }

    public AbilityManager(int[][] mapLayout, GamePanel panel, long seed, boolean multiplayer) {
        this.mapLayout = mapLayout;
        this.panel = panel;
        this.multiplayer = multiplayer;
        this.rng = new Random(seed);
        loadImages();
        spawnInitial();
    }

    // --- MERGED IMAGE LOADING ---
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
        
        if (staminaImage == null) {
            System.out.println("Notice: Some ability icons missing in 'res' folder. Using colored circles.");
        }
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
        
        // Enemy-trap abilities are useless in single-player
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

    // --- MERGED UPDATE LOGIC ---
    public int update(Player player) {
        int pickedUpIndex = -1;

        // 1. Floating Abilities Logic (Multiplayer Sync)
        for (int i = 0; i < abilities.size(); i++) {
            Ability a = abilities.get(i);
            if (a.active) {
                if (a.intersects(player.x, player.y, player.size)) {
                    applyLocalEffect(a, player);
                    a.active = false;
                    a.respawnTimer = 0;
                    pickedUpIndex = i; // Save the index to return it to GamePanel
                    break; // Only pick up one item per frame
                }
            } else {
                a.respawnTimer++;
                if (a.respawnTimer >= Ability.RESPAWN_TICKS) respawn(a);
            }
        }

        // 2. UPLB Landmarks Logic (Main branch logic)
        int r = player.getCenterRow();
        int c = player.getCenterCol();
        if (r >= 0 && r < mapLayout.length && c >= 0 && c < mapLayout[0].length) {
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
            }
        }

        return pickedUpIndex; // Returns to GamePanel so it can tell the Server
    }

    /** Apply the ability effect to the LOCAL player (only for self-targeting abilities). */
    private void applyLocalEffect(Ability a, Player player) {
        switch (a.type) {
            // Note: Make sure Player.java has applyPuddle() method!
            case STAMINA_BOOST:    player.applyStaminaBoost(); break;
            case INVISIBILITY:     player.applyInvisibility(); break;
            case SHIELD:           player.applyShield();       break;
            //case PUDDLE:           player.applyPuddle();       break; 
            case SLOW_DOWN:        /* handled via network */ break;
            case REVERSE_CONTROLS: /* handled via network */ break;
        }
    }

    /** Returns the type of the ability at the given index (valid even after pickup). */
    public Ability.Type getAbilityType(int index) {
        if (index >= 0 && index < abilities.size()) return abilities.get(index).type;
        return null;
    }

    // --- NETWORK LOGIC: Called when server tells us someone else grabbed an item ---
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