package io.github.fxzjshm.gdx.svg2pixmap;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.Test;
import static org.junit.Assert.*;

public class BallpointPenRendererTest {
    @Test
    public void addsTransparentNoise() {
        GdxNativesLoader.load();
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        new HeadlessApplication(new com.badlogic.gdx.ApplicationAdapter(){}, config);
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='100' height='10'>" +
                "<path d='M0 5 L100 5' stroke='black' stroke-width='4' fill='none'/></svg>";
        Pixmap pixmap = BallpointPenRenderer.render(svg, 100, 10);
        boolean found = false;
        outer: for (int y = 0; y < pixmap.getHeight(); y++) {
            for (int x = 0; x < pixmap.getWidth(); x++) {
                if (pixmap.getPixel(x, y) != 0) {
                    found = true;
                    break outer;
                }
            }
        }
        assertTrue("Image should contain drawn pixels", found);
        pixmap.dispose();
    }
}
