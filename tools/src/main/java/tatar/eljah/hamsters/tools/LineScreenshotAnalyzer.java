package tatar.eljah.hamsters.tools;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import javax.imageio.ImageIO;

public class LineScreenshotAnalyzer {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: LineScreenshotAnalyzer <screenshot.png> [<screenshot.png>...]");
            System.exit(2);
        }
        Locale.setDefault(Locale.US);
        for (String path : args) {
            analyze(path);
        }
    }

    private static void analyze(String path) throws Exception {
        BufferedImage image = ImageIO.read(new File(path));
        if (image == null) {
            throw new IllegalArgumentException("Not an image: " + path);
        }
        ArrayList<Component> components = findInkComponents(image);
        System.out.println(path + " (" + image.getWidth() + "x" + image.getHeight() + ")");
        for (int i = 0; i < components.size(); i++) {
            Component component = components.get(i);
            Metrics metrics = measure(image, component.bounds);
            System.out.printf("  object%-2d bbox=%4d,%4d %3dx%-3d ink=%5.2f%% meanStroke=%5.2fpx norm=%5.2f%% maxStroke=%5.2fpx darkness=%5.2f%% rgb=%5.1f,%5.1f,%5.1f%n",
                    i + 1,
                    component.bounds.x,
                    component.bounds.y,
                    component.bounds.width,
                    component.bounds.height,
                    metrics.inkCoverage * 100f,
                    metrics.meanStrokeWidth,
                    metrics.meanStrokeWidth / Math.max(1f, component.bounds.height) * 100f,
                    metrics.maxStrokeWidth,
                    metrics.meanDarkness * 100f,
                    metrics.meanR,
                    metrics.meanG,
                    metrics.meanB);
        }
    }

    private static ArrayList<Component> findInkComponents(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] ink = new boolean[width][height];
        boolean[][] visited = new boolean[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                ink[x][y] = isInk(image.getRGB(x, y));
            }
        }

        ArrayList<Component> result = new ArrayList<>();
        int[] dx = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dy = {0, 0, -1, 1, -1, 1, -1, 1};
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!ink[x][y] || visited[x][y]) {
                    continue;
                }
                int minX = x;
                int maxX = x;
                int minY = y;
                int maxY = y;
                int count = 0;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                visited[x][y] = true;
                while (!queue.isEmpty()) {
                    int[] point = queue.removeFirst();
                    int px = point[0];
                    int py = point[1];
                    count++;
                    minX = Math.min(minX, px);
                    maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);
                    for (int i = 0; i < dx.length; i++) {
                        int nx = px + dx[i];
                        int ny = py + dy[i];
                        if (nx >= 0 && ny >= 0 && nx < width && ny < height && ink[nx][ny] && !visited[nx][ny]) {
                            visited[nx][ny] = true;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }
                Rectangle bounds = new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
                if (count >= 20 && bounds.width >= 4 && bounds.height >= 4) {
                    result.add(new Component(bounds, count));
                }
            }
        }
        result = mergeNearbyComponents(result, Math.max(10, Math.min(width, height) / 30));
        Collections.sort(result, new Comparator<Component>() {
            @Override
            public int compare(Component a, Component b) {
                return Integer.compare(a.bounds.x, b.bounds.x);
            }
        });
        return result;
    }

    private static ArrayList<Component> mergeNearbyComponents(ArrayList<Component> components, int padding) {
        boolean changed;
        do {
            changed = false;
            outer: for (int i = 0; i < components.size(); i++) {
                for (int j = i + 1; j < components.size(); j++) {
                    Rectangle a = expanded(components.get(i).bounds, padding);
                    Rectangle b = expanded(components.get(j).bounds, padding);
                    if (a.intersects(b) || a.contains(components.get(j).bounds) || b.contains(components.get(i).bounds)) {
                        Component merged = merge(components.get(i), components.get(j));
                        components.set(i, merged);
                        components.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        } while (changed);
        return components;
    }

    private static Rectangle expanded(Rectangle source, int padding) {
        return new Rectangle(source.x - padding, source.y - padding,
                source.width + padding * 2, source.height + padding * 2);
    }

    private static Component merge(Component a, Component b) {
        int minX = Math.min(a.bounds.x, b.bounds.x);
        int minY = Math.min(a.bounds.y, b.bounds.y);
        int maxX = Math.max(a.bounds.x + a.bounds.width, b.bounds.x + b.bounds.width);
        int maxY = Math.max(a.bounds.y + a.bounds.height, b.bounds.y + b.bounds.height);
        return new Component(new Rectangle(minX, minY, maxX - minX, maxY - minY), a.pixels + b.pixels);
    }

    private static boolean isInk(int argb) {
        int r = (argb >>> 16) & 0xff;
        int g = (argb >>> 8) & 0xff;
        int b = argb & 0xff;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int distanceFromWhite = Math.max(Math.max(255 - r, 255 - g), 255 - b);
        if (max < 25) {
            return false;
        }
        return distanceFromWhite > 25 && max - min > 10;
    }

    private static Metrics measure(BufferedImage image, Rectangle bounds) {
        int pad = 2;
        int x0 = Math.max(0, bounds.x - pad);
        int y0 = Math.max(0, bounds.y - pad);
        int x1 = Math.min(image.getWidth(), bounds.x + bounds.width + pad);
        int y1 = Math.min(image.getHeight(), bounds.y + bounds.height + pad);
        int width = Math.max(1, x1 - x0);
        int height = Math.max(1, y1 - y0);

        boolean[][] ink = new boolean[width][height];
        int inkPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean isInk = isInk(image.getRGB(x0 + x, y0 + y));
                ink[x][y] = isInk;
                if (isInk) {
                    inkPixels++;
                }
            }
        }
        if (inkPixels == 0) {
            return new Metrics(0f, 0f, 0f, 0f, 0f, 0f, 0f);
        }

        int[][] distance = new int[width][height];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!ink[x][y] || x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    distance[x][y] = 0;
                    queue.add(new int[]{x, y});
                } else {
                    distance[x][y] = Integer.MAX_VALUE;
                }
            }
        }

        int[] dx = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dy = {0, 0, -1, 1, -1, 1, -1, 1};
        while (!queue.isEmpty()) {
            int[] point = queue.removeFirst();
            int next = distance[point[0]][point[1]] + 1;
            for (int i = 0; i < dx.length; i++) {
                int nx = point[0] + dx[i];
                int ny = point[1] + dy[i];
                if (nx >= 0 && ny >= 0 && nx < width && ny < height && distance[nx][ny] > next) {
                    distance[nx][ny] = next;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        float sumStroke = 0f;
        float maxStroke = 0f;
        float sumDarkness = 0f;
        float sumR = 0f;
        float sumG = 0f;
        float sumB = 0f;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ink[x][y]) {
                    float stroke = Math.max(1f, distance[x][y] * 2f);
                    int argb = image.getRGB(x0 + x, y0 + y);
                    int r = (argb >>> 16) & 0xff;
                    int g = (argb >>> 8) & 0xff;
                    int b = argb & 0xff;
                    sumStroke += stroke;
                    maxStroke = Math.max(maxStroke, stroke);
                    sumDarkness += Math.max(Math.max(255 - r, 255 - g), 255 - b) / 255f;
                    sumR += r;
                    sumG += g;
                    sumB += b;
                }
            }
        }
        return new Metrics((float) inkPixels / (width * height), sumStroke / inkPixels, maxStroke,
                sumDarkness / inkPixels, sumR / inkPixels, sumG / inkPixels, sumB / inkPixels);
    }

    private static class Component {
        final Rectangle bounds;
        final int pixels;

        Component(Rectangle bounds, int pixels) {
            this.bounds = bounds;
            this.pixels = pixels;
        }
    }

    private static class Metrics {
        final float inkCoverage;
        final float meanStrokeWidth;
        final float maxStrokeWidth;
        final float meanDarkness;
        final float meanR;
        final float meanG;
        final float meanB;

        Metrics(float inkCoverage, float meanStrokeWidth, float maxStrokeWidth,
                float meanDarkness, float meanR, float meanG, float meanB) {
            this.inkCoverage = inkCoverage;
            this.meanStrokeWidth = meanStrokeWidth;
            this.maxStrokeWidth = maxStrokeWidth;
            this.meanDarkness = meanDarkness;
            this.meanR = meanR;
            this.meanG = meanG;
            this.meanB = meanB;
        }
    }
}
