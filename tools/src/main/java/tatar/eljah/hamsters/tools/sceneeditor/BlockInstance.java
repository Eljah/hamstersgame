package tatar.eljah.hamsters.tools.sceneeditor;

import java.awt.geom.Rectangle2D;

final class BlockInstance {
    private final String blockReference;
    private BlockDefinition definition;
    private double x;
    private double y;

    BlockInstance(BlockDefinition definition, double x, double y) {
        this.blockReference = definition.getJsonFileName();
        this.definition = definition;
        this.x = x;
        this.y = y;
    }

    String getBlockReference() {
        return blockReference;
    }

    BlockDefinition getDefinition() {
        return definition;
    }

    void setDefinition(BlockDefinition definition) {
        this.definition = definition;
    }

    double getX() {
        return x;
    }

    double getY() {
        return y;
    }

    void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    void setY(double y) {
        this.y = y;
    }

    Rectangle2D.Double getBoundingBox() {
        if (definition == null) {
            return new Rectangle2D.Double(x, y, 0, 0);
        }
        Rectangle2D.Double base = definition.getContentBounds();
        return new Rectangle2D.Double(base.x + x, base.y + y, base.width, base.height);
    }

    Rectangle2D.Double getBodyBounds() {
        if (definition == null) {
            return null;
        }
        Rectangle2D.Double body = definition.getBodyRect();
        if (body == null) {
            return null;
        }
        return new Rectangle2D.Double(body.x + x, body.y + y, body.width, body.height);
    }
}
