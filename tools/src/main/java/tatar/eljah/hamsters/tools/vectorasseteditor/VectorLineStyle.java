package tatar.eljah.hamsters.tools.vectorasseteditor;

import java.awt.Color;

enum VectorLineStyle {
    PLAIN_SVG("Plain SVG", new Color(20, 20, 20), 2.0f),
    GEL_HOOLIGAN("Gel pen hamster", new Color(0, 0, 139), 2.0f),
    BALLPOINT_TEACHER_RED("Ballpoint teacher red", new Color(190, 0, 0), 2.0f),
    BALLPOINT_SCHOOL_BLUE("Ballpoint school blue", new Color(0, 0, 139), 2.0f);

    private final String displayName;
    private final Color previewColor;
    private final float previewStrokeWidth;

    VectorLineStyle(String displayName, Color previewColor, float previewStrokeWidth) {
        this.displayName = displayName;
        this.previewColor = previewColor;
        this.previewStrokeWidth = previewStrokeWidth;
    }

    Color getPreviewColor() {
        return previewColor;
    }

    float getPreviewStrokeWidth() {
        return previewStrokeWidth;
    }

    String getSvgStrokeColor() {
        return String.format("#%02x%02x%02x", previewColor.getRed(), previewColor.getGreen(), previewColor.getBlue());
    }

    @Override
    public String toString() {
        return displayName;
    }
}
