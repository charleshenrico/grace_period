import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MazeGenerator {
    public static int[][] generateMap() {
        int roomsX = 25;
        int roomsY = 15;

        int actualCols = 1 + roomsX * 4;
        int actualRows = 1 + roomsY * 4;

        int[][] map = new int[actualRows][actualCols];

        for (int r = 0; r < actualRows; r++) {
            for (int c = 0; c < actualCols; c++) {
                map[r][c] = 6;
            }
        }

        boolean[][] visited = new boolean[roomsY][roomsX];
        carve(0, 0, visited, map, roomsX, roomsY);

        for (int c = 0; c < actualCols; c++) {
            map[0][c] = 1;
            map[actualRows - 1][c] = 1;
        }
        for (int r = 0; r < actualRows; r++) {
            map[r][0] = 1;
            map[r][actualCols - 1] = 1;
        }

        int midC = actualCols / 2 - 4;

        int physciStartR = 1;
        int physciStartC = midC;
        placeLandmark(map, physciStartR, physciStartC, 2, actualRows, actualCols);

        int gateStartR = actualRows - 11;
        int gateStartC = midC;
        placeLandmark(map, gateStartR, gateStartC, 3, actualRows, actualCols);

        int towerStartR = Math.max(2, actualRows / 4 - 4);
        int towerStartC = Math.max(2, actualCols / 4 - 4);
        placeLandmark(map, towerStartR, towerStartC, 7, actualRows, actualCols);

        int obleStartR = Math.min(actualRows - 12, Math.max(2, (actualRows * 3) / 4 - 4));
        int obleStartC = Math.min(actualCols - 12, Math.max(2, (actualCols * 3) / 4 - 4));
        placeLandmark(map, obleStartR, obleStartC, 8, actualRows, actualCols);

        int carillonStartR = Math.max(2, actualRows / 4 - 4);
        int carillonStartC = Math.min(actualCols - 12, Math.max(2, (actualCols * 3) / 4 - 4));
        placeLandmark(map, carillonStartR, carillonStartC, 9, actualRows, actualCols);

        int libraryStartR = Math.min(actualRows - 12, Math.max(2, (actualRows * 3) / 4 - 4));
        int libraryStartC = Math.max(2, actualCols / 4 - 4);
        placeLandmark(map, libraryStartR, libraryStartC, 10, actualRows, actualCols);

        return map;
    }

    private static void placeLandmark(int[][] map, int startR, int startC, int tileType, int rows, int cols) {
        for (int r = startR - 1; r < startR + 11; r++) {
            for (int c = startC - 1; c < startC + 11; c++) {
                if (r >= 1 && c >= 1 && r < rows - 1 && c < cols - 1) {
                    int cur = map[r][c];
                    if (cur != 2 && cur != 3 && cur != 7 && cur != 8 && cur != 9 && cur != 10) {
                        map[r][c] = 0;
                    }
                }
            }
        }
        for (int r = startR; r < startR + 9; r++) {
            for (int c = startC; c < startC + 9; c++) {
                if (r >= 1 && c >= 1 && r < rows - 1 && c < cols - 1) {
                    map[r][c] = tileType;
                }
            }
        }
    }

    private static void carve(int x, int y, boolean[][] visited, int[][] map, int roomsX, int roomsY) {
        visited[y][x] = true;
        int startR = 1 + y * 4;
        int startC = 1 + x * 4;

        for (int r = startR; r < startR + 3; r++) {
            for (int c = startC; c < startC + 3; c++) {
                map[r][c] = 0;
            }
        }

        int[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        List<int[]> dirs = new ArrayList<>();
        for (int[] d : directions) dirs.add(d);
        Collections.shuffle(dirs);

        for (int[] dir : dirs) {
            int nx = x + dir[0];
            int ny = y + dir[1];

            if (nx >= 0 && nx < roomsX && ny >= 0 && ny < roomsY && !visited[ny][nx]) {
                if (dir[0] == 1) {
                    map[startR][startC + 3] = 0; map[startR + 1][startC + 3] = 0; map[startR + 2][startC + 3] = 0;
                } else if (dir[0] == -1) {
                    map[startR][startC - 1] = 0; map[startR + 1][startC - 1] = 0; map[startR + 2][startC - 1] = 0;
                } else if (dir[1] == 1) {
                    map[startR + 3][startC] = 0; map[startR + 3][startC + 1] = 0; map[startR + 3][startC + 2] = 0;
                } else if (dir[1] == -1) {
                    map[startR - 1][startC] = 0; map[startR - 1][startC + 1] = 0; map[startR - 1][startC + 2] = 0;
                }
                carve(nx, ny, visited, map, roomsX, roomsY);
            }
        }
    }
}