package tatar.eljah.hamsters;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;

/** Utility to apply a ballpoint pen style effect to pixmaps. */
public class BallpointEffect {
    private BallpointEffect() {}

    /**
     * Darkens stroke edges while lightening their centers to mimic a ballpoint pen.
     * The pixmap is modified in place.
     */
    public static void apply(Pixmap pixmap) {
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        int[][] dist = new int[width][height];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        Color color = new Color();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color.rgba8888ToColor(color, pixmap.getPixel(x, y));
                if (color.a == 0f) {
                    dist[x][y] = 0;
                    queue.add(new int[]{x, y});
                } else {
                    dist[x][y] = Integer.MAX_VALUE;
                }
            }
        }

        int[] dx = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dy = {0, 0, -1, 1, -1, 1, -1, 1};

        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int x = p[0];
            int y = p[1];
            int d = dist[x][y];
            for (int i = 0; i < 8; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && dist[nx][ny] > d + 1) {
                    dist[nx][ny] = d + 1;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        int maxDist = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color.rgba8888ToColor(color, pixmap.getPixel(x, y));
                if (color.a > 0f && dist[x][y] > maxDist) {
                    maxDist = dist[x][y];
                }
            }
        }

        Pixmap.Blending old = pixmap.getBlending();
        pixmap.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixmap.getPixel(x, y);
                Color.rgba8888ToColor(color, pixel);
                if (color.a > 0f) {
                    float factor = 1f - (float) dist[x][y] / (float) maxDist;
                    factor = 0.2f + 0.8f * factor;
                    color.a *= factor;
                    pixmap.drawPixel(x, y, Color.rgba8888(color));
                }
            }
        }
        pixmap.setBlending(old);
    }
}
