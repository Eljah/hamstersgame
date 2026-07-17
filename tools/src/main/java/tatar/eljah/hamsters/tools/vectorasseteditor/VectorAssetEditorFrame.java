package tatar.eljah.hamsters.tools.vectorasseteditor;

import tatar.eljah.hamsters.tools.blockeditor.BlockRegionType;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class VectorAssetEditorFrame extends JFrame {
    private final VectorAssetModel model = new VectorAssetModel();
    private final VectorAssetEditorPanel editorPanel = new VectorAssetEditorPanel(model);
    private final JLabel statusLabel = new JLabel("Load a raster reference or start drawing", SwingConstants.LEFT);
    private final JComboBox<VectorFrame> frameCombo = new JComboBox<>();
    private final JComboBox<VectorAssetType> typeCombo = new JComboBox<>(VectorAssetType.values());
    private final JComboBox<VectorLineStyle> lineStyleCombo = new JComboBox<>(VectorLineStyle.values());
    private final JComboBox<EditorMode> modeCombo = new JComboBox<>(EditorMode.values());
    private final JTextField assetNameField = new JTextField("new-asset", 14);

    public VectorAssetEditorFrame() {
        super("Vector Asset Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JScrollPane scrollPane = new JScrollPane(editorPanel);
        scrollPane.setPreferredSize(new Dimension(1100, 760));
        add(scrollPane, BorderLayout.CENTER);

        add(buildToolbar(), BorderLayout.NORTH);
        add(statusLabel, BorderLayout.SOUTH);

        refreshFrameCombo();
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildToolbar() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));

        controls.add(new JLabel("Name:"));
        controls.add(assetNameField);

        controls.add(new JLabel("Type:"));
        typeCombo.addActionListener(e -> model.setType((VectorAssetType) typeCombo.getSelectedItem()));
        controls.add(typeCombo);

        JButton loadRaster = new JButton("Load Raster");
        loadRaster.addActionListener(this::onLoadRaster);
        controls.add(loadRaster);

        controls.add(new JLabel("Mode:"));
        modeCombo.addActionListener(e -> {
            EditorMode mode = (EditorMode) modeCombo.getSelectedItem();
            if (mode != null) {
                editorPanel.setMode(mode);
                statusLabel.setText("Mode: " + mode);
            }
        });
        controls.add(modeCombo);

        controls.add(new JLabel("Line:"));
        lineStyleCombo.setSelectedItem(VectorLineStyle.BALLPOINT_SCHOOL_BLUE);
        lineStyleCombo.addActionListener(e -> {
            VectorLineStyle style = (VectorLineStyle) lineStyleCombo.getSelectedItem();
            if (style != null) {
                editorPanel.setLineStyle(style);
                statusLabel.setText("Line style: " + style);
            }
        });
        controls.add(lineStyleCombo);

        JButton toggleControls = new JButton("Hide Controls");
        toggleControls.addActionListener(e -> {
            boolean visible = !editorPanel.areControlsVisible();
            editorPanel.setControlsVisible(visible);
            toggleControls.setText(visible ? "Hide Controls" : "Show Controls");
            statusLabel.setText(visible ? "Bezier controls enabled" : "Bezier controls hidden");
        });
        controls.add(toggleControls);

        JButton body = new JButton("Body Bounds");
        body.addActionListener(e -> startBoundary(BlockRegionType.BODY));
        controls.add(body);

        JButton asc = new JButton("Asc Bounds");
        asc.addActionListener(e -> startBoundary(BlockRegionType.ASCENDER));
        controls.add(asc);

        JButton desc = new JButton("Desc Bounds");
        desc.addActionListener(e -> startBoundary(BlockRegionType.DESCENDER));
        controls.add(desc);

        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> statusLabel.setText(editorPanel.removeSelected()));
        controls.add(remove);

        JButton continueStart = new JButton("Continue Start");
        continueStart.addActionListener(e -> {
            modeCombo.setSelectedItem(EditorMode.DRAW_CURVE);
            statusLabel.setText(editorPanel.continueFromStart());
        });
        controls.add(continueStart);

        JButton continueEnd = new JButton("Continue End");
        continueEnd.addActionListener(e -> {
            modeCombo.setSelectedItem(EditorMode.DRAW_CURVE);
            statusLabel.setText(editorPanel.continueFromEnd());
        });
        controls.add(continueEnd);

        controls.add(new JLabel("Frame:"));
        frameCombo.addActionListener(e -> {
            VectorFrame frame = (VectorFrame) frameCombo.getSelectedItem();
            if (frame != null) {
                editorPanel.setCurrentFrameIndex(model.getFrames().indexOf(frame));
                statusLabel.setText("Frame: " + frame.getName());
            }
        });
        controls.add(frameCombo);

        JButton duplicateFrame = new JButton("Duplicate Frame");
        duplicateFrame.addActionListener(e -> {
            editorPanel.duplicateCurrentFrame();
            refreshFrameCombo();
            statusLabel.setText("Frame duplicated; move curves to compose animation");
        });
        controls.add(duplicateFrame);

        JButton save = new JButton("Save");
        save.addActionListener(this::onSave);
        controls.add(save);

        return controls;
    }

    private void startBoundary(BlockRegionType type) {
        modeCombo.setSelectedItem(EditorMode.BOUNDARIES);
        statusLabel.setText(editorPanel.startRegionCreation(type));
    }

    private void refreshFrameCombo() {
        DefaultComboBoxModel<VectorFrame> comboModel = new DefaultComboBoxModel<>();
        for (VectorFrame frame : model.getFrames()) {
            comboModel.addElement(frame);
        }
        frameCombo.setModel(comboModel);
        frameCombo.setSelectedIndex(editorPanel.getCurrentFrameIndex());
    }

    private void onLoadRaster(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Raster images", "png", "jpg", "jpeg", "bmp", "gif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            editorPanel.loadRasterReference(file);
            statusLabel.setText("Loaded raster reference: " + file.getName());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to load raster: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onSave(ActionEvent event) {
        String assetName = normalizeAssetName(assetNameField.getText());
        if (assetName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Asset name is empty", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Path targetDir = Paths.get("assets", "vector-assets", assetName);
        String baseSvgName = assetName + ".svg";
        try {
            Files.createDirectories(targetDir);
            for (int i = 0; i < model.getFrames().size(); i++) {
                VectorFrame frame = model.getFrames().get(i);
                String frameSvgName = model.frameSvgName(baseSvgName, i);
                Files.write(targetDir.resolve(frameSvgName), model.buildSvg(frame).getBytes(StandardCharsets.UTF_8));
            }
            Files.write(targetDir.resolve(assetName + ".json"), model.buildMetadataJson(baseSvgName).getBytes(StandardCharsets.UTF_8));
            statusLabel.setText(String.format(Locale.US, "Saved %d frame(s) to %s", model.getFrames().size(), targetDir));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save asset: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String normalizeAssetName(String value) {
        return value.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    }
}
