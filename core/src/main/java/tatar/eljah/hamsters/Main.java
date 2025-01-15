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
    private int hamsterScore;
    private int gradeScore;
    private OnscreenControlRenderer controlRenderer;

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

        resetGame();
    }

    private void resetGame() {
        gameOver = false;
        hamsterWin = false;

        Gdx.app.log("hamsters", "Game is reset");
        hamster = new Rectangle();
        hamster.x = 400 - 32;
        hamster.y = 300 - 32;
        hamster.width = 64;
        hamster.height = 64;

        grade = new Rectangle();
        grade.x = MathUtils.random(0, 800 - 64);
        grade.y = MathUtils.random(0, 600 - 64);
        grade.width = 64;
        grade.height = 64;

        blocks = new Array<>();
        Array<Float> usedXPositions = new Array<>();
        Array<Float> usedYPositions = new Array<>();

        for (int i = 0; i < 10; i++) {
            Rectangle block = new Rectangle();
            float blockX, blockY;

            // Place blocks on a grid for Y and check for overlap on X
            do {
                blockY = (116 * MathUtils.random(0, 4)) + 71 - block.height - 8; // Grid for Y-axis
                blockX = MathUtils.random(0, 800 - block.width); // Random X
            } while (usedXPositions.contains(blockX, false) && usedYPositions.contains(blockY, false));

            block.x = blockX;
            block.y = blockY;
            block.width = 64;
            block.height = 64;

            blocks.add(block);
            usedXPositions.add(blockX);
            usedYPositions.add(blockY);
        }

        gradeDirection = new Vector2(MathUtils.random(-1, 1), MathUtils.random(-1, 1)).nor();
    }

    @Override
    public void render() {
        if (gameOver) {
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
            if (hamster.overlaps(block)) {
                if (hamster.x < block.x) hamster.x = block.x - hamster.width;
                if (hamster.x > block.x) hamster.x = block.x + block.width;
                if (hamster.y < block.y) hamster.y = block.y - hamster.height;
                if (hamster.y > block.y) hamster.y = block.y + block.height;
            }

            if (grade.overlaps(block)) {
                if (grade.x < block.x) gradeDirection.x = -Math.abs(gradeDirection.x);
                if (grade.x > block.x) gradeDirection.x = Math.abs(gradeDirection.x);
                if (grade.y < block.y) gradeDirection.y = -Math.abs(gradeDirection.y);
                if (grade.y > block.y) gradeDirection.y = Math.abs(gradeDirection.y);
            }
        }

        if (hamster.overlaps(grade)) {
            if (hamster.y >= grade.y + grade.height - 5) { // Hamster attacks from above
                blocks.clear(); // Hamster wins by defeating the grade
                gameOver = true;
                hamsterWin = true;
                hamsterScore++;
            } else {
                gameOver = true; // Grade wins otherwise
                hamsterWin = false;
                gradeScore++;
            }
        }
        controlRenderer.render();
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
