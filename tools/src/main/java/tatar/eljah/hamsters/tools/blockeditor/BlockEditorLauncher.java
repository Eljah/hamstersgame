package tatar.eljah.hamsters.tools.blockeditor;

import javax.swing.SwingUtilities;

public final class BlockEditorLauncher {
    private BlockEditorLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BlockEditorFrame frame = new BlockEditorFrame();
            frame.setVisible(true);
        });
    }
}
