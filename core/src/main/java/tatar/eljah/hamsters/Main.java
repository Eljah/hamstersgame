package tatar.eljah.hamsters;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.utils.Array;

public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private Texture hamsterTexture;
    private Texture gradeTexture;
    private Texture blockTexture;
    private Texture backgroundTexture;
    private BitmapFont font;

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
    private float gameOverElapsed;
    private static final float GAME_OVER_INPUT_DELAY = 0.15f;
    // Fallback delay that moves the game to the next scene even if the player
    // doesn't provide any input (useful for desktop builds without touch).
    private static final float GAME_OVER_AUTO_RESET_DELAY = 1.5f;
    private static final float AUTO_WIN_DELAY = 0.75f;
    private static final String TAG = "HamstersGame";

    private float autoWinTimer;
    private boolean autoWinTriggered;
    private String currentScene;
    private static final String SCENE_GAMEPLAY = "Scene 1 (Gameplay)";
    private static final String SCENE_GAME_OVER = "Scene 2 (Game Over)";

    @Override
    public void create() {
        batch = new SpriteBatch();
        hamsterTexture = new Texture("hamster.png");
        gradeTexture = new Texture("grade.png");
        blockTexture = new Texture("block.png");
        backgroundTexture = new Texture("liner.png");
        font = new BitmapFont();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, 800, 600);

        controlRenderer = new OnscreenControlRenderer();

        hamsterScore = 0;
        gradeScore = 0;

        resetGameWithReason("initial startup");
    }

    static final int GRID_WIDTH = 800 / 64;
    static final int GRID_HEIGHT = 600 / 64;

    Rectangle getHamster() { return hamster; }
    Rectangle getGrade() { return grade; }
    boolean[][] getGrid() { return grid; }

    void resetGame() {
        resetGameWithReason(currentScene == null ? "initial startup" : "restart");
    }

    void resetGameWithReason(String reason) {
        gameOver = false;
        hamsterWin = false;
        gameOverElapsed = 0f;
        autoWinTimer = 0f;
        autoWinTriggered = false;

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
            resetGameWithReason("grade spawn retry");
            return;
        }

        do {
            gradeDirection = new Vector2(MathUtils.random(-1f, 1f), MathUtils.random(-1f, 1f));
        } while (gradeDirection.isZero());
        gradeDirection.nor();

        logSceneTransition(currentScene, SCENE_GAMEPLAY, reason);
        currentScene = SCENE_GAMEPLAY;
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
        if (gameOver) {
            gameOverElapsed += Gdx.graphics.getDeltaTime();
            Gdx.gl.glClearColor(1, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();
            font.draw(batch, "Hamster: " + hamsterScore, 10, 590);
            font.draw(batch, "Grade: " + gradeScore, 10, 560);
            if (hamsterWin) {
                batch.draw(hamsterTexture, 350, 250, 100, 100);
            } else {
                batch.draw(gradeTexture, 350, 250, 100, 100);
            }
            batch.end();
            boolean allowRestart = gameOverElapsed >= GAME_OVER_INPUT_DELAY;
            if ((allowRestart && shouldRestartGame()) || gameOverElapsed >= GAME_OVER_AUTO_RESET_DELAY) {
                resetGameWithReason("post-game-over restart");
                return;
            }
            return;
        }

        if (!autoWinTriggered) {
            autoWinTimer += Gdx.graphics.getDeltaTime();
            if (autoWinTimer >= AUTO_WIN_DELAY) {
                triggerGameOver(true, "auto-win");
            }
        }

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();
        batch.draw(backgroundTexture, 0, 0, 800, 600); // Draw background
        batch.draw(hamsterTexture, hamster.x, hamster.y);
        batch.draw(gradeTexture, grade.x, grade.y);
        for (Rectangle block : blocks) {
            batch.draw(blockTexture, block.x, block.y);
        }
        font.draw(batch, "Hamster: " + hamsterScore, 10, 590);
        font.draw(batch, "Grade: " + gradeScore, 10, 560);
        batch.end();

        // Hamster movement
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {

            float x0 = (Gdx.input.getX(0) / (float)Gdx.graphics.getWidth()) * 480;
            float x1 = (Gdx.input.getX(1) / (float)Gdx.graphics.getWidth()) * 480;
            float y0 = 320 - (Gdx.input.getY(0) / (float)Gdx.graphics.getHeight()) * 320;

            boolean leftButton = (Gdx.input.isTouched(0) && x0 < 70) || (Gdx.input.isTouched(1) && x1 < 70);
            boolean rightButton = (Gdx.input.isTouched(0) && x0 > 70 && x0 < 134) || (Gdx.input.isTouched(1) && x1 > 70 && x1 < 134);
            boolean downButton = (Gdx.input.isTouched(0) && x0 > 416 && x0 < 480 && y0 > 320 - 128 && y0 < 320 - 64)
                || (Gdx.input.isTouched(1) && x1 > 416 && x1 < 480 && y0 > 320 - 128 && y0 < 320 -64);
            boolean upButton = (Gdx.input.isTouched(0) && x0 > 416 && x0 < 480 && y0 > 320 - 64)
                || (Gdx.input.isTouched(1) && x1 > 416 && x1 < 480 && y0 > 320 - 64);


            if (upButton){
                hamster.y += 200 * Gdx.graphics.getDeltaTime(); // Y increases upwards
            }
            if (leftButton ){
                hamster.x -= 200 * Gdx.graphics.getDeltaTime();
            }
            if (downButton){
                hamster.y -= 200 * Gdx.graphics.getDeltaTime(); // Y decreases downwards
            }
            if (rightButton){
                hamster.x += 200 * Gdx.graphics.getDeltaTime();
            }

        } else {
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) hamster.x -= 200 * Gdx.graphics.getDeltaTime();
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) hamster.x += 200 * Gdx.graphics.getDeltaTime();
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) hamster.y += 200 * Gdx.graphics.getDeltaTime(); // Y increases upwards
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) hamster.y -= 200 * Gdx.graphics.getDeltaTime(); // Y decreases downwards
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

        if (!gameOver && hamster.overlaps(grade)) {
            triggerGameOver(true, "collision");
        }
        controlRenderer.render();
    }

    private void triggerGameOver(boolean hamsterWon, String reason) {
        if (gameOver) {
            return;
        }
        gameOver = true;
        hamsterWin = hamsterWon;
        gameOverElapsed = 0f;
        if (hamsterWon) {
            hamsterScore++;
            blocks.clear();
        } else {
            gradeScore++;
        }
        autoWinTriggered = true;
        logSceneTransition(currentScene, SCENE_GAME_OVER, (hamsterWon ? "hamster victory" : "grade victory") + " via " + reason);
        currentScene = SCENE_GAME_OVER;
    }

    private void logSceneStart(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(TAG, message + " on " + Gdx.app.getType());
        } else {
            System.out.println(TAG + ": " + message);
        }
    }

    private void logSceneTransition(String fromScene, String toScene, String reason) {
        StringBuilder builder = new StringBuilder();
        if (fromScene == null) {
            builder.append("Entering ").append(toScene);
        } else if (fromScene.equals(toScene)) {
            builder.append("Staying on ").append(toScene);
        } else {
            builder.append("Transition ").append(fromScene).append(" -> ").append(toScene);
        }
        if (reason != null && !reason.isEmpty()) {
            builder.append(" (reason: ").append(reason).append(")");
        }
        logSceneStart(builder.toString());
    }

    private boolean shouldRestartGame() {
        if (Gdx.input.justTouched()) {
            return true;
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            return true;
        }
        return false;
    }

    @Override
    public void dispose() {
        batch.dispose();
        hamsterTexture.dispose();
        gradeTexture.dispose();
        blockTexture.dispose();
        backgroundTexture.dispose();
        font.dispose();
        controlRenderer.dispose();
    }
}
