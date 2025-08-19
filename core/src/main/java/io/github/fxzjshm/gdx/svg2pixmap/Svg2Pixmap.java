package io.github.fxzjshm.gdx.svg2pixmap;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.async.ThreadUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Svg2Pixmap {
    /**
     * Generate shapes on a (width * generateScale) x (height * generateScale) Pixmap, then resize to the original size.
     * This seems to be called super-sampling.
     * TODO use "signed distance field" method
     * This affects {@link Svg2Pixmap#svg2Pixmap} but not {@link Svg2Pixmap#path2Pixmap}.
     */
    public static int generateScale = 2;

    public static Color defaultColor = Color.BLACK;

    private static class Transform {
        float sx = 1f, sy = 1f, tx = 0f, ty = 0f;
    }

    private static Transform parseTransform(String transform) {
        Transform t = new Transform();
        if (transform == null) return t;
        Matcher m = Pattern.compile("(\\w+)\\(([^)]+)\\)").matcher(transform);
        List<String> types = new ArrayList<>();
        List<String[]> params = new ArrayList<>();
        while (m.find()) {
            types.add(m.group(1));
            params.add(m.group(2).trim().split("[,\\s]+"));
        }
        for (int i = types.size() - 1; i >= 0; i--) {
            String type = types.get(i);
            String[] p = params.get(i);
            if ("scale".equals(type)) {
                float sx = Float.parseFloat(p[0]);
                float sy = p.length > 1 ? Float.parseFloat(p[1]) : sx;
                t.sx *= sx;
                t.sy *= sy;
                t.tx *= sx;
                t.ty *= sy;
            } else if ("translate".equals(type)) {
                float tx = Float.parseFloat(p[0]);
                float ty = p.length > 1 ? Float.parseFloat(p[1]) : 0f;
                t.tx += tx;
                t.ty += ty;
            }
        }
        return t;
    }

    private static void drawElement(XmlReader.Element element, Pixmap pixmap, float sx, float sy, float tx, float ty) {
        Transform t = parseTransform(element.getAttribute("transform", null));
        float nsx = sx * t.sx;
        float nsy = sy * t.sy;
        float ntx = sx * t.tx + tx;
        float nty = sy * t.ty + ty;
        for (int i = 0; i < element.getChildCount(); i++) {
            XmlReader.Element child = element.getChild(i);
            String name = child.getName();
            switch (name) {
                case "path":
                    path(child, pixmap, nsx, nsy, ntx, nty);
                    break;
                case "circle":
                    circle(child, pixmap, nsx, nsy, ntx, nty);
                    break;
                case "ellipse":
                    ellipse(child, pixmap, nsx, nsy, ntx, nty);
                    break;
                case "rect":
                    rect(child, pixmap, nsx, nsy, ntx, nty);
                    break;
                case "g":
                    drawElement(child, pixmap, nsx, nsy, ntx, nty);
                    break;
                default:
                    Gdx.app.error("svg2PixmapDirectDraw", "Unsupported element " + name);
            }
        }
    }

    /**
     * Convert a SVG {@code <path />} element into a {@link Pixmap}.
     * Will scale if (width != {@link Pixmap#getWidth()} || height != {@link Pixmap#getHeight()}).
     *
     * @param width       the origin width of the SVG file, maybe defined by viewbox
     * @param height      the origin height of the SVG file, maybe defined by viewbox
     * @param d           the property d of the origin path element
     * @param fill        the color to fill in the shape.
     * @param stroke      the color of th path
     * @param strokeWidth width to draw. WARNING: WILL BE SCALED if (width != {@link Pixmap#getWidth()} || height != {@link Pixmap#getHeight()}).
     * @param pixmap      the pixmap to draw the path in.
     * @return Drawn pixmap.
     */
    public static Pixmap path2Pixmap(int width, int height, String d, Color fill, Color stroke, double strokeWidth, Pixmap pixmap, float sx, float sy, float tx, float ty) {
        checkGWT();

        StringTokenizer stringTokenizer = new StringTokenizer(H.splitMixedTokens(d));
        int strokeRadius = (int) Math.round(strokeWidth * Math.sqrt(1.0 * (pixmap.getWidth() * pixmap.getHeight()) / (width * height)) / 2);

        Vector2 currentPosition = new Vector2(0, 0);// Current position in pixmap.
        Vector2 initialPoint = new Vector2(0, 0);// Used by command 'M'.
        Vector2 currentOrig = new Vector2(0, 0); // Current position in original coordinates.
        Vector2 initialOrig = new Vector2(0, 0);
        Vector2 lastCPoint = null; // Last control point of last 'C' or 'S' command.
        Vector2 lastQPoint = null; // Last control point of last 'Q' or 'T' command.
        boolean[][] border = new boolean[pixmap.getWidth()][pixmap.getHeight()];

        char lastCommand = 0; // Last command.
        LinkedList<String> params = new LinkedList<String>(); // Real parameters.
        while (stringTokenizer.hasMoreTokens()) {
            String tmp; // Next token. Maybe a command or an argument.
            char command; //  The real command.
            int paramAmount = 0; // The amount of parameters to read.

            try {
                tmp = stringTokenizer.nextToken();
            } catch (NoSuchElementException nsee) { // No more tokens.
                break;
            }

            if (1 == tmp.length() && Character.isLetter(tmp.charAt(0))) {
                lastCommand = command = tmp.charAt(0);
            } else {
                if (lastCommand != 0) {
                    command = lastCommand;
                    params.add(tmp);
                    paramAmount--;
                } else throw new IllegalArgumentException("No command at the beginning ?");
            }
            paramAmount += H.getParamAmount(command);
            for (int i = 0; i < paramAmount; i++) {
                params.add(stringTokenizer.nextToken());
            }

            // convert relative positions to absolute positions using original coordinates
            H.r2a(command, params, new Vector2(currentOrig.x, currentOrig.y));

            char newCommand = Character.toUpperCase(command);

            float lastOrigX = currentOrig.x, lastOrigY = currentOrig.y;
            switch (newCommand) {
                case 'M':
                case 'L':
                case 'T':
                    lastOrigX = Float.parseFloat(params.get(params.size() - 2));
                    lastOrigY = Float.parseFloat(params.get(params.size() - 1));
                    break;
                case 'H':
                    lastOrigX = Float.parseFloat(params.get(params.size() - 1));
                    break;
                case 'V':
                    lastOrigY = Float.parseFloat(params.get(params.size() - 1));
                    break;
                case 'C':
                    lastOrigX = Float.parseFloat(params.get(4));
                    lastOrigY = Float.parseFloat(params.get(5));
                    break;
                case 'S':
                case 'Q':
                    lastOrigX = Float.parseFloat(params.get(2));
                    lastOrigY = Float.parseFloat(params.get(3));
                    break;
                case 'A':
                    lastOrigX = Float.parseFloat(params.get(5));
                    lastOrigY = Float.parseFloat(params.get(6));
                    break;
                case 'Z':
                    lastOrigX = initialOrig.x;
                    lastOrigY = initialOrig.y;
                    break;
            }

            applyTransform(newCommand, params, sx, sy, tx, ty);

            if (newCommand == 'M') {
                initialPoint.x = currentPosition.x = Float.parseFloat(params.get(0)) / width * pixmap.getWidth();
                initialPoint.y = currentPosition.y = Float.parseFloat(params.get(1)) / height * pixmap.getHeight();
                initialOrig.set(lastOrigX, lastOrigY);
                currentOrig.set(lastOrigX, lastOrigY);
            } else if (newCommand == 'Z') {
                H.drawCurve(pixmap, new Vector2[]{currentPosition, initialPoint}, stroke, strokeRadius, border);
                currentPosition.x = initialPoint.x;
                currentPosition.y = initialPoint.y;
                currentOrig.set(lastOrigX, lastOrigY);
            } else if (newCommand == 'L') {
                float x2 = Float.parseFloat(params.get(0)) / width * pixmap.getWidth();
                float y2 = Float.parseFloat(params.get(1)) / height * pixmap.getHeight();
                H.drawCurve(pixmap, new Vector2[]{currentPosition, new Vector2(x2, y2)}, stroke, strokeRadius, border);
                currentPosition.set(x2, y2);
                currentOrig.set(lastOrigX, lastOrigY);
            } else if (newCommand == 'H') {
                float x2 = Float.parseFloat(params.get(0)) / width * pixmap.getWidth();
                H.drawCurve(pixmap, new Vector2[]{currentPosition, new Vector2(x2, currentPosition.y)}, stroke, strokeRadius, border);
                currentPosition.x = x2;
                currentOrig.set(lastOrigX, lastOrigY);
            } else if (newCommand == 'V') {
                float y2 = Float.parseFloat(params.get(0)) / height * pixmap.getHeight();
                H.drawCurve(pixmap, new Vector2[]{currentPosition, new Vector2(currentPosition.x, y2)}, stroke, strokeRadius, border);
                currentPosition.y = y2;
                currentOrig.set(lastOrigX, lastOrigY);
            } else if (newCommand == 'C') {
                float x1 = Float.parseFloat(params.get(0)) / width * pixmap.getWidth();
                float y1 = Float.parseFloat(params.get(1)) / height * pixmap.getHeight();
                float x2 = Float.parseFloat(params.get(2)) / width * pixmap.getWidth();
                float y2 = Float.parseFloat(params.get(3)) / height * pixmap.getHeight();
                float x = Float.parseFloat(params.get(4)) / width * pixmap.getWidth();
                float y = Float.parseFloat(params.get(5)) / height * pixmap.getHeight();
                lastCPoint = new Vector2(x2, y2);
                H.drawCurve(pixmap, new Vector2[]{currentPosition, new Vector2(x1, y1), lastCPoint, new Vector2(x, y)}, stroke, strokeRadius, border);
                currentPosition.set(x, y);
                currentOrig.set(lastOrigX, lastOrigY);
            } else if (newCommand == 'S') {
                float x2 = Float.parseFloat(params.get(0)) / width * pixmap.getWidth();
                float y2 = Float.parseFloat(params.get(1)) / height * pixmap.getHeight();
                float x = Float.parseFloat(params.get(2)) / width * pixmap.getWidth();
                float y = Float.parseFloat(params.get(3)) / height * pixmap.getHeight();
                float x1, y1;
                if (lastCPoint != null) {
                    x1 = 2 * currentPosition.x - lastCPoint.x;
                    y1 = 2 * currentPosition.y - lastCPoint.y;
                } else {
                    x1 = x2;
                    y1 = y2;
                }
                lastCPoint = new Vector2(x2, y2);
                H.drawCurve(pixmap, new Vector2[]{currentPosition, new Vector2(x1, y1), lastCPoint, new Vector2(x, y)}, stroke, strokeRadius, border);
                currentPosition.set(x, y);
                currentOrig.set(lastOrigX, lastOrigY);
            } else if (newCommand == 'Q') {
                float x1 = Float.parseFloat(params.get(0)) / width * pixmap.getWidth();
                float y1 = Float.parseFloat(params.get(1)) / height * pixmap.getHeight();
                float x = Float.parseFloat(params.get(2)) / width * pixmap.getWidth();
                float y = Float.parseFloat(params.get(3)) / height * pixmap.getHeight();
                lastQPoint = new Vector2(x1, y1);
                H.drawCurve(pixmap, new Vector2[]{currentPosition, lastQPoint, new Vector2(x, y)}, stroke, strokeRadius, border);
                currentPosition.set(x, y);
                currentOrig.set(lastOrigX, lastOrigY);
            } else if (newCommand == 'T') {
                float x = Float.parseFloat(params.get(0)) / width * pixmap.getWidth();
                float y = Float.parseFloat(params.get(1)) / height * pixmap.getHeight();
                float x1, y1;
                if (lastQPoint != null) {
                    x1 = 2 * currentPosition.x - lastQPoint.x;
                    y1 = 2 * currentPosition.y - lastQPoint.y;
                } else {
                    x1 = x;
                    y1 = y;
                }
                lastQPoint = new Vector2(x1, y1);
                H.drawCurve(pixmap, new Vector2[]{currentPosition, lastQPoint, new Vector2(x, y)}, stroke, strokeRadius, border);
                currentPosition.set(x, y);
                currentOrig.set(lastOrigX, lastOrigY);
            } else if (newCommand == 'A') {
                float rx = Float.parseFloat(params.get(0)) / width * pixmap.getWidth();
                float ry = Float.parseFloat(params.get(1)) / height * pixmap.getHeight();
                float x_axis_rotation = Float.parseFloat(params.get(2));
                float x = Float.parseFloat(params.get(5)) / width * pixmap.getWidth();
                float y = Float.parseFloat(params.get(6)) / height * pixmap.getHeight();
                int large_arc_flag = Math.abs(Integer.parseInt(params.get(3)));
                int sweep_flag = Math.abs(Integer.parseInt(params.get(4)));
                List<Vector2[]> curves = SvgArcToCubicBezier.arcToBezier(currentPosition.x, currentPosition.y, x, y, rx, ry, x_axis_rotation, large_arc_flag, sweep_flag);
                for (Vector2[] curve : curves) {
                    ArrayList<Vector2> points = new ArrayList<>(4);
                    points.add(currentPosition);
                    points.addAll(Arrays.asList(curve));
                    H.drawCurve(pixmap, points.toArray(new Vector2[4]), stroke, strokeRadius, border);
                    currentPosition.x = curve[2].x;
                    currentPosition.y = curve[2].y;
                }
                currentPosition.x = x;
                currentPosition.y = y;
                currentOrig.set(lastOrigX, lastOrigY);
            }

            // Clear useless control points
            if (newCommand != 'Q' && newCommand != 'T') lastQPoint = null;
            if (newCommand != 'C' && newCommand != 'S') lastCPoint = null;

            params.clear();
        }

        if (fill != null && !fill.equals(Color.CLEAR)) {
            H.fillColor(pixmap, border, fill);
        }

        return pixmap;
    }

    public static Pixmap path2Pixmap(int width, int height, String d, Color fill, Color stroke, double strokeWidth, Pixmap pixmap) {
        return path2Pixmap(width, height, d, fill, stroke, strokeWidth, pixmap, 1f, 1f, 0f, 0f);
    }

    private static void applyTransform(char command, List<String> params, float sx, float sy, float tx, float ty) {
        switch (command) {
            case 'M':
            case 'L':
            case 'T':
                for (int i = 0; i < params.size(); i += 2) {
                    float x = Float.parseFloat(params.get(i));
                    float y = Float.parseFloat(params.get(i + 1));
                    params.set(i, Float.toString(sx * x + tx));
                    params.set(i + 1, Float.toString(sy * y + ty));
                }
                break;
            case 'H':
                for (int i = 0; i < params.size(); i++) {
                    float x = Float.parseFloat(params.get(i));
                    params.set(i, Float.toString(sx * x + tx));
                }
                break;
            case 'V':
                for (int i = 0; i < params.size(); i++) {
                    float y = Float.parseFloat(params.get(i));
                    params.set(i, Float.toString(sy * y + ty));
                }
                break;
            case 'C':
            case 'S':
            case 'Q':
                for (int i = 0; i < params.size(); i += 2) {
                    float x = Float.parseFloat(params.get(i));
                    float y = Float.parseFloat(params.get(i + 1));
                    params.set(i, Float.toString(sx * x + tx));
                    params.set(i + 1, Float.toString(sy * y + ty));
                }
                break;
            case 'A':
                float rx = Float.parseFloat(params.get(0));
                float ry = Float.parseFloat(params.get(1));
                params.set(0, Float.toString(Math.abs(sx) * rx));
                params.set(1, Float.toString(Math.abs(sy) * ry));
                float x = Float.parseFloat(params.get(5));
                float y = Float.parseFloat(params.get(6));
                params.set(5, Float.toString(sx * x + tx));
                params.set(6, Float.toString(sy * y + ty));
                break;
            default:
        }
    }

    /**
     * Parse a SVG file to a Pixmap.
     *
     * @param fileContent SVG file
     * @param width       The width to Pixmap.
     * @param height      The height of Pixmap.
     */
    public static Pixmap svg2Pixmap(String fileContent, int width, int height) {
        checkGWT();

        if (generateScale == 1) return svg2PixmapDirectDraw(fileContent, width, height);

        final int scaledWidth = width * generateScale, scaledHeight = height * generateScale,
                scale2 = generateScale * generateScale;
        final Pixmap scaledPixmap = svg2PixmapDirectDraw(fileContent, scaledWidth, scaledHeight),
                pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        AtomicInteger count = new AtomicInteger(0);
        for (int x = 0; x < width; x++) {
            final int x0 = x;
            H.asyncExecutor.submit(() -> {
                Color tmpColor = new Color();
                float r, g, b, a;
                for (int y = 0; y < height; y++) {
                    final int y0 = y;
                    r = g = b = a = 0;
                    for (int i = 0; i < generateScale; i++) {
                        for (int j = 0; j < generateScale; j++) {
                            int color = scaledPixmap.getPixel(x0 * generateScale + i, y0 * generateScale + j);
                            Color.rgba8888ToColor(tmpColor, color);
                            r += tmpColor.r;
                            g += tmpColor.g;
                            b += tmpColor.b;
                            a += tmpColor.a;
                        }
                    }
                    r /= scale2;
                    g /= scale2;
                    b /= scale2;
                    a /= scale2;
                    pixmap.drawPixel(x0, y0, Color.rgba8888(r, g, b, a));
                }
                count.incrementAndGet();
                return null;
            });
        }
        while (count.get() < width) {
            ThreadUtils.yield();
        }
        scaledPixmap.dispose();
        return pixmap;
    }

    /**
     * Convert SVG file to Pixmap using browser apis in GWT mode.
     * Obviously, do not call this on other backends.
     *
     * @param callback due to image loading limitations, results cannot be provided instantly,
     *                 a callback function/method is required to return the result.
     * @see ICallback
     */
    // @off
    // @formatter:off
    public static native void svg2PixmapJSNI(String fileContent, int width, int height, ICallback callback)/*-{
        var img = new Image();
        img.src = 'data:image/svg+xml; charset=utf8, ' + encodeURIComponent(fileContent);
        img.width = width;
        img.height = height;
        img.onload = function(){
            var pixmap = @com.badlogic.gdx.graphics.Pixmap::new(Lcom/google/gwt/dom/client/ImageElement;)(img);
            callback.@io.github.fxzjshm.gdx.svg2pixmap.Svg2Pixmap.ICallback::onload(Lcom/badlogic/gdx/graphics/Pixmap;)(pixmap);
        }
    }-*/;
    // @on
    // @formatter:on

    public static Pixmap svg2PixmapDirectDraw(String fileContent, int width, int height) {
        XmlReader reader = new XmlReader();
        XmlReader.Element root = reader.parse(fileContent);

        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            drawElement(root, pixmap, 1f, 1f, 0f, 0f);
        } catch (Exception e) { //TODO Dangerous here !!!
            Gdx.app.debug("Svg2Pixmap", "File content:\n" + fileContent + "\nError stacktrace: ", e);
        }
        return pixmap;
    }

    public static Pixmap svg2Pixmap(String fileContent) {
        XmlReader reader = new XmlReader();
        XmlReader.Element root = reader.parse(fileContent);
        int width = H.svgReadInt(root.getAttribute("width"));
        int height = H.svgReadInt(root.getAttribute("height"));
        return svg2Pixmap(fileContent, width, height);
    }

    public static void path(XmlReader.Element element, Pixmap pixmap, float sx, float sy, float tx, float ty) {
        H.SVGBasicInfo info = new H.SVGBasicInfo(element);
        String d = H.getAttribute(element, "d");

        path2Pixmap(info.width, info.height, d, info.fill, info.stroke, info.strokeWidth, pixmap, sx, sy, tx, ty);
    }

    public static void path(XmlReader.Element element, Pixmap pixmap) {
        path(element, pixmap, 1f, 1f, 0f, 0f);
    }

    public static void rect(XmlReader.Element element, Pixmap pixmap, float sx, float sy, float tx, float ty) {
        H.SVGBasicInfo info = new H.SVGBasicInfo(element);
        double x = Double.parseDouble(element.getAttribute("x", "0")),
                y = Double.parseDouble(element.getAttribute("y", "0")),
                w = Double.parseDouble(H.getAttribute(element, "width")),
                h = Double.parseDouble(H.getAttribute(element, "height")),
                rx = Double.parseDouble(element.getAttribute("rx", "0")),
                ry = Double.parseDouble(element.getAttribute("ry", "0"));
        if (rx == 0 && ry > 0) rx = ry;
        if (ry == 0 && rx > 0) ry = rx;
        rx = Math.min(rx, w / 2.0);
        ry = Math.min(ry, h / 2.0);
        String d;
        if (rx == 0 && ry == 0) {
            d = "M " + x + " " + y + " " +
                "L " + (x + w) + " " + y + " " +
                "L " + (x + w) + " " + (y + h) + " " +
                "L " + x + " " + (y + h) + " Z";
        } else {
            d = "M " + (x + rx) + " " + y + " " +
                "L " + (x + w - rx) + " " + y + " " +
                "A " + rx + " " + ry + " 0 0 1 " + (x + w) + " " + (y + ry) + " " +
                "L " + (x + w) + " " + (y + h - ry) + " " +
                "A " + rx + " " + ry + " 0 0 1 " + (x + w - rx) + " " + (y + h) + " " +
                "L " + (x + rx) + " " + (y + h) + " " +
                "A " + rx + " " + ry + " 0 0 1 " + x + " " + (y + h - ry) + " " +
                "L " + x + " " + (y + ry) + " " +
                "A " + rx + " " + ry + " 0 0 1 " + (x + rx) + " " + y + " Z";
        }
        path2Pixmap(info.width, info.height, d, info.fill, info.stroke, info.strokeWidth, pixmap, sx, sy, tx, ty);
    }

    public static void rect(XmlReader.Element element, Pixmap pixmap) {
        rect(element, pixmap, 1f, 1f, 0f, 0f);
    }

    public static void circle(XmlReader.Element element, Pixmap pixmap, float sx, float sy, float tx, float ty) {
        H.SVGBasicInfo info = new H.SVGBasicInfo(element);
        double cx = Double.parseDouble(H.getAttribute(element, "cx")),
                cy = Double.parseDouble(H.getAttribute(element, "cy")),
                r = Double.parseDouble(H.getAttribute(element, "r"));

        String d = "M " + (cx - r) + " " + cy + " " +
                "A " + r + " " + r + " 0 1 1 " + (cx + r) + " " + cy + " " +
                "A " + r + " " + r + " 0 1 1 " + (cx - r) + " " + cy + " ";
        path2Pixmap(info.width, info.height, d, info.fill, info.stroke, info.strokeWidth, pixmap, sx, sy, tx, ty);
    }

    public static void circle(XmlReader.Element element, Pixmap pixmap) {
        circle(element, pixmap, 1f, 1f, 0f, 0f);
    }

    public static void ellipse(XmlReader.Element element, Pixmap pixmap, float sx, float sy, float tx, float ty) {
        H.SVGBasicInfo info = new H.SVGBasicInfo(element);
        double cx = Double.parseDouble(H.getAttribute(element, "cx")),
                cy = Double.parseDouble(H.getAttribute(element, "cy")),
                rx = Double.parseDouble(H.getAttribute(element, "rx")),
                ry = Double.parseDouble(H.getAttribute(element, "ry"));

        String d = "M " + (cx - rx) + " " + cy + " " +
                "A " + rx + " " + ry + " 0 1 1 " + (cx + rx) + " " + cy + " " +
                "A " + rx + " " + ry + " 0 1 1 " + (cx - rx) + " " + cy + " ";
        path2Pixmap(info.width, info.height, d, info.fill, info.stroke, info.strokeWidth, pixmap, sx, sy, tx, ty);
    }

    public static void ellipse(XmlReader.Element element, Pixmap pixmap) {
        ellipse(element, pixmap, 1f, 1f, 0f, 0f);
    }

    protected static void checkGWT() {
        if (Gdx.app.getType().equals(Application.ApplicationType.WebGL)) {
            Gdx.app.error("Svg2Pixmap", "Due to performance issue, in GWT mode please use functions with suffix -JSNI instead.");
        }
    }

    /**
     * @see Svg2Pixmap#svg2PixmapJSNI(String, int, int, ICallback)
     */
    public interface ICallback {
        void onload(Pixmap pixmap);
    }

}