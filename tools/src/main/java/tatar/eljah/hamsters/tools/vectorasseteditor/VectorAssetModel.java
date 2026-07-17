package tatar.eljah.hamsters.tools.vectorasseteditor;

import tatar.eljah.hamsters.tools.blockeditor.BlockRegion;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class VectorAssetModel {
    private VectorAssetType type = VectorAssetType.STATIC_BLOCK;
    private int canvasWidth = 1024;
    private int canvasHeight = 768;
    private String rasterReferenceName;
    private final List<VectorFrame> frames = new ArrayList<>();
    private final List<BlockRegion> regions = new ArrayList<>();

    VectorAssetModel() {
        frames.add(new VectorFrame("frame-001"));
    }

    VectorAssetType getType() {
        return type;
    }

    void setType(VectorAssetType type) {
        this.type = type;
    }

    int getCanvasWidth() {
        return canvasWidth;
    }

    int getCanvasHeight() {
        return canvasHeight;
    }

    void setCanvasSize(int canvasWidth, int canvasHeight) {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
    }

    String getRasterReferenceName() {
        return rasterReferenceName;
    }

    void setRasterReferenceName(String rasterReferenceName) {
        this.rasterReferenceName = rasterReferenceName;
    }

    List<VectorFrame> getFrames() {
        return frames;
    }

    List<BlockRegion> getRegions() {
        return regions;
    }

    String buildSvg(VectorFrame frame) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\" shape-rendering=\"geometricPrecision\" data-asset-type=\"%s\">\n",
                canvasWidth, canvasHeight, canvasWidth, canvasHeight, type.name()));
        for (VectorCurve curve : frame.getCurvesView()) {
            sb.append("  ").append(curve.toSvgPath()).append('\n');
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    String buildMetadataJson(String svgFileName) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format(Locale.US, "  \"type\": \"%s\",\n", type.name()));
        sb.append(String.format(Locale.US, "  \"svg\": \"%s\",\n", svgFileName));
        if (rasterReferenceName != null) {
            sb.append(String.format(Locale.US, "  \"rasterReference\": \"%s\",\n", escapeJson(rasterReferenceName)));
        }
        sb.append(String.format(Locale.US, "  \"canvasWidth\": %d,\n", canvasWidth));
        sb.append(String.format(Locale.US, "  \"canvasHeight\": %d,\n", canvasHeight));
        sb.append(String.format(Locale.US, "  \"animated\": %s,\n", type.isAnimated() ? "true" : "false"));
        sb.append("  \"frames\": [\n");
        for (int i = 0; i < frames.size(); i++) {
            VectorFrame frame = frames.get(i);
            sb.append(String.format(Locale.US,
                    "    {\"name\": \"%s\", \"svg\": \"%s\"}",
                    escapeJson(frame.getName()),
                    escapeJson(frameSvgName(svgFileName, i))));
            if (i < frames.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ],\n");
        sb.append("  \"boundaries\": [");
        if (!regions.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < regions.size(); i++) {
                BlockRegion region = regions.get(i);
                Rectangle2D.Double rect = region.getRect();
                sb.append(String.format(Locale.US,
                        "    {\"type\": \"%s\", \"x\": %.5f, \"y\": %.5f, \"width\": %.5f, \"height\": %.5f}",
                        region.getType().name(), rect.x, rect.y, rect.width, rect.height));
                if (i < regions.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append("  ");
        }
        sb.append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    String frameSvgName(String baseSvgName, int frameIndex) {
        if (!type.isAnimated()) {
            return baseSvgName;
        }
        int dot = baseSvgName.lastIndexOf('.');
        String stem = dot > 0 ? baseSvgName.substring(0, dot) : baseSvgName;
        String ext = dot > 0 ? baseSvgName.substring(dot) : ".svg";
        return String.format(Locale.US, "%s-%03d%s", stem, frameIndex + 1, ext);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
