package tatar.eljah.hamsters.tools.sceneeditor;

import javax.swing.SwingUtilities;

public final class SceneEditorLauncher {
    private SceneEditorLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SceneEditorFrame frame = new SceneEditorFrame();
            frame.setVisible(true);
        });
    }
}
