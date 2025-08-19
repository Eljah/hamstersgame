package tatar.eljah.hamsters.tools;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;

public class ToolsLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Assets Preview");
        config.setWindowedMode(800, 600);
        new Lwjgl3Application(new AssetViewer(), config);
    }

    private static class AssetViewer extends ApplicationAdapter {
        private SpriteBatch batch;
        private Texture[] textures;
        private final String[] files = {
                "block.png",
                "controls.png",
                "grade.png",
                "hamster.png",
                "libgdx.png",
                "liner.png"
        };

        @Override
        public void create() {
            batch = new SpriteBatch();
            textures = new Texture[files.length];
            for (int i = 0; i < files.length; i++) {
                textures[i] = new Texture(files[i]);
            }
        }

        @Override
        public void render() {
            ScreenUtils.clear(0f, 0f, 0f, 1f);
            batch.begin();
            for (int i = 0; i < textures.length; i++) {
                int x = (i % 3) * 200 + 20;
                int y = 400 - (i / 3) * 200;
                batch.draw(textures[i], x, y, 128, 128);
            }
            batch.end();
        }

        @Override
        public void dispose() {
            for (Texture t : textures) {
                t.dispose();
            }
            batch.dispose();
        }
    }
}
