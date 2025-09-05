package tools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** Utility to generate block.svg by tracing a temporary hand-drawn PNG of the Cyrillic letter "\u04e9". */
public class BlockSvgGenerator {
    public static void main(String[] args) throws Exception {
        int W = 64;
        int H = 32;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0,0,0,0));
        g.fillRect(0,0,W,H);
        g.setColor(new Color(0x00,0x00,0x8b)); // same pen color as hamster
        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Path2D.Float p = new Path2D.Float();
        p.moveTo(32, 8);
        p.curveTo(44, 8, 52, 14, 52, 16);
        p.curveTo(52, 20, 44, 24, 32, 24);
        p.curveTo(20, 24, 12, 20, 12, 16);
        p.curveTo(12, 14, 20, 8, 32, 8);
        g.draw(p);
        g.drawLine(18, 16, 46, 16);
        g.dispose();

        File tmp = File.createTempFile("block-letter", ".png");
        tmp.deleteOnExit();
        ImageIO.write(img, "PNG", tmp);

        PngToSvgTracer.main(new String[]{tmp.getAbsolutePath(), "assets/block.svg", "128", "0.25", "0.6", "4", "0.5", "1.0"});
        tmp.delete();
    }
}
