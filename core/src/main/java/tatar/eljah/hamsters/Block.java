package tatar.eljah.hamsters;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * Represents an obstacle on the lined paper. A block may have three kinds of
 * solid regions: the body (between the lines), optional ascenders (above the
 * line) and optional descenders (below the line). All regions are solid for
 * collision purposes and characters may only touch them.
 */
public class Block {
    /** Solid part between the lines. */
    public final Rectangle body;
    /** Optional solid areas above the line. */
    public final Array<Rectangle> ascenders;
    /** Optional solid areas below the line. */
    public final Array<Rectangle> descenders;

    public Block(Rectangle body) {
        this(body, null, null);
    }

    public Block(Rectangle body, Array<Rectangle> ascenders, Array<Rectangle> descenders) {
        this.body = body;
        this.ascenders = ascenders != null ? ascenders : new Array<>();
        this.descenders = descenders != null ? descenders : new Array<>();
    }
}

