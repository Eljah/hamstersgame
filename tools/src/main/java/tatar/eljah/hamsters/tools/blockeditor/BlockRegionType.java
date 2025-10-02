package tatar.eljah.hamsters.tools.blockeditor;

import java.awt.Color;

public enum BlockRegionType {
    BODY(new Color(204, 0, 0, 120), new Color(204, 0, 0)),
    ASCENDER(new Color(0, 128, 0, 120), new Color(0, 128, 0)),
    DESCENDER(new Color(0, 0, 204, 120), new Color(0, 0, 204));

    private final Color fillColor;
    private final Color borderColor;

    BlockRegionType(Color fillColor, Color borderColor) {
        this.fillColor = fillColor;
        this.borderColor = borderColor;
    }

    public Color getFillColor() {
        return fillColor;
    }

    public Color getBorderColor() {
        return borderColor;
    }
}
