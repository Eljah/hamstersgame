package tatar.eljah.hamsters;

import com.badlogic.gdx.math.Rectangle;

/**
 * Represents an obstacle on the line paper. A block may have three parts:
 * ascender (above the line), body (between lines) and descender (below the line).
 * Only the body is solid for collision purposes. Ascender and descender regions
 * are passable for the hamster.
 */
public class Block {
    /** Solid part between the lines. */
    public final Rectangle body;
    /** Optional area above the top line. Hamster can pass through. */
    public final Rectangle ascender;
    /** Optional area below the bottom line. Hamster can pass through. */
    public final Rectangle descender;

    public Block(Rectangle body) {
        this(body, null, null);
    }

    public Block(Rectangle body, Rectangle ascender, Rectangle descender) {
        this.body = body;
        this.ascender = ascender;
        this.descender = descender;
    }
}

