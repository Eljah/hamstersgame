package tatar.eljah.hamsters.tools.sceneeditor;

import tatar.eljah.hamsters.tools.svg.SvgHandle;
import tatar.eljah.hamsters.tools.svg.SvgLoader;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LinerGuides {
    private static final Pattern HORIZONTAL_LINE_PATTERN = Pattern.compile("M0\\s+([0-9.]+)H800");

    private final BufferedImage backgroundImage;
    private final List<BodyStripe> bodyStripes;
    private final String backgroundAssetName;

    private LinerGuides(BufferedImage backgroundImage, List<BodyStripe> bodyStripes, String backgroundAssetName) {
        this.backgroundImage = backgroundImage;
        this.bodyStripes = bodyStripes;
        this.backgroundAssetName = backgroundAssetName;
    }

    static LinerGuides load() throws IOException {
        BufferedImage background = loadBackgroundImage();
        List<BodyStripe> stripes = parseBodyStripes();
        return new LinerGuides(background, stripes, "liner.svg");
    }

    BufferedImage getBackgroundImage() {
        return backgroundImage;
    }

    List<BodyStripe> getBodyStripes() {
        return bodyStripes;
    }

    String getBackgroundAssetName() {
        return backgroundAssetName;
    }

    private static BufferedImage loadBackgroundImage() throws IOException {
        try (InputStream pngStream = LinerGuides.class.getResourceAsStream("/liner.png")) {
            if (pngStream != null) {
                BufferedImage image = ImageIO.read(pngStream);
                if (image != null) {
                    return image;
                }
            }
        }

        URL svgUrl = LinerGuides.class.getResource("/liner.svg");
        if (svgUrl == null) {
            throw new IOException("liner.svg resource is missing");
        }

        SvgHandle handle = SvgLoader.load(svgUrl.toString());
        int width = (int) Math.ceil(handle.getBounds().getWidth());
        int height = (int) Math.ceil(handle.getBounds().getHeight());
        if (width <= 0 || height <= 0) {
            throw new IOException("liner.svg has invalid dimensions");
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, height);
            g2d.translate(-handle.getBounds().getX(), -handle.getBounds().getY());
            handle.getGraphicsNode().paint(g2d);
        } finally {
            g2d.dispose();
        }
        return image;
    }

    private static List<BodyStripe> parseBodyStripes() throws IOException {
        List<Double> yPositions = new ArrayList<>();
        try (InputStream svgStream = LinerGuides.class.getResourceAsStream("/liner.svg")) {
            if (svgStream == null) {
                return Collections.emptyList();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(svgStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = HORIZONTAL_LINE_PATTERN.matcher(line);
                    if (matcher.find()) {
                        double y = Double.parseDouble(matcher.group(1));
                        yPositions.add(y);
                    }
                }
            }
        }
        Collections.sort(yPositions);
        List<BodyStripe> stripes = new ArrayList<>();
        List<Double> group = new ArrayList<>(4);
        for (double y : yPositions) {
            if (y < 100) {
                continue;
            }
            group.add(y);
            if (group.size() == 4) {
                stripes.add(new BodyStripe(group.get(0), group.get(1)));
                group.clear();
            }
        }
        return Collections.unmodifiableList(stripes);
    }

    static final class BodyStripe {
        private final double top;
        private final double bottom;

        BodyStripe(double top, double bottom) {
            this.top = top;
            this.bottom = bottom;
        }

        double getTop() {
            return top;
        }

        double getBottom() {
            return bottom;
        }
    }
}
