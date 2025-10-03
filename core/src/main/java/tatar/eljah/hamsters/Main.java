package tatar.eljah.hamsters;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.async.AsyncExecutor;
import com.badlogic.gdx.utils.async.AsyncResult;
import com.badlogic.gdx.utils.async.AsyncTask;
import com.badlogic.gdx.files.FileHandle;
import io.github.fxzjshm.gdx.svg2pixmap.Svg2Pixmap;
import tatar.eljah.hamsters.PixmapCache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private Texture hamsterTexture;
    private Texture gradeTexture;
    private Texture backgroundTexture;
    private Pixmap backgroundPixmap;
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;
    private Music backgroundMusic;

    private OrthographicCamera camera;

    private Rectangle hamster;
    private Rectangle grade;
    private Array<Block> blocks;
    private Array<Block> sceneBlocks = new Array<>();

    private Vector2 gradeDirection;
    private boolean gameOver;
    private boolean hamsterWin;
    private boolean[][] grid;
    private int hamsterScore;
    private int gradeScore;
    private OnscreenControlRenderer controlRenderer;
    private int framesRendered;
    private static final boolean AUTO_EXIT = Boolean.parseBoolean(System.getProperty("headless", "false"));
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private volatile boolean loading;
    private volatile float loadingProgress;
    private FileHandle cacheDir;
    private int[] corridorCenters;
    private final ObjectMap<String, BlockTemplate> blockTemplateCache = new ObjectMap<>();
    private AsyncExecutor loaderExecutor;
    private final Array<LoadingStep> loadingSteps = new Array<>();
    private LoadingStep currentStep;
    private int currentStepIndex;
    private float completedWeight;
    private float totalWeight;
    private JsonValue sceneJson;
    private int sceneWidth = 800;
    private int sceneHeight = 600;
    private Array<String> levelFiles = new Array<>();
    private int currentLevelIndex;
    private boolean advanceToNextLevel;
    private boolean showVictoryScreen;

    private static final Pattern SCENE_FILE_PATTERN = Pattern.compile("scene(\\d+)\\.json");
    @Override
    public void create() {
        if (Gdx.app.getType() == Application.ApplicationType.WebGL) {
            Svg2Pixmap.generateScale = 1;
        }

        discoverLevels();
        currentLevelIndex = 0;

        batch = new SpriteBatch();
        font = new BitmapFont();
        shapeRenderer = new ShapeRenderer();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, sceneWidth, sceneHeight);

        backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal("aldermeshka.mp3"));
        backgroundMusic.setLooping(true);
        backgroundMusic.setVolume(0.5f);
        backgroundMusic.play();

        hamsterScore = 0;
        gradeScore = 0;

        loading = true;
        loadingProgress = 0f;
        if (PixmapCache.isSupported()) {
            cacheDir = PixmapCache.resolveCacheDir();
        }

        loaderExecutor = new AsyncExecutor(1);
        startLevel(currentLevelIndex);
    }

    private void discoverLevels() {
        levelFiles.clear();
        FileHandle scenesDir = Gdx.files.internal("scenes");
        if (scenesDir.exists() && scenesDir.isDirectory()) {
            for (FileHandle file : scenesDir.list()) {
                Matcher matcher = SCENE_FILE_PATTERN.matcher(file.name());
                if (matcher.matches()) {
                    levelFiles.add(file.name());
                }
            }
        }
        levelFiles.sort(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return Integer.compare(extractLevelNumber(o1), extractLevelNumber(o2));
            }
        });
        if (levelFiles.size == 0) {
            levelFiles.add("scene1.json");
        }
    }

    private int extractLevelNumber(String fileName) {
        Matcher matcher = SCENE_FILE_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return Integer.MAX_VALUE;
    }

    private void startLevel(int levelIndex) {
        if (levelFiles.size == 0) {
            Gdx.app.log("Main", "No level files found; cannot start level");
            return;
        }
        int targetIndex = MathUtils.clamp(levelIndex, 0, levelFiles.size - 1);
        currentLevelIndex = targetIndex;
        advanceToNextLevel = false;
        showVictoryScreen = false;
        gameOver = false;
        hamsterWin = false;
        loading = true;
        loadingProgress = 0f;

        disposeLevelAssets();

        loadSceneForCurrentLevel();

        if (camera != null) {
            camera.setToOrtho(false, sceneWidth, sceneHeight);
        }

        prepareLoadingSteps();
        startNextLoadingStep();
    }

    private void loadSceneForCurrentLevel() {
        sceneJson = null;
        String levelFile = levelFiles.get(currentLevelIndex);
        try {
            sceneJson = new JsonReader().parse(Gdx.files.internal("scenes/" + levelFile));
        } catch (Exception e) {
            Gdx.app.error("Main", "Failed to load scene configuration from scenes/" + levelFile, e);
        }
        if (sceneJson == null) {
            sceneWidth = 800;
            sceneHeight = 600;
            Gdx.app.log("Main", "Scene configuration '" + levelFile + "' unavailable; falling back to default layout");
        } else {
            sceneWidth = sceneJson.getInt("canvasWidth", 800);
            sceneHeight = sceneJson.getInt("canvasHeight", 600);
        }
    }

    private void disposeLevelAssets() {
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
            backgroundTexture = null;
        }
        if (backgroundPixmap != null) {
            backgroundPixmap.dispose();
            backgroundPixmap = null;
        }
    }

    private void restartCampaign() {
        hamsterScore = 0;
        gradeScore = 0;
        startLevel(0);
    }

    private void calculateCorridors() {
        int width = backgroundPixmap.getWidth();
        int height = backgroundPixmap.getHeight();
        java.util.ArrayList<Integer> lines = new java.util.ArrayList<>();
        Color c = new Color();
        boolean inLine = false;
        for (int y = 0; y < height; y++) {
            int nonWhite = 0;
            for (int x = 0; x < width; x++) {
                Color.rgba8888ToColor(c, backgroundPixmap.getPixel(x, y));
                if (c.r < 0.9f || c.g < 0.9f || c.b < 0.9f) {
                    nonWhite++;
                }
            }
            if (nonWhite > 50) {
                if (!inLine) {
                    lines.add(y);
                    inLine = true;
                }
            } else if (inLine) {
                lines.add(y - 1);
                inLine = false;
            }
        }
        if (inLine) {
            lines.add(height - 1);
        }
        java.util.ArrayList<Integer> centers = new java.util.ArrayList<>();
        int prevEnd = -1;
        for (int i = 0; i < lines.size(); i += 2) {
            int start = lines.get(i);
            int end = lines.get(i + 1);
            int top = prevEnd + 1;
            int bottom = start - 1;
            centers.add((top + bottom) / 2);
            prevEnd = end;
        }
        centers.add((prevEnd + 1 + height - 1) / 2);
        corridorCenters = centers.stream().mapToInt(Integer::intValue).toArray();
    }

    private Array<Block> loadSceneBlocks(JsonValue sceneJson) {
        Array<Block> result = new Array<>();
        if (sceneJson == null) {
            return result;
        }
        JsonValue blocksJson = sceneJson.get("blocks");
        if (blocksJson == null) {
            return result;
        }
        for (JsonValue blockValue : blocksJson) {
            String blockFile = blockValue.getString("block", null);
            if (blockFile == null) {
                continue;
            }
            BlockTemplate template = loadBlockTemplate(blockFile);
            if (template == null) {
                continue;
            }
            float offsetX = blockValue.getFloat("x", 0f);
            float offsetY = blockValue.getFloat("y", 0f);
            Block block = instantiateBlock(template, offsetX, offsetY);
            if (block != null) {
                result.add(block);
            }
        }
        return result;
    }

    private void prepareLoadingSteps() {
        loadingSteps.clear();
        currentStep = null;
        currentStepIndex = 0;
        completedWeight = 0f;
        totalWeight = 0f;

        final String hamsterSvgOriginal = Gdx.files.internal("hamster4.svg").readString();
        final float finalHamsterStroke = Math.max(1.5f, Gdx.graphics.getWidth() / 400f);
        final float hamsterStrokeScale = computeStrokeScale(hamsterSvgOriginal, finalHamsterStroke);
        final String hamsterSvg = hamsterSvgOriginal.replaceAll("stroke-width=\\\"[0-9.]+\\\"",
                "stroke-width=\\\"" + hamsterStrokeScale + "\\\"");
        loadingSteps.add(new SvgTextureStep(1f, "hamster", hamsterSvg, 256, 256,
                new PixmapProcessor() {
                    @Override
                    public Pixmap process(Pixmap pixmap) {
                        applyBallpointEffect(pixmap);
                        return trimTransparent(pixmap);
                    }
                },
                new PixmapConsumer() {
                    @Override
                    public void accept(Pixmap pixmap) {
                        hamsterTexture = new Texture(pixmap);
                        hamsterTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                        pixmap.dispose();
                    }
                }));

        final String gradeSvg = Gdx.files.internal("grade.svg").readString();
        loadingSteps.add(new SvgTextureStep(1f, "grade", gradeSvg, 64, 64,
                null,
                new PixmapConsumer() {
                    @Override
                    public void accept(Pixmap pixmap) {
                        gradeTexture = new Texture(pixmap);
                        pixmap.dispose();
                    }
                }));

        String backgroundFile = "liner.svg";
        if (sceneJson != null) {
            backgroundFile = sceneJson.getString("background", backgroundFile);
        }
        FileHandle backgroundHandle = Gdx.files.internal(backgroundFile);
        String backgroundSvg;
        try {
            backgroundSvg = backgroundHandle.readString();
        } catch (Exception e) {
            Gdx.app.error("Main", "Failed to load background '" + backgroundFile + "', falling back to liner.svg", e);
            backgroundFile = "liner.svg";
            backgroundHandle = Gdx.files.internal(backgroundFile);
            backgroundSvg = backgroundHandle.readString();
        }
        final String backgroundCacheName = ("scene-" + backgroundFile).replace('/', '_').replace('\\', '_').replace('.', '_');
        final String backgroundSvgFinal = backgroundSvg;
        loadingSteps.add(new SvgTextureStep(1f, backgroundCacheName, backgroundSvgFinal, sceneWidth, sceneHeight,
                null,
                new PixmapConsumer() {
                    @Override
                    public void accept(Pixmap pixmap) {
                        backgroundPixmap = pixmap;
                        backgroundTexture = new Texture(backgroundPixmap);
                    }
                }));

        ObjectSet<String> blockFiles = new ObjectSet<>();
        if (sceneJson != null) {
            JsonValue blocksJson = sceneJson.get("blocks");
            if (blocksJson != null) {
                for (JsonValue blockValue : blocksJson) {
                    String blockFile = blockValue.getString("block", null);
                    if (blockFile == null) {
                        continue;
                    }
                    String resolved = blockFile.contains("/") ? blockFile : "blocks/" + blockFile;
                    blockFiles.add(resolved);
                }
            }
        }

        for (String resolved : blockFiles) {
            loadingSteps.add(new BlockTemplateStep(resolved));
        }

        totalWeight = 0f;
        for (LoadingStep step : loadingSteps) {
            totalWeight += step.getWeight();
        }
        if (totalWeight <= 0f) {
            totalWeight = 1f;
        }
    }

    private void startNextLoadingStep() {
        if (currentStepIndex < loadingSteps.size) {
            currentStep = loadingSteps.get(currentStepIndex);
            currentStep.start();
        } else {
            currentStep = null;
            if (loading) {
                finishLoading();
            }
        }
    }

    private void updateLoading() {
        if (!loading) {
            return;
        }
        if (currentStep == null && currentStepIndex < loadingSteps.size) {
            startNextLoadingStep();
        }

        float currentContribution = 0f;
        if (currentStep != null) {
            if (currentStep.update()) {
                completedWeight += currentStep.getWeight();
                currentStep = null;
                currentStepIndex++;
                if (currentStepIndex >= loadingSteps.size) {
                    loadingProgress = completedWeight / totalWeight;
                    finishLoading();
                    return;
                }
                startNextLoadingStep();
            } else {
                currentContribution = currentStep.getProgress() * currentStep.getWeight();
            }
        }
        loadingProgress = (completedWeight + currentContribution) / totalWeight;
    }

    private void finishLoading() {
        calculateCorridors();
        sceneBlocks = loadSceneBlocks(sceneJson);
        if (controlRenderer == null) {
            controlRenderer = new OnscreenControlRenderer();
        }
        resetGame();
        loading = false;
        Gdx.app.log("Main", "Assets loaded for level " + (currentLevelIndex + 1));
    }

    private BlockTemplate loadBlockTemplate(String blockFile) {
        if (blockFile == null || blockFile.isEmpty()) {
            return null;
        }
        String resolved = blockFile.contains("/") ? blockFile : "blocks/" + blockFile;
        BlockTemplate cached = blockTemplateCache.get(resolved);
        if (cached != null) {
            return cached;
        }
        BlockTemplateLoadResult result = loadBlockTemplateData(resolved);
        if (result == null) {
            return null;
        }
        Texture texture = null;
        if (result.pixmap != null) {
            texture = new Texture(result.pixmap);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            result.pixmap.dispose();
        }
        BlockTemplate template = new BlockTemplate(result.canvasWidth, result.canvasHeight, result.body, result.ascenders, result.descenders, result.drawBounds, texture);
        blockTemplateCache.put(resolved, template);
        return template;
    }

    private BlockTemplateLoadResult loadBlockTemplateData(String resolved) {
        JsonValue json;
        FileHandle jsonHandle;
        try {
            jsonHandle = Gdx.files.internal(resolved);
            json = new JsonReader().parse(jsonHandle);
        } catch (Exception e) {
            Gdx.app.error("Main", "Failed to load block template '" + resolved + "'", e);
            return null;
        }
        RectangleData body = parseRectangle(json.get("body"));
        Array<RectangleData> ascenders = parseRectangleArray(json.get("ascenders"));
        Array<RectangleData> descenders = parseRectangleArray(json.get("descenders"));
        float templateCanvasWidth = json.getFloat("canvasWidth", sceneWidth);
        float templateCanvasHeight = json.getFloat("canvasHeight", sceneHeight);
        float svgScale = json.getFloat("scale", 1f);
        float svgY = json.getFloat("svgY", 0f);
        RectangleData svgBounds = parseRectangle(json.get("svgBounds"));

        Pixmap pixmap = null;
        RectangleData drawBounds = null;
        String svgFileName = json.getString("svg", null);
        if (svgFileName != null) {
            FileHandle svgHandle = jsonHandle.sibling(svgFileName);
            if (!svgHandle.exists()) {
                Gdx.app.error("Main", "SVG '" + svgHandle.path() + "' for block template '" + resolved + "' not found");
            } else {
                BlockVisualPixmap visual = loadBlockVisualPixmap(resolved, svgHandle, svgBounds, svgScale, svgY, templateCanvasWidth, templateCanvasHeight);
                if (visual != null) {
                    pixmap = visual.pixmap;
                    drawBounds = visual.drawBounds;
                }
            }
        }

        return new BlockTemplateLoadResult(templateCanvasWidth, templateCanvasHeight, body, ascenders, descenders, drawBounds, pixmap);
    }

    private static RectangleData parseRectangle(JsonValue value) {
        if (value == null) {
            return null;
        }
        float x = value.getFloat("x", 0f);
        float y = value.getFloat("y", 0f);
        float width = value.getFloat("width", 0f);
        float height = value.getFloat("height", 0f);
        return new RectangleData(x, y, width, height);
    }

    private static Array<RectangleData> parseRectangleArray(JsonValue arrayValue) {
        Array<RectangleData> result = new Array<>();
        if (arrayValue == null) {
            return result;
        }
        for (JsonValue value : arrayValue) {
            RectangleData data = parseRectangle(value);
            if (data != null) {
                result.add(data);
            }
        }
        return result;
    }

    private Block instantiateBlock(BlockTemplate template, float offsetX, float offsetY) {
        if (template == null) {
            return null;
        }
        Rectangle body = convertRectangle(template.body, offsetX, offsetY, template.canvasHeight);
        Array<Rectangle> ascenders = convertRectangles(template.ascenders, offsetX, offsetY, template.canvasHeight);
        Array<Rectangle> descenders = convertRectangles(template.descenders, offsetX, offsetY, template.canvasHeight);
        if (body == null && ascenders.size == 0 && descenders.size == 0) {
            return null;
        }
        Rectangle drawBounds = convertRectangle(template.drawBounds, offsetX, offsetY, template.canvasHeight);
        return new Block(body, ascenders, descenders, drawBounds, template.texture);
    }

    private Array<Rectangle> convertRectangles(Array<RectangleData> source, float offsetX, float offsetY, float canvasHeight) {
        Array<Rectangle> result = new Array<>();
        if (source == null) {
            return result;
        }
        for (RectangleData data : source) {
            Rectangle rect = convertRectangle(data, offsetX, offsetY, canvasHeight);
            if (rect != null) {
                result.add(rect);
            }
        }
        return result;
    }

    private Rectangle convertRectangle(RectangleData data, float offsetX, float offsetY, float canvasHeight) {
        if (data == null) {
            return null;
        }
        float x = data.x + offsetX;
        float topY = data.y + offsetY;
        float y = canvasHeight - (topY + data.height);
        return new Rectangle(x, y, data.width, data.height);
    }

    private BlockVisualPixmap loadBlockVisualPixmap(String cacheKey,
                                                    FileHandle svgHandle,
                                                    RectangleData svgBounds,
                                                    float svgScale,
                                                    float svgY,
                                                    float canvasWidth,
                                                    float canvasHeight) {
        String svg;
        try {
            svg = svgHandle.readString();
        } catch (Exception e) {
            Gdx.app.error("Main", "Failed to read SVG '" + svgHandle.path() + "'", e);
            return null;
        }

        SvgViewBox viewBox = parseSvgViewBox(svg);
        if (viewBox == null) {
            float fallbackWidth = svgBounds != null ? svgBounds.width : canvasWidth;
            float fallbackHeight = svgBounds != null ? svgBounds.height : canvasHeight;
            float safeScale = svgScale == 0f ? 1f : svgScale;
            viewBox = new SvgViewBox(fallbackWidth / safeScale, fallbackHeight / safeScale);
        }

        int targetWidth = Math.max(1, MathUtils.ceil(viewBox.width * svgScale));
        int targetHeight = Math.max(1, MathUtils.ceil(viewBox.height * svgScale));

        float finalBlockStroke = Math.max(1.5f, Gdx.graphics.getWidth() / 400f);
        float strokeScale = computeStrokeScale(svg, finalBlockStroke);
        String adjustedSvg = svg.replaceAll("stroke-width=\\\"[0-9.]+\\\"",
                "stroke-width=\\\"" + strokeScale + "\\\"");

        Pixmap pixmap = loadCachedSvg("block-" + sanitizeCacheKey(cacheKey), adjustedSvg, targetWidth, targetHeight);

        if (svgBounds != null) {
            int cropX = Math.max(0, MathUtils.floor(svgBounds.x * svgScale));
            int cropY = Math.max(0, MathUtils.floor(svgBounds.y * svgScale));
            int cropWidth = Math.max(1, MathUtils.ceil(svgBounds.width * svgScale));
            int cropHeight = Math.max(1, MathUtils.ceil(svgBounds.height * svgScale));
            cropWidth = Math.min(cropWidth, pixmap.getWidth() - cropX);
            cropHeight = Math.min(cropHeight, pixmap.getHeight() - cropY);
            if (cropWidth > 0 && cropHeight > 0) {
                pixmap = cropPixmap(pixmap, cropX, cropY, cropWidth, cropHeight);
            }
        }

        applyBallpointEffect(pixmap, 0.7f);

        float drawWidth = svgBounds != null ? svgBounds.width * svgScale : pixmap.getWidth();
        float drawHeight = svgBounds != null ? svgBounds.height * svgScale : pixmap.getHeight();
        RectangleData drawBounds = new RectangleData(
                (canvasWidth - drawWidth) / 2f,
                svgY,
                drawWidth,
                drawHeight
        );

        return new BlockVisualPixmap(pixmap, drawBounds);
    }

    private static String sanitizeCacheKey(String key) {
        return key.replace('/', '_').replace('\\', '_');
    }

    private Pixmap cropPixmap(Pixmap source, int x, int y, int width, int height) {
        width = Math.max(1, Math.min(width, source.getWidth() - x));
        height = Math.max(1, Math.min(height, source.getHeight() - y));
        if (width <= 0 || height <= 0) {
            return source;
        }
        Pixmap cropped = new Pixmap(width, height, source.getFormat());
        cropped.drawPixmap(source, 0, 0, x, y, width, height);
        source.dispose();
        return cropped;
    }

    private static class RectangleData {
        final float x;
        final float y;
        final float width;
        final float height;

        RectangleData(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static class BlockTemplate {
        final float canvasWidth;
        final float canvasHeight;
        final RectangleData body;
        final Array<RectangleData> ascenders;
        final Array<RectangleData> descenders;
        final RectangleData drawBounds;
        final Texture texture;

        BlockTemplate(float canvasWidth,
                      float canvasHeight,
                      RectangleData body,
                      Array<RectangleData> ascenders,
                      Array<RectangleData> descenders,
                      RectangleData drawBounds,
                      Texture texture) {
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
            this.body = body;
            this.ascenders = ascenders != null ? ascenders : new Array<>();
            this.descenders = descenders != null ? descenders : new Array<>();
            this.drawBounds = drawBounds;
            this.texture = texture;
        }
    }

    private interface LoadingStep {
        void start();

        boolean update();

        float getProgress();

        float getWeight();
    }

    private abstract class AsyncLoadingStep implements LoadingStep {
        private final float weight;
        private AsyncResult<Void> future;
        private volatile boolean started;
        private volatile boolean finished;
        private volatile float progress;
        private volatile Throwable failure;

        AsyncLoadingStep(float weight) {
            this.weight = weight;
        }

        @Override
        public void start() {
            if (started) {
                return;
            }
            started = true;
            future = loaderExecutor.submit(new AsyncTask<Void>() {
                @Override
                public Void call() {
                    try {
                        runAsync();
                    } catch (Throwable t) {
                        failure = t;
                    }
                    return null;
                }
            });
        }

        @Override
        public boolean update() {
            if (!started || finished) {
                return finished;
            }
            if (future.isDone()) {
                finished = true;
                try {
                    future.get();
                } catch (Throwable t) {
                    if (failure == null) {
                        failure = t;
                    }
                }
                if (failure != null) {
                    Gdx.app.error("Main", "Loading step failed", failure);
                    onAsyncFailed(failure);
                } else {
                    completeOnMainThread();
                }
                return true;
            }
            return false;
        }

        @Override
        public float getProgress() {
            return finished ? 1f : progress;
        }

        protected void setProgress(float progress) {
            this.progress = MathUtils.clamp(progress, 0f, 1f);
        }

        @Override
        public float getWeight() {
            return weight;
        }

        protected void onAsyncFailed(Throwable throwable) {
            // default no-op
        }

        protected abstract void runAsync();

        protected abstract void completeOnMainThread();
    }

    private interface PixmapProcessor {
        Pixmap process(Pixmap pixmap);
    }

    private interface PixmapConsumer {
        void accept(Pixmap pixmap);
    }

    private static class BlockTemplateLoadResult {
        final float canvasWidth;
        final float canvasHeight;
        final RectangleData body;
        final Array<RectangleData> ascenders;
        final Array<RectangleData> descenders;
        final RectangleData drawBounds;
        final Pixmap pixmap;

        BlockTemplateLoadResult(float canvasWidth,
                               float canvasHeight,
                               RectangleData body,
                               Array<RectangleData> ascenders,
                               Array<RectangleData> descenders,
                               RectangleData drawBounds,
                               Pixmap pixmap) {
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
            this.body = body;
            this.ascenders = ascenders;
            this.descenders = descenders;
            this.drawBounds = drawBounds;
            this.pixmap = pixmap;
        }
    }

    private class SvgTextureStep extends AsyncLoadingStep {
        private final String cacheKey;
        private final String svg;
        private final int width;
        private final int height;
        private final PixmapProcessor processor;
        private final PixmapConsumer consumer;
        private Pixmap result;

        SvgTextureStep(float weight,
                       String cacheKey,
                       String svg,
                       int width,
                       int height,
                       PixmapProcessor processor,
                       PixmapConsumer consumer) {
            super(weight);
            this.cacheKey = cacheKey;
            this.svg = svg;
            this.width = width;
            this.height = height;
            this.processor = processor;
            this.consumer = consumer;
        }

        @Override
        protected void runAsync() {
            Pixmap pixmap = loadCachedSvg(cacheKey, svg, width, height);
            if (processor != null) {
                Pixmap processed = processor.process(pixmap);
                if (processed != null) {
                    pixmap = processed;
                }
            }
            result = pixmap;
        }

        @Override
        protected void completeOnMainThread() {
            if (result == null) {
                return;
            }
            try {
                if (consumer != null) {
                    consumer.accept(result);
                } else {
                    result.dispose();
                }
            } finally {
                result = null;
            }
        }
    }

    private class BlockTemplateStep extends AsyncLoadingStep {
        private final String resolved;
        private BlockTemplateLoadResult result;

        BlockTemplateStep(String resolved) {
            super(1f);
            this.resolved = resolved;
        }

        @Override
        protected void runAsync() {
            result = loadBlockTemplateData(resolved);
        }

        @Override
        protected void completeOnMainThread() {
            if (result == null) {
                return;
            }
            try {
                if (blockTemplateCache.containsKey(resolved)) {
                    if (result.pixmap != null) {
                        result.pixmap.dispose();
                    }
                    return;
                }
                Texture texture = null;
                if (result.pixmap != null) {
                    texture = new Texture(result.pixmap);
                    texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                    result.pixmap.dispose();
                }
                BlockTemplate template = new BlockTemplate(result.canvasWidth, result.canvasHeight, result.body, result.ascenders, result.descenders, result.drawBounds, texture);
                blockTemplateCache.put(resolved, template);
            } finally {
                result = null;
            }
        }
    }

    private static class BlockVisualPixmap {
        final Pixmap pixmap;
        final RectangleData drawBounds;

        BlockVisualPixmap(Pixmap pixmap, RectangleData drawBounds) {
            this.pixmap = pixmap;
            this.drawBounds = drawBounds;
        }
    }

    private static class SvgViewBox {
        final float width;
        final float height;

        SvgViewBox(float width, float height) {
            this.width = width;
            this.height = height;
        }
    }

    private static SvgViewBox parseSvgViewBox(String svg) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("viewBox=\\\"[0-9.+-eE]+ [0-9.+-eE]+ ([0-9.+-eE]+) ([0-9.+-eE]+)\\\"")
                .matcher(svg);
        if (matcher.find()) {
            float width = Float.parseFloat(matcher.group(1));
            float height = Float.parseFloat(matcher.group(2));
            return new SvgViewBox(width, height);
        }
        return null;
    }

    private static float computeStrokeScale(String svg, float finalStroke) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("viewBox=\\\"0 0 ([0-9.]+) [0-9.]+\\\"")
                .matcher(svg);
        float viewBoxWidth = 80f;
        if (m.find()) {
            viewBoxWidth = Float.parseFloat(m.group(1));
        }
        return finalStroke * (viewBoxWidth / 80f);
    }

    private Pixmap loadCachedSvg(String name, String svg, int width, int height) {
        if (Gdx.app.getType() == Application.ApplicationType.WebGL) {
            // The HTML backend cannot access the desktop cache, but CPU rasterization works reliably.
            return Svg2Pixmap.svg2Pixmap(svg, width, height);
        }

        if (PixmapCache.isSupported()) {
            String hash = md5(svg + width + "x" + height);
            FileHandle file = cacheDir.child(name + "-" + hash + ".png");
            if (file.exists()) {
                return PixmapCache.load(file);
            }
            Pixmap pixmap = Svg2Pixmap.svg2Pixmap(svg, width, height);
            PixmapCache.save(file, pixmap);
            return pixmap;
        }
        return Svg2Pixmap.svg2Pixmap(svg, width, height);
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int v = b & 0xFF;
                sb.append(HEX[v >>> 4]);
                sb.append(HEX[v & 0x0F]);
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    static final int CELL_SIZE = 32;
    static final int GRID_WIDTH = 800 / CELL_SIZE;
    static final int GRID_HEIGHT = 600 / CELL_SIZE;

    Rectangle getHamster() { return hamster; }
    Rectangle getGrade() { return grade; }
    boolean[][] getGrid() { return grid; }

    void resetGame() {
        gameOver = false;
        hamsterWin = false;
        advanceToNextLevel = false;
        showVictoryScreen = false;

        hamster = new Rectangle(sceneWidth / 2f - 32f, sceneHeight / 2f - 32f, 64, 64);

        blocks = new Array<>();
        for (Block block : sceneBlocks) {
            blocks.add(block);
        }

        grid = new boolean[GRID_WIDTH][GRID_HEIGHT];
        for (Block block : blocks) {
            markGridCells(block.body);
            for (Rectangle ascender : block.ascenders) {
                markGridCells(ascender);
            }
            for (Rectangle descender : block.descenders) {
                markGridCells(descender);
            }
        }

        int hx = (int) (hamster.x / CELL_SIZE);
        int hy = (int) (hamster.y / CELL_SIZE);
        boolean placed = false;
        int corridorCount = corridorCenters != null ? corridorCenters.length : 0;
        for (int attempt = 0; attempt < 1000 && !placed; attempt++) {
            int gx = MathUtils.random(0, GRID_WIDTH - 1);
            int centerY = corridorCount > 0
                    ? corridorCenters[MathUtils.random(0, corridorCount - 1)]
                    : sceneHeight / 2;
            int yTop = centerY - 32;
            if (yTop < 0 || yTop + 64 > sceneHeight) continue;
            int gy = yTop / CELL_SIZE;
            if (gy < 0 || gy + 2 >= GRID_HEIGHT) continue;
            if (grid[gx][gy] || grid[gx][gy + 1] || grid[gx][gy + 2]) continue;
            if (gx == hx && gy == hy) continue;
            grid[gx][gy] = true;
            grid[gx][gy + 1] = true;
            boolean canReachAbove = isReachable(hx, hy, gx, gy + 2);
            grid[gx][gy] = false;
            grid[gx][gy + 1] = false;

            if (canReachAbove && isReachable(hx, hy, gx, gy)) {
                grade = new Rectangle(gx * CELL_SIZE, yTop, 64, 64);
                placed = true;
            }
        }
        if (!placed) {
            resetGame();
            return;
        }

        do {
            gradeDirection = new Vector2(MathUtils.random(-1f, 1f), MathUtils.random(-1f, 1f));
        } while (gradeDirection.isZero());
        gradeDirection.nor();
    }

    private void markGridCells(Rectangle rect) {
        if (rect == null || grid == null) {
            return;
        }
        float maxX = rect.x + rect.width;
        float maxY = rect.y + rect.height;
        if (maxX <= 0 || maxY <= 0) {
            return;
        }
        int startX = Math.max(0, MathUtils.floor(rect.x / CELL_SIZE));
        int endX = MathUtils.floor((maxX - 0.001f) / CELL_SIZE);
        if (endX < startX) {
            return;
        }
        endX = Math.min(GRID_WIDTH - 1, endX);
        int startY = Math.max(0, MathUtils.floor(rect.y / CELL_SIZE));
        int endY = MathUtils.floor((maxY - 0.001f) / CELL_SIZE);
        if (endY < startY) {
            return;
        }
        endY = Math.min(GRID_HEIGHT - 1, endY);
        for (int gx = startX; gx <= endX; gx++) {
            for (int gy = startY; gy <= endY; gy++) {
                grid[gx][gy] = true;
            }
        }
    }

    private void resolveHamsterCollision(Rectangle obstacle, Rectangle intersection) {
        if (obstacle == null) {
            return;
        }
        resolveCollision(hamster, obstacle, intersection);
    }

    private void handleGradeCollision(Rectangle obstacle, Rectangle intersection) {
        if (obstacle == null) {
            return;
        }
        int axis = resolveCollision(grade, obstacle, intersection);
        if (axis == 0) {
            gradeDirection.x = -gradeDirection.x;
        } else if (axis == 1) {
            gradeDirection.y = -gradeDirection.y;
        }
    }

    private int resolveCollision(Rectangle mover, Rectangle obstacle, Rectangle intersection) {
        if (obstacle == null) {
            return -1;
        }
        if (!Intersector.intersectRectangles(mover, obstacle, intersection)) {
            return -1;
        }
        if (intersection.width < intersection.height) {
            if (mover.x < obstacle.x) {
                mover.x -= intersection.width;
            } else {
                mover.x += intersection.width;
            }
            return 0;
        } else {
            if (mover.y < obstacle.y) {
                mover.y -= intersection.height;
            } else {
                mover.y += intersection.height;
            }
            return 1;
        }
    }

    private boolean isReachable(int startX, int startY, int targetX, int targetY) {
        if (grid[targetX][targetY]) return false;
        boolean[][] visited = new boolean[GRID_WIDTH][GRID_HEIGHT];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        visited[startX][startY] = true;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            if (p[0] == targetX && p[1] == targetY) return true;
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx >= 0 && ny >= 0 && nx < GRID_WIDTH && ny < GRID_HEIGHT && !grid[nx][ny] && !visited[nx][ny]) {
                    visited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return false;
    }

    private boolean isCellClear(int gx, int gy) {
        if (backgroundPixmap == null) return true;
        int startX = gx * CELL_SIZE;
        int startY = gy * CELL_SIZE;
        Color c = new Color();
        for (int x = startX; x < startX + CELL_SIZE; x++) {
            for (int y = startY; y < startY + CELL_SIZE; y++) {
                Color.rgba8888ToColor(c, backgroundPixmap.getPixel(x, y));
                if (c.r < 0.95f || c.g < 0.95f || c.b < 0.95f) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isAreaClear(int x, int y, int width, int height) {
        if (backgroundPixmap == null) return true;
        Color c = new Color();
        for (int px = x; px < x + width; px++) {
            for (int py = y; py < y + height; py++) {
                Color.rgba8888ToColor(c, backgroundPixmap.getPixel(px, py));
                if (c.r < 0.95f || c.g < 0.95f || c.b < 0.95f) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void applyBallpointEffect(Pixmap pixmap) {
        applyBallpointEffect(pixmap, 0.5f);
    }

    public static void applyBallpointEffect(Pixmap pixmap, float baseOpacity) {
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        int[][] dist = new int[width][height];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        Color color = new Color();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color.rgba8888ToColor(color, pixmap.getPixel(x, y));
                if (color.a == 0f) {
                    dist[x][y] = 0;
                    queue.add(new int[]{x, y});
                } else {
                    dist[x][y] = Integer.MAX_VALUE;
                }
            }
        }

        int[] dx = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dy = {0, 0, -1, 1, -1, 1, -1, 1};

        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int x = p[0];
            int y = p[1];
            int d = dist[x][y];
            for (int i = 0; i < 8; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && dist[nx][ny] > d + 1) {
                    dist[nx][ny] = d + 1;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        int maxDist = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color.rgba8888ToColor(color, pixmap.getPixel(x, y));
                if (color.a > 0f && dist[x][y] > maxDist) {
                    maxDist = dist[x][y];
                }
            }
        }
        Pixmap.Blending old = pixmap.getBlending();
        pixmap.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixmap.getPixel(x, y);
                Color.rgba8888ToColor(color, pixel);
                if (color.a > 0f) {
                    float factor;
                    if (maxDist <= 1) {
                        factor = 1f;
                    } else {
                        factor = ((float) dist[x][y] - 1f) / ((float) maxDist - 1f);
                        factor = baseOpacity + (1f - baseOpacity) * factor;
                    }
                    color.a *= factor;
                    pixmap.drawPixel(x, y, Color.rgba8888(color));
                }
            }
        }
        pixmap.setBlending(old);
    }

    private static Pixmap trimTransparent(Pixmap pixmap) {
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        Color c = new Color();
        int top = 0;
        outer: for (; top < height; top++) {
            for (int x = 0; x < width; x++) {
                Color.rgba8888ToColor(c, pixmap.getPixel(x, top));
                if (c.a != 0f) break outer;
            }
        }
        int bottom = height - 1;
        outer: for (; bottom >= top; bottom--) {
            for (int x = 0; x < width; x++) {
                Color.rgba8888ToColor(c, pixmap.getPixel(x, bottom));
                if (c.a != 0f) break outer;
            }
        }
        int left = 0;
        outer: for (; left < width; left++) {
            for (int y = top; y <= bottom; y++) {
                Color.rgba8888ToColor(c, pixmap.getPixel(left, y));
                if (c.a != 0f) break outer;
            }
        }
        int right = width - 1;
        outer: for (; right >= left; right--) {
            for (int y = top; y <= bottom; y++) {
                Color.rgba8888ToColor(c, pixmap.getPixel(right, y));
                if (c.a != 0f) break outer;
            }
        }
        int newWidth = Math.max(1, right - left + 1);
        int newHeight = Math.max(1, bottom - top + 1);
        Pixmap trimmed = new Pixmap(newWidth, newHeight, pixmap.getFormat());
        trimmed.drawPixmap(pixmap, 0, 0, left, top, newWidth, newHeight);
        pixmap.dispose();
        return trimmed;
    }


    @Override
    public void render() {
        if (loading) {
            updateLoading();
        }
        if (loading) {
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.rect(100, 300, 600 * loadingProgress, 20);
            shapeRenderer.end();

            batch.begin();
            font.draw(batch, "Loading...", 350, 340);
            batch.end();
            return;
        }
        if (gameOver) {
            Gdx.gl.glClearColor(1, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();
            font.draw(batch, "Hamster: " + hamsterScore, 10, 590);
            font.draw(batch, "Grade: " + gradeScore, 10, 560);
            if (showVictoryScreen) {
                font.draw(batch, "All levels complete!", 300, 340);
                font.draw(batch, "Tap to restart", 320, 300);
                batch.draw(hamsterTexture, 350, 250, 120, 120);
            } else if (hamsterWin) {
                batch.draw(hamsterTexture, 350, 250, 120, 120);
                font.draw(batch, "Level " + (currentLevelIndex + 1) + " complete", 310, 340);
                font.draw(batch, "Tap to continue", 320, 300);
            } else {
                batch.draw(gradeTexture, 350, 250, 100, 100);
                font.draw(batch, "Try again", 350, 340);
                font.draw(batch, "Tap to restart", 330, 300);
            }
            batch.end();
            if (Gdx.input.isTouched() && Gdx.input.justTouched()) {
                if (showVictoryScreen) {
                    restartCampaign();
                } else if (advanceToNextLevel) {
                    startLevel(currentLevelIndex + 1);
                } else {
                    resetGame();
                }
            }
            return;
        }

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();
        batch.draw(backgroundTexture, 0, 0, sceneWidth, sceneHeight);
        batch.end();

        batch.begin();
        batch.draw(hamsterTexture, hamster.x, hamster.y, hamster.width, hamster.height);
        batch.draw(gradeTexture, grade.x, grade.y);
        for (Block block : blocks) {
            if (block.texture != null && block.drawBounds != null) {
                Rectangle drawBounds = block.drawBounds;
                batch.draw(block.texture, drawBounds.x, drawBounds.y, drawBounds.width, drawBounds.height);
            }
        }
        font.draw(batch, "Hamster: " + hamsterScore, 10, 590);
        font.draw(batch, "Grade: " + gradeScore, 10, 560);
        font.draw(batch, "Level: " + (currentLevelIndex + 1) + "/" + Math.max(1, levelFiles.size), 10, 530);
        batch.end();

        // Hamster movement
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            float x0 = (Gdx.input.getX(0) / (float) Gdx.graphics.getWidth()) * 480;
            float x1 = (Gdx.input.getX(1) / (float) Gdx.graphics.getWidth()) * 480;
            float y0 = 320 - (Gdx.input.getY(0) / (float) Gdx.graphics.getHeight()) * 320;

            boolean leftButton = (Gdx.input.isTouched(0) && x0 < 70) || (Gdx.input.isTouched(1) && x1 < 70);
            boolean rightButton = (Gdx.input.isTouched(0) && x0 > 70 && x0 < 134) || (Gdx.input.isTouched(1) && x1 > 70 && x1 < 134);
            boolean downButton = (Gdx.input.isTouched(0) && x0 > 416 && x0 < 480 && y0 > 320 - 128 && y0 < 320 - 64)
                    || (Gdx.input.isTouched(1) && x1 > 416 && x1 < 480 && y0 > 320 - 128 && y0 < 320 - 64);
            boolean upButton = (Gdx.input.isTouched(0) && x0 > 416 && x0 < 480 && y0 > 320 - 64)
                    || (Gdx.input.isTouched(1) && x1 > 416 && x1 < 480 && y0 > 320 - 64);

            if (upButton) {
                hamster.y += 200 * Gdx.graphics.getDeltaTime();
            }
            if (leftButton) {
                hamster.x -= 200 * Gdx.graphics.getDeltaTime();
            }
            if (downButton) {
                hamster.y -= 200 * Gdx.graphics.getDeltaTime();
            }
            if (rightButton) {
                hamster.x += 200 * Gdx.graphics.getDeltaTime();
            }
        } else {
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) hamster.x -= 200 * Gdx.graphics.getDeltaTime();
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) hamster.x += 200 * Gdx.graphics.getDeltaTime();
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) hamster.y += 200 * Gdx.graphics.getDeltaTime();
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) hamster.y -= 200 * Gdx.graphics.getDeltaTime();
        }

        hamster.x = MathUtils.clamp(hamster.x, 0, sceneWidth - hamster.width);
        hamster.y = MathUtils.clamp(hamster.y, 0, sceneHeight - hamster.height);

        grade.x += gradeDirection.x * 100 * Gdx.graphics.getDeltaTime();
        grade.y += gradeDirection.y * 100 * Gdx.graphics.getDeltaTime();

        if (grade.x < 0 || grade.x > sceneWidth - grade.width) gradeDirection.x = -gradeDirection.x;
        if (grade.y < 0 || grade.y > sceneHeight - grade.height) gradeDirection.y = -gradeDirection.y;

        Rectangle intersection = new Rectangle();
        if (blocks != null) {
            for (Block block : blocks) {
                resolveHamsterCollision(block.body, intersection);
                for (Rectangle ascender : block.ascenders) {
                    resolveHamsterCollision(ascender, intersection);
                }
                for (Rectangle descender : block.descenders) {
                    resolveHamsterCollision(descender, intersection);
                }

                handleGradeCollision(block.body, intersection);
                for (Rectangle ascender : block.ascenders) {
                    handleGradeCollision(ascender, intersection);
                }
                for (Rectangle descender : block.descenders) {
                    handleGradeCollision(descender, intersection);
                }
            }
        }

        hamster.x = MathUtils.clamp(hamster.x, 0, sceneWidth - hamster.width);
        hamster.y = MathUtils.clamp(hamster.y, 0, sceneHeight - hamster.height);
        grade.x = MathUtils.clamp(grade.x, 0, sceneWidth - grade.width);
        grade.y = MathUtils.clamp(grade.y, 0, sceneHeight - grade.height);

        if (hamster.overlaps(grade)) {
            if (hamster.y >= grade.y + grade.height - 5) {
                blocks.clear();
                gameOver = true;
                hamsterWin = true;
                hamsterScore++;
                advanceToNextLevel = currentLevelIndex + 1 < levelFiles.size;
                showVictoryScreen = !advanceToNextLevel;
            } else {
                gameOver = true;
                hamsterWin = false;
                gradeScore++;
                advanceToNextLevel = false;
                showVictoryScreen = false;
            }
        }

        controlRenderer.render();

        if (AUTO_EXIT && ++framesRendered > 2) {
            Gdx.app.exit();
        }
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (hamsterTexture != null) hamsterTexture.dispose();
        if (gradeTexture != null) gradeTexture.dispose();
        if (backgroundTexture != null) backgroundTexture.dispose();
        if (backgroundPixmap != null) backgroundPixmap.dispose();
        if (font != null) font.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (controlRenderer != null) controlRenderer.dispose();
        if (backgroundMusic != null) {
            backgroundMusic.stop();
            backgroundMusic.dispose();
        }
        if (loaderExecutor != null) {
            loaderExecutor.dispose();
        }
        for (BlockTemplate template : blockTemplateCache.values()) {
            if (template.texture != null) {
                template.texture.dispose();
            }
        }
    }
}
