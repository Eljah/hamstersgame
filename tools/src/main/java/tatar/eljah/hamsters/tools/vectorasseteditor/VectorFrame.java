package tatar.eljah.hamsters.tools.vectorasseteditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class VectorFrame {
    private final String name;
    private final List<VectorCurve> curves = new ArrayList<>();

    VectorFrame(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }

    List<VectorCurve> getCurves() {
        return curves;
    }

    List<VectorCurve> getCurvesView() {
        return Collections.unmodifiableList(curves);
    }

    VectorFrame copy(String newName) {
        VectorFrame frame = new VectorFrame(newName);
        for (VectorCurve curve : curves) {
            frame.curves.add(curve.copy());
        }
        return frame;
    }

    @Override
    public String toString() {
        return name;
    }
}
