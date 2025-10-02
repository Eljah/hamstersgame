package tatar.eljah.hamsters.tools.blockeditor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

public class BlockRegion {
    private final BlockRegionType type;
    private final Rectangle2D.Double rect;

    public BlockRegion(BlockRegionType type, Rectangle2D.Double rect) {
        this.type = type;
        this.rect = rect;
    }

    public BlockRegionType getType() {
        return type;
    }

    public Rectangle2D.Double getRect() {
        return rect;
    }

    public void translate(double dx, double dy) {
        rect.x += dx;
        rect.y += dy;
    }

    public void draw(Graphics2D g2d, boolean selected) {
        Color fill = type.getFillColor();
        Color border = type.getBorderColor();
        g2d.setColor(fill);
        g2d.fill(rect);
        g2d.setColor(border);
        g2d.setStroke(new BasicStroke(selected ? 3f : 1.5f));
        g2d.draw(rect);
    }

    public BlockRegion copy() {
        return new BlockRegion(type, new Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height));
    }
}
