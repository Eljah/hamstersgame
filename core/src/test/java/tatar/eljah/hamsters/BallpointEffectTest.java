package tatar.eljah.hamsters;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.Test;

import static org.junit.Assert.*;

public class BallpointEffectTest {
    @Test
    public void edgePixelsAreMoreOpaqueThanCenter() {
        GdxNativesLoader.load();
        Pixmap pixmap = new Pixmap(7, 7, Pixmap.Format.RGBA8888);
        // transparent background
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();
        // draw a filled square
        pixmap.setColor(0, 0, 0, 1);
        pixmap.fillRectangle(2, 2, 3, 3);

        BallpointEffect.apply(pixmap);

        Color color = new Color();
        Color.rgba8888ToColor(color, pixmap.getPixel(2, 2));
        float edgeAlpha = color.a;
        Color.rgba8888ToColor(color, pixmap.getPixel(3, 3));
        float centerAlpha = color.a;
        pixmap.dispose();

        assertTrue("Edge should be less transparent than center", edgeAlpha > centerAlpha);
    }
}
