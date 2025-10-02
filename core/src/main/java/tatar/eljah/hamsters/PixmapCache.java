package tatar.eljah.hamsters;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;

/**
 * Platform-specific helpers for caching pixmaps as PNG files.
 */
public final class PixmapCache {
    private PixmapCache() {
    }

    static boolean isSupported() {
        return true;
    }

    static Pixmap load(FileHandle file) {
        return new Pixmap(file);
    }

    static void save(FileHandle file, Pixmap pixmap) {
        PixmapIO.writePNG(file, pixmap);
    }

    static FileHandle resolveCacheDir() {
        String tmp = System.getProperty("java.io.tmpdir");
        FileHandle base = tmp != null ? Gdx.files.absolute(tmp) : Gdx.files.local(".");
        FileHandle dir = base.child("hamstersgame-cache");
        dir.mkdirs();
        return dir;
    }
}
