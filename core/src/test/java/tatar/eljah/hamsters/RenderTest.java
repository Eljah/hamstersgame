package tatar.eljah.hamsters;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import io.github.fxzjshm.gdx.svg2pixmap.Svg2Pixmap;
import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class RenderTest {
    @Test
    public void svgCharactersRender() throws Exception {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        new HeadlessApplication(new ApplicationAdapter(){}, config);
        String hamsterSvg = new String(Files.readAllBytes(Paths.get("../assets/hamster.svg")));
        String gradeSvg = new String(Files.readAllBytes(Paths.get("../assets/grade.svg")));
        Pixmap h = Svg2Pixmap.svg2Pixmap(hamsterSvg);
        Pixmap g = Svg2Pixmap.svg2Pixmap(gradeSvg);
        Pixmap background = new Pixmap(h.getWidth() + g.getWidth(), Math.max(h.getHeight(), g.getHeight()), Pixmap.Format.RGBA8888);
        background.setColor(Color.WHITE);
        background.fill();
        background.drawPixmap(h, 0, 0);
        background.drawPixmap(g, h.getWidth(), 0);
        PixmapIO.writePNG(Gdx.files.local("build/render_test.png"), background);
        boolean hasPixels = false;
        for (int x = 0; x < background.getWidth() && !hasPixels; x++) {
            for (int y = 0; y < background.getHeight(); y++) {
                int color = background.getPixel(x, y);
                if (color != Color.rgba8888(Color.WHITE) && ((color >>> 24) & 0xff) != 0) {
                    hasPixels = true;
                    break;
                }
            }
        }
        h.dispose();
        g.dispose();
        background.dispose();
        assertTrue("Rendered image is blank", hasPixels);
    }
}
