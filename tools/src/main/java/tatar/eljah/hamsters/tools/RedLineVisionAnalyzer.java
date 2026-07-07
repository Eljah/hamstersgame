package tatar.eljah.hamsters.tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Locale;
import javax.imageio.ImageIO;

public class RedLineVisionAnalyzer {
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        BufferedImage image = ImageIO.read(new File(args.length == 0 ? "android-phone-line.png" : args[0]));
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] mask = new boolean[width][height];
        int minX = width, minY = height, maxX = -1, maxY = -1, count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >>> 16) & 255;
                int g = (rgb >>> 8) & 255;
                int b = rgb & 255;
                boolean red = r > 120 && r - g > 45 && r - b > 45;
                mask[x][y] = red;
                if (red) {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                    count++;
                }
            }
        }
        System.out.printf("red pixels=%d bbox=%d,%d %dx%d%n", count, minX, minY, maxX - minX + 1, maxY - minY + 1);
        int pad = 8;
        int x0 = Math.max(0, minX - pad), y0 = Math.max(0, minY - pad);
        int x1 = Math.min(width, maxX + 1 + pad), y1 = Math.min(height, maxY + 1 + pad);
        int w = x1 - x0, h = y1 - y0;
        boolean[][] cropMask = new boolean[w][h];
        int[][] dist = new int[w][h];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean ink = mask[x0 + x][y0 + y];
                cropMask[x][y] = ink;
                if (!ink || x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    dist[x][y] = 0; queue.add(new int[]{x, y});
                } else {
                    dist[x][y] = Integer.MAX_VALUE;
                }
            }
        }
        int[] dx = {-1,1,0,0,-1,-1,1,1}, dy = {0,0,-1,1,-1,1,-1,1};
        while (!queue.isEmpty()) {
            int[] p = queue.removeFirst();
            int next = dist[p[0]][p[1]] + 1;
            for (int i = 0; i < dx.length; i++) {
                int nx = p[0] + dx[i], ny = p[1] + dy[i];
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && dist[nx][ny] > next) {
                    dist[nx][ny] = next; queue.add(new int[]{nx, ny});
                }
            }
        }
        int maxD = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (cropMask[x][y]) maxD = Math.max(maxD, dist[x][y]);
        System.out.printf("max distance radius=%d approx maxStroke=%d%n", maxD, maxD * 2);

        int bins = 10;
        double[] sumDark = new double[bins];
        int[] binCount = new int[bins];
        double centerWaveSum = 0, centerWaveSum2 = 0;
        int centerWaveN = 0;
        double[] xSum = new double[20];
        int[] xCount = new int[20];
        int segments = 5;
        double[][] segmentDark = new double[segments][bins];
        int[][] segmentCount = new int[segments][bins];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!cropMask[x][y]) continue;
                double u = maxD == 0 ? 0 : (double) dist[x][y] / maxD;
                int bin = Math.min(bins - 1, Math.max(0, (int)Math.floor(u * bins)));
                int rgb = image.getRGB(x0 + x, y0 + y);
                int r = (rgb >>> 16) & 255, g = (rgb >>> 8) & 255, b = rgb & 255;
                double dark = Math.max(Math.max(255 - r, 255 - g), 255 - b) / 255.0;
                sumDark[bin] += dark; binCount[bin]++;
                int segment = Math.min(segments - 1, Math.max(0, x * segments / Math.max(1, w)));
                segmentDark[segment][bin] += dark;
                segmentCount[segment][bin]++;
                if (u >= 0.35 && u <= 0.75) {
                    centerWaveSum += dark; centerWaveSum2 += dark * dark; centerWaveN++;
                    int xb = Math.min(19, Math.max(0, x * 20 / w));
                    xSum[xb] += dark; xCount[xb]++;
                }
            }
        }
        System.out.println("profile u=edge(0)..center(1):");
        for (int i = 0; i < bins; i++) {
            if (binCount[i] > 0) {
                System.out.printf("  %.1f-%.1f n=%5d darkMean=%6.2f%%%n", i / 10.0, (i + 1) / 10.0, binCount[i], sumDark[i] / binCount[i] * 100.0);
            }
        }
        double edgePeak = avg(sumDark, binCount, 1, 2);
        double innerShoulder = avg(sumDark, binCount, 3, 4);
        double centerPeak = avg(sumDark, binCount, 8, 9);
        double centerMean = centerWaveSum / Math.max(1, centerWaveN);
        double centerStd = Math.sqrt(Math.max(0, centerWaveSum2 / Math.max(1, centerWaveN) - centerMean * centerMean));
        double minXMean = 1, maxXMean = 0;
        for (int i = 0; i < 20; i++) {
            if (xCount[i] < 20) continue;
            double m = xSum[i] / xCount[i];
            minXMean = Math.min(minXMean, m);
            maxXMean = Math.max(maxXMean, m);
        }
        System.out.printf("score edgePeak=%.2f%% innerShoulder=%.2f%% centerPeak=%.2f%% centerBandStd=%.2f%% centerXRange=%.2f%%%n",
                edgePeak * 100, innerShoulder * 100, centerPeak * 100, centerStd * 100, (maxXMean - minXMean) * 100);
        System.out.println("local profiles by x segment, edge/vein/inner/center:");
        for (int s = 0; s < segments; s++) {
            double edge = avg(segmentDark[s], segmentCount[s], 0, 0);
            double vein = avg(segmentDark[s], segmentCount[s], 1, 2);
            double inner = avg(segmentDark[s], segmentCount[s], 3, 5);
            double center = avg(segmentDark[s], segmentCount[s], 6, 9);
            System.out.printf("  segment%d edge=%.2f%% vein=%.2f%% inner=%.2f%% center=%.2f%%%n",
                    s + 1, edge * 100, vein * 100, inner * 100, center * 100);
        }
    }

    private static double avg(double[] sums, int[] counts, int from, int to) {
        double sum = 0; int n = 0;
        for (int i = from; i <= to; i++) { sum += sums[i]; n += counts[i]; }
        return n == 0 ? 0 : sum / n;
    }
}
