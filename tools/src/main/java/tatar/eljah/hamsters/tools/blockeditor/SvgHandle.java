package tatar.eljah.hamsters.tools.blockeditor;

import org.apache.batik.gvt.GraphicsNode;

import java.awt.geom.Rectangle2D;

final class SvgHandle {
    private final GraphicsNode graphicsNode;
    private final Rectangle2D bounds;

    SvgHandle(GraphicsNode graphicsNode, Rectangle2D bounds) {
        this.graphicsNode = graphicsNode;
        this.bounds = bounds;
    }

    GraphicsNode getGraphicsNode() {
        return graphicsNode;
    }

    Rectangle2D getBounds() {
        return bounds;
    }
}
