package tatar.eljah.hamsters.tools.vectorasseteditor;

import java.awt.geom.CubicCurve2D;
import java.awt.geom.Point2D;
import java.util.Locale;

final class VectorCurve {
    private final Point2D.Double start;
    private final Point2D.Double control1;
    private final Point2D.Double control2;
    private final Point2D.Double end;
    private final VectorLineStyle lineStyle;

    VectorCurve(Point2D.Double start,
                Point2D.Double control1,
                Point2D.Double control2,
                Point2D.Double end,
                VectorLineStyle lineStyle) {
        this.start = copyPoint(start);
        this.control1 = copyPoint(control1);
        this.control2 = copyPoint(control2);
        this.end = copyPoint(end);
        this.lineStyle = lineStyle;
    }

    Point2D.Double getStart() {
        return start;
    }

    Point2D.Double getControl1() {
        return control1;
    }

    Point2D.Double getControl2() {
        return control2;
    }

    Point2D.Double getEnd() {
        return end;
    }

    VectorLineStyle getLineStyle() {
        return lineStyle;
    }

    CubicCurve2D.Double toCubicCurve() {
        return new CubicCurve2D.Double(
                start.x, start.y,
                control1.x, control1.y,
                control2.x, control2.y,
                end.x, end.y);
    }

    void translate(double dx, double dy) {
        start.x += dx;
        start.y += dy;
        control1.x += dx;
        control1.y += dy;
        control2.x += dx;
        control2.y += dy;
        end.x += dx;
        end.y += dy;
    }

    VectorCurve copy() {
        return new VectorCurve(start, control1, control2, end, lineStyle);
    }

    String toSvgPath() {
        return String.format(Locale.US,
                "<path d=\"M %.3f %.3f C %.3f %.3f %.3f %.3f %.3f %.3f\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.3f\" stroke-linecap=\"round\" stroke-linejoin=\"round\" data-line-style=\"%s\"/>",
                start.x, start.y,
                control1.x, control1.y,
                control2.x, control2.y,
                end.x, end.y,
                lineStyle.getSvgStrokeColor(),
                lineStyle.getPreviewStrokeWidth(),
                lineStyle.name());
    }

    private static Point2D.Double copyPoint(Point2D.Double point) {
        return new Point2D.Double(point.x, point.y);
    }
}
