package tatar.eljah.hamsters;

import static org.junit.Assert.assertTrue;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.LifecycleListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Ensures scene transition logging covers both gameplay and game over screens for each platform.
 */
public class MainSceneTransitionTest {

    private Application previousApp;

    @Before
    public void stashApp() {
        previousApp = Gdx.app;
    }

    @After
    public void restoreApp() {
        Gdx.app = previousApp;
    }

    @Test
    public void logsGameplayAndGameOverScenesForAllPlatforms() throws Exception {
        for (Application.ApplicationType type : new Application.ApplicationType[]{
                Application.ApplicationType.Android,
                Application.ApplicationType.Desktop
        }) {
            RecordingApplication app = new RecordingApplication(type);
            Gdx.app = app;

            Main main = new Main();
            main.resetGameWithReason("unit test start");

            Method triggerGameOver = Main.class.getDeclaredMethod("triggerGameOver", boolean.class, String.class);
            triggerGameOver.setAccessible(true);
            triggerGameOver.invoke(main, true, "unit test win");

            List<String> logs = app.getLogs();

            assertTrue("Scene 1 should be logged for " + type,
                    logs.stream().anyMatch(message -> message.contains("Scene 1")));
            assertTrue("Scene 2 should be logged for " + type,
                    logs.stream().anyMatch(message -> message.contains("Scene 2")));
            assertTrue("Platform name should be present in logs for " + type,
                    logs.stream().anyMatch(message -> message.contains(type.name())));
        }
    }

    private static final class RecordingApplication implements Application {
        private final ApplicationType type;
        private final List<String> logs = new ArrayList<>();
        private final RecordingLogger logger = new RecordingLogger();
        private int logLevel = LOG_INFO;

        private RecordingApplication(ApplicationType type) {
            this.type = type;
        }

        List<String> getLogs() {
            return Collections.unmodifiableList(logs);
        }

        @Override
        public ApplicationListener getApplicationListener() {
            throw new UnsupportedOperationException("Not needed for the test");
        }

        @Override
        public Graphics getGraphics() {
            return null;
        }

        @Override
        public Audio getAudio() {
            return null;
        }

        @Override
        public Input getInput() {
            return null;
        }

        @Override
        public Files getFiles() {
            return null;
        }

        @Override
        public Net getNet() {
            return null;
        }

        @Override
        public void log(String tag, String message) {
            logs.add(tag + ": " + message);
            logger.log(tag, message);
        }

        @Override
        public void log(String tag, String message, Throwable exception) {
            logs.add(tag + ": " + message + " (" + exception + ")");
            logger.log(tag, message, exception);
        }

        @Override
        public void error(String tag, String message) {
            logs.add("ERROR " + tag + ": " + message);
            logger.error(tag, message);
        }

        @Override
        public void error(String tag, String message, Throwable exception) {
            logs.add("ERROR " + tag + ": " + message + " (" + exception + ")");
            logger.error(tag, message, exception);
        }

        @Override
        public void debug(String tag, String message) {
            logs.add("DEBUG " + tag + ": " + message);
            logger.debug(tag, message);
        }

        @Override
        public void debug(String tag, String message, Throwable exception) {
            logs.add("DEBUG " + tag + ": " + message + " (" + exception + ")");
            logger.debug(tag, message, exception);
        }

        @Override
        public ApplicationType getType() {
            return type;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public long getJavaHeap() {
            return 0;
        }

        @Override
        public long getNativeHeap() {
            return 0;
        }

        @Override
        public Preferences getPreferences(String name) {
            throw new UnsupportedOperationException("Preferences not available in test");
        }

        @Override
        public Clipboard getClipboard() {
            return null;
        }

        @Override
        public void postRunnable(Runnable runnable) {
            runnable.run();
        }

        @Override
        public void exit() {
            // no-op
        }

        @Override
        public void addLifecycleListener(LifecycleListener listener) {
            // no-op
        }

        @Override
        public void removeLifecycleListener(LifecycleListener listener) {
            // no-op
        }

        @Override
        public void setLogLevel(int logLevel) {
            this.logLevel = logLevel;
        }

        @Override
        public int getLogLevel() {
            return logLevel;
        }

        @Override
        public ApplicationLogger getApplicationLogger() {
            return logger;
        }

        @Override
        public void setApplicationLogger(ApplicationLogger applicationLogger) {
            // ignore custom logger so we always capture entries
        }

        private final class RecordingLogger implements ApplicationLogger {
            @Override
            public void log(String tag, String message) {
                logs.add("LOGGER " + tag + ": " + message);
            }

            @Override
            public void log(String tag, String message, Throwable exception) {
                logs.add("LOGGER " + tag + ": " + message + " (" + exception + ")");
            }

            @Override
            public void error(String tag, String message) {
                logs.add("LOGGER ERROR " + tag + ": " + message);
            }

            @Override
            public void error(String tag, String message, Throwable exception) {
                logs.add("LOGGER ERROR " + tag + ": " + message + " (" + exception + ")");
            }

            @Override
            public void debug(String tag, String message) {
                logs.add("LOGGER DEBUG " + tag + ": " + message);
            }

            @Override
            public void debug(String tag, String message, Throwable exception) {
                logs.add("LOGGER DEBUG " + tag + ": " + message + " (" + exception + ")");
            }
        }
    }
}
