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
import com.badlogic.gdx.graphics.PixmapIO;
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
    private static final float HAMSTER_DRAW_SIZE = 64f;
    private static final String LEGACY_HAMSTER_SVG_FILE = "hamster4.svg";
    private static final String HAMSTER_SVG_FILE = "hamster-line.svg";
    private static final float GRADE_DRAW_WIDTH = 64f;
    private static final float PEN_BALL_WIDTH_MM = 0.38f;
    private static final float MIN_PEN_WIDTH_PX = 2.75f;
    private static final float MAX_PEN_WIDTH_PX = 7.5f;
    private static final float NEW_LINE_STROKE_MULTIPLIER = 0.90f;
    private static final float GRADE_BALLPOINT_STROKE_MULTIPLIER = 3.25f;
    private static final float BLOCK_BALLPOINT_STROKE_MULTIPLIER = 1.55f;
    private static final float BALLPOINT_TARGET_COVERAGE = 3.55f;
    private static final int BALLPOINT_RENDER_SCALE = 4;
    private static final int BALLPOINT_RENDER_PADDING_PX = 8;
    private static final float GRADE_SVG_PADDING = 6f;
    private static final float BLOCK_SVG_PADDING = 4f;
    private static final float LINE_EFFECT_BASE_OPACITY = 0.88f;
    private static final float NEW_LINE_INK_ALPHA_MULTIPLIER = 1.95f;
    private static final String LINE_RENDER_CACHE_VERSION = "line-render-v85-vector-roll";

    private SpriteBatch batch;
    private Texture hamsterTexture;
    private Texture legacyHamsterTexture;
    private Texture gradeTexture;
    private Texture backgroundTexture;
    private Pixmap backgroundPixmap;
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;
    private Music backgroundMusic;

    private OrthographicCamera camera;

    private Rectangle hamster;
    private Rectangle legacyHamster;
    private Rectangle candidateHamster;
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
    private final boolean lineDiagnosticMode;
    private Array<Block> diagnosticBlocks = new Array<>();
    private boolean lineDiagnosticScreenshotSaved;

    private static final Pattern SCENE_FILE_PATTERN = Pattern.compile("scene(\\d+)\\.json");

    public Main() {
        this(Boolean.parseBoolean(System.getProperty("lineDiagnostic", "false")));
    }

    public Main(boolean lineDiagnosticMode) {
        this.lineDiagnosticMode = lineDiagnosticMode;
    }

    @Override
    public void create() {
        if (lineDiagnosticMode || Gdx.app.getType() == Application.ApplicationType.WebGL) {
            Svg2Pixmap.generateScale = 1;
        }

        discoverLevels();
        currentLevelIndex = 0;

        batch = new SpriteBatch();
        font = new BitmapFont();
        shapeRenderer = new ShapeRenderer();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, sceneWidth, sceneHeight);

        if (!lineDiagnosticMode) {
            backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal("aldermeshka.mp3"));
            backgroundMusic.setLooping(true);
            backgroundMusic.setVolume(0.5f);
            backgroundMusic.play();
        }

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
            FileHandle[] sceneEntries = scenesDir.list();
            if (sceneEntries == null) {
                Gdx.app.log("Main", "scenesDir.list() returned null");
            } else {
                StringBuilder listing = new StringBuilder();
                for (int i = 0; i < sceneEntries.length; i++) {
                    if (i > 0) {
                        listing.append(", ");
                    }
                    listing.append(sceneEntries[i].name());
                }
                Gdx.app.log("Main", "scenesDir.list(): [" + listing + "]");
                for (FileHandle file : sceneEntries) {
                    Matcher matcher = SCENE_FILE_PATTERN.matcher(file.name());
                    if (matcher.matches()) {
                        levelFiles.add(file.name());
                    }
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

        final float finalStroke = computeScreenStrokeWidth();
        if (lineDiagnosticMode) {
            final String legacyHamsterSvgOriginal = Gdx.files.internal(LEGACY_HAMSTER_SVG_FILE).readString();
            final float legacyStroke = Math.max(1.5f, Gdx.graphics.getWidth() / 400f);
            final float legacyStrokeScale = computeLegacyStrokeScale(legacyHamsterSvgOriginal, legacyStroke);
            final String legacyHamsterSvg = legacyHamsterSvgOriginal.replaceAll("stroke-width=\\\"[0-9.]+\\\"",
                    "stroke-width=\\\"" + legacyStrokeScale + "\\\"");
            loadingSteps.add(new SvgTextureStep(1f, "legacy-hamster-reference", legacyHamsterSvg, 256, 256,
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
                            legacyHamsterTexture = new Texture(pixmap);
                            legacyHamsterTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                            pixmap.dispose();
                        }
                    }));
        }

        final String hamsterSvgOriginal = Gdx.files.internal(HAMSTER_SVG_FILE).readString();
        final String hamsterSvg = withScreenStrokeWidth(hamsterSvgOriginal, finalStroke * NEW_LINE_STROKE_MULTIPLIER, HAMSTER_DRAW_SIZE);
        loadingSteps.add(new SvgTextureStep(1f, "hamster", hamsterSvg, 256, 256,
                new PixmapProcessor() {
                    @Override
                    public Pixmap process(Pixmap pixmap) {
                        applyLineEffect(pixmap);
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

        final String gradeSvg = withScreenStrokeWidth(Gdx.files.internal("grade.svg").readString(),
                finalStroke * GRADE_BALLPOINT_STROKE_MULTIPLIER, GRADE_DRAW_WIDTH);
        final PaddedSvg paddedGradeSvg = padSvgCanvas(gradeSvg, GRADE_SVG_PADDING, GRADE_SVG_PADDING);
        int gradeRenderWidth = MathUtils.ceil(64f * BALLPOINT_RENDER_SCALE * paddedGradeSvg.width / 32f);
        int gradeRenderHeight = MathUtils.ceil(64f * BALLPOINT_RENDER_SCALE * paddedGradeSvg.height / 45f);
        loadingSteps.add(new SvgTextureStep(1f, "grade", paddedGradeSvg.svg, gradeRenderWidth, gradeRenderHeight,
                new PixmapProcessor() {
                    @Override
                    public Pixmap process(Pixmap pixmap) {
                        applyTeacherRedBallpointEffect(pixmap, paddedGradeSvg.svg);
                        return pixmap;
                    }
                },
                new PixmapConsumer() {
                    @Override
                    public void accept(Pixmap pixmap) {
                        gradeTexture = new Texture(pixmap);
                        gradeTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                        pixmap.dispose();
                    }
                }));

        if (lineDiagnosticMode) {
            loadingSteps.add(new BlockTemplateStep("blocks/block2.json"));
            totalWeight = 0f;
            for (LoadingStep step : loadingSteps) {
                totalWeight += step.getWeight();
            }
            if (totalWeight <= 0f) {
                totalWeight = 1f;
            }
            return;
        }

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
        if (lineDiagnosticMode) {
            prepareLineDiagnosticScene();
            loading = false;
            Gdx.app.log("Main", "Line diagnostic assets loaded");
            return;
        }
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

    private void prepareLineDiagnosticScene() {
        diagnosticBlocks.clear();
        BlockTemplate block2Template = loadBlockTemplate("block2.json");
        Block block2 = instantiateBlockAtDrawBounds(block2Template, 512f, 244f);
        if (block2 != null) {
            diagnosticBlocks.add(block2);
        }
        legacyHamster = new Rectangle(108f, 244f, HAMSTER_DRAW_SIZE, HAMSTER_DRAW_SIZE);
        candidateHamster = new Rectangle(232f, 244f, HAMSTER_DRAW_SIZE, HAMSTER_DRAW_SIZE);
        hamster = candidateHamster;
        grade = new Rectangle(376f, 234f, GRADE_DRAW_WIDTH, GRADE_DRAW_WIDTH);
    }

    private Block instantiateBlockAtDrawBounds(BlockTemplate template, float drawX, float drawY) {
        if (template == null || template.drawBounds == null) {
            return null;
        }
        float offsetX = drawX - template.drawBounds.x;
        float offsetY = template.canvasHeight - drawY - template.drawBounds.height - template.drawBounds.y;
        return instantiateBlock(template, offsetX, offsetY);
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

        float renderScale = BALLPOINT_RENDER_SCALE;
        float drawWidth = svgBounds != null ? svgBounds.width * svgScale : viewBox.width * svgScale;
        String adjustedSvg = withScreenStrokeWidth(svg, computeScreenStrokeWidth() * BLOCK_BALLPOINT_STROKE_MULTIPLIER, drawWidth);
        PaddedSvg paddedSvg = padSvgCanvas(adjustedSvg, BLOCK_SVG_PADDING, BLOCK_SVG_PADDING);
        int targetWidth = Math.max(1, MathUtils.ceil(paddedSvg.width * svgScale * renderScale));
        int targetHeight = Math.max(1, MathUtils.ceil(paddedSvg.height * svgScale * renderScale));

        Pixmap pixmap = loadCachedSvg("block-" + sanitizeCacheKey(cacheKey), paddedSvg.svg, targetWidth, targetHeight);
        applySchoolBlueBallpointEffect(pixmap, paddedSvg.svg);

        float drawPadding = 0f;
        if (svgBounds != null) {
            int cropX = Math.max(0, MathUtils.floor((svgBounds.x + BLOCK_SVG_PADDING) * svgScale * renderScale) - BALLPOINT_RENDER_PADDING_PX);
            int cropY = Math.max(0, MathUtils.floor((svgBounds.y + BLOCK_SVG_PADDING) * svgScale * renderScale) - BALLPOINT_RENDER_PADDING_PX);
            int cropRight = Math.min(pixmap.getWidth(), MathUtils.ceil((svgBounds.x + svgBounds.width + BLOCK_SVG_PADDING) * svgScale * renderScale) + BALLPOINT_RENDER_PADDING_PX);
            int cropBottom = Math.min(pixmap.getHeight(), MathUtils.ceil((svgBounds.y + svgBounds.height + BLOCK_SVG_PADDING) * svgScale * renderScale) + BALLPOINT_RENDER_PADDING_PX);
            int cropWidth = Math.max(1, cropRight - cropX);
            int cropHeight = Math.max(1, cropBottom - cropY);
            drawPadding = BLOCK_SVG_PADDING * svgScale + BALLPOINT_RENDER_PADDING_PX / renderScale;
            if (cropWidth > 0 && cropHeight > 0) {
                pixmap = cropPixmap(pixmap, cropX, cropY, cropWidth, cropHeight);
            }
        }

        float drawHeight = svgBounds != null ? svgBounds.height * svgScale : pixmap.getHeight() / renderScale;
        RectangleData drawBounds = new RectangleData(
                (canvasWidth - drawWidth) / 2f - drawPadding,
                svgY - drawPadding,
                drawWidth + drawPadding * 2f,
                drawHeight + drawPadding * 2f
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

    private Pixmap padPixmap(Pixmap source, int padding) {
        if (padding <= 0) {
            return source;
        }
        Pixmap padded = new Pixmap(source.getWidth() + padding * 2, source.getHeight() + padding * 2, source.getFormat());
        padded.setBlending(Pixmap.Blending.None);
        padded.setColor(0f, 0f, 0f, 0f);
        padded.fill();
        padded.drawPixmap(source, padding, padding);
        source.dispose();
        return padded;
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
        private final String name;
        private AsyncResult<Void> future;
        private volatile boolean started;
        private volatile boolean finished;
        private volatile float progress;
        private volatile Throwable failure;

        AsyncLoadingStep(float weight, String name) {
            this.weight = weight;
            this.name = name;
        }

        @Override
        public void start() {
            if (started) {
                return;
            }
            started = true;
            Gdx.app.log("Main", "Loading step start: " + name);
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
                    Gdx.app.error("Main", "Loading step failed: " + name, failure);
                    onAsyncFailed(failure);
                } else {
                    completeOnMainThread();
                    Gdx.app.log("Main", "Loading step done: " + name);
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
            super(weight, "svg:" + cacheKey);
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
            super(1f, "block:" + resolved);
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

    private static class PaddedSvg {
        final String svg;
        final float width;
        final float height;

        PaddedSvg(String svg, float width, float height) {
            this.svg = svg;
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

    private static PaddedSvg padSvgCanvas(String svg, float padX, float padY) {
        SvgViewBox viewBox = parseSvgViewBox(svg);
        if (viewBox == null || padX <= 0f && padY <= 0f) {
            return new PaddedSvg(svg, viewBox != null ? viewBox.width : 0f, viewBox != null ? viewBox.height : 0f);
        }
        float paddedWidth = viewBox.width + padX * 2f;
        float paddedHeight = viewBox.height + padY * 2f;
        String result = svg.replaceFirst("viewBox=\\\"[0-9.+-eE]+ [0-9.+-eE]+ [0-9.+-eE]+ [0-9.+-eE]+\\\"",
                "viewBox=\"0 0 " + paddedWidth + " " + paddedHeight + "\"");
        result = result.replaceFirst("width=\\\"[0-9.+-eE]+\\\"", "width=\"" + paddedWidth + "\"");
        result = result.replaceFirst("height=\\\"[0-9.+-eE]+\\\"", "height=\"" + paddedHeight + "\"");
        int svgStartEnd = result.indexOf('>');
        int svgEnd = result.lastIndexOf("</svg>");
        if (svgStartEnd >= 0 && svgEnd > svgStartEnd) {
            result = result.substring(0, svgStartEnd + 1)
                    + "<g transform=\"translate(" + padX + " " + padY + ")\">"
                    + result.substring(svgStartEnd + 1, svgEnd)
                    + "</g>"
                    + result.substring(svgEnd);
        }
        return new PaddedSvg(result, paddedWidth, paddedHeight);
    }

    private static float computeScreenStrokeWidth() {
        float worldToScreen = Math.max(0.001f, computeWorldToScreenScale());
        return computePenWidthPixels() / worldToScreen;
    }

    private static float computeWorldToScreenScale() {
        if (Gdx.graphics == null) {
            return 1f;
        }
        float sx = Gdx.graphics.getWidth() / 800f;
        float sy = Gdx.graphics.getHeight() / 600f;
        return Math.min(sx, sy);
    }

    private static float computePenWidthPixels() {
        float ppi = 96f;
        if (Gdx.graphics != null) {
            float ppiX = Gdx.graphics.getPpiX();
            float ppiY = Gdx.graphics.getPpiY();
            if (ppiX > 0f && ppiY > 0f) {
                ppi = (ppiX + ppiY) * 0.5f;
            } else if (Gdx.graphics.getDensity() > 0f) {
                ppi = 160f * Gdx.graphics.getDensity();
            }
        }
        float physicalPenPx = PEN_BALL_WIDTH_MM / 25.4f * ppi;
        return MathUtils.clamp(physicalPenPx, MIN_PEN_WIDTH_PX, MAX_PEN_WIDTH_PX);
    }

    private static String withScreenStrokeWidth(String svg, float screenStrokeWidth, float screenDrawWidth) {
        float strokeWidth = computeSvgStrokeWidth(svg, screenStrokeWidth, screenDrawWidth);
        return svg.replaceAll("stroke-width=\\\"[0-9.]+\\\"",
                "stroke-width=\\\"" + strokeWidth + "\\\"");
    }

    private static float computeLegacyStrokeScale(String svg, float finalStroke) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("viewBox=\\\"0 0 ([0-9.]+) [0-9.]+\\\"")
                .matcher(svg);
        float viewBoxWidth = 80f;
        if (m.find()) {
            viewBoxWidth = Float.parseFloat(m.group(1));
        }
        return finalStroke * (viewBoxWidth / 80f);
    }

    private static float computeSvgStrokeWidth(String svg, float screenStrokeWidth, float screenDrawWidth) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("viewBox=\\\"0 0 ([0-9.]+) [0-9.]+\\\"")
                .matcher(svg);
        float viewBoxWidth = 80f;
        if (m.find()) {
            viewBoxWidth = Float.parseFloat(m.group(1));
        }
        float safeDrawWidth = screenDrawWidth <= 0f ? viewBoxWidth : screenDrawWidth;
        return screenStrokeWidth * (viewBoxWidth / safeDrawWidth);
    }

    private Pixmap loadCachedSvg(String name, String svg, int width, int height) {
        if (Gdx.app.getType() == Application.ApplicationType.WebGL) {
            // The HTML backend cannot access the desktop cache, but CPU rasterization works reliably.
            return Svg2Pixmap.svg2Pixmap(svg, width, height);
        }

        if (PixmapCache.isSupported()) {
            String hash = md5(LINE_RENDER_CACHE_VERSION + svg + width + "x" + height);
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
        applyBallpointEffect(pixmap, 0.5f, false);
    }

    public static void applyLineEffect(Pixmap pixmap) {
        applyBallpointEffect(pixmap, LINE_EFFECT_BASE_OPACITY, true);
        addInkEdgeFeather(pixmap);
    }

    public static void applyTeacherRedBallpointEffect(Pixmap pixmap) {
        applyBallpointPenEffect(pixmap, 0.74f, 0.92f, 0.95f, 0.08f, 0.97f, 0.08f, 0.04f);
    }

    public static void applyTeacherRedBallpointEffect(Pixmap pixmap, String svg) {
        applyVectorBallpointPenEffect(pixmap, svg);
    }

    public static void applySchoolBlueBallpointEffect(Pixmap pixmap) {
        applyBallpointPenEffect(pixmap, 0.74f, 0.92f, 0.95f, 0.08f, 0.97f, 0.08f, 0.04f);
    }

    public static void applySchoolBlueBallpointEffect(Pixmap pixmap, String svg) {
        applyVectorBallpointPenEffect(pixmap, svg);
    }

    public static void applyBallpointEffect(Pixmap pixmap, float baseOpacity) {
        applyBallpointEffect(pixmap, baseOpacity, false);
    }

    private static void applyVectorBallpointPenEffect(Pixmap pixmap, String svg) {
        SvgViewBox viewBox = parseSvgViewBox(svg);
        java.util.ArrayList<VectorStroke> strokes = parseVectorStrokes(svg);
        if (viewBox == null || strokes.isEmpty()) {
            applyBallpointPenEffect(pixmap, 0.74f, 0.92f, 0.95f, 0.08f, 0.97f, 0.08f, 0.04f);
            return;
        }

        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        float scaleX = width / Math.max(1f, viewBox.width);
        float scaleY = height / Math.max(1f, viewBox.height);
        float scale = (scaleX + scaleY) * 0.5f;
        float[][] coverage = new float[width][height];
        Color inkColor = strokes.get(0).color;

        for (VectorStroke stroke : strokes) {
            inkColor = stroke.color;
            renderVectorBallpointStroke(coverage, width, height, stroke, scaleX, scaleY, scale);
        }

        Pixmap.Blending old = pixmap.getBlending();
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float alpha = coverage[x][y];
                if (alpha <= 0.01f) {
                    continue;
                }
                pixmap.setColor(inkColor.r, inkColor.g, inkColor.b, MathUtils.clamp(alpha, 0f, 0.96f));
                pixmap.drawPixel(x, y);
            }
        }
        pixmap.setBlending(old);
    }

    private static void renderVectorBallpointStroke(float[][] coverage,
                                                    int width,
                                                    int height,
                                                    VectorStroke stroke,
                                                    float scaleX,
                                                    float scaleY,
                                                    float scale) {
        java.util.ArrayList<VectorSample> samples = sampleVectorStroke(stroke);
        if (samples.size() < 2) {
            return;
        }
        float lineWidth = Math.max(2f, stroke.strokeWidth * scale);
        float radius = Math.max(2f, lineWidth * 0.62f);
        float period = Math.max(8f, lineWidth * 3.1415927f);
        float pathLength = samples.get(samples.size() - 1).s * scale;
        float stampStep = Math.max(0.55f, lineWidth * 0.10f);
        float nextStamp = 0f;

        VectorSample prev = samples.get(0);
        for (int i = 1; i < samples.size(); i++) {
            VectorSample current = samples.get(i);
            float x0 = prev.x * scaleX;
            float y0 = prev.y * scaleY;
            float x1 = current.x * scaleX;
            float y1 = current.y * scaleY;
            float s0 = prev.s * scale;
            float s1 = current.s * scale;
            float segmentLength = Math.max(0.001f, s1 - s0);
            float dx = x1 - x0;
            float dy = y1 - y0;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len <= 0.001f) {
                prev = current;
                continue;
            }
            float tx = dx / len;
            float ty = dy / len;
            float nx = -ty;
            float ny = tx;
            while (nextStamp <= s1) {
                if (nextStamp >= s0) {
                    float t = (nextStamp - s0) / segmentLength;
                    float cx = MathUtils.lerp(x0, x1, t);
                    float cy = MathUtils.lerp(y0, y1, t);
                    float pressure = rollingPressure(nextStamp, pathLength, lineWidth);
                    drawRollingBallStamp(coverage, width, height, cx, cy, tx, ty, nx, ny, nextStamp, radius, lineWidth, period, pressure);
                }
                nextStamp += stampStep;
            }
            prev = current;
        }
    }

    private static float rollingPressure(float s, float pathLength, float lineWidth) {
        float endLength = Math.max(lineWidth * 2.8f, Math.min(pathLength * 0.22f, lineWidth * 5f));
        float start = 1f + 0.20f * (1f - smoothstep(0f, lineWidth * 3f, s));
        float end = smoothstep(0f, endLength, Math.max(0f, pathLength - s));
        return MathUtils.clamp(start * end, 0f, 1.18f);
    }

    private static void drawRollingBallStamp(float[][] coverage,
                                             int width,
                                             int height,
                                             float cx,
                                             float cy,
                                             float tx,
                                             float ty,
                                             float nx,
                                             float ny,
                                             float s,
                                             float radius,
                                             float lineWidth,
                                             float period,
                                             float pressure) {
        int minX = Math.max(0, MathUtils.floor(cx - radius - 1f));
        int maxX = Math.min(width - 1, MathUtils.ceil(cx + radius + 1f));
        int minY = Math.max(0, MathUtils.floor(cy - radius - 1f));
        int maxY = Math.min(height - 1, MathUtils.ceil(cy + radius + 1f));
        float phase = s / period;
        int periodIndex = MathUtils.floor(phase);
        float inPeriod = phase - periodIndex;
        float rollingWave = 0.22f + 0.78f * (0.5f + 0.5f * MathUtils.sin(inPeriod * 6.2831855f));
        float wetSeed = pseudoInkNoise(periodIndex * 47 + 9, 113);
        float drySeed = pseudoInkNoise(periodIndex * 71 + 17, 211);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f - cx;
                float py = y + 0.5f - cy;
                float along = px * tx + py * ty;
                float cross = px * nx + py * ny;
                float r = Math.abs(cross) / Math.max(1f, radius);
                float alongLimit = Math.max(1.2f, lineWidth * 0.18f);
                float alongFalloff = 1f - smoothstep(alongLimit * 0.45f, alongLimit, Math.abs(along));
                if (r > 1.08f || alongFalloff <= 0f) {
                    continue;
                }
                float body = 1f - smoothstep(0.94f, 1.08f, r);
                float edgeVein = smoothstep(0.72f, 0.92f, r) * (1f - smoothstep(0.98f, 1.08f, r));
                float inwardRamp = smoothstep(0f, 0.92f, r);
                float interiorInk = 0.18f + 0.42f * inwardRamp + 0.28f * rollingWave;
                float profile = body * interiorInk + edgeVein * 0.92f;
                float local = (inPeriod + 0.17f * r) % 1f;
                float repeatedScratch = 1f;
                if (drySeed > 0.42f && local > 0.34f && local < 0.52f && r < 0.84f) {
                    repeatedScratch = 0.18f + 0.18f * pseudoInkNoise(periodIndex * 97 + 31, MathUtils.floor(r * 19f) + 5);
                }
                float fiber = 0.78f + 0.34f * pseudoInkNoise(MathUtils.floor(s * 0.37f) * 31 + MathUtils.floor(cross * 3f), MathUtils.floor(r * 23f) * 17 + 3);
                if (wetSeed > 0.76f && local < 0.22f) {
                    fiber *= 1.20f;
                }
                float waveMix = 0.74f + 0.26f * rollingWave;
                float alpha = BALLPOINT_TARGET_COVERAGE * 0.25f * pressure * profile * waveMix * repeatedScratch * fiber * alongFalloff;
                coverage[x][y] = Math.max(coverage[x][y], MathUtils.clamp(alpha, 0f, 0.96f));
            }
        }
    }

    private static java.util.ArrayList<VectorSample> sampleVectorStroke(VectorStroke stroke) {
        java.util.ArrayList<VectorSample> samples = new java.util.ArrayList<>();
        float cx = 0f;
        float cy = 0f;
        float sx = 0f;
        float sy = 0f;
        float cumulative = 0f;
        boolean hasPoint = false;
        for (PathCommand command : stroke.commands) {
            if (command.type == 'M') {
                cx = command.values[0];
                cy = command.values[1];
                sx = cx;
                sy = cy;
                if (samples.isEmpty()) {
                    samples.add(new VectorSample(cx, cy, cumulative));
                }
                hasPoint = true;
            } else if (command.type == 'L' && hasPoint) {
                float x = command.values[0];
                float y = command.values[1];
                cumulative = appendLineSamples(samples, cx, cy, x, y, cumulative);
                cx = x;
                cy = y;
            } else if (command.type == 'C' && hasPoint) {
                float x1 = command.values[0];
                float y1 = command.values[1];
                float x2 = command.values[2];
                float y2 = command.values[3];
                float x3 = command.values[4];
                float y3 = command.values[5];
                float px = cx;
                float py = cy;
                int steps = 24;
                for (int i = 1; i <= steps; i++) {
                    float t = i / (float) steps;
                    float omt = 1f - t;
                    float x = omt * omt * omt * cx + 3f * omt * omt * t * x1 + 3f * omt * t * t * x2 + t * t * t * x3;
                    float y = omt * omt * omt * cy + 3f * omt * omt * t * y1 + 3f * omt * t * t * y2 + t * t * t * y3;
                    cumulative = appendLineSamples(samples, px, py, x, y, cumulative);
                    px = x;
                    py = y;
                }
                cx = x3;
                cy = y3;
            } else if ((command.type == 'Z' || command.type == 'z') && hasPoint) {
                cumulative = appendLineSamples(samples, cx, cy, sx, sy, cumulative);
                cx = sx;
                cy = sy;
            }
        }
        return samples;
    }

    private static float appendLineSamples(java.util.ArrayList<VectorSample> samples,
                                           float x0,
                                           float y0,
                                           float x1,
                                           float y1,
                                           float cumulative) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001f) {
            return cumulative;
        }
        cumulative += length;
        samples.add(new VectorSample(x1, y1, cumulative));
        return cumulative;
    }

    private static java.util.ArrayList<VectorStroke> parseVectorStrokes(String svg) {
        java.util.ArrayList<VectorStroke> strokes = new java.util.ArrayList<>();
        float translateX = 0f;
        float translateY = 0f;
        java.util.regex.Matcher transform = java.util.regex.Pattern
                .compile("translate\\(([0-9.+\\-eE]+)\\s+([0-9.+\\-eE]+)\\)")
                .matcher(svg);
        if (transform.find()) {
            translateX = Float.parseFloat(transform.group(1));
            translateY = Float.parseFloat(transform.group(2));
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<path[^>]*d=\\\"([^\\\"]+)\\\"[^>]*>")
                .matcher(svg);
        while (matcher.find()) {
            String pathTag = matcher.group(0);
            String d = matcher.group(1);
            Color color = parseStrokeColor(pathTag);
            float strokeWidth = parseStrokeWidth(pathTag);
            java.util.ArrayList<PathCommand> commands = parsePathCommands(d, translateX, translateY);
            if (!commands.isEmpty()) {
                strokes.add(new VectorStroke(commands, color, strokeWidth));
            }
        }
        return strokes;
    }

    private static java.util.ArrayList<PathCommand> parsePathCommands(String d, float translateX, float translateY) {
        java.util.ArrayList<PathCommand> commands = new java.util.ArrayList<>();
        java.util.regex.Matcher tokenMatcher = java.util.regex.Pattern
                .compile("[A-Za-z]|[-+]?(?:\\d*\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?")
                .matcher(d);
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        while (tokenMatcher.find()) {
            tokens.add(tokenMatcher.group());
        }
        int index = 0;
        char command = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (isPathCommandToken(token)) {
                command = token.charAt(0);
                index++;
            }
            int count = command == 'M' || command == 'm' || command == 'L' || command == 'l' ? 2
                    : command == 'C' || command == 'c' ? 6
                    : command == 'Z' || command == 'z' ? 0 : -1;
            if (count < 0) {
                break;
            }
            if (count == 0) {
                commands.add(new PathCommand(command, new float[0]));
                continue;
            }
            while (index + count <= tokens.size() && !isPathCommandToken(tokens.get(index))) {
                float[] values = new float[count];
                for (int i = 0; i < count; i++) {
                    values[i] = Float.parseFloat(tokens.get(index++));
                }
                for (int i = 0; i < count; i += 2) {
                    values[i] += translateX;
                    values[i + 1] += translateY;
                }
                commands.add(new PathCommand(Character.toUpperCase(command), values));
                if (command == 'M' || command == 'm') {
                    command = command == 'm' ? 'l' : 'L';
                    count = 2;
                }
            }
        }
        return commands;
    }

    private static boolean isPathCommandToken(String token) {
        return token.length() == 1 && Character.isLetter(token.charAt(0));
    }

    private static Color parseStrokeColor(String pathTag) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("stroke=\\\"#([0-9a-fA-F]{6})\\\"")
                .matcher(pathTag);
        if (!matcher.find()) {
            return new Color(0f, 0f, 0f, 1f);
        }
        int rgb = Integer.parseInt(matcher.group(1), 16);
        return new Color(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
    }

    private static float parseStrokeWidth(String pathTag) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("stroke-width=\\\"([0-9.+\\-eE]+)\\\"")
                .matcher(pathTag);
        return matcher.find() ? Float.parseFloat(matcher.group(1)) : 1f;
    }

    private static class VectorStroke {
        final java.util.ArrayList<PathCommand> commands;
        final Color color;
        final float strokeWidth;

        VectorStroke(java.util.ArrayList<PathCommand> commands, Color color, float strokeWidth) {
            this.commands = commands;
            this.color = color;
            this.strokeWidth = strokeWidth;
        }
    }

    private static class PathCommand {
        final char type;
        final float[] values;

        PathCommand(char type, float[] values) {
            this.type = type;
            this.values = values;
        }
    }

    private static class VectorSample {
        final float x;
        final float y;
        final float s;

        VectorSample(float x, float y, float s) {
            this.x = x;
            this.y = y;
            this.s = s;
        }
    }

    private static void applyBallpointEffect(Pixmap pixmap, float baseOpacity, boolean textured) {
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
                    if (textured) {
                        float grain = 0.88f + 0.12f * pseudoInkNoise(x, y);
                        if (pseudoInkNoise(x * 3 + 17, y * 5 + 29) > 0.82f) {
                            grain *= 0.86f;
                        }
                        float tone = 0.96f + 0.08f * pseudoInkNoise(x * 11 + 7, y * 13 + 3);
                        color.r = MathUtils.clamp(color.r * tone, 0f, 1f);
                        color.g = MathUtils.clamp(color.g * tone, 0f, 1f);
                        color.b = MathUtils.clamp(color.b * tone, 0f, 1f);
                        color.a *= grain;
                        color.a = MathUtils.clamp(color.a * NEW_LINE_INK_ALPHA_MULTIPLIER, 0f, 1f);
                    }
                    pixmap.drawPixel(x, y, Color.rgba8888(color));
                }
            }
        }
        pixmap.setBlending(old);
    }

    private static void applyBallpointPenEffect(Pixmap pixmap,
                                                float edgeOpacity,
                                                float centerOpacity,
                                                float grainBase,
                                                float grainRange,
                                                float voidThreshold,
                                                float voidStrength,
                                                float edgeFeatherOpacity) {
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        int[][] dist = computeInkDistance(pixmap);
        int maxDist = maxInkDistance(pixmap, dist);
        float[][] pathDistance = computeBallpointPathDistance(dist, maxDist);
        float maxPathDistance = maxBallpointPathDistance(pathDistance, dist);
        Color color = new Color();
        Color inkColor = sampleInkColor(pixmap);
        float[][] coverage = new float[width][height];

        Pixmap.Blending old = pixmap.getBlending();
        pixmap.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixmap.getPixel(x, y);
                Color.rgba8888ToColor(color, pixel);
                if (color.a <= 0f) {
                    continue;
                }

                float center = maxDist <= 1 ? 1f : ((float) dist[x][y] - 1f) / ((float) maxDist - 1f);
                center = MathUtils.clamp(center, 0f, 1f);
                float path = Math.max(0f, pathDistance[x][y]);
                float shape = color.a > 0.02f ? 1f : 0f;
                float u = center;
                float passCoverage = plomaBallpointCoverage(path, u, maxPathDistance, maxDist, grainBase, grainRange) * shape;
                passCoverage = MathUtils.clamp(passCoverage, 0f, 0.94f);
                coverage[x][y] = Math.max(coverage[x][y], passCoverage);
            }
        }

        addBallpointCoverageFeather(coverage, width, height, edgeFeatherOpacity);

        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        int safeMaxDist = Math.max(1, maxDist);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float alpha = coverage[x][y];
                if (alpha <= 0.01f) {
                    continue;
                }
                float center = safeMaxDist <= 1 ? 1f : ((float) dist[x][y] - 1f) / ((float) safeMaxDist - 1f);
                center = MathUtils.clamp(center, 0f, 1f);
                float tone = ballpointPeriodicTone(Math.max(0f, pathDistance[x][y]), center, safeMaxDist);
                pixmap.setColor(
                        MathUtils.clamp(inkColor.r * tone, 0f, 1f),
                        MathUtils.clamp(inkColor.g * tone, 0f, 1f),
                        MathUtils.clamp(inkColor.b * tone, 0f, 1f),
                        alpha);
                pixmap.drawPixel(x, y);
            }
        }
        pixmap.setBlending(old);
    }

    private static float ballRollWave(float pathDistance, int maxDist) {
        float period = ballRollPeriod(maxDist);
        float travel = Math.max(0f, pathDistance);
        float wave = 0.40f + 0.90f * (0.5f + 0.5f * MathUtils.sin(travel / period * 6.2831855f));
        wave += 0.10f * MathUtils.sin(travel / period * 12.566371f + 1.7f);
        float wetPatch = pseudoInkNoise((int) (travel / Math.max(1f, period)) + 17, (int) (travel / Math.max(1f, period * 0.37f)) + 31);
        if (wetPatch > 0.74f) {
            wave += 0.045f;
        } else if (wetPatch < 0.18f) {
            wave -= 0.045f;
        }
        return MathUtils.clamp(wave, 0.34f, 1.40f);
    }

    private static float ballRollPeriod(int maxDist) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        return Math.max(20f, lineWidth * 3.1415927f);
    }

    private static float ballpointPeriodicFiber(float pathDistance,
                                                float center,
                                                int maxDist,
                                                float grainBase,
                                                float grainRange) {
        float noise = ballpointPeriodicNoise(pathDistance, center, maxDist);
        return MathUtils.clamp(0.76f + 0.44f * noise, 0.64f, 1.24f);
    }

    private static float ballpointPeriodicScratch(float pathDistance,
                                                  float center,
                                                  int maxDist,
                                                  float voidThreshold,
                                                  float voidStrength) {
        float phaseNoise = ballpointPeriodicNoise(pathDistance + ballRollPeriod(maxDist) * 0.31f, center * 0.83f + 0.11f, maxDist);
        float scratch = 1f;
        float repeatedBreak = ballpointRepeatedBreak(pathDistance, center, maxDist);
        scratch *= repeatedBreak;
        if (phaseNoise > voidThreshold - 0.08f) {
            scratch -= voidStrength * 1.2f * (0.55f + 0.45f * center);
        }
        return MathUtils.clamp(scratch, 0.10f, 1.08f);
    }

    private static float ballpointPeriodicTone(float pathDistance, float center, int maxDist) {
        float noise = ballpointPeriodicNoise(pathDistance + ballRollPeriod(maxDist) * 0.17f, center, maxDist);
        return 0.92f + 0.16f * noise;
    }

    private static float ballpointHorizontalCoverage(float pathDistance, int maxDist) {
        return ballRollWave(pathDistance, maxDist);
    }

    private static float p5PenCoverage(float pathDistance,
                                       float u,
                                       float maxPathDistance,
                                       int maxDist,
                                       float grainBase,
                                       float grainRange) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float strokeWeight = lineWidth;
        float p5Weight = 0.30f * strokeWeight;
        float p5Scatter = 0.15f * strokeWeight;
        float p5Sharpness = 0.90f;
        float p5Grain = 0.70f;
        float p5Opacity = 0.92f;
        float p5Spacing = Math.max(1f, 0.10f * strokeWeight);

        float pressure = p5PenPressure(pathDistance, maxPathDistance, maxDist);
        float plotted = Math.max(0f, pathDistance);
        int step = MathUtils.floor(plotted / p5Spacing);
        float local = (plotted - step * p5Spacing) / p5Spacing;
        int row = MathUtils.floor(u * 18f);
        float stampRandom = pseudoInkNoise(step * 131 + row * 17 + 3, row * 67 + 41);
        float grainGate = stampRandom >= p5Grain * pressure
                ? 0.34f + 0.18f * pseudoInkNoise(step * 59 + 13, row * 97 + 3)
                : 0.86f + 0.14f * pseudoInkNoise(step * 43 + 5, row * 71 + 17);

        float scatterNoise = pseudoInkNoise(step * 97 + row * 23 + 11, row * 53 + 7) * 2f - 1f;
        float alongNoise = pseudoInkNoise(step * 89 + row * 29 + 19, row * 31 + 13) * 2f - 1f;
        float vibration = p5Scatter * (p5Sharpness + (1f - p5Sharpness) * Math.abs(scatterNoise) / Math.max(0.25f, pressure));
        float radialOffset = scatterNoise * vibration / Math.max(1f, lineWidth * 0.5f);
        float alongStamp = 1f - Math.abs(local - (0.5f + 0.20f * alongNoise)) / 0.58f;
        alongStamp = MathUtils.clamp(alongStamp, 0f, 1f);
        alongStamp = alongStamp * alongStamp * (3f - 2f * alongStamp);

        float radius = Math.max(0.05f, (p5Weight * pressure * pressure) / Math.max(1f, lineWidth));
        float stampU = MathUtils.clamp(u + radialOffset, 0f, 1f);
        float radialStamp = 1f - Math.abs(1f - stampU) / Math.max(0.20f, radius);
        radialStamp = MathUtils.clamp(radialStamp, 0f, 1f);
        radialStamp = radialStamp * radialStamp * (3f - 2f * radialStamp);

        float fiber = p5PenFiber(plotted, u, maxDist, grainBase, grainRange);
        float microBreak = p5PenMicroBreak(plotted, u, maxDist);
        float edgeFalloff = p5PenEdgeFalloff(u);
        float startPress = 1f + 0.45f * ballpointStartPressure(pathDistance, maxDist);
        float endFade = ballpointPressureEnvelope(pathDistance, maxPathDistance, maxDist);
        float stampBody = Math.max(0.58f * edgeFalloff, radialStamp * alongStamp);
        return BALLPOINT_TARGET_COVERAGE
                * p5Opacity
                * stampBody
                * grainGate
                * fiber
                * microBreak
                * pressure
                * startPress
                * endFade;
    }

    private static float plomaBallpointCoverage(float pathDistance,
                                                float u,
                                                float maxPathDistance,
                                                int maxDist,
                                                float grainBase,
                                                float grainRange) {
        float pressure = plomaPressure(pathDistance, maxPathDistance, maxDist);
        float radialDistance = (1f - MathUtils.clamp(u, 0f, 1f)) * 1.55f;
        float widthOffset = plomaWidthOffset(pressure);
        float alpha = (1.5f / Math.max(0.35f, radialDistance - widthOffset)) - 0.425f;
        if (radialDistance < widthOffset) {
            alpha = 1f;
        }
        alpha = MathUtils.clamp(alpha * 1.85f, 0f, 1f);

        float texture = plomaTextureSample(pathDistance, u, maxDist);
        float grain = plomaGrain(pathDistance, u, pressure);
        float textureAmp = plomaTextureAmplifier(pathDistance, u, maxDist);
        float edgeFiber = 0.84f + 0.16f * smoothstep(0.05f, 0.42f, u);
        float envelope = ballpointPressureEnvelope(pathDistance, maxPathDistance, maxDist);
        float start = 1f + 0.18f * ballpointStartPressure(pathDistance, maxDist);
        float paper = MathUtils.clamp(grainBase + grainRange * (texture - 0.5f), 0.70f, 1.10f);
        return BALLPOINT_TARGET_COVERAGE * alpha * texture * grain * textureAmp * edgeFiber * envelope * start * paper;
    }

    private static float plomaPressure(float pathDistance, float maxPathDistance, int maxDist) {
        float t = maxPathDistance <= 1f ? 0f : MathUtils.clamp(pathDistance / maxPathDistance, 0f, 1f);
        float slowInk = 0.72f + 0.18f * MathUtils.sin(t * 6.2831855f + 0.45f)
                + 0.08f * MathUtils.sin(t * 18.849556f + 1.7f);
        float endLength = Math.max(2f, Math.min(Math.max(2f, maxDist * 0.75f), Math.max(2f, maxPathDistance * 0.25f)));
        float remaining = Math.max(0f, maxPathDistance - pathDistance);
        float end = smoothstep(0f, endLength, remaining);
        float start = 1f + 0.12f * ballpointStartPressure(pathDistance, maxDist);
        return MathUtils.clamp(slowInk * end * start, 0f, 1f);
    }

    private static float plomaWidthOffset(float pressure) {
        float p = MathUtils.clamp(pressure, 0f, 1f);
        if (p < 0.2f) {
            return MathUtils.lerp(-3.50f, -3.20f, p / 0.2f);
        }
        if (p < 0.45f) {
            return MathUtils.lerp(-3.20f, -2.50f, (p - 0.2f) / 0.25f);
        }
        if (p < 0.8f) {
            return MathUtils.lerp(-2.50f, -1.70f, (p - 0.45f) / 0.35f);
        }
        if (p < 0.95f) {
            return MathUtils.lerp(-1.70f, -1.55f, (p - 0.8f) / 0.15f);
        }
        return MathUtils.lerp(-1.55f, -1.30f, (p - 0.95f) / 0.05f);
    }

    private static float plomaTextureSample(float pathDistance, float u, int maxDist) {
        float step = Math.max(1f, maxDist * 0.33f);
        int cell = MathUtils.floor(pathDistance / step);
        float local = pathDistance / step - cell;
        int row = MathUtils.floor(u * 17f);
        float a = pseudoInkNoise(cell * 47 + row * 13 + 5, row * 83 + 19);
        float b = pseudoInkNoise((cell + 1) * 47 + row * 13 + 5, row * 83 + 19);
        float texture = MathUtils.lerp(a, b, smoothstep(0f, 1f, local));
        float fiber = 0.5f + 0.5f * MathUtils.sin((pathDistance / Math.max(8f, maxDist * 1.9f)) * 6.2831855f + row * 0.37f);
        return MathUtils.clamp(0.42f + 0.42f * texture + 0.32f * fiber, 0.22f, 1.20f);
    }

    private static float plomaGrain(float pathDistance, float u, float pressure) {
        float p = MathUtils.clamp(pressure, 0f, 1f);
        float base = MathUtils.lerp(0.80f, 0.95f, p);
        float prob = 1f - p * p * p * p * p;
        int cell = MathUtils.floor(pathDistance * 1.7f);
        int row = MathUtils.floor(u * 23f);
        float rnd = pseudoInkNoise(cell * 109 + 17, row * 61 + 31);
        if (rnd < prob * 0.42f && u > 0.12f) {
            return 0.08f + 0.20f * pseudoInkNoise(cell * 131 + 7, row * 41 + 3);
        }
        return base;
    }

    private static float plomaTextureAmplifier(float pathDistance, float u, int maxDist) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float longStep = Math.max(1.2f, lineWidth * 0.18f);
        int cell = MathUtils.floor(pathDistance / longStep);
        int row = MathUtils.floor(u * 31f);
        float n = pseudoInkNoise(cell * 193 + row * 17 + 29, row * 137 + 11);
        float fiber = 0.5f + 0.5f * MathUtils.sin(pathDistance * 1.17f + row * 0.91f);
        float mixed = 0.65f * n + 0.35f * fiber;
        if (mixed < 0.28f && u > 0.10f) {
            return 0.16f + 0.26f * mixed;
        }
        if (mixed > 0.78f) {
            return 1.08f + 0.28f * mixed;
        }
        return 0.68f + 0.42f * mixed;
    }

    private static float p5PenPressure(float pathDistance, float maxPathDistance, int maxDist) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float t = maxPathDistance <= 1f ? 0f : MathUtils.clamp(pathDistance / maxPathDistance, 0f, 1f);
        float curveA = 0.5f + 0.15f * 0.18f;
        float curveB = 1f - 0.20f * 1.15f;
        float peak = curveA;
        float halfWidth = (t < peak ? curveB * 1.2f : curveB * 0.8f) * 0.5f;
        float gaussian = 1f / (1f + (float) Math.pow(Math.abs((t - peak) / Math.max(0.02f, halfWidth)), 6.4f));
        float pressure = 1.2f + (1.0f - 1.2f) * MathUtils.clamp(gaussian, 0f, 1f);
        float startLength = Math.max(8f, lineWidth * 1.4f);
        float endLength = Math.max(2f, Math.min(lineWidth * 2.6f, Math.max(2f, maxPathDistance * 0.35f)));
        float start = 1f + 0.22f * (1f - smoothstep(0f, startLength, pathDistance));
        float remaining = Math.max(0f, maxPathDistance - pathDistance);
        float end = smoothstep(0f, endLength, remaining);
        return MathUtils.clamp(pressure * start * end, 0f, 1.38f);
    }

    private static float p5PenEdgeFalloff(float u) {
        float edge = 0.35f + 0.65f * smoothstep(0.00f, 0.18f, u);
        float body = 1f - 0.30f * smoothstep(0.72f, 1.00f, u);
        return MathUtils.clamp(edge * body, 0f, 1f);
    }

    private static float p5PenFiber(float pathDistance,
                                    float u,
                                    int maxDist,
                                    float grainBase,
                                    float grainRange) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float cell = Math.max(1f, lineWidth * 0.36f);
        int xCell = MathUtils.floor(pathDistance / cell);
        int yCell = MathUtils.floor(u * 24f);
        float n0 = pseudoInkNoise(xCell * 47 + yCell * 17 + 5, yCell * 83 + 29);
        float n1 = pseudoInkNoise((xCell + 1) * 47 + yCell * 17 + 5, yCell * 83 + 29);
        float t = smoothstep(0f, 1f, pathDistance / cell - xCell);
        float noise = MathUtils.lerp(n0, n1, t);
        float periodWave = ballRollWave(pathDistance, maxDist);
        return MathUtils.clamp(grainBase + grainRange * (noise - 0.5f) + 0.22f * (periodWave - 0.8f), 0.48f, 1.24f);
    }

    private static float p5PenMicroBreak(float pathDistance, float u, int maxDist) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float cell = Math.max(1f, lineWidth * 0.33f);
        int index = MathUtils.floor(pathDistance / cell);
        float local = pathDistance / cell - index;
        float chance = pseudoInkNoise(index * 71 + 17, MathUtils.floor(u * 12f) * 43 + 23);
        if (chance < 0.62f || u < 0.18f) {
            return 1f;
        }
        float gapCenter = 0.25f + 0.50f * pseudoInkNoise(index * 97 + 31, 7);
        float gapWidth = 0.12f + 0.10f * pseudoInkNoise(index * 101 + 11, 19);
        float gap = 1f - smoothstep(gapWidth * 0.45f, gapWidth, Math.abs(local - gapCenter));
        return 1f - (0.55f + 0.32f * chance) * gap;
    }

    private static float ballpointBrushProfile(float u, float pathDistance, int maxDist) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float wave = ballRollWave(pathDistance, maxDist);
        float edgeVein = 1f - smoothstep(0.00f, 0.24f, u);
        float edgeShoulder = 1f - smoothstep(0.00f, 0.72f, u);
        float center = smoothstep(0.28f, 1.00f, u);
        float centerInk = (0.36f + 0.66f * wave) * (0.76f + 0.18f * center);
        float profile = centerInk + 1.38f * edgeVein + 0.44f * edgeShoulder;

        float phase = pathDistance / Math.max(1f, ballRollPeriod(maxDist));
        phase -= MathUtils.floor(phase);
        float bristlePhase = phase * 7f;
        int bristleCell = MathUtils.floor(bristlePhase);
        float bristleLocal = bristlePhase - bristleCell;
        float bristleJitter = pseudoInkNoise(bristleCell * 73 + 19, 211);
        float bristleWidth = 0.045f + 0.035f * pseudoInkNoise(bristleCell * 91 + 23, 19);
        float bristleCenter = 0.30f + 0.50f * bristleJitter;
        float bristlePulse = smoothPulseCircular(bristleLocal, 0.5f, 0.30f);
        float bristle = MathUtils.clamp(1f - Math.abs(u - bristleCenter) / bristleWidth, 0f, 1f);
        bristle = bristle * bristle * (3f - 2f * bristle);
        profile += 0.22f * bristle * bristlePulse;

        float stampScatter = 0.88f + 0.18f * pseudoInkNoise(
                MathUtils.floor(pathDistance / Math.max(1.0f, lineWidth * 0.45f)) * 31 + 7,
                MathUtils.floor(u * 12f) * 47 + 5);
        return MathUtils.clamp(profile * stampScatter, 0f, 2.85f);
    }

    private static float ballpointBrushGrain(float pathDistance, float u, int maxDist) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float step = Math.max(1.0f, lineWidth * 0.28f);
        int cell = MathUtils.floor(pathDistance / step);
        int row = MathUtils.floor(u * 15f);
        float grain = pseudoInkNoise(cell * 97 + row * 13 + 5, row * 61 + 31);
        float keep = grain > 0.16f ? 1f : 0.58f + 0.30f * grain;
        float wetness = 0.92f + 0.18f * pseudoInkNoise(cell * 43 + 11, row * 79 + 17);
        return MathUtils.clamp(keep * wetness, 0.30f, 1.10f);
    }

    private static float ballpointBrushGap(float pathDistance, float u, int maxDist) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float cell = lineWidth;
        int cellIndex = MathUtils.floor(pathDistance / cell);
        float inCell = pathDistance - cellIndex * cell;
        float gapStart = cell * (0.15f + 0.30f * pseudoInkNoise(cellIndex * 37 + 5, 13));
        float gapLength = cell * (0.24f + 0.15f * pseudoInkNoise(cellIndex * 41 + 17, 29));
        float edgeSharpness = Math.max(0.9f, lineWidth * 0.035f);
        float gap = smoothstep(gapStart, gapStart + edgeSharpness, inCell)
                * (1f - smoothstep(gapStart + gapLength - edgeSharpness, gapStart + gapLength, inCell));
        float hasGap = pseudoInkNoise(cellIndex * 53 + 7, 97) > 0.28f ? 1f : 0f;
        float gapDepth = 0.70f + 0.25f * pseudoInkNoise(cellIndex * 61 + 11, 131);
        float protectEdgeVeins = smoothstep(0.14f, 0.42f, u);
        return 1f - hasGap * gapDepth * gap * protectEdgeVeins;
    }

    private static float ballpointBrushFiber(float pathDistance,
                                             float center,
                                             int maxDist,
                                             float grainBase,
                                             float grainRange) {
        float noise = ballpointPeriodicNoise(pathDistance, center, maxDist);
        float slow = ballRollWave(pathDistance + ballRollPeriod(maxDist) * 0.19f, maxDist);
        return MathUtils.clamp(0.72f + 0.28f * noise + 0.18f * slow, 0.58f, 1.22f);
    }

    private static float ballpointRepeatedBreak(float pathDistance, float center, int maxDist) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float cell = lineWidth;
        int cellIndex = MathUtils.floor(pathDistance / cell);
        float inCell = pathDistance - cellIndex * cell;
        float gapStart = cell * (0.12f + 0.32f * pseudoInkNoise(cellIndex * 37 + 5, 13));
        float gapLength = cell * (0.26f + 0.12f * pseudoInkNoise(cellIndex * 41 + 17, 29));
        float edgeSharpness = Math.max(1.2f, lineWidth * 0.045f);
        float gap = smoothstep(gapStart, gapStart + edgeSharpness, inCell)
                * (1f - smoothstep(gapStart + gapLength - edgeSharpness, gapStart + gapLength, inCell));
        float hasGap = pseudoInkNoise(cellIndex * 53 + 7, 97) > 0.22f ? 1f : 0f;
        float gapDepth = 0.70f + 0.25f * pseudoInkNoise(cellIndex * 61 + 11, 131);
        float protectVeins = smoothstep(0.18f, 0.46f, center);
        return 1f - hasGap * gapDepth * gap * protectVeins;
    }

    private static float ballpointVerticalProfile(float u) {
        float edge = 3.0f;
        float center = 0.72f;
        float edgeInfluence = 1f - smoothstep(0.0f, 0.72f, u);
        return center + (edge - center) * edgeInfluence;
    }

    private static float ballpointMiniVein(float u, float pathDistance, int maxDist) {
        float period = ballRollPeriod(maxDist);
        float lineWidth = Math.max(2f, maxDist * 2f);
        float phase = pathDistance / period;
        phase -= MathUtils.floor(phase);
        float length = MathUtils.clamp((lineWidth * 0.75f) / period, 0.08f, 0.24f);
        float pulse = smoothPulseCircular(phase, 0.47f, length);
        float radial = MathUtils.clamp(1f - Math.abs(u - 0.15f) / 0.045f, 0f, 1f);
        radial = radial * radial * (3f - 2f * radial);
        float gate = pseudoInkNoise(43, 91) > 0.35f ? 1f : 0f;
        return gate * pulse * radial * 0.45f;
    }

    private static float smoothPulseCircular(float phase, float center, float halfWidth) {
        float d = Math.abs(phase - center);
        d = Math.min(d, 1f - d);
        float pulse = 1f - smoothstep(halfWidth * 0.35f, halfWidth, d);
        return MathUtils.clamp(pulse, 0f, 1f);
    }

    private static float ballpointPeriodicNoise(float pathDistance, float center, int maxDist) {
        float period = ballRollPeriod(maxDist);
        float phase = pathDistance / period;
        phase -= MathUtils.floor(phase);
        float radial = MathUtils.clamp(center, 0f, 1f);

        float phasePos = phase * 16f;
        int p0 = MathUtils.floor(phasePos);
        int p1 = (p0 + 1) & 15;
        float pt = smoothstep(0f, 1f, phasePos - p0);
        p0 &= 15;

        float radialPos = radial * 5f;
        int r0 = MathUtils.clamp(MathUtils.floor(radialPos), 0, 5);
        int r1 = Math.min(5, r0 + 1);
        float rt = smoothstep(0f, 1f, radialPos - r0);

        float n00 = pseudoInkNoise(p0 * 37 + 11, r0 * 53 + 7);
        float n10 = pseudoInkNoise(p1 * 37 + 11, r0 * 53 + 7);
        float n01 = pseudoInkNoise(p0 * 37 + 11, r1 * 53 + 7);
        float n11 = pseudoInkNoise(p1 * 37 + 11, r1 * 53 + 7);
        float along0 = MathUtils.lerp(n00, n10, pt);
        float along1 = MathUtils.lerp(n01, n11, pt);
        float tableNoise = MathUtils.lerp(along0, along1, rt);

        float harmonic = 0.5f
                + 0.22f * MathUtils.sin(phase * 6.2831855f * 3f + radial * 1.7f)
                + 0.16f * MathUtils.sin(phase * 6.2831855f * 7f + radial * 4.1f)
                + 0.10f * MathUtils.sin(phase * 6.2831855f * 11f + radial * 2.9f);
        return MathUtils.clamp(0.58f * tableNoise + 0.42f * harmonic, 0f, 1f);
    }

    private static float ballpointPressureEnvelope(float pathDistance, float maxPathDistance, int maxDist) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float endLength = Math.max(8f, Math.min(lineWidth * 3f, Math.max(8f, maxPathDistance * 0.35f)));
        float remaining = Math.max(0f, maxPathDistance - pathDistance);
        float endFade = smoothstep(0f, endLength, remaining);
        return MathUtils.clamp(endFade, 0f, 1f);
    }

    private static float ballpointStartPressure(float pathDistance, int maxDist) {
        float lineWidth = Math.max(2f, maxDist * 2f);
        float startLength = Math.max(12f, lineWidth * 3f);
        return 1f - smoothstep(0f, startLength, pathDistance);
    }

    private static float ballpointSideRidge(float u) {
        float ridge = MathUtils.clamp(1f - Math.abs(u - 0.19f) / 0.26f, 0f, 1f);
        ridge = ridge * ridge * (3f - 2f * ridge);
        float edgeLift = smoothstep(0.02f, 0.11f, u);
        float innerFalloff = 1f - 0.10f * smoothstep(0.35f, 0.62f, u);
        return ridge * edgeLift * innerFalloff;
    }

    private static float ballpointCenterBody(float u, float longitudinal) {
        float fromVeinToCenter = smoothstep(0.19f, 1f, u);
        float wave = MathUtils.clamp((longitudinal - 0.34f) / (1.16f - 0.34f), 0f, 1f);
        float centerTarget = 0.28f + 0.76f * wave;
        float veinShoulder = 0.66f;
        float monotonicHalfProfile = veinShoulder + (centerTarget - veinShoulder) * fromVeinToCenter;
        float edgeEntry = smoothstep(0.07f, 0.19f, u);
        return edgeEntry * monotonicHalfProfile;
    }

    private static float maxBallpointPathDistance(float[][] pathDistance, int[][] dist) {
        int width = pathDistance.length;
        int height = pathDistance[0].length;
        float max = 0f;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (dist[x][y] > 0 && dist[x][y] < Integer.MAX_VALUE && pathDistance[x][y] > max) {
                    max = pathDistance[x][y];
                }
            }
        }
        return max;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge1 == edge0) {
            return value < edge0 ? 0f : 1f;
        }
        float t = MathUtils.clamp((value - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float[][] computeBallpointPathDistance(int[][] dist, int maxDist) {
        int width = dist.length;
        int height = dist[0].length;
        boolean[][] center = new boolean[width][height];
        float[][] centerDistance = new float[width][height];
        boolean[][] visited = new boolean[width][height];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        int threshold = Math.max(1, maxDist - 1);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                centerDistance[x][y] = -1f;
            }
        }
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (dist[x][y] >= threshold && dist[x][y] < Integer.MAX_VALUE) {
                    center[x][y] = true;
                }
            }
        }

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (!center[x][y] || visited[x][y]) {
                    continue;
                }
                int[] start = findCenterComponentStart(center, visited, x, y);
                assignCenterDistances(center, centerDistance, start[0], start[1]);
            }
        }

        float[][] pathDistance = new float[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pathDistance[x][y] = -1f;
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (centerDistance[x][y] >= 0f) {
                    pathDistance[x][y] = centerDistance[x][y];
                    queue.add(new int[]{x, y});
                }
            }
        }

        int[] dx = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dy = {0, 0, -1, 1, -1, 1, -1, 1};
        while (!queue.isEmpty()) {
            int[] point = queue.poll();
            for (int i = 0; i < 8; i++) {
                int nx = point[0] + dx[i];
                int ny = point[1] + dy[i];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height
                        && dist[nx][ny] > 0 && dist[nx][ny] < Integer.MAX_VALUE && pathDistance[nx][ny] < 0f) {
                    pathDistance[nx][ny] = pathDistance[point[0]][point[1]];
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return pathDistance;
    }

    private static int[] findCenterComponentStart(boolean[][] center, boolean[][] globalVisited, int seedX, int seedY) {
        int width = center.length;
        int height = center[0].length;
        java.util.ArrayList<int[]> points = new java.util.ArrayList<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{seedX, seedY});
        globalVisited[seedX][seedY] = true;
        int[] dx = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dy = {0, 0, -1, 1, -1, 1, -1, 1};
        while (!queue.isEmpty()) {
            int[] point = queue.poll();
            points.add(point);
            for (int i = 0; i < 8; i++) {
                int nx = point[0] + dx[i];
                int ny = point[1] + dy[i];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && center[nx][ny] && !globalVisited[nx][ny]) {
                    globalVisited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        int[] best = points.get(0);
        int bestDegree = 9;
        for (int[] point : points) {
            int degree = centerDegree(center, point[0], point[1]);
            if (degree < bestDegree || (degree == bestDegree && point[0] + point[1] < best[0] + best[1])) {
                best = point;
                bestDegree = degree;
            }
        }
        return best;
    }

    private static void assignCenterDistances(boolean[][] center, float[][] centerDistance, int startX, int startY) {
        int width = center.length;
        int height = center[0].length;
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        centerDistance[startX][startY] = 0f;
        queue.add(new int[]{startX, startY});
        int[] dx = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dy = {0, 0, -1, 1, -1, 1, -1, 1};
        while (!queue.isEmpty()) {
            int[] point = queue.poll();
            float base = centerDistance[point[0]][point[1]];
            for (int i = 0; i < 8; i++) {
                int nx = point[0] + dx[i];
                int ny = point[1] + dy[i];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && center[nx][ny] && centerDistance[nx][ny] < 0f) {
                    centerDistance[nx][ny] = base + (dx[i] == 0 || dy[i] == 0 ? 1f : 1.4142135f);
                    queue.add(new int[]{nx, ny});
                }
            }
        }
    }

    private static int centerDegree(boolean[][] center, int x, int y) {
        int width = center.length;
        int height = center[0].length;
        int degree = 0;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) {
                    continue;
                }
                int nx = x + ox;
                int ny = y + oy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && center[nx][ny]) {
                    degree++;
                }
            }
        }
        return degree;
    }

    private static Color sampleInkColor(Pixmap pixmap) {
        Color color = new Color();
        Color result = new Color(0f, 0f, 0f, 1f);
        float bestDarkness = -1f;
        for (int y = 0; y < pixmap.getHeight(); y++) {
            for (int x = 0; x < pixmap.getWidth(); x++) {
                Color.rgba8888ToColor(color, pixmap.getPixel(x, y));
                if (color.a <= 0f) {
                    continue;
                }
                float darkness = Math.max(Math.max(1f - color.r, 1f - color.g), 1f - color.b);
                if (darkness > bestDarkness) {
                    bestDarkness = darkness;
                    result.set(color.r, color.g, color.b, 1f);
                }
            }
        }
        return result;
    }

    private static void addBallpointCoverageFeather(float[][] coverage, int width, int height, float edgeFeatherOpacity) {
        float[][] source = new float[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                source[x][y] = coverage[x][y];
            }
        }
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (source[x][y] > 0.03f) {
                    continue;
                }
                float best = Math.max(Math.max(source[x - 1][y], source[x + 1][y]),
                        Math.max(source[x][y - 1], source[x][y + 1]));
                if (best > 0.18f) {
                    coverage[x][y] = Math.max(coverage[x][y], best * edgeFeatherOpacity);
                }
            }
        }
    }

    private static int[][] computeInkDistance(Pixmap pixmap) {
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
        return dist;
    }

    private static int maxInkDistance(Pixmap pixmap, int[][] dist) {
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        int maxDist = 0;
        Color color = new Color();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color.rgba8888ToColor(color, pixmap.getPixel(x, y));
                if (color.a > 0f && dist[x][y] > maxDist) {
                    maxDist = dist[x][y];
                }
            }
        }
        return maxDist;
    }

    private static void addBallpointEdgeFeather(Pixmap pixmap, float edgeFeatherOpacity) {
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        int[][] pixels = new int[width][height];
        Color color = new Color();
        Color neighbor = new Color();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[x][y] = pixmap.getPixel(x, y);
            }
        }

        Pixmap.Blending old = pixmap.getBlending();
        pixmap.setBlending(Pixmap.Blending.None);
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                Color.rgba8888ToColor(color, pixels[x][y]);
                if (color.a > 0.03f) {
                    continue;
                }
                float bestAlpha = 0f;
                Color best = null;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        if (Math.abs(ox) + Math.abs(oy) != 1) {
                            continue;
                        }
                        Color.rgba8888ToColor(neighbor, pixels[x + ox][y + oy]);
                        if (neighbor.a > bestAlpha) {
                            bestAlpha = neighbor.a;
                            if (best == null) {
                                best = new Color();
                            }
                            best.set(neighbor);
                        }
                    }
                }
                if (bestAlpha > 0.24f && best != null) {
                    pixmap.setColor(best.r, best.g, best.b, MathUtils.clamp(bestAlpha * edgeFeatherOpacity, 0.10f, 0.22f));
                    pixmap.drawPixel(x, y);
                }
            }
        }
        pixmap.setBlending(old);
    }

    private static float pseudoInkNoise(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0x7fffffff) / 2147483647f;
    }

    private static void addInkEdgeFeather(Pixmap pixmap) {
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        int[][] pixels = new int[width][height];
        Color color = new Color();
        Color neighbor = new Color();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[x][y] = pixmap.getPixel(x, y);
            }
        }

        Pixmap.Blending old = pixmap.getBlending();
        pixmap.setBlending(Pixmap.Blending.None);
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                Color.rgba8888ToColor(color, pixels[x][y]);
                if (color.a > 0.04f) {
                    continue;
                }
                float bestAlpha = 0f;
                Color best = null;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        if (ox == 0 && oy == 0) {
                            continue;
                        }
                        Color.rgba8888ToColor(neighbor, pixels[x + ox][y + oy]);
                        if (neighbor.a > bestAlpha) {
                            bestAlpha = neighbor.a;
                            if (best == null) {
                                best = new Color();
                            }
                            best.set(neighbor);
                        }
                    }
                }
                if (bestAlpha > 0.55f && best != null) {
                    float alpha = MathUtils.clamp(bestAlpha * 0.47f, 0.29f, 0.37f);
                    pixmap.setColor(best.r, best.g, best.b, alpha);
                    pixmap.drawPixel(x, y);
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
        if (lineDiagnosticMode) {
            renderLineDiagnostic();
            if (AUTO_EXIT && ++framesRendered > 2) {
                Gdx.app.exit();
            }
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
                drawGradeTexture(350, 250, 100, 100);
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
        drawGradeTexture(grade.x, grade.y, grade.width, grade.height);
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

    private void renderLineDiagnostic() {
        Gdx.gl.glClearColor(1, 1, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (legacyHamsterTexture != null && legacyHamster != null) {
            batch.draw(legacyHamsterTexture, legacyHamster.x, legacyHamster.y, legacyHamster.width, legacyHamster.height);
        }
        if (hamsterTexture != null && candidateHamster != null) {
            batch.draw(hamsterTexture, candidateHamster.x, candidateHamster.y, candidateHamster.width, candidateHamster.height);
        }
        drawGradeTexture(grade.x, grade.y, grade.width, grade.height);
        for (Block block : diagnosticBlocks) {
            if (block.texture != null && block.drawBounds != null) {
                Rectangle drawBounds = block.drawBounds;
                batch.draw(block.texture, drawBounds.x, drawBounds.y, drawBounds.width, drawBounds.height);
            }
        }
        batch.end();

        saveLineDiagnosticScreenshot();
    }

    private void drawGradeTexture(float x, float y, float width, float height) {
        if (gradeTexture == null) {
            return;
        }
        float padX = width * GRADE_SVG_PADDING / 32f;
        float padY = height * GRADE_SVG_PADDING / 45f;
        batch.draw(gradeTexture, x - padX, y - padY, width + padX * 2f, height + padY * 2f);
    }

    private void saveLineDiagnosticScreenshot() {
        if (lineDiagnosticScreenshotSaved) {
            return;
        }
        String path = System.getProperty("lineDiagnosticScreenshot", "");
        if (path.length() == 0) {
            return;
        }
        lineDiagnosticScreenshotSaved = true;
        Pixmap screenshot = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        try {
            PixmapIO.writePNG(Gdx.files.absolute(path), screenshot);
            Gdx.app.log("Main", "Line diagnostic screenshot saved to " + path);
        } finally {
            screenshot.dispose();
        }
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (hamsterTexture != null) hamsterTexture.dispose();
        if (legacyHamsterTexture != null) legacyHamsterTexture.dispose();
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
