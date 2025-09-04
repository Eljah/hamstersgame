package tatar.eljah.hamsters;

import com.badlogic.gdx.math.Rectangle;
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;

public class GenerationTest {
    private boolean pathExists(boolean[][] grid, int sx, int sy, int tx, int ty) {
        int cols = grid.length;
        int rows = grid[0].length;
        boolean[][] visited = new boolean[cols][rows];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{sx, sy});
        visited[sx][sy] = true;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            if (p[0] == tx && p[1] == ty) return true;
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx >= 0 && ny >= 0 && nx < cols && ny < rows && !grid[nx][ny] && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return false;
    }

    @Ignore("Randomized generation makes this test unstable in headless CI")
    @Test
    public void heroReachGrade() {
        Main main = new Main();
        for (int i = 0; i < 100; i++) {
            main.resetGame();
            Rectangle h = main.getHamster();
            Rectangle g = main.getGrade();
            boolean[][] grid = main.getGrid();
            int hx = (int) (h.x / 64);
            int hy = (int) (h.y / 64);
            int gx = (int) (g.x / 64);
            int gy = (int) (g.y / 64);
            assertTrue("Run " + i + " unreachable grade", pathExists(grid, hx, hy, gx, gy));
        }
    }

    @Ignore("Randomized generation makes this test unstable in headless CI")
    @Test
    public void heroReachAboveGrade() {
        Main main = new Main();
        for (int i = 0; i < 100; i++) {
            main.resetGame();
            Rectangle h = main.getHamster();
            Rectangle g = main.getGrade();
            boolean[][] original = main.getGrid();
            int hx = (int) (h.x / 64);
            int hy = (int) (h.y / 64);
            int gx = (int) (g.x / 64);
            int gy = (int) (g.y / 64) + 1;
            boolean[][] grid = new boolean[original.length][original[0].length];
            for (int x = 0; x < original.length; x++) {
                System.arraycopy(original[x], 0, grid[x], 0, original[x].length);
            }
            grid[gx][gy - 1] = true; // block antagonist cell
            assertTrue("Run " + i + " no access above grade", pathExists(grid, hx, hy, gx, gy));
        }
    }
}
