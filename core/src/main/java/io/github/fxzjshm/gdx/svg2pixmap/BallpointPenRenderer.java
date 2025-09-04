package io.github.fxzjshm.gdx.svg2pixmap;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;

import tatar.eljah.hamsters.Main;

/**
 * Utility for rendering SVG line art into a PNG that mimics a blue ballpoint pen.
 * <p>
 * The class converts an SVG into a {@link Pixmap} using {@link Svg2Pixmap}, applies
 * the existing {@link Main#applyBallpointEffect(Pixmap)} to soften edges, then adds
 * deterministic colour and transparency jitter so that the result resembles a
 * hand‑drawn ballpoint pen line. The produced pixmap can optionally be written
 * to a PNG file.
 */
public class BallpointPenRenderer {

    /** Base colour of the ballpoint pen (a vivid blue). */
    private static final Color BASE_COLOR = Color.valueOf("2f2aa8");

    private BallpointPenRenderer() {
        // Utility class
    }

    /**
     * Render the supplied SVG content into a pixmap with ballpoint style.
     *
     * @param svg    SVG string containing line elements.
     * @param width  width of the target pixmap.
     * @param height height of the target pixmap.
     * @return pixmap with ballpoint pen rendering. Caller must dispose it.
     */
    public static Pixmap render(String svg, int width, int height) {
        Pixmap pixmap = Svg2Pixmap.svg2Pixmap(svg, width, height);
        Main.applyBallpointEffect(pixmap);
        applyInkNoise(pixmap);
        return pixmap;
    }

    /**
     * Render the SVG and immediately write the result to a PNG file.
     *
     * @param svg    SVG string.
     * @param width  width of the target image.
     * @param height height of the target image.
     * @param output where to write the PNG.
     */
    public static void renderToFile(String svg, int width, int height, FileHandle output) {
        Pixmap pixmap = render(svg, width, height);
        PixmapIO.writePNG(output, pixmap);
        pixmap.dispose();
    }

    /**
     * Apply deterministic colour jitter and occasional transparent spots to emulate
     * the texture of a ballpoint pen.
     */
    private static void applyInkNoise(Pixmap pixmap) {
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        Color color = new Color();
        Pixmap.Blending old = pixmap.getBlending();
        pixmap.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixmap.getPixel(x, y);
                Color.rgba8888ToColor(color, pixel);
                if (color.a > 0f) {
                    // Base vivid blue tone
                    color.r = BASE_COLOR.r;
                    color.g = BASE_COLOR.g;
                    color.b = BASE_COLOR.b;
                    // Small colour variation
                    float jitter = pseudoRandom(x, y) * 0.05f - 0.025f;
                    color.r = MathUtils.clamp(color.r + jitter, 0f, 1f);
                    color.g = MathUtils.clamp(color.g + jitter, 0f, 1f);
                    color.b = MathUtils.clamp(color.b + jitter, 0f, 1f);
                    float baseAlpha = color.a;
                    // Deterministic sparse transparent dots
                    if (((x + y) % 20) == 0) {
                        color.a = baseAlpha * 0.5f;
                    } else {
                        color.a = baseAlpha;
                    }
                    pixmap.drawPixel(x, y, Color.rgba8888(color));
                }
            }
        }
        pixmap.setBlending(old);
    }

    /**
     * Deterministic pseudo-random value in [-1,1] for the given coordinates.
     */
    private static float pseudoRandom(int x, int y) {
        int n = x * 1619 + y * 31337;
        n = (n << 13) ^ n;
        return 1f - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824f;
    }
}
