package tatar.eljah.hamsters.tools.sceneeditor;

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
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class SceneEditorPanel extends JPanel {
    private static final Color SELECTION_COLOR = new Color(0, 120, 215, 140);
    private static final Color BODY_FILL = new Color(204, 0, 0, 80);
    private static final Color BODY_BORDER = new Color(204, 0, 0);
    private static final Color ASC_FILL = new Color(0, 128, 0, 60);
    private static final Color ASC_BORDER = new Color(0, 128, 0);
    private static final Color DESC_FILL = new Color(0, 0, 204, 60);
    private static final Color DESC_BORDER = new Color(0, 0, 204);

    private static final double SNAP_DISTANCE = 12.0;
    private static final double STRIPE_SWITCH_MARGIN = 16.0;
    private static final double POSITION_EPSILON = 1e-6;

    private final BufferedImage backgroundImage;
    private final List<LinerGuides.BodyStripe> bodyStripes;
    private final List<BlockInstance> instances = new ArrayList<>();

    private BlockInstance selectedInstance;
    private Point dragStart;
    private double initialX;
    private double initialY;
    private Consumer<BlockInstance> selectionListener;
    private List<StripePlacement> currentStripePlacements = Collections.emptyList();
    private int activeStripeIndex = -1;

    SceneEditorPanel(LinerGuides guides) {
        this.backgroundImage = guides.getBackgroundImage();
        this.bodyStripes = guides.getBodyStripes();
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
        setFocusable(true);

        MouseHandler handler = new MouseHandler();
        addMouseListener(handler);
        addMouseMotionListener(handler);

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "deleteBlock");
        getActionMap().put("deleteBlock", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelectedInstance();
            }
        });
    }

    void setSelectionListener(Consumer<BlockInstance> listener) {
        this.selectionListener = listener;
    }

    BlockInstance addBlock(BlockDefinition definition) {
        double x = computeDefaultX(definition);
        double y = computeDefaultY(definition);
        return addBlock(definition, x, y, true);
    }

    BlockInstance addBlock(BlockDefinition definition, double x, double y, boolean select) {
        double clampedY = clampY(definition, y);
        BlockInstance instance = new BlockInstance(definition, x, clampedY);
        instances.add(instance);
        if (select) {
            selectedInstance = instance;
            updateStripeContext(selectedInstance);
            notifySelectionChanged();
        }
        repaint();
        return instance;
    }

    boolean removeSelectedInstance() {
        if (selectedInstance == null) {
            return false;
        }
        boolean removed = instances.remove(selectedInstance);
        if (removed) {
            selectedInstance = null;
            updateStripeContext(null);
            repaint();
            notifySelectionChanged();
        }
        return removed;
    }

    void clearScene() {
        instances.clear();
        selectedInstance = null;
        updateStripeContext(null);
        repaint();
        notifySelectionChanged();
    }

    List<BlockInstance> getInstances() {
        return Collections.unmodifiableList(instances);
    }

    BlockInstance getSelectedInstance() {
        return selectedInstance;
    }

    boolean moveSelectedInstanceUp() {
        return moveSelectedInstance(-1);
    }

    boolean moveSelectedInstanceDown() {
        return moveSelectedInstance(1);
    }

    void remapDefinitions(Map<String, BlockDefinition> definitions) {
        boolean changed = false;
        for (BlockInstance instance : instances) {
            BlockDefinition updated = definitions.get(instance.getBlockReference());
            if (updated != null && updated != instance.getDefinition()) {
                instance.setDefinition(updated);
                instance.setY(clampY(updated, instance.getY()));
                changed = true;
            }
        }
        if (changed) {
            if (selectedInstance != null) {
                updateStripeContext(selectedInstance);
            }
            repaint();
        }
    }

    int getCanvasWidth() {
        return backgroundImage.getWidth();
    }

    int getCanvasHeight() {
        return backgroundImage.getHeight();
    }

    double clampY(BlockDefinition definition, double desiredY) {
        if (definition == null) {
            return desiredY;
        }
        List<StripePlacement> placements = computeStripePlacements(definition);
        if (placements.isEmpty()) {
            return desiredY;
        }

        double bestRangeY = desiredY;
        double bestRangeDistance = Double.POSITIVE_INFINITY;
        Double fallbackSnapY = null;
        double fallbackDistance = Double.POSITIVE_INFINITY;

        for (StripePlacement placement : placements) {
            double clamped = placement.clamp(desiredY);
            if (desiredY >= placement.minY - POSITION_EPSILON && desiredY <= placement.maxY + POSITION_EPSILON) {
                return clamped;
            }

            double distance = Math.abs(desiredY - clamped);
            if (placement.hasRange()) {
                if (distance < bestRangeDistance) {
                    bestRangeDistance = distance;
                    bestRangeY = clamped;
                }
            } else {
                if (distance < fallbackDistance) {
                    fallbackDistance = distance;
                    fallbackSnapY = placement.snapY;
                }
            }
        }

        if (bestRangeDistance < Double.POSITIVE_INFINITY) {
            return bestRangeY;
        }
        return fallbackSnapY != null ? fallbackSnapY : desiredY;
    }

    private boolean moveSelectedInstance(int deltaStripe) {
        if (selectedInstance == null) {
            return false;
        }
        BlockDefinition definition = selectedInstance.getDefinition();
        if (definition == null) {
            return false;
        }
        if (currentStripePlacements.isEmpty()) {
            updateStripeContext(selectedInstance);
        }
        if (currentStripePlacements.isEmpty()) {
            return false;
        }
        int currentIndex = activeStripeIndex;
        if (currentIndex < 0) {
            currentIndex = findStripeIndex(currentStripePlacements, selectedInstance.getY());
        }
        int targetIndex = currentIndex + deltaStripe;
        if (targetIndex < 0 || targetIndex >= currentStripePlacements.size()) {
            return false;
        }
        StripePlacement placement = currentStripePlacements.get(targetIndex);
        selectedInstance.setY(placement.snapY);
        activeStripeIndex = targetIndex;
        repaint();
        notifySelectionChanged();
        return true;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(backgroundImage, 0, 0, null);

        for (BlockInstance instance : instances) {
            BlockDefinition definition = instance.getDefinition();
            if (definition == null) {
                continue;
            }
            Graphics2D blockGraphics = (Graphics2D) g2d.create();
            blockGraphics.translate(instance.getX(), instance.getY());
            drawBlock(blockGraphics, definition);
            blockGraphics.dispose();

            if (instance == selectedInstance) {
                drawSelectionOverlays(g2d, instance, definition);
            }
        }

        g2d.dispose();
    }

    private void drawBlock(Graphics2D g2d, BlockDefinition definition) {
        Graphics2D svgGraphics = (Graphics2D) g2d.create();
        svgGraphics.translate(definition.getSvgDrawX(), definition.getSvgY());
        svgGraphics.scale(definition.getSvgScale(), definition.getSvgScale());
        Rectangle2D svgBounds = definition.getSvgBounds();
        svgGraphics.translate(-svgBounds.getX(), -svgBounds.getY());
        definition.getSvgHandle().getGraphicsNode().paint(svgGraphics);
        svgGraphics.dispose();
    }

    private void drawSelectionOverlays(Graphics2D g2d, BlockInstance instance, BlockDefinition definition) {
        Rectangle2D.Double bounds = instance.getBoundingBox();
        Graphics2D outline = (Graphics2D) g2d.create();
        outline.setColor(SELECTION_COLOR);
        outline.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6f, 6f}, 0f));
        outline.draw(bounds);
        outline.dispose();

        Graphics2D overlay = (Graphics2D) g2d.create();
        overlay.setStroke(new BasicStroke(1.4f));
        Rectangle2D.Double body = definition.getBodyRect();
        if (body != null) {
            Rectangle2D.Double bodyRect = new Rectangle2D.Double(body.x + instance.getX(), body.y + instance.getY(), body.width, body.height);
            overlay.setColor(BODY_FILL);
            overlay.fill(bodyRect);
            overlay.setColor(BODY_BORDER);
            overlay.draw(bodyRect);
        }
        overlay.setColor(ASC_BORDER);
        for (Rectangle2D.Double asc : definition.getAscenders()) {
            Rectangle2D.Double rect = new Rectangle2D.Double(asc.x + instance.getX(), asc.y + instance.getY(), asc.width, asc.height);
            overlay.setColor(ASC_FILL);
            overlay.fill(rect);
            overlay.setColor(ASC_BORDER);
            overlay.draw(rect);
        }
        overlay.setColor(DESC_BORDER);
        for (Rectangle2D.Double desc : definition.getDescenders()) {
            Rectangle2D.Double rect = new Rectangle2D.Double(desc.x + instance.getX(), desc.y + instance.getY(), desc.width, desc.height);
            overlay.setColor(DESC_FILL);
            overlay.fill(rect);
            overlay.setColor(DESC_BORDER);
            overlay.draw(rect);
        }
        overlay.dispose();
    }

    private double computeDefaultX(BlockDefinition definition) {
        Rectangle2D.Double bounds = definition.getContentBounds();
        double margin = 24.0;
        double availableWidth = getCanvasWidth() - bounds.width - margin;
        double proposal = margin + instances.size() * 40.0;
        if (availableWidth <= margin) {
            return margin;
        }
        return Math.max(margin, Math.min(proposal, availableWidth));
    }

    private double computeDefaultY(BlockDefinition definition) {
        Rectangle2D.Double body = definition.getBodyRect();
        if (body == null || bodyStripes.isEmpty()) {
            return 0.0;
        }
        double top = bodyStripes.get(0).getTop();
        double desired = top - body.y;
        return clampY(definition, desired);
    }

    private BlockInstance findInstanceAt(Point point) {
        for (int i = instances.size() - 1; i >= 0; i--) {
            BlockInstance instance = instances.get(i);
            Rectangle2D.Double bounds = instance.getBoundingBox();
            if (bounds.contains(point)) {
                return instance;
            }
        }
        return null;
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.accept(selectedInstance);
        }
    }

    private void updateStripeContext(BlockInstance instance) {
        if (instance == null) {
            currentStripePlacements = Collections.emptyList();
            activeStripeIndex = -1;
            return;
        }
        currentStripePlacements = computeStripePlacements(instance.getDefinition());
        if (currentStripePlacements.isEmpty()) {
            activeStripeIndex = -1;
        } else {
            activeStripeIndex = findStripeIndex(currentStripePlacements, instance.getY());
        }
    }

    private List<StripePlacement> computeStripePlacements(BlockDefinition definition) {
        if (definition == null) {
            return Collections.emptyList();
        }
        Rectangle2D.Double body = definition.getBodyRect();
        if (body == null || bodyStripes.isEmpty()) {
            return Collections.emptyList();
        }
        List<StripePlacement> placements = new ArrayList<>(bodyStripes.size());
        for (LinerGuides.BodyStripe stripe : bodyStripes) {
            placements.add(createStripePlacement(body, stripe));
        }
        return placements;
    }

    private int findStripeIndex(List<StripePlacement> placements, double y) {
        if (placements.isEmpty()) {
            return -1;
        }
        int bestIndex = 0;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < placements.size(); i++) {
            StripePlacement placement = placements.get(i);
            if (y >= placement.minY - POSITION_EPSILON && y <= placement.maxY + POSITION_EPSILON) {
                return i;
            }
            double clamped = Math.max(placement.minY, Math.min(y, placement.maxY));
            double distance = Math.abs(y - clamped);
            if (distance < closestDistance) {
                closestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private StripePlacement createStripePlacement(Rectangle2D.Double body, LinerGuides.BodyStripe stripe) {
        double alignTop = stripe.getTop() - body.y;
        double alignBottom = stripe.getBottom() - (body.y + body.height);
        if (alignTop <= alignBottom) {
            return new StripePlacement(alignTop, alignBottom);
        }
        double snap = (alignTop + alignBottom) / 2.0;
        return new StripePlacement(snap, snap);
    }

    private static final class StripePlacement {
        final double minY;
        final double maxY;
        final double snapY;

        StripePlacement(double minY, double maxY) {
            this.minY = minY;
            this.maxY = maxY;
            this.snapY = (minY + maxY) / 2.0;
        }

        boolean hasRange() {
            return maxY - minY > POSITION_EPSILON;
        }

        double clamp(double value) {
            if (value <= minY) {
                return minY;
            }
            if (value >= maxY) {
                return maxY;
            }
            return value;
        }
    }

    private final class MouseHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            Point point = e.getPoint();
            BlockInstance found = findInstanceAt(point);
            if (found != null) {
                selectedInstance = found;
                updateStripeContext(found);
                dragStart = point;
                initialX = found.getX();
                initialY = found.getY();
                instances.remove(found);
                instances.add(found);
                repaint();
                notifySelectionChanged();
            } else if (selectedInstance != null) {
                selectedInstance = null;
                updateStripeContext(null);
                repaint();
                notifySelectionChanged();
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || selectedInstance == null || dragStart == null) {
                return;
            }
            double dx = e.getX() - dragStart.x;
            double dy = e.getY() - dragStart.y;
            double desiredY = initialY + dy;
            double newX = initialX + dx;
            double newY;
            if (!currentStripePlacements.isEmpty() && activeStripeIndex >= 0) {
                int newIndex = activeStripeIndex;
                while (newIndex > 0 && desiredY <= currentStripePlacements.get(newIndex).minY - STRIPE_SWITCH_MARGIN) {
                    newIndex--;
                }
                while (newIndex < currentStripePlacements.size() - 1
                        && desiredY >= currentStripePlacements.get(newIndex).maxY + STRIPE_SWITCH_MARGIN) {
                    newIndex++;
                }
                activeStripeIndex = newIndex;
                StripePlacement placement = currentStripePlacements.get(activeStripeIndex);
                double clamped = Math.max(placement.minY, Math.min(desiredY, placement.maxY));
                if (Math.abs(desiredY - placement.snapY) <= SNAP_DISTANCE) {
                    newY = placement.snapY;
                } else {
                    newY = clamped;
                }
            } else {
                newY = clampY(selectedInstance.getDefinition(), desiredY);
            }
            selectedInstance.setPosition(newX, newY);
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            dragStart = null;
        }
    }
}
