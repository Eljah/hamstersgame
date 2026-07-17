package tatar.eljah.hamsters.tools.vectorasseteditor;

import tatar.eljah.hamsters.tools.blockeditor.BlockRegion;
import tatar.eljah.hamsters.tools.blockeditor.BlockRegionType;

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
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class VectorAssetEditorPanel extends JPanel {
    private static final Color CONTROL_COLOR = new Color(30, 110, 220);
    private static final Color SELECTED_COLOR = new Color(255, 155, 0);
    private static final Color REFERENCE_TINT = new Color(255, 255, 255, 110);

    private final VectorAssetModel model;
    private BufferedImage rasterReference;
    private int currentFrameIndex;
    private EditorMode mode = EditorMode.DRAW_CURVE;
    private VectorLineStyle lineStyle = VectorLineStyle.BALLPOINT_SCHOOL_BLUE;
    private boolean controlsVisible = true;
    private VectorCurve selectedCurve;
    private DragPoint selectedPoint;
    private Point2D.Double selectedAnchor;
    private DragTarget dragTarget;
    private Continuation continuation;
    private Point lastDragPoint;
    private Point2D.Double pendingAnchor;

    private BlockRegion selectedRegion;
    private BlockRegionType pendingRegionType;
    private Point regionStart;
    private Rectangle2D.Double pendingRegionRect;
    private boolean draggingRegion;
    private Rectangle2D.Double regionDragInitialRect;

    VectorAssetEditorPanel(VectorAssetModel model) {
        this.model = model;
        setBackground(Color.WHITE);
        setFocusable(true);
        MouseHandler mouseHandler = new MouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "deleteSelectionOrLastSegment");
        getActionMap().put("deleteSelectionOrLastSegment", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectionOrLastSegment();
            }
        });
    }

    void setMode(EditorMode mode) {
        this.mode = mode;
        pendingAnchor = null;
        continuation = null;
        dragTarget = null;
        pendingRegionType = null;
        pendingRegionRect = null;
        repaint();
    }

    void setLineStyle(VectorLineStyle lineStyle) {
        this.lineStyle = lineStyle;
    }

    boolean areControlsVisible() {
        return controlsVisible;
    }

    void setControlsVisible(boolean controlsVisible) {
        this.controlsVisible = controlsVisible;
        if (!controlsVisible) {
            selectedPoint = null;
            selectedAnchor = null;
            dragTarget = null;
        }
        repaint();
    }

    int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    void setCurrentFrameIndex(int currentFrameIndex) {
        if (currentFrameIndex < 0 || currentFrameIndex >= model.getFrames().size()) {
            return;
        }
        this.currentFrameIndex = currentFrameIndex;
        selectedCurve = null;
        selectedPoint = null;
        selectedAnchor = null;
        pendingAnchor = null;
        continuation = null;
        repaint();
    }

    void loadRasterReference(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Unsupported image file");
        }
        rasterReference = image;
        model.setCanvasSize(image.getWidth(), image.getHeight());
        model.setRasterReferenceName(file.getName());
        setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
        revalidate();
        repaint();
    }

    String startRegionCreation(BlockRegionType type) {
        mode = EditorMode.BOUNDARIES;
        pendingRegionType = type;
        pendingRegionRect = null;
        regionStart = null;
        requestFocusInWindow();
        return "Draw " + type.name().toLowerCase() + " boundary";
    }

    String removeSelected() {
        if (selectedCurve != null) {
            currentFrame().getCurves().remove(selectedCurve);
            selectedCurve = null;
            selectedPoint = null;
            selectedAnchor = null;
            continuation = null;
            repaint();
            return "Curve removed";
        }
        if (selectedRegion != null) {
            model.getRegions().remove(selectedRegion);
            selectedRegion = null;
            repaint();
            return "Boundary removed";
        }
        Toolkit.getDefaultToolkit().beep();
        return "Nothing selected";
    }

    void duplicateCurrentFrame() {
        VectorFrame source = currentFrame();
        VectorFrame copy = source.copy(String.format("frame-%03d", model.getFrames().size() + 1));
        model.getFrames().add(currentFrameIndex + 1, copy);
        currentFrameIndex++;
        selectedCurve = null;
        selectedPoint = null;
        selectedAnchor = null;
        continuation = null;
        repaint();
    }

    String continueFromStart() {
        if (selectedCurve == null) {
            Toolkit.getDefaultToolkit().beep();
            return "Select a curve first";
        }
        mode = EditorMode.DRAW_CURVE;
        continuation = Continuation.START;
        pendingAnchor = null;
        requestFocusInWindow();
        repaint();
        return "Click a new anchor to extend from curve start";
    }

    String continueFromEnd() {
        if (selectedCurve == null) {
            Toolkit.getDefaultToolkit().beep();
            return "Select a curve first";
        }
        mode = EditorMode.DRAW_CURVE;
        continuation = Continuation.END;
        pendingAnchor = null;
        requestFocusInWindow();
        repaint();
        return "Click a new anchor to extend from curve end";
    }

    private VectorFrame currentFrame() {
        return model.getFrames().get(currentFrameIndex);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(model.getCanvasWidth(), model.getCanvasHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (rasterReference != null) {
            g2d.drawImage(rasterReference, 0, 0, null);
            g2d.setColor(REFERENCE_TINT);
            g2d.fillRect(0, 0, model.getCanvasWidth(), model.getCanvasHeight());
        } else {
            drawPaperGrid(g2d);
        }

        for (VectorCurve curve : currentFrame().getCurvesView()) {
            drawCurve(g2d, curve, curve == selectedCurve);
        }
        drawPendingAnchor(g2d);
        drawRegions(g2d);

        g2d.dispose();
    }

    private void drawPaperGrid(Graphics2D g2d) {
        g2d.setColor(new Color(250, 250, 250));
        g2d.fillRect(0, 0, model.getCanvasWidth(), model.getCanvasHeight());
        g2d.setColor(new Color(210, 225, 255));
        for (int y = 40; y < model.getCanvasHeight(); y += 40) {
            g2d.drawLine(0, y, model.getCanvasWidth(), y);
        }
        g2d.setColor(new Color(255, 210, 210));
        g2d.drawLine(80, 0, 80, model.getCanvasHeight());
    }

    private void drawCurve(Graphics2D g2d, VectorCurve curve, boolean selected) {
        VectorLineStyle style = curve.getLineStyle();
        g2d.setColor(selected ? SELECTED_COLOR : style.getPreviewColor());
        g2d.setStroke(new BasicStroke(
                style.getPreviewStrokeWidth(),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        Path2D.Double path = new Path2D.Double();
        path.moveTo(curve.getStart().x, curve.getStart().y);
        path.curveTo(
                curve.getControl1().x, curve.getControl1().y,
                curve.getControl2().x, curve.getControl2().y,
                curve.getEnd().x, curve.getEnd().y);
        g2d.draw(path);

        if (controlsVisible) {
            g2d.setColor(CONTROL_COLOR);
            g2d.setStroke(new BasicStroke(1.0f));
            g2d.drawLine((int) curve.getStart().x, (int) curve.getStart().y, (int) curve.getControl1().x, (int) curve.getControl1().y);
            g2d.drawLine((int) curve.getEnd().x, (int) curve.getEnd().y, (int) curve.getControl2().x, (int) curve.getControl2().y);
            drawAnchor(g2d, curve.getStart(), isSelectedAnchor(curve.getStart()));
            drawControlHandle(g2d, curve.getControl1(), selected && selectedPoint == DragPoint.CONTROL1);
            drawControlHandle(g2d, curve.getControl2(), selected && selectedPoint == DragPoint.CONTROL2);
            drawAnchor(g2d, curve.getEnd(), isSelectedAnchor(curve.getEnd()));
        }
    }

    private void drawPendingAnchor(Graphics2D g2d) {
        if (pendingAnchor == null) {
            return;
        }
        g2d.setColor(CONTROL_COLOR);
        drawAnchor(g2d, pendingAnchor, false);
    }

    private void drawAnchor(Graphics2D g2d, Point2D.Double point, boolean selected) {
        int size = selected ? 12 : 8;
        g2d.setColor(selected ? SELECTED_COLOR : CONTROL_COLOR);
        g2d.fillOval((int) Math.round(point.x) - size / 2, (int) Math.round(point.y) - size / 2, size, size);
        if (selected) {
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(1.2f));
            g2d.drawOval((int) Math.round(point.x) - size / 2, (int) Math.round(point.y) - size / 2, size, size);
        }
    }

    private void drawControlHandle(Graphics2D g2d, Point2D.Double point, boolean selected) {
        int size = selected ? 13 : 10;
        g2d.setColor(new Color(CONTROL_COLOR.getRed(), CONTROL_COLOR.getGreen(), CONTROL_COLOR.getBlue(), 50));
        g2d.fillOval((int) Math.round(point.x) - size / 2, (int) Math.round(point.y) - size / 2, size, size);
        g2d.setColor(selected ? SELECTED_COLOR : CONTROL_COLOR);
        g2d.setStroke(new BasicStroke(1.2f));
        g2d.drawOval((int) Math.round(point.x) - size / 2, (int) Math.round(point.y) - size / 2, size, size);
    }

    private boolean isSelectedAnchor(Point2D.Double point) {
        return selectedAnchor != null && samePoint(point, selectedAnchor);
    }

    private void drawRegions(Graphics2D g2d) {
        for (BlockRegion region : model.getRegions()) {
            region.draw(g2d, region == selectedRegion);
        }
        if (pendingRegionRect != null && pendingRegionType != null) {
            Graphics2D tmp = (Graphics2D) g2d.create();
            tmp.setColor(pendingRegionType.getBorderColor());
            tmp.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6f, 6f}, 0f));
            tmp.draw(pendingRegionRect);
            tmp.dispose();
        }
    }

    private VectorCurve findCurve(Point point) {
        VectorCurve best = null;
        double bestDistance = 14.0;
        List<VectorCurve> curves = currentFrame().getCurves();
        for (int i = curves.size() - 1; i >= 0; i--) {
            VectorCurve curve = curves.get(i);
            double distance = sampledDistance(curve.toCubicCurve(), point);
            if (distance < bestDistance) {
                best = curve;
                bestDistance = distance;
            }
        }
        return best;
    }

    private DragTarget findDragTarget(Point point) {
        if (controlsVisible) {
            DragTarget handleTarget = findAnyHandleTarget(point);
            if (handleTarget != null) {
                selectDragTarget(handleTarget);
                return handleTarget;
            }
        }
        VectorCurve curve = findCurve(point);
        if (curve == null) {
            return null;
        }
        selectedCurve = curve;
        selectedPoint = DragPoint.WHOLE_CURVE;
        selectedAnchor = null;
        return new DragTarget(curve, DragPoint.WHOLE_CURVE);
    }

    private DragTarget findSelectedHandleTarget(Point point) {
        if (!controlsVisible) {
            return null;
        }
        DragTarget target = findAnyHandleTarget(point);
        if (target != null) {
            selectDragTarget(target);
        }
        return target;
    }

    private DragTarget findAnyHandleTarget(Point point) {
        List<VectorCurve> curves = currentFrame().getCurves();
        for (int i = curves.size() - 1; i >= 0; i--) {
            DragTarget target = findDragTarget(curves.get(i), point);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private void selectDragTarget(DragTarget target) {
        selectedCurve = target.curve;
        selectedPoint = target.point;
        if (target.point == DragPoint.START) {
            selectedAnchor = copyPoint(target.curve.getStart());
        } else if (target.point == DragPoint.END) {
            selectedAnchor = copyPoint(target.curve.getEnd());
        } else {
            selectedAnchor = null;
        }
    }

    private DragTarget findDragTarget(VectorCurve curve, Point point) {
        if (near(curve.getStart(), point, 10.0)) {
            return new DragTarget(curve, DragPoint.START);
        }
        if (near(curve.getEnd(), point, 10.0)) {
            return new DragTarget(curve, DragPoint.END);
        }
        if (near(curve.getControl1(), point, 11.0)) {
            return new DragTarget(curve, DragPoint.CONTROL1);
        }
        if (near(curve.getControl2(), point, 11.0)) {
            return new DragTarget(curve, DragPoint.CONTROL2);
        }
        return null;
    }

    private static boolean near(Point2D.Double handle, Point point, double radius) {
        return handle.distance(point.x, point.y) <= radius;
    }

    private void addSegmentTo(Point2D.Double newAnchor) {
        if (continuation != null && selectedCurve != null) {
            if (continuation == Continuation.END) {
                VectorCurve curve = createSegment(selectedCurve.getEnd(), newAnchor, lineStyle);
                currentFrame().getCurves().add(curve);
                selectedCurve = curve;
            } else {
                VectorCurve curve = createSegment(newAnchor, selectedCurve.getStart(), lineStyle);
                currentFrame().getCurves().add(0, curve);
                selectedCurve = curve;
            }
            continuation = null;
            pendingAnchor = null;
            repaint();
            return;
        }

        if (pendingAnchor == null) {
            pendingAnchor = newAnchor;
            repaint();
            return;
        }

        VectorCurve curve = createSegment(pendingAnchor, newAnchor, lineStyle);
        currentFrame().getCurves().add(curve);
        selectedCurve = curve;
        pendingAnchor = newAnchor;
        repaint();
    }

    private static VectorCurve createSegment(Point2D.Double start, Point2D.Double end, VectorLineStyle lineStyle) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        return new VectorCurve(
                start,
                new Point2D.Double(start.x + dx / 3.0, start.y + dy / 3.0),
                new Point2D.Double(start.x + dx * 2.0 / 3.0, start.y + dy * 2.0 / 3.0),
                end,
                lineStyle);
    }

    private String deleteSelectionOrLastSegment() {
        if (selectedAnchor != null && selectedCurve != null && (selectedPoint == DragPoint.START || selectedPoint == DragPoint.END)) {
            String result = deleteSelectedAnchor();
            repaint();
            return result;
        }
        if (selectedRegion != null) {
            model.getRegions().remove(selectedRegion);
            selectedRegion = null;
            repaint();
            return "Boundary removed";
        }
        List<VectorCurve> curves = currentFrame().getCurves();
        if (!curves.isEmpty()) {
            VectorCurve removed = curves.remove(curves.size() - 1);
        if (selectedCurve == removed) {
                selectedCurve = null;
                selectedPoint = null;
                selectedAnchor = null;
            }
            pendingAnchor = curves.isEmpty() ? null : copyPoint(curves.get(curves.size() - 1).getEnd());
            repaint();
            return "Last segment removed";
        }
        Toolkit.getDefaultToolkit().beep();
        return "Nothing to delete";
    }

    private String deleteSelectedAnchor() {
        Point2D.Double anchor = selectedAnchor;
        List<EndpointRef> incident = findIncidentEndpoints(anchor);
        if (incident.size() == 2) {
            EndpointRef first = incident.get(0);
            EndpointRef second = incident.get(1);
            Point2D.Double bridgeStart = copyPoint(first.otherEndpoint());
            Point2D.Double bridgeEnd = copyPoint(second.otherEndpoint());
            int insertAt = Math.min(first.index, second.index);
            removeIncidentCurves(incident);
            if (bridgeStart.distance(bridgeEnd) > 0.001) {
                VectorLineStyle style = selectedCurve.getLineStyle();
                VectorCurve bridge = createSegment(bridgeStart, bridgeEnd, style);
                currentFrame().getCurves().add(insertAt, bridge);
                selectedCurve = bridge;
                selectedPoint = null;
                selectedAnchor = null;
                pendingAnchor = copyPoint(bridge.getEnd());
                return "Middle anchor removed and neighbours joined";
            }
            selectedCurve = null;
            selectedPoint = null;
            selectedAnchor = null;
            pendingAnchor = null;
            return "Middle anchor removed";
        }

        for (int i = incident.size() - 1; i >= 0; i--) {
            currentFrame().getCurves().remove(incident.get(i).curve);
        }
        pendingAnchor = null;
        selectedCurve = null;
        selectedPoint = null;
        selectedAnchor = null;
        return "Edge anchor removed with adjacent segment";
    }

    private List<EndpointRef> findIncidentEndpoints(Point2D.Double anchor) {
        List<EndpointRef> refs = new ArrayList<>();
        List<VectorCurve> curves = currentFrame().getCurves();
        for (int i = 0; i < curves.size(); i++) {
            VectorCurve curve = curves.get(i);
            if (samePoint(curve.getStart(), anchor)) {
                refs.add(new EndpointRef(i, curve, DragPoint.START));
            }
            if (samePoint(curve.getEnd(), anchor)) {
                refs.add(new EndpointRef(i, curve, DragPoint.END));
            }
        }
        return refs;
    }

    private void removeIncidentCurves(List<EndpointRef> refs) {
        List<Integer> indexes = new ArrayList<>();
        for (EndpointRef ref : refs) {
            if (!indexes.contains(ref.index)) {
                indexes.add(ref.index);
            }
        }
        indexes.sort((a, b) -> b - a);
        for (Integer index : indexes) {
            currentFrame().getCurves().remove((int) index);
        }
    }

    private static boolean samePoint(Point2D.Double a, Point2D.Double b) {
        return a.distance(b) <= 0.5;
    }

    private static Point2D.Double copyPoint(Point2D.Double point) {
        return new Point2D.Double(point.x, point.y);
    }

    private void dragSelected(Point point) {
        if (dragTarget == null || lastDragPoint == null) {
            return;
        }
        double dx = point.x - lastDragPoint.x;
        double dy = point.y - lastDragPoint.y;
        VectorCurve curve = dragTarget.curve;
        switch (dragTarget.point) {
            case START:
                dragSharedAnchor(curve.getStart(), dx, dy);
                selectedAnchor = copyPoint(curve.getStart());
                selectedPoint = DragPoint.START;
                break;
            case END:
                dragSharedAnchor(curve.getEnd(), dx, dy);
                selectedAnchor = copyPoint(curve.getEnd());
                selectedPoint = DragPoint.END;
                break;
            case CONTROL1:
                curve.getControl1().x += dx;
                curve.getControl1().y += dy;
                selectedAnchor = null;
                selectedPoint = DragPoint.CONTROL1;
                break;
            case CONTROL2:
                curve.getControl2().x += dx;
                curve.getControl2().y += dy;
                selectedAnchor = null;
                selectedPoint = DragPoint.CONTROL2;
                break;
            case WHOLE_CURVE:
                curve.translate(dx, dy);
                selectedAnchor = null;
                selectedPoint = DragPoint.WHOLE_CURVE;
                break;
            default:
                break;
        }
        lastDragPoint = point;
        repaint();
    }

    private void dragSharedAnchor(Point2D.Double anchor, double dx, double dy) {
        List<EndpointRef> incident = findIncidentEndpoints(anchor);
        for (EndpointRef ref : incident) {
            if (ref.point == DragPoint.START) {
                ref.curve.getStart().x += dx;
                ref.curve.getStart().y += dy;
                ref.curve.getControl1().x += dx;
                ref.curve.getControl1().y += dy;
            } else if (ref.point == DragPoint.END) {
                ref.curve.getEnd().x += dx;
                ref.curve.getEnd().y += dy;
                ref.curve.getControl2().x += dx;
                ref.curve.getControl2().y += dy;
            }
        }
    }

    private static double sampledDistance(CubicCurve2D.Double curve, Point point) {
        double best = Double.MAX_VALUE;
        Point2D.Double previous = pointOnCurve(curve, 0.0);
        for (int i = 1; i <= 32; i++) {
            double t = i / 32.0;
            Point2D.Double current = pointOnCurve(curve, t);
            best = Math.min(best, java.awt.geom.Line2D.ptSegDist(previous.x, previous.y, current.x, current.y, point.x, point.y));
            previous = current;
        }
        return best;
    }

    private static Point2D.Double pointOnCurve(CubicCurve2D.Double c, double t) {
        double u = 1.0 - t;
        double x = u * u * u * c.x1 + 3 * u * u * t * c.ctrlx1 + 3 * u * t * t * c.ctrlx2 + t * t * t * c.x2;
        double y = u * u * u * c.y1 + 3 * u * u * t * c.ctrly1 + 3 * u * t * t * c.ctrly2 + t * t * t * c.y2;
        return new Point2D.Double(x, y);
    }

    private BlockRegion findRegion(Point point) {
        List<BlockRegion> regions = model.getRegions();
        for (int i = regions.size() - 1; i >= 0; i--) {
            if (regions.get(i).getRect().contains(point)) {
                return regions.get(i);
            }
        }
        return null;
    }

    private void finishPendingRegion() {
        if (pendingRegionRect == null || pendingRegionType == null) {
            return;
        }
        double width = Math.abs(pendingRegionRect.width);
        double height = Math.abs(pendingRegionRect.height);
        if (width >= 2.0 && height >= 2.0) {
            Rectangle2D.Double rect = new Rectangle2D.Double(
                    Math.min(pendingRegionRect.x, pendingRegionRect.x + pendingRegionRect.width),
                    Math.min(pendingRegionRect.y, pendingRegionRect.y + pendingRegionRect.height),
                    width,
                    height);
            selectedRegion = new BlockRegion(pendingRegionType, rect);
            model.getRegions().add(selectedRegion);
        }
        pendingRegionRect = null;
        regionStart = null;
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
            DragTarget handleTarget = findSelectedHandleTarget(point);
            if (handleTarget != null) {
                dragTarget = handleTarget;
                lastDragPoint = point;
                repaint();
                return;
            }
            if (mode == EditorMode.DRAW_CURVE) {
                addSegmentTo(new Point2D.Double(point.x, point.y));
                return;
            }
            if (mode == EditorMode.MOVE_CURVES) {
                dragTarget = findDragTarget(point);
                selectedRegion = null;
                lastDragPoint = point;
                repaint();
                return;
            }
            if (mode == EditorMode.BOUNDARIES) {
                if (pendingRegionType != null) {
                    regionStart = point;
                    pendingRegionRect = new Rectangle2D.Double(point.x, point.y, 0, 0);
                } else {
                    selectedRegion = findRegion(point);
                    selectedCurve = null;
                    selectedPoint = null;
                    selectedAnchor = null;
                    if (selectedRegion != null) {
                        draggingRegion = true;
                        regionDragInitialRect = new Rectangle2D.Double(
                                selectedRegion.getRect().x,
                                selectedRegion.getRect().y,
                                selectedRegion.getRect().width,
                                selectedRegion.getRect().height);
                        lastDragPoint = point;
                    }
                    repaint();
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            Point point = e.getPoint();
            if (dragTarget != null && selectedCurve != null && lastDragPoint != null) {
                dragSelected(point);
                repaint();
                return;
            }
            if (mode == EditorMode.BOUNDARIES && pendingRegionRect != null && regionStart != null) {
                pendingRegionRect.width = point.x - regionStart.x;
                pendingRegionRect.height = point.y - regionStart.y;
                repaint();
                return;
            }
            if (mode == EditorMode.BOUNDARIES && draggingRegion && selectedRegion != null && regionDragInitialRect != null && lastDragPoint != null) {
                Rectangle2D.Double rect = selectedRegion.getRect();
                rect.x = regionDragInitialRect.x + point.x - lastDragPoint.x;
                rect.y = regionDragInitialRect.y + point.y - lastDragPoint.y;
                repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            if (mode == EditorMode.BOUNDARIES && pendingRegionRect != null) {
                finishPendingRegion();
            }
            draggingRegion = false;
            dragTarget = null;
            regionDragInitialRect = null;
            lastDragPoint = null;
        }
    }

    private enum Continuation {
        START,
        END
    }

    private enum DragPoint {
        START,
        CONTROL1,
        CONTROL2,
        END,
        WHOLE_CURVE
    }

    private static final class DragTarget {
        private final VectorCurve curve;
        private final DragPoint point;

        private DragTarget(VectorCurve curve, DragPoint point) {
            this.curve = curve;
            this.point = point;
        }
    }

    private static final class EndpointRef {
        private final int index;
        private final VectorCurve curve;
        private final DragPoint point;

        private EndpointRef(int index, VectorCurve curve, DragPoint point) {
            this.index = index;
            this.curve = curve;
            this.point = point;
        }

        private Point2D.Double otherEndpoint() {
            return point == DragPoint.START ? curve.getEnd() : curve.getStart();
        }
    }
}
