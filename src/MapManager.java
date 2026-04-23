import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class MapManager {

    public final int tileSize = 40;
    public int[][] mapLayout;

    // Path tile images (1-9)
    // path1 = wall on TOP + LEFT
    // path2 = wall on TOP
    // path3 = wall on TOP + RIGHT
    // path4 = wall on LEFT
    // path5 = no walls (open center)
    // path6 = wall on RIGHT
    // path7 = wall on LEFT + BOTTOM
    // path8 = wall on BOTTOM
    // path9 = wall on RIGHT + BOTTOM
    private BufferedImage[] pathImages = new BufferedImage[10];

    private BufferedImage treeImage;
    private BufferedImage wallImage;

    // Physci: 3x3 composite (physci1..physci9)
    // physci1 | physci2 | physci3
    // physci4 | physci5 | physci6
    // physci7 | physci8 | physci9
    private BufferedImage[] physciPieces = new BufferedImage[10];

    // Gate: 3x3 composite (gate1..gate8, open center)
    // gate1 | gate2 | gate3
    // gate4 | open  | gate5
    // gate6 | gate7 | gate8
    private BufferedImage[] gatePieces = new BufferedImage[10];

    public MapManager(int[][] layout) {
        this.mapLayout = layout;
        loadImages();
    }

    //Load Images from res folder
    private void loadImages() {
        try {
            for (int i = 1; i <= 9; i++) {
                pathImages[i] = ImageIO.read(getClass().getResourceAsStream("/path" + i + ".png"));
            }
            treeImage = ImageIO.read(getClass().getResourceAsStream("/tree.png"));
            wallImage = ImageIO.read(getClass().getResourceAsStream("/wall.png"));
            for (int i = 1; i <= 9; i++) {
                physciPieces[i] = ImageIO.read(getClass().getResourceAsStream("/physci" + i + ".png"));
            }
            for (int i = 1; i <= 8; i++) {
                gatePieces[i] = ImageIO.read(getClass().getResourceAsStream("/gate" + i + ".png"));
            }
        } catch (Exception e) {
            System.out.println("Notice: Some images missing in 'res' folder. Using colors instead.");
        }
    }

    // Make sure user does not go through wall
    private boolean isWall(int row, int col) {
        if (row < 0 || col < 0 || row >= mapLayout.length || col >= mapLayout[0].length) return true;
        int t = mapLayout[row][col];
        return t == 1 || t == 6;
    }

    // Path Design(path1-9.png)
    private int getPathIndex(int row, int col) {
        boolean top    = isWall(row - 1, col);
        boolean bottom = isWall(row + 1, col);
        boolean left   = isWall(row, col - 1);
        boolean right  = isWall(row, col + 1);

        // Priority: corners first, then edges, then open
        if (top && left)     return 1;
        if (top && right)    return 3;
        if (bottom && left)  return 7;
        if (bottom && right) return 9;
        if (top)             return 2;
        if (left)            return 4;
        if (right)           return 6;
        if (bottom)          return 8;
        return 5;
    }

    // Finds the top-left corner of the 3x3 block of a given tileType containing (row, col)
    private int[] getBlockOrigin(int row, int col, int tileType) {
        for (int dr = 0; dr <= 2; dr++) {
            for (int dc = 0; dc <= 2; dc++) {
                int checkR = row - dr;
                int checkC = col - dc;
                if (checkR >= 0 && checkC >= 0 && mapLayout[checkR][checkC] == tileType) {
                    boolean topEdge  = (checkR == 0 || mapLayout[checkR - 1][checkC] != tileType);
                    boolean leftEdge = (checkC == 0 || mapLayout[checkR][checkC - 1] != tileType);
                    if (topEdge && leftEdge) return new int[]{checkR, checkC};
                }
            }
        }
        return new int[]{row, col};
    }

    //Rendering: loops through your entire 2D array
    //Tile 0: Calls the Autotiling logic to draw dirt.
    //Tile 1/6: Draws basic trees or walls.
    //Tile 2 (Physci): Uses the "Puzzle Finder" to draw the correct piece of the 3x3 Physci building.
    //Tile 3 (Gate): Similar to Physci, but it leaves the center empty (null) so the player can walk through the middle of the gate.
    public void draw(Graphics2D g2) {
        for (int row = 0; row < mapLayout.length; row++) {
            for (int col = 0; col < mapLayout[row].length; col++) {

                int tileType = mapLayout[row][col];
                int x = col * tileSize;
                int y = row * tileSize;

                if (tileType == 0) {
                    // Smart path: pick tile based on adjacent walls
                    int idx = getPathIndex(row, col);
                    if (pathImages[idx] != null) {
                        g2.drawImage(pathImages[idx], x, y, tileSize, tileSize, null);
                    } else {
                        g2.setColor(new Color(210, 180, 140));
                        g2.fillRect(x, y, tileSize, tileSize);
                    }

                } else if (tileType == 1) {
                    // Tree (border edge)
                    if (treeImage != null) {
                        g2.drawImage(treeImage, x, y, tileSize, tileSize, null);
                    } else {
                        g2.setColor(new Color(34, 139, 34));
                        g2.fillRect(x, y, tileSize, tileSize);
                    }

                } else if (tileType == 6) {
                    // Wall
                    if (wallImage != null) {
                        g2.drawImage(wallImage, x, y, tileSize, tileSize, null);
                    } else {
                        g2.setColor(new Color(100, 100, 100));
                        g2.fillRect(x, y, tileSize, tileSize);
                    }

                } else if (tileType == 2) {
                    // Physci (ending) — 3x3 composite
                    int[] origin = getBlockOrigin(row, col, 2);
                    int localRow = row - origin[0];
                    int localCol = col - origin[1];
                    int pieceIndex = localRow * 3 + localCol + 1;

                    if (pieceIndex >= 1 && pieceIndex <= 9 && physciPieces[pieceIndex] != null) {
                        g2.drawImage(physciPieces[pieceIndex], x, y, tileSize, tileSize, null);
                    } else {
                        g2.setColor(new Color(135, 206, 250));
                        g2.fillRect(x, y, tileSize, tileSize);
                    }

                } else if (tileType == 3) {
                    // Gate (starting) — 3x3 composite, open center
                    int[] origin = getBlockOrigin(row, col, 3);
                    int localRow = row - origin[0];
                    int localCol = col - origin[1];

                    BufferedImage gatePiece = null;
                    if (localRow == 0) {
                        gatePiece = gatePieces[localCol + 1]; // 1, 2, 3
                    } else if (localRow == 1) {
                        if      (localCol == 0) gatePiece = gatePieces[4];
                        else if (localCol == 1) gatePiece = null; // open center
                        else if (localCol == 2) gatePiece = gatePieces[5];
                    } else if (localRow == 2) {
                        gatePiece = gatePieces[localCol + 6]; // 6, 7, 8
                    }

                    if (gatePiece != null) {
                        g2.drawImage(gatePiece, x, y, tileSize, tileSize, null);
                    } else {
                        // Open center of gate — draw path underneath
                        int idx = getPathIndex(row, col);
                        if (pathImages[idx] != null) {
                            g2.drawImage(pathImages[idx], x, y, tileSize, tileSize, null);
                        } else {
                            g2.setColor(new Color(210, 180, 140));
                            g2.fillRect(x, y, tileSize, tileSize);
                        }
                    }
                }
            }
        }
    }
}