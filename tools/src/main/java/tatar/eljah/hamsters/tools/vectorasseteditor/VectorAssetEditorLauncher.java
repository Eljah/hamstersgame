package tatar.eljah.hamsters.tools.vectorasseteditor;

import javax.swing.SwingUtilities;

public final class VectorAssetEditorLauncher {
    private VectorAssetEditorLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VectorAssetEditorFrame frame = new VectorAssetEditorFrame();
            frame.setVisible(true);
        });
    }
}
