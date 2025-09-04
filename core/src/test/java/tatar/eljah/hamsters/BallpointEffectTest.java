package tatar.eljah.hamsters;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;

import org.junit.Test;
import static org.junit.Assert.*;

public class BallpointEffectTest {
    @Test
    public void edgePixelsBecomeMoreTransparent() throws Exception {
        GdxNativesLoader.load();
        Pixmap pixmap = new Pixmap(10,5, Pixmap.Format.RGBA8888);
        pixmap.setColor(0,0,0,0);
        pixmap.fill();
        pixmap.setColor(Color.BLACK);
        pixmap.fillRectangle(0,1,10,3);

        Main.applyBallpointEffect(pixmap);

        Color edge = new Color();
        Color center = new Color();
        Color.rgba8888ToColor(edge, pixmap.getPixel(5,1));
        Color.rgba8888ToColor(center, pixmap.getPixel(5,2));
        assertTrue("Center should be more opaque than edge", center.a > edge.a);

        // Save for visual inspection
        PixmapIO.writePNG(new FileHandle(new java.io.File("ballpoint.png")), pixmap);
        pixmap.dispose();
    }
}
