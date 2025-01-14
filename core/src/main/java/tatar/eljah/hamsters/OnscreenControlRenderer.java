
package tatar.eljah.hamsters;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class OnscreenControlRenderer {
	SpriteBatch batch;
	TextureRegion dpad;
	TextureRegion left;
	TextureRegion right;
	TextureRegion up;
	TextureRegion down;
	TextureRegion cubeFollow;

	public OnscreenControlRenderer() {
		loadAssets();
	}

	private void loadAssets () {
		Texture texture = new Texture("controls.png");
		TextureRegion[] buttons = TextureRegion.split(texture, 64, 64)[0];
		left = buttons[0];
		right = buttons[1];
		up = buttons[2];
		down = buttons[3];
		cubeFollow = TextureRegion.split(texture, 64, 64)[1][2];
		dpad = new TextureRegion(texture, 0, 64, 128, 128);
		batch = new SpriteBatch();
		batch.getProjectionMatrix().setToOrtho2D(0, 0, 480, 320);
	}

	public void render () {
		if (Gdx.app.getType() != ApplicationType.Android && Gdx.app.getType() != ApplicationType.iOS) return;
		//if (map.cube.state != CONTROLLED) {
			batch.begin();
			batch.draw(left, 0, 0);
			batch.draw(right, 70, 0);
			batch.draw(up, 480 - 64, 320 - 64);
			batch.draw(down, 480 - 64, 320 - 138);
			batch.draw(cubeFollow, 480 - 64, 0);
			batch.end();
//		} else {
//			batch.begin();
//			batch.draw(dpad, 0, 0);
//			batch.draw(cubeFollow, 480 - 64, 320 - 138);
//			batch.draw(cubeControl, 480 - 64, 320 - 64);
//			batch.end();
//		}
	}

	public void dispose () {
		dpad.getTexture().dispose();
		batch.dispose();
	}
}
