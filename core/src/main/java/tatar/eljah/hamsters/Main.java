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
import io.github.fxzjshm.gdx.svg2pixmap.Svg2Pixmap;

public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private Texture hamsterTexture;
    private Texture gradeTexture;
    private Texture blockTexture;
    private Texture backgroundTexture;
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;

    private OrthographicCamera camera;

    private Rectangle hamster;
    private Rectangle grade;
    private Array<Rectangle> blocks;

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

        new Thread(() -> {
            String hamsterSvg = Gdx.files.internal("hamster4.svg").readString();
            float finalStroke = Math.max(1.5f, Gdx.graphics.getWidth() / 400f);
            float strokeScale = finalStroke * (1024f / 80f);
            hamsterSvg = hamsterSvg.replaceAll("stroke-width=\"[0-9.]+\"",
                    "stroke-width=\"" + strokeScale + "\"");
            Pixmap hamsterPixmap = Svg2Pixmap.svg2Pixmap(hamsterSvg, 256, 256);
            loadingProgress = 1f / 3f;
            Gdx.app.postRunnable(() -> {
                hamsterTexture = new Texture(hamsterPixmap);
                hamsterTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                hamsterPixmap.dispose();
            });

            Pixmap gradePixmap = Svg2Pixmap.svg2Pixmap(
                    Gdx.files.internal("grade.svg").readString(), 64, 64);
            loadingProgress = 2f / 3f;
            Gdx.app.postRunnable(() -> {
                gradeTexture = new Texture(gradePixmap);
                gradePixmap.dispose();
            });

            Pixmap blockPixmap = Svg2Pixmap.svg2Pixmap(
                    Gdx.files.internal("block.svg").readString(), 64, 64);
            loadingProgress = 1f;
            Gdx.app.postRunnable(() -> {
                blockTexture = new Texture(blockPixmap);
                blockPixmap.dispose();
                backgroundTexture = new Texture("liner.png");
                controlRenderer = new OnscreenControlRenderer();
                resetGame();
                loading = false;
            });
        }).start();
    }

    static final int GRID_WIDTH = 800 / 64;
    static final int GRID_HEIGHT = 600 / 64;

    Rectangle getHamster() { return hamster; }
    Rectangle getGrade() { return grade; }
    boolean[][] getGrid() { return grid; }

    void resetGame() {
        gameOver = false;
        hamsterWin = false;

        hamster = new Rectangle(400 - 32, 300 - 32, 64, 64);

        blocks = new Array<>();
        grid = new boolean[GRID_WIDTH][GRID_HEIGHT];

        // generate random blocks
        for (int i = 0; i < 10; i++) {
            int gx;
            int gy;
            do {
                gx = MathUtils.random(0, GRID_WIDTH - 1);
                gy = MathUtils.random(0, GRID_HEIGHT - 1);
            } while (grid[gx][gy] || (gx == (int)(hamster.x / 64) && gy == (int)(hamster.y / 64)));

            Rectangle block = new Rectangle(gx * 64f, gy * 64f, 64, 64);
            blocks.add(block);
            grid[gx][gy] = true;
        }

        int hx = (int) (hamster.x / 64);
        int hy = (int) (hamster.y / 64);
        boolean placed = false;
        for (int attempt = 0; attempt < 1000 && !placed; attempt++) {
            int gx = MathUtils.random(0, GRID_WIDTH - 1);
            int gy = MathUtils.random(0, GRID_HEIGHT - 2); // ensure space above
            if (grid[gx][gy] || grid[gx][gy + 1]) continue;
            if (gx == hx && gy == hy) continue;

            grid[gx][gy] = true;
            boolean canReachAbove = isReachable(hx, hy, gx, gy + 1);
            grid[gx][gy] = false;

            if (canReachAbove && isReachable(hx, hy, gx, gy)) {
                grade = new Rectangle(gx * 64f, gy * 64f, 64, 64);
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
        batch.draw(hamsterTexture, hamster.x, hamster.y, 80, 80);
        batch.draw(gradeTexture, grade.x, grade.y);
        for (Rectangle block : blocks) {
            batch.draw(blockTexture, block.x, block.y);
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

        for (Rectangle block : blocks) {
            Rectangle intersection = new Rectangle();
            if (Intersector.intersectRectangles(hamster, block, intersection)) {
                if (intersection.width < intersection.height) {
                    if (hamster.x < block.x) {
                        hamster.x -= intersection.width;
                    } else {
                        hamster.x += intersection.width;
                    }
                } else {
                    if (hamster.y < block.y) {
                        hamster.y -= intersection.height;
                    } else {
                        hamster.y += intersection.height;
                    }
                }
            }

            if (Intersector.intersectRectangles(grade, block, intersection)) {
                if (intersection.width < intersection.height) {
                    if (grade.x < block.x) {
                        grade.x -= intersection.width;
                    } else {
                        grade.x += intersection.width;
                    }
                    gradeDirection.x = -gradeDirection.x;
                } else {
                    if (grade.y < block.y) {
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
        if (font != null) font.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (controlRenderer != null) controlRenderer.dispose();
    }
}
