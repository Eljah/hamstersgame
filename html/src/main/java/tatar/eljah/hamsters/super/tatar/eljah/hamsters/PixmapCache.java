package tatar.eljah.hamsters;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * GWT backend does not support pixmap disk caching.
 */
public final class PixmapCache {
    private PixmapCache() {
    }

    static boolean isSupported() {
        return false;
    }

    static Pixmap load(FileHandle file) {
        throw new UnsupportedOperationException("Pixmap caching is not supported on GWT");
    }

    static void save(FileHandle file, Pixmap pixmap) {
        // No-op
    }

    static FileHandle resolveCacheDir() {
        throw new UnsupportedOperationException("Pixmap caching is not supported on GWT");
    }
}
