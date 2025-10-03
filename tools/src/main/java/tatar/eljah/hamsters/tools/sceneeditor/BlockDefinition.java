package tatar.eljah.hamsters.tools.sceneeditor;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import tatar.eljah.hamsters.tools.svg.SvgHandle;
import tatar.eljah.hamsters.tools.svg.SvgLoader;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BlockDefinition {
    private final String jsonFileName;
    private final String displayName;
    private final SvgHandle svgHandle;
    private final Rectangle2D svgBounds;
    private final double svgScale;
    private final double svgY;
    private final int canvasWidth;
    private final int canvasHeight;
    private final Rectangle2D.Double bodyRect;
    private final List<Rectangle2D.Double> ascenders;
    private final List<Rectangle2D.Double> descenders;
    private final Rectangle2D.Double contentBounds;
    private final double svgDrawX;

    private BlockDefinition(String jsonFileName,
                            String displayName,
                            SvgHandle svgHandle,
                            Rectangle2D svgBounds,
                            double svgScale,
                            double svgY,
                            int canvasWidth,
                            int canvasHeight,
                            Rectangle2D.Double bodyRect,
                            List<Rectangle2D.Double> ascenders,
                            List<Rectangle2D.Double> descenders,
                            Rectangle2D.Double contentBounds,
                            double svgDrawX) {
        this.jsonFileName = jsonFileName;
        this.displayName = displayName;
        this.svgHandle = svgHandle;
        this.svgBounds = svgBounds;
        this.svgScale = svgScale;
        this.svgY = svgY;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.bodyRect = bodyRect;
        this.ascenders = ascenders;
        this.descenders = descenders;
        this.contentBounds = contentBounds;
        this.svgDrawX = svgDrawX;
    }

    static BlockDefinition load(File jsonFile) throws IOException {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(new FileHandle(jsonFile));
        String svgFileName = root.getString("svg");
        File svgFile = new File(jsonFile.getParentFile(), svgFileName);
        if (!svgFile.isFile()) {
            throw new IOException("SVG file not found for block: " + svgFile.getAbsolutePath());
        }
        SvgHandle handle = SvgLoader.load(svgFile);
        Rectangle2D svgBounds = handle.getBounds();
        double svgScale = root.getDouble("scale", 1.0);
        double svgY = root.getDouble("svgY", 0.0);
        int canvasWidth = root.getInt("canvasWidth", 800);
        int canvasHeight = root.getInt("canvasHeight", 600);
        JsonValue boundsValue = root.get("svgBounds");
        if (boundsValue != null) {
            double x = boundsValue.getDouble("x", svgBounds.getX());
            double y = boundsValue.getDouble("y", svgBounds.getY());
            double width = boundsValue.getDouble("width", svgBounds.getWidth());
            double height = boundsValue.getDouble("height", svgBounds.getHeight());
            svgBounds = new Rectangle2D.Double(x, y, width, height);
        }

        Rectangle2D.Double bodyRect = null;
        JsonValue bodyValue = root.get("body");
        if (bodyValue != null && !bodyValue.isNull()) {
            bodyRect = readRectangle(bodyValue);
        }

        List<Rectangle2D.Double> ascenders = readRectangles(root.get("ascenders"));
        List<Rectangle2D.Double> descenders = readRectangles(root.get("descenders"));

        Rectangle2D.Double contentBounds = computeContentBounds(bodyRect, ascenders, descenders);
        double svgDrawWidth = svgBounds.getWidth() * svgScale;
        double svgDrawX = (canvasWidth - svgDrawWidth) / 2.0;
        if (contentBounds == null) {
            contentBounds = new Rectangle2D.Double(svgDrawX, svgY, svgDrawWidth, svgBounds.getHeight() * svgScale);
        }

        String jsonName = jsonFile.getName();
        String displayName = stripExtension(jsonName);
        return new BlockDefinition(
                jsonName,
                displayName,
                handle,
                svgBounds,
                svgScale,
                svgY,
                canvasWidth,
                canvasHeight,
                bodyRect,
                Collections.unmodifiableList(ascenders),
                Collections.unmodifiableList(descenders),
                contentBounds,
                svgDrawX
        );
    }

    private static Rectangle2D.Double readRectangle(JsonValue value) {
        double x = value.getDouble("x", 0.0);
        double y = value.getDouble("y", 0.0);
        double width = value.getDouble("width", 0.0);
        double height = value.getDouble("height", 0.0);
        return new Rectangle2D.Double(x, y, width, height);
    }

    private static List<Rectangle2D.Double> readRectangles(JsonValue array) {
        if (array == null || array.isNull()) {
            return Collections.emptyList();
        }
        List<Rectangle2D.Double> list = new ArrayList<>();
        for (JsonValue child : array) {
            list.add(readRectangle(child));
        }
        return list;
    }

    private static Rectangle2D.Double computeContentBounds(Rectangle2D.Double body,
                                                            List<Rectangle2D.Double> ascenders,
                                                            List<Rectangle2D.Double> descenders) {
        Rectangle2D.Double bounds = null;
        if (body != null) {
            bounds = new Rectangle2D.Double(body.x, body.y, body.width, body.height);
        }
        bounds = extend(bounds, ascenders);
        bounds = extend(bounds, descenders);
        return bounds;
    }

    private static Rectangle2D.Double extend(Rectangle2D.Double base, List<Rectangle2D.Double> additions) {
        Rectangle2D.Double result = base;
        for (Rectangle2D.Double rect : additions) {
            if (result == null) {
                result = new Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height);
            } else {
                double minX = Math.min(result.x, rect.x);
                double minY = Math.min(result.y, rect.y);
                double maxX = Math.max(result.x + result.width, rect.x + rect.width);
                double maxY = Math.max(result.y + result.height, rect.y + rect.height);
                result = new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
            }
        }
        return result;
    }

    private static String stripExtension(String name) {
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            return name.substring(0, dotIndex);
        }
        return name;
    }

    String getJsonFileName() {
        return jsonFileName;
    }

    String getDisplayName() {
        return displayName;
    }

    SvgHandle getSvgHandle() {
        return svgHandle;
    }

    Rectangle2D getSvgBounds() {
        return svgBounds;
    }

    double getSvgScale() {
        return svgScale;
    }

    double getSvgY() {
        return svgY;
    }

    int getCanvasWidth() {
        return canvasWidth;
    }

    int getCanvasHeight() {
        return canvasHeight;
    }

    Rectangle2D.Double getBodyRect() {
        return bodyRect;
    }

    List<Rectangle2D.Double> getAscenders() {
        return ascenders;
    }

    List<Rectangle2D.Double> getDescenders() {
        return descenders;
    }

    Rectangle2D.Double getContentBounds() {
        return contentBounds;
    }

    double getSvgDrawX() {
        return svgDrawX;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
