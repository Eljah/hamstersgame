package tatar.eljah.hamsters.tools.blockeditor;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class BlockEditorPanel extends JPanel {
    private final BufferedImage backgroundImage;
    private final List<Double> guideLineCenters;
    private SvgHandle svgHandle;
    private Rectangle2D svgBounds;
    private double svgScale = 1.0;
    private double svgY = 0.0;

    private final List<BlockRegion> ascenders = new ArrayList<>();
    private final List<BlockRegion> descenders = new ArrayList<>();
    private BlockRegion bodyRegion;
    private BlockRegion selectedRegion;

    private BlockRegionType creationType;
    private Point creationStart;
    private Rectangle2D.Double creationRect;

    private boolean draggingSvg;
    private int dragStartY;
    private double initialSvgY;

    private boolean draggingRegion;
    private Point regionDragStart;
    private Rectangle2D.Double regionInitialRect;

    BlockEditorPanel() {
        backgroundImage = loadBackgroundImage();
        guideLineCenters = detectGuideLineCenters(backgroundImage);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
        setFocusable(true);
        MouseHandler mouseHandler = new MouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "deleteRegion");
        getActionMap().put("deleteRegion", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelectedRegion();
            }
        });
    }

    boolean hasSvgLoaded() {
        return svgHandle != null;
    }

    String moveSelectedRegionUpOneLine() {
        return moveSelectedRegionVertical(-1);
    }

    String moveSelectedRegionDownOneLine() {
        return moveSelectedRegionVertical(1);
    }

    double getSvgScale() {
        return svgScale;
    }

    void loadSvg(File file) throws IOException {
        svgHandle = SvgLoader.load(file);
        svgBounds = svgHandle.getBounds();
        svgScale = 1.0;
        svgY = (backgroundImage.getHeight() - getSvgDrawHeight()) / 2.0;
        creationType = null;
        creationRect = null;
        creationStart = null;
        selectedRegion = null;
        ascenders.clear();
        descenders.clear();
        bodyRegion = null;
        repaint();
    }

    private static BufferedImage loadBackgroundImage() {
        try {
            try (InputStream pngStream = BlockEditorPanel.class.getResourceAsStream("/liner.png")) {
                if (pngStream != null) {
                    return ImageIO.read(pngStream);
                }
            }

            URL svgUrl = BlockEditorPanel.class.getResource("/liner.svg");
            if (svgUrl != null) {
                try (InputStream svgStream = svgUrl.openStream()) {
                    SvgHandle handle = SvgLoader.load(svgStream, svgUrl.toString());
                    return rasterizeSvg(handle);
                }
            }

            throw new IOException("Missing liner background asset (liner.png or liner.svg)");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load background", e);
        }
    }

    private static List<Double> detectGuideLineCenters(BufferedImage image) {
        List<Double> centers = new ArrayList<>();
        if (image == null) {
            return centers;
        }
        int sampleX = image.getWidth() / 2;
        boolean inLine = false;
        int lineStart = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            int argb = image.getRGB(sampleX, y);
            int alpha = (argb >>> 24) & 0xFF;
            int rgb = argb & 0x00FFFFFF;
            boolean isLine = alpha > 0 && rgb != 0x00FFFFFF;
            if (isLine) {
                if (!inLine) {
                    inLine = true;
                    lineStart = y;
                }
            } else if (inLine) {
                int lineEnd = y - 1;
                centers.add((lineStart + lineEnd) / 2.0);
                inLine = false;
            }
        }
        if (inLine) {
            centers.add((lineStart + image.getHeight() - 1) / 2.0);
        }
        return centers;
    }

    private static BufferedImage rasterizeSvg(SvgHandle handle) throws IOException {
        Rectangle2D bounds = handle.getBounds();
        int width = (int) Math.ceil(bounds.getWidth());
        int height = (int) Math.ceil(bounds.getHeight());

        if (width <= 0 || height <= 0) {
            throw new IOException("SVG has invalid dimensions");
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.translate(-bounds.getX(), -bounds.getY());
        handle.getGraphicsNode().paint(graphics);
        graphics.dispose();
        return image;
    }

    void setSvgScale(double scale) {
        if (!hasSvgLoaded()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        double oldHeight = getSvgDrawHeight();
        double centerY = svgY + oldHeight / 2.0;
        svgScale = scale;
        double newHeight = getSvgDrawHeight();
        svgY = centerY - newHeight / 2.0;
        clampSvgY();
        repaint();
    }

    String centerSvgVertically() {
        if (!hasSvgLoaded()) {
            Toolkit.getDefaultToolkit().beep();
            return "Load an SVG first";
        }
        svgY = (backgroundImage.getHeight() - getSvgDrawHeight()) / 2.0;
        clampSvgY();
        repaint();
        return "SVG centered vertically";
    }

    String prepareRegionCreation(BlockRegionType type) {
        if (!hasSvgLoaded()) {
            Toolkit.getDefaultToolkit().beep();
            return "Load an SVG first";
        }
        if (type == BlockRegionType.BODY && bodyRegion != null) {
            Toolkit.getDefaultToolkit().beep();
            return "Body region already exists";
        }
        creationType = type;
        creationStart = null;
        creationRect = null;
        requestFocusInWindow();
        return String.format(Locale.US, "Draw %s region by dragging", type.name().toLowerCase(Locale.US));
    }

    String removeSelectedRegion() {
        if (selectedRegion == null) {
            Toolkit.getDefaultToolkit().beep();
            return "No region selected";
        }
        if (selectedRegion == bodyRegion) {
            bodyRegion = null;
        } else if (ascenders.remove(selectedRegion)) {
            // removed from ascenders
        } else {
            descenders.remove(selectedRegion);
        }
        selectedRegion = null;
        repaint();
        return "Region removed";
    }

    void clearSelection() {
        selectedRegion = null;
        repaint();
    }

    String buildConfigurationJson(String svgFileName) {
        if (!hasSvgLoaded()) {
            throw new IllegalStateException("SVG not loaded");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format(Locale.US, "  \"svg\": \"%s\",\n", svgFileName));
        sb.append(String.format(Locale.US, "  \"scale\": %.5f,\n", svgScale));
        sb.append(String.format(Locale.US, "  \"svgY\": %.5f,\n", svgY));
        sb.append(String.format(Locale.US, "  \"canvasWidth\": %d,\n", backgroundImage.getWidth()));
        sb.append(String.format(Locale.US, "  \"canvasHeight\": %d,\n", backgroundImage.getHeight()));
        sb.append(String.format(Locale.US, "  \"svgBounds\": {\n    \"x\": %.5f,\n    \"y\": %.5f,\n    \"width\": %.5f,\n    \"height\": %.5f\n  },\n",
                svgBounds.getX(), svgBounds.getY(), svgBounds.getWidth(), svgBounds.getHeight()));
        sb.append("  \"body\": ");
        if (bodyRegion != null) {
            appendRectangle(sb, bodyRegion.getRect());
        } else {
            sb.append("null");
        }
        sb.append(",\n");
        appendRegionArray(sb, "ascenders", ascenders);
        sb.append(",\n");
        appendRegionArray(sb, "descenders", descenders);
        sb.append('\n');
        sb.append("}\n");
        return sb.toString();
    }

    private void appendRegionArray(StringBuilder sb, String name, List<BlockRegion> regions) {
        sb.append("  \"").append(name).append("\": [");
        if (!regions.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < regions.size(); i++) {
                Rectangle2D.Double rect = regions.get(i).getRect();
                sb.append("    ");
                appendRectangle(sb, rect);
                if (i < regions.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append("  ");
        }
        sb.append("]");
    }

    private void appendRectangle(StringBuilder sb, Rectangle2D.Double rect) {
        sb.append(String.format(Locale.US,
                "{\"x\": %.5f, \"y\": %.5f, \"width\": %.5f, \"height\": %.5f}",
                rect.x, rect.y, rect.width, rect.height));
    }

    private String moveSelectedRegionVertical(int direction) {
        if (selectedRegion == null) {
            Toolkit.getDefaultToolkit().beep();
            return "No region selected";
        }
        if (guideLineCenters.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return "No guide lines detected";
        }
        Rectangle2D.Double rect = selectedRegion.getRect();
        double centerY = rect.y + rect.height / 2.0;
        int currentIndex = findClosestGuideLineIndex(centerY);
        if (currentIndex < 0) {
            Toolkit.getDefaultToolkit().beep();
            return "No nearby guide line";
        }
        int targetIndex = currentIndex + Integer.compare(direction, 0);
        if (targetIndex < 0 || targetIndex >= guideLineCenters.size()) {
            Toolkit.getDefaultToolkit().beep();
            return direction < 0 ? "Already at top line" : "Already at bottom line";
        }
        double targetCenter = guideLineCenters.get(targetIndex);
        double dy = targetCenter - centerY;
        rect.y += dy;
        clampRegion(rect);
        repaint();
        return direction < 0 ? "Moved block up" : "Moved block down";
    }

    private int findClosestGuideLineIndex(double centerY) {
        if (guideLineCenters.isEmpty()) {
            return -1;
        }
        int closestIndex = 0;
        double closestDistance = Math.abs(guideLineCenters.get(0) - centerY);
        for (int i = 1; i < guideLineCenters.size(); i++) {
            double distance = Math.abs(guideLineCenters.get(i) - centerY);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

    private void clampRegion(Rectangle2D.Double rect) {
        double maxX = backgroundImage.getWidth() - rect.width;
        double maxY = backgroundImage.getHeight() - rect.height;
        if (rect.x < 0) {
            rect.x = 0;
        }
        if (rect.y < 0) {
            rect.y = 0;
        }
        if (rect.x > maxX) {
            rect.x = maxX;
        }
        if (rect.y > maxY) {
            rect.y = maxY;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(backgroundImage, 0, 0, null);

        if (hasSvgLoaded()) {
            double svgWidth = getSvgDrawWidth();
            double svgHeight = getSvgDrawHeight();
            double svgX = (backgroundImage.getWidth() - svgWidth) / 2.0;

            Graphics2D svgGraphics = (Graphics2D) g2d.create();
            svgGraphics.translate(svgX, svgY);
            svgGraphics.scale(svgScale, svgScale);
            svgGraphics.translate(-svgBounds.getX(), -svgBounds.getY());
            svgHandle.getGraphicsNode().paint(svgGraphics);
            svgGraphics.dispose();

            Rectangle2D svgOutline = new Rectangle2D.Double(svgX, svgY, svgWidth, svgHeight);
            g2d.setColor(new Color(0, 0, 0, 80));
            g2d.setStroke(new BasicStroke(1.2f));
            g2d.draw(svgOutline);
        }

        if (bodyRegion != null) {
            bodyRegion.draw(g2d, selectedRegion == bodyRegion);
        }
        for (BlockRegion region : ascenders) {
            region.draw(g2d, selectedRegion == region);
        }
        for (BlockRegion region : descenders) {
            region.draw(g2d, selectedRegion == region);
        }

        if (creationRect != null && creationType != null) {
            Graphics2D tmp = (Graphics2D) g2d.create();
            tmp.setColor(creationType.getBorderColor());
            tmp.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6f, 6f}, 0f));
            tmp.draw(creationRect);
            tmp.dispose();
        }

        g2d.dispose();
    }

    private double getSvgDrawWidth() {
        if (svgBounds == null) {
            return 0.0;
        }
        return svgBounds.getWidth() * svgScale;
    }

    private double getSvgDrawHeight() {
        if (svgBounds == null) {
            return 0.0;
        }
        return svgBounds.getHeight() * svgScale;
    }

    private Rectangle2D getSvgDrawBounds() {
        double svgWidth = getSvgDrawWidth();
        double svgHeight = getSvgDrawHeight();
        double svgX = (backgroundImage.getWidth() - svgWidth) / 2.0;
        return new Rectangle2D.Double(svgX, svgY, svgWidth, svgHeight);
    }

    private BlockRegion findRegionAt(Point point) {
        if (bodyRegion != null && bodyRegion.getRect().contains(point)) {
            return bodyRegion;
        }
        for (int i = ascenders.size() - 1; i >= 0; i--) {
            BlockRegion region = ascenders.get(i);
            if (region.getRect().contains(point)) {
                return region;
            }
        }
        for (int i = descenders.size() - 1; i >= 0; i--) {
            BlockRegion region = descenders.get(i);
            if (region.getRect().contains(point)) {
                return region;
            }
        }
        return null;
    }

    private void clampSvgY() {
        double svgHeight = getSvgDrawHeight();
        double minY = -svgHeight;
        double maxY = backgroundImage.getHeight();
        if (svgY < minY) {
            svgY = minY;
        }
        if (svgY > maxY) {
            svgY = maxY;
        }
    }

    private void finalizeCreationRect() {
        if (creationRect == null || creationType == null) {
            return;
        }
        double width = Math.abs(creationRect.width);
        double height = Math.abs(creationRect.height);
        if (width < 2 || height < 2) {
            creationRect = null;
            creationStart = null;
            return;
        }
        Rectangle2D.Double normalized = new Rectangle2D.Double(
                Math.min(creationRect.x, creationRect.x + creationRect.width),
                Math.min(creationRect.y, creationRect.y + creationRect.height),
                width,
                height);
        BlockRegion region = new BlockRegion(creationType, normalized);
        if (creationType == BlockRegionType.BODY) {
            bodyRegion = region;
            creationType = null;
        } else if (creationType == BlockRegionType.ASCENDER) {
            ascenders.add(region);
        } else if (creationType == BlockRegionType.DESCENDER) {
            descenders.add(region);
        }
        selectedRegion = region;
        creationRect = null;
        creationStart = null;
        repaint();
    }

    private final class MouseHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            Point point = e.getPoint();
            if (creationType != null) {
                creationStart = point;
                creationRect = new Rectangle2D.Double(point.x, point.y, 0, 0);
                return;
            }

            BlockRegion region = findRegionAt(point);
            if (region != null) {
                selectedRegion = region;
                draggingRegion = true;
                regionDragStart = point;
                Rectangle2D.Double rect = region.getRect();
                regionInitialRect = new Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height);
                repaint();
                return;
            }

            if (hasSvgLoaded() && getSvgDrawBounds().contains(point)) {
                draggingSvg = true;
                dragStartY = point.y;
                initialSvgY = svgY;
            } else {
                selectedRegion = null;
                repaint();
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            Point point = e.getPoint();
            if (creationType != null && creationRect != null && creationStart != null) {
                creationRect.width = point.x - creationStart.x;
                creationRect.height = point.y - creationStart.y;
                repaint();
                return;
            }
            if (draggingRegion && selectedRegion != null && regionInitialRect != null && regionDragStart != null) {
                double dx = point.x - regionDragStart.x;
                double dy = point.y - regionDragStart.y;
                Rectangle2D.Double rect = selectedRegion.getRect();
                rect.x = regionInitialRect.x + dx;
                rect.y = regionInitialRect.y + dy;
                repaint();
                return;
            }
            if (draggingSvg) {
                double dy = point.y - dragStartY;
                svgY = initialSvgY + dy;
                clampSvgY();
                repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            if (creationType != null && creationRect != null) {
                finalizeCreationRect();
            }
            draggingSvg = false;
            draggingRegion = false;
            regionInitialRect = null;
            regionDragStart = null;
        }
    }
}
