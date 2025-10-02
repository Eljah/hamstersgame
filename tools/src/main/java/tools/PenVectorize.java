package tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Generate ballpoint-pen style PNG and SVG from input PNG.
 *
 * The SVG uses a radial gradient so that color fades towards the edges,
 * imitating the pressure variation of a real pen. The PNG alpha channel
 * is modulated using a distance transform so that the center of strokes is
 * more opaque.
 */
public class PenVectorize {
    private static final float SQRT2 = (float) Math.sqrt(2.0);

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: PenVectorize <image.png> --color <blue|red>");
            return;
        }
        Path imagePath = Paths.get(args[0]);
        String colorArg;
        if ("--color".equals(args[1]) && args.length >= 3) {
            colorArg = args[2];
        } else if (args[1].startsWith("--color=")) {
            colorArg = args[1].substring("--color=".length());
        } else {
            colorArg = args[1];
        }
        process(imagePath, colorArg);
    }

    private static void process(Path inputPath, String color) throws IOException {
        color = color.toLowerCase(Locale.ROOT);
        int rgb;
        String gradientId;
        String colorHex;
        if ("blue".equals(color)) {
            rgb = 0x0033cc;
            gradientId = "bluePen";
            colorHex = "#0033cc";
        } else if ("red".equals(color)) {
            rgb = 0xcc0000;
            gradientId = "redPen";
            colorHex = "#cc0000";
        } else {
            throw new IllegalArgumentException("Unsupported color; use 'blue' or 'red'");
        }

        penPng(inputPath, rgb);
        penSvg(inputPath, gradientId, colorHex);
    }

    private static void penPng(Path path, int rgb) throws IOException {
        BufferedImage img = ImageIO.read(path.toFile());
        int w = img.getWidth();
        int h = img.getHeight();
        float[][] alpha = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (img.getRGB(x, y) >>> 24) & 0xff;
                alpha[y][x] = a / 255f;
            }
        }

        float[][] dist = distanceTransform(alpha, w, h);
        float max = 0f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (alpha[y][x] > 0f) {
                    max = Math.max(max, dist[y][x]);
                }
            }
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (alpha[y][x] == 0f) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                float a = alpha[y][x];
                float d = max == 0f ? a : dist[y][x] / max;
                float newAlpha = (0.3f + 0.7f * d) * a;
                int ia = Math.min(255, Math.max(0, Math.round(newAlpha * 255f)));
                int argb = (ia << 24) | rgb;
                out.setRGB(x, y, argb);
            }
        }
        ImageIO.write(out, "PNG", path.toFile());
    }

    private static float[][] distanceTransform(float[][] alpha, int w, int h) {
        float[][] dist = new float[h][w];
        float INF = 1e6f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dist[y][x] = alpha[y][x] == 0f ? 0f : INF;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (dist[y][x] == 0f) continue;
                float min = dist[y][x];
                if (x > 0) min = Math.min(min, dist[y][x - 1] + 1f);
                if (y > 0) min = Math.min(min, dist[y - 1][x] + 1f);
                if (x > 0 && y > 0) min = Math.min(min, dist[y - 1][x - 1] + SQRT2);
                if (x + 1 < w && y > 0) min = Math.min(min, dist[y - 1][x + 1] + SQRT2);
                dist[y][x] = min;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                if (dist[y][x] == 0f) continue;
                float min = dist[y][x];
                if (x + 1 < w) min = Math.min(min, dist[y][x + 1] + 1f);
                if (y + 1 < h) min = Math.min(min, dist[y + 1][x] + 1f);
                if (x + 1 < w && y + 1 < h) min = Math.min(min, dist[y + 1][x + 1] + SQRT2);
                if (x > 0 && y + 1 < h) min = Math.min(min, dist[y + 1][x - 1] + SQRT2);
                dist[y][x] = min;
            }
        }
        return dist;
    }

    private static void penSvg(Path inputPng, String gradientId, String colorHex) throws IOException {
        BufferedImage img = ImageIO.read(inputPng.toFile());
        int w = img.getWidth();
        int h = img.getHeight();
        StringBuilder rects = new StringBuilder();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (img.getRGB(x, y) >>> 24) & 0xff;
                if (a > 0) {
                    rects.append("<rect x=\"").append(x).append("\" y=\"")
                         .append(y).append("\" width=\"1\" height=\"1\" fill=\"url(#")
                         .append(gradientId).append(")\" />\n");
                }
            }
        }
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + w +
                "\" height=\"" + h + "\" viewBox=\"0 0 " + w + " " + h +
                "\" shape-rendering=\"crispEdges\">\n" +
                "<defs>\n" +
                "  <radialGradient id=\"" + gradientId + "\" cx=\"0.5\" cy=\"0.5\" r=\"0.5\">\n" +
                "    <stop offset=\"0%\" stop-color=\"" + colorHex + "\" stop-opacity=\"1\"/>\n" +
                "    <stop offset=\"100%\" stop-color=\"" + colorHex + "\" stop-opacity=\"0.2\"/>\n" +
                "  </radialGradient>\n" +
                "</defs>\n" +
                rects +
                "</svg>\n";
        Path outSvg = inputPng.resolveSibling(replaceExtension(inputPng.getFileName().toString(), "svg"));
        Files.write(outSvg, svg.getBytes(StandardCharsets.UTF_8));
    }

    private static String replaceExtension(String fileName, String newExt) {
        int idx = fileName.lastIndexOf('.');
        if (idx >= 0) {
            return fileName.substring(0, idx + 1) + newExt;
        }
        return fileName + '.' + newExt;
    }
}
