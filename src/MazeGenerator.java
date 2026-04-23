import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//Maze
public class MazeGenerator {
    public static int[][] generateMap() {
        int roomsX = 25;
        int roomsY = 15;

        int actualCols = 1 + roomsX * 4;
        int actualRows = 1 + roomsY * 4;

        int[][] map = new int[actualRows][actualCols];

        // Maze Maker
        for (int r = 0; r < actualRows; r++) {
            for (int c = 0; c < actualCols; c++) {
                map[r][c] = 6; // 6 = Wall
            }
        }

        boolean[][] visited = new boolean[roomsY][roomsX];
        carve(0, 0, visited, map, roomsX, roomsY);

        // Edges as Tree
        // Top and bottom edges
        for (int c = 0; c < actualCols; c++) {
            map[0][c] = 1;
            map[actualRows - 1][c] = 1;
        }
        // Left and right edges
        for (int r = 0; r < actualRows; r++) {
            map[r][0] = 1;
            map[r][actualCols - 1] = 1;
        }

        // Finish Line
        int midRoomX = roomsX / 2;
        int topR = 1 + 0 * 4;
        int midC = 1 + midRoomX * 4;

        for (int r = topR; r < topR + 3; r++) {
            for (int c = midC; c < midC + 3; c++) {
                map[r][c] = 2;
            }
        }

        // Start Position
        int botR = 1 + (roomsY - 1) * 4;
        for (int r = botR; r < botR + 3; r++) {
            for (int c = midC; c < midC + 3; c++) {
                map[r][c] = 3;
            }
        }

        return map;
    }

    private static void carve(int x, int y, boolean[][] visited, int[][] map, int roomsX, int roomsY) {
        visited[y][x] = true;

        int startR = 1 + y * 4;
        int startC = 1 + x * 4;

        // 3x3 paths (0 = Dirt Path)
        for (int r = startR; r < startR + 3; r++) {
            for (int c = startC; c < startC + 3; c++) {
                map[r][c] = 0;
            }
        }

        //Make maze variations by shuffling
        int[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        List<int[]> dirs = new ArrayList<>();
        for (int[] d : directions) dirs.add(d);
        Collections.shuffle(dirs);

        for (int[] dir : dirs) {
            int nx = x + dir[0];
            int ny = y + dir[1];

            if (nx >= 0 && nx < roomsX && ny >= 0 && ny < roomsY && !visited[ny][nx]) {
                if (dir[0] == 1) { // Right
                    map[startR][startC + 3] = 0; map[startR + 1][startC + 3] = 0; map[startR + 2][startC + 3] = 0;
                } else if (dir[0] == -1) { // Left
                    map[startR][startC - 1] = 0; map[startR + 1][startC - 1] = 0; map[startR + 2][startC - 1] = 0;
                } else if (dir[1] == 1) { // Down
                    map[startR + 3][startC] = 0; map[startR + 3][startC + 1] = 0; map[startR + 3][startC + 2] = 0;
                } else if (dir[1] == -1) { // Up
                    map[startR - 1][startC] = 0; map[startR - 1][startC + 1] = 0; map[startR - 1][startC + 2] = 0;
                }
                carve(nx, ny, visited, map, roomsX, roomsY);
            }
        }
    }
}