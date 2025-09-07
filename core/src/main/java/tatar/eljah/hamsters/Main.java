package tatar.eljah.hamsters;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.PixmapIO;
import io.github.fxzjshm.gdx.svg2pixmap.Svg2Pixmap;

import java.io.BufferedReader;
import java.io.StringReader;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private Texture hamsterTexture;
    private Texture gradeTexture;
    private Texture blockTexture;
    private Texture backgroundTexture;
    private Pixmap backgroundPixmap;
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;

    private OrthographicCamera camera;

    private Rectangle hamster;
    private Rectangle grade;
    private Array<Block> blocks;

    private Vector2 gradeDirection;
    private boolean gameOver;
    private boolean hamsterWin;
    private boolean[][] grid;
    private int hamsterScore;
    private int gradeScore;
    private OnscreenControlRenderer controlRenderer;
    private int framesRendered;
    private static final boolean AUTO_EXIT = Boolean.parseBoolean(System.getProperty("headless", "false"));

    private volatile boolean loading;
    private volatile float loadingProgress;
    private FileHandle cacheDir;
    private int[] corridorCenters;
    private float[][] blockRanges;
    private static final float GUIDE_STROKE_WIDTH = 2f;

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        shapeRenderer = new ShapeRenderer();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 800, 600);

        hamsterScore = 0;
        gradeScore = 0;

        loading = true;
        loadingProgress = 0f;
        String tmp = System.getProperty("java.io.tmpdir");
        cacheDir = (tmp != null ? Gdx.files.absolute(tmp) : Gdx.files.local(".")).child("hamstersgame-cache");
        cacheDir.mkdirs();

        ExecutorService executor = Executors.newFixedThreadPool(3);
        AtomicInteger completed = new AtomicInteger();
        int total = 4;

        String linerSvg = Gdx.files.internal("liner.svg").readString();
        blockRanges = parseBlockRanges(linerSvg);

        CompletableFuture<Pixmap> hamsterFuture = CompletableFuture.supplyAsync(() -> {
            String hamsterSvg = Gdx.files.internal("hamster4.svg").readString();
            float finalStroke = Math.max(1.5f, Gdx.graphics.getWidth() / 400f);
            float strokeScale = finalStroke * (1024f / 80f);
            hamsterSvg = hamsterSvg.replaceAll("stroke-width=\\\"[0-9.]+\\\"",
                    "stroke-width=\\\"" + strokeScale + "\\\"");
            Pixmap hamsterPixmap = loadCachedSvg("hamster", hamsterSvg, 256, 256);
            applyBallpointEffect(hamsterPixmap);
            loadingProgress = completed.incrementAndGet() / (float) total;
            return hamsterPixmap;
        }, executor);
        hamsterFuture.thenAccept(pixmap -> Gdx.app.postRunnable(() -> {
            hamsterTexture = new Texture(pixmap);
            hamsterTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            pixmap.dispose();
        }));

        CompletableFuture<Pixmap> gradeFuture = CompletableFuture.supplyAsync(() -> {
            Pixmap gradePixmap = loadCachedSvg("grade", Gdx.files.internal("grade.svg").readString(), 64, 64);
            loadingProgress = completed.incrementAndGet() / (float) total;
            return gradePixmap;
        }, executor);
        gradeFuture.thenAccept(pixmap -> Gdx.app.postRunnable(() -> {
            gradeTexture = new Texture(pixmap);
            pixmap.dispose();
        }));

        CompletableFuture<Pixmap> blockFuture = CompletableFuture.supplyAsync(() -> {
            String blockSvg = Gdx.files.internal("block.svg").readString();
            float finalStroke = Math.max(1.5f, Gdx.graphics.getWidth() / 400f);
            float strokeScale = finalStroke * (1024f / 80f);
            blockSvg = blockSvg.replaceAll("stroke-width=\\\"[0-9.]+\\\"",
                    "stroke-width=\\\"" + strokeScale + "\\\"");
            Pixmap blockPixmap = loadCachedSvg("block", blockSvg, 256, 256);
            applyBallpointEffect(blockPixmap);
            blockPixmap = trimTransparent(blockPixmap);
            loadingProgress = completed.incrementAndGet() / (float) total;
            return blockPixmap;
        }, executor);
        blockFuture.thenAccept(pixmap -> Gdx.app.postRunnable(() -> {
            blockTexture = new Texture(pixmap);
            blockTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            pixmap.dispose();
        }));

        CompletableFuture<Pixmap> backgroundFuture = CompletableFuture.supplyAsync(() -> {
            Pixmap bgPixmap = loadCachedSvg("liner", linerSvg, 800, 600);
            loadingProgress = completed.incrementAndGet() / (float) total;
            return bgPixmap;
        }, executor);
        backgroundFuture.thenAccept(pixmap -> Gdx.app.postRunnable(() -> {
            backgroundTexture = new Texture(pixmap);
            backgroundPixmap = pixmap;
        }));
        CompletableFuture.allOf(hamsterFuture, gradeFuture, blockFuture, backgroundFuture).thenRun(() -> {
            Gdx.app.postRunnable(() -> {
                calculateCorridors();
                controlRenderer = new OnscreenControlRenderer();
                resetGame();
                loading = false;
            });
            executor.shutdown();
        });
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

    private float[][] parseBlockRanges(String svg) {
        java.util.ArrayList<Float> ys = new java.util.ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.StringReader(svg))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("stroke-width=\"2\"") && line.contains("H800")) {
                    int m = line.indexOf("M0 ");
                    int h = line.indexOf("H800", m);
                    if (m >= 0 && h >= 0) {
                        try {
                            ys.add(Float.parseFloat(line.substring(m + 3, h)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        java.util.Collections.sort(ys);
        java.util.ArrayList<float[]> pairs = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < ys.size();) {
            float y1 = ys.get(i);
            float y2 = ys.get(i + 1);
            if (y2 - y1 < 60f) {
                float topFromSvg = y1 + GUIDE_STROKE_WIDTH;
                float bottomFromSvg = y2 - GUIDE_STROKE_WIDTH;
                if (bottomFromSvg > topFromSvg) {
                    float top = 600f - bottomFromSvg;
                    float bottom = 600f - topFromSvg;
                    pairs.add(new float[]{top, bottom});
                }
                i += 2;
            } else {
                i++;
            }
        }
        return pairs.toArray(new float[0][]);
    }

    private Pixmap loadCachedSvg(String name, String svg, int width, int height) {
        String hash = md5(svg + width + "x" + height);
        FileHandle file = cacheDir.child(name + "-" + hash + ".png");
        if (file.exists()) {
            return new Pixmap(file);
        }
        Pixmap pixmap = Svg2Pixmap.svg2Pixmap(svg, width, height);
        PixmapIO.writePNG(file, pixmap);
        return pixmap;
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
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

        hamster = new Rectangle(400 - 32, 300 - 32, 64, 64);

        blocks = new Array<>();
        grid = new boolean[GRID_WIDTH][GRID_HEIGHT];

        // generate blocks that span between thin horizontal lines
        for (int i = 0; i < 10; i++) {
            int attempts = 0;
            boolean placed = false;
            while (attempts++ < 1000 && !placed) {
                int pairIndex = MathUtils.random(blockRanges.length - 1);
                float top = blockRanges[pairIndex][0];
                float bottom = blockRanges[pairIndex][1];
                float height = bottom - top;
                int gx = MathUtils.random(0, GRID_WIDTH - 1);
                float x = gx * CELL_SIZE;
                int startCellY = (int) (top / CELL_SIZE);
                int endCellY = (int) ((bottom - 1) / CELL_SIZE);
                boolean occupied = false;
                for (int cy = startCellY; cy <= endCellY; cy++) {
                    if (grid[gx][cy]) {
                        occupied = true;
                        break;
                    }
                }
                if (occupied) continue;
                Block block = new Block(new Rectangle(x, top, CELL_SIZE, height));
                if (hamster.overlaps(block.body)) continue;
                blocks.add(block);
                for (int cy = startCellY; cy <= endCellY; cy++) {
                    grid[gx][cy] = true;
                }
                placed = true;
            }
        }
        int hx = (int) (hamster.x / CELL_SIZE);
        int hy = (int) (hamster.y / CELL_SIZE);
        boolean placed = false;
        for (int attempt = 0; attempt < 1000 && !placed; attempt++) {
            int gx = MathUtils.random(0, GRID_WIDTH - 1);
            int corridorIndex = MathUtils.random(0, corridorCenters.length - 1);
            int centerY = corridorCenters[corridorIndex];
            int yTop = centerY - 32;
            if (yTop < 0 || yTop + 64 > 600) continue;
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
                System.out.println("GRADE_CENTER_Y=" + (grade.y + grade.height / 2));
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
                        // Increase base opacity so SVG strokes look brighter on screen
                        factor = 0.5f + 0.5f * factor;
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
            if (hamsterWin) {
                batch.draw(hamsterTexture, 350, 250, 120, 120);
            } else {
                batch.draw(gradeTexture, 350, 250, 100, 100);
            }
            batch.end();
            if (Gdx.input.isTouched() && Gdx.input.justTouched()) {
                resetGame();
            }
            return;
        }

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();
        batch.draw(backgroundTexture, 0, 0, 800, 600);
        batch.end();

        batch.begin();
        batch.draw(hamsterTexture, hamster.x, hamster.y, hamster.width, hamster.height);
        batch.draw(gradeTexture, grade.x, grade.y);
        for (Block block : blocks) {
            Rectangle body = block.body;
            batch.draw(blockTexture, body.x, body.y, body.width, body.height);
        }
        font.draw(batch, "Hamster: " + hamsterScore, 10, 590);
        font.draw(batch, "Grade: " + gradeScore, 10, 560);
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

        hamster.x = MathUtils.clamp(hamster.x, 0, 800 - hamster.width);
        hamster.y = MathUtils.clamp(hamster.y, 0, 600 - hamster.height);

        grade.x += gradeDirection.x * 100 * Gdx.graphics.getDeltaTime();
        grade.y += gradeDirection.y * 100 * Gdx.graphics.getDeltaTime();

        if (grade.x < 0 || grade.x > 800 - 64) gradeDirection.x = -gradeDirection.x;
        if (grade.y < 0 || grade.y > 600 - 64) gradeDirection.y = -gradeDirection.y;

        for (Block block : blocks) {
            Rectangle body = block.body;
            Rectangle intersection = new Rectangle();
            if (Intersector.intersectRectangles(hamster, body, intersection)) {
                if (intersection.width < intersection.height) {
                    if (hamster.x < body.x) {
                        hamster.x -= intersection.width;
                    } else {
                        hamster.x += intersection.width;
                    }
                } else {
                    if (hamster.y < body.y) {
                        hamster.y -= intersection.height;
                    } else {
                        hamster.y += intersection.height;
                    }
                }
            }

            if (Intersector.intersectRectangles(grade, body, intersection)) {
                if (intersection.width < intersection.height) {
                    if (grade.x < body.x) {
                        grade.x -= intersection.width;
                    } else {
                        grade.x += intersection.width;
                    }
                    gradeDirection.x = -gradeDirection.x;
                } else {
                    if (grade.y < body.y) {
                        grade.y -= intersection.height;
                    } else {
                        grade.y += intersection.height;
                    }
                    gradeDirection.y = -gradeDirection.y;
                }
            }
        }

        hamster.x = MathUtils.clamp(hamster.x, 0, 800 - hamster.width);
        hamster.y = MathUtils.clamp(hamster.y, 0, 600 - hamster.height);
        grade.x = MathUtils.clamp(grade.x, 0, 800 - grade.width);
        grade.y = MathUtils.clamp(grade.y, 0, 600 - grade.height);

        if (hamster.overlaps(grade)) {
            if (hamster.y >= grade.y + grade.height - 5) {
                blocks.clear();
                gameOver = true;
                hamsterWin = true;
                hamsterScore++;
            } else {
                gameOver = true;
                hamsterWin = false;
                gradeScore++;
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
        if (blockTexture != null) blockTexture.dispose();
        if (backgroundTexture != null) backgroundTexture.dispose();
        if (backgroundPixmap != null) backgroundPixmap.dispose();
        if (font != null) font.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (controlRenderer != null) controlRenderer.dispose();
    }
}
