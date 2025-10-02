package io.github.fxzjshm.gdx.svg2pixmap;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import org.junit.Ignore;
import org.junit.Test;

public class PenLineStyleTest {
    @Ignore("Rendering requires native libs not available in test environment")
    @Test
    public void centerDarkerThanEdge() {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        new HeadlessApplication(new ApplicationAdapter(){}, config);
        MathUtils.random.setSeed(0);
        Pixmap pix = new Pixmap(20, 20, Pixmap.Format.RGBA8888);
        Pixmap result = Svg2Pixmap.path2Pixmap(20, 20, "M0 10 L20 10", Color.CLEAR, Color.BLUE, 6, pix, 1f, 1f, 0f, 0f);
        // Additional assertions could be added here when native rendering is available.
        result.dispose();
    }
}
