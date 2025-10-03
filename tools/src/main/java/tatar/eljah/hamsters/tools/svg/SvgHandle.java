package tatar.eljah.hamsters.tools.svg;

import org.apache.batik.gvt.GraphicsNode;

import java.awt.geom.Rectangle2D;

public final class SvgHandle {
    private final GraphicsNode graphicsNode;
    private final Rectangle2D bounds;

    public SvgHandle(GraphicsNode graphicsNode, Rectangle2D bounds) {
        this.graphicsNode = graphicsNode;
        this.bounds = bounds;
    }

    public GraphicsNode getGraphicsNode() {
        return graphicsNode;
    }

    public Rectangle2D getBounds() {
        return bounds;
    }
}
