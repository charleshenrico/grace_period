import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class MapManager {

    public final int tileSize = 40;
    public int[][] mapLayout;

    private BufferedImage[] pathImages = new BufferedImage[10];

    private BufferedImage treeImage;
    private BufferedImage wallImage;

    private BufferedImage physciImage;
    private BufferedImage gateImage;
    private BufferedImage towerImage;
    private BufferedImage obleImage;
    private BufferedImage carillonImage;
    private BufferedImage libraryImage;

    public MapManager(int[][] layout) {
        this.mapLayout = layout;
        loadImages();
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
        try {
            for (int i = 1; i <= 9; i++) {
                pathImages[i] = loadImage("path" + i + ".png");
            }
            treeImage     = loadImage("tree.png");
            wallImage     = loadImage("wall.png");
            physciImage   = loadImage("PhySci.png");
            gateImage     = loadImage("Gate.png");
            towerImage    = loadImage("Tower.png");
            obleImage     = loadImage("Oble.png");
            carillonImage = loadImage("Carilion.png");
            libraryImage  = loadImage("Library.png");
        } catch (Exception e) {
            System.out.println("Notice: Some images missing in 'res' folder. Using colors instead.");
        }
    }

    // Only trees and walls are solid now — landmarks are walkable
    private boolean isWall(int row, int col) {
        if (row < 0 || col < 0 || row >= mapLayout.length || col >= mapLayout[0].length) return true;
        int t = mapLayout[row][col];
        return t == 1 || t == 6;
    }

    private int getPathIndex(int row, int col) {
        boolean top    = isWall(row - 1, col);
        boolean bottom = isWall(row + 1, col);
        boolean left   = isWall(row, col - 1);
        boolean right  = isWall(row, col + 1);

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

    private int[] getBlockOrigin(int row, int col, int type) {
        for (int dr = 0; dr < 9; dr++) {
            for (int dc = 0; dc < 9; dc++) {
                int checkR = row - dr;
                int checkC = col - dc;
                if (checkR >= 0 && checkC >= 0 && mapLayout[checkR][checkC] == type) {
                    boolean topEdge  = (checkR == 0 || mapLayout[checkR - 1][checkC] != type);
                    boolean leftEdge = (checkC == 0 || mapLayout[checkR][checkC - 1] != type);
                    if (topEdge && leftEdge) return new int[]{checkR, checkC};
                }
            }
        }
        return new int[]{row, col};
    }

    private void drawLandmark(Graphics2D g2, int row, int col, int tileType,
                              BufferedImage image, Color fallback, String label,
                              boolean[] drawn) {
        int[] origin = getBlockOrigin(row, col, tileType);
        if (row == origin[0] && col == origin[1] && !drawn[0]) {
            int x    = col * tileSize;
            int y    = row * tileSize;
            int size = tileSize * 9;
            if (image != null) {
                g2.drawImage(image, x, y, size, size, null);
            } else {
                g2.setColor(fallback);
                g2.fillRect(x, y, size, size);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Arial", Font.BOLD, 22));
                g2.drawString(label, x + 10, y + size / 2);
            }
            drawn[0] = true;
        }
    }

    public void draw(Graphics2D g2) {
        boolean[] physciDrawn    = {false};
        boolean[] gateDrawn      = {false};
        boolean[] towerDrawn     = {false};
        boolean[] obleDrawn      = {false};
        boolean[] carillonDrawn  = {false};
        boolean[] libraryDrawn   = {false};

        for (int row = 0; row < mapLayout.length; row++) {
            for (int col = 0; col < mapLayout[row].length; col++) {

                int tileType = mapLayout[row][col];
                int x = col * tileSize;
                int y = row * tileSize;

                switch (tileType) {
                    case 0:
                        int idx = getPathIndex(row, col);
                        if (pathImages[idx] != null) {
                            g2.drawImage(pathImages[idx], x, y, tileSize, tileSize, null);
                        } else {
                            g2.setColor(new Color(210, 180, 140));
                            g2.fillRect(x, y, tileSize, tileSize);
                        }
                        break;

                    case 1:
                        if (treeImage != null) {
                            g2.drawImage(treeImage, x, y, tileSize, tileSize, null);
                        } else {
                            g2.setColor(new Color(34, 139, 34));
                            g2.fillRect(x, y, tileSize, tileSize);
                        }
                        break;

                    case 6:
                        if (wallImage != null) {
                            g2.drawImage(wallImage, x, y, tileSize, tileSize, null);
                        } else {
                            g2.setColor(new Color(100, 100, 100));
                            g2.fillRect(x, y, tileSize, tileSize);
                        }
                        break;

                    case 2:
                        drawLandmark(g2, row, col, 2, physciImage,
                                new Color(70, 130, 200), "PHYSCI", physciDrawn);
                        break;

                    case 3:
                        drawLandmark(g2, row, col, 3, gateImage,
                                new Color(128, 0, 0), "GATE", gateDrawn);
                        break;

                    case 7:
                        drawLandmark(g2, row, col, 7, towerImage,
                                new Color(70, 130, 180), "TOWER", towerDrawn);
                        break;

                    case 8:
                        drawLandmark(g2, row, col, 8, obleImage,
                                new Color(139, 90, 43), "OBLE", obleDrawn);
                        break;

                    case 9:
                        drawLandmark(g2, row, col, 9, carillonImage,
                                new Color(60, 120, 60), "CARILLON", carillonDrawn);
                        break;

                    case 10:
                        drawLandmark(g2, row, col, 10, libraryImage,
                                new Color(80, 80, 160), "LIBRARY", libraryDrawn);
                        break;
                }
            }
        }
    }
}