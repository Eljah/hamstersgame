package tatar.eljah.hamsters.tools.blockeditor;

import javax.swing.JButton;
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
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public class BlockEditorFrame extends JFrame {
    private final BlockEditorPanel editorPanel;
    private final JTextField scaleField;
    private final JLabel statusLabel;
    private File currentSvgFile;

    public BlockEditorFrame() {
        super("Block Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        editorPanel = new BlockEditorPanel();
        JScrollPane scrollPane = new JScrollPane(editorPanel);
        scrollPane.setPreferredSize(new Dimension(1024, 600));
        add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel("Load an SVG to begin", SwingConstants.LEFT);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton loadButton = new JButton("Load SVG");
        loadButton.addActionListener(this::onLoadSvg);
        controls.add(loadButton);

        controls.add(new JLabel("Scale:"));
        scaleField = new JTextField("1.0", 6);
        scaleField.addActionListener(e -> applyScale());
        controls.add(scaleField);

        JButton applyScale = new JButton("Apply");
        applyScale.addActionListener(e -> applyScale());
        controls.add(applyScale);

        JButton resetPosition = new JButton("Center Vertically");
        resetPosition.addActionListener(e -> {
            statusLabel.setText(editorPanel.centerSvgVertically());
        });
        controls.add(resetPosition);

        JButton addBody = new JButton("Add Body");
        addBody.addActionListener(e -> statusLabel.setText(editorPanel.prepareRegionCreation(BlockRegionType.BODY)));
        controls.add(addBody);

        JButton addAsc = new JButton("Add Asc");
        addAsc.addActionListener(e -> statusLabel.setText(editorPanel.prepareRegionCreation(BlockRegionType.ASCENDER)));
        controls.add(addAsc);

        JButton addDesc = new JButton("Add Desc");
        addDesc.addActionListener(e -> statusLabel.setText(editorPanel.prepareRegionCreation(BlockRegionType.DESCENDER)));
        controls.add(addDesc);

        JButton remove = new JButton("Remove Selected");
        remove.addActionListener(e -> statusLabel.setText(editorPanel.removeSelectedRegion()));
        controls.add(remove);

        JButton save = new JButton("Save");
        save.addActionListener(this::onSave);
        controls.add(save);

        add(controls, BorderLayout.NORTH);

        add(statusLabel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private void applyScale() {
        try {
            double scale = Double.parseDouble(scaleField.getText().trim().replace(',', '.'));
            if (scale <= 0) {
                throw new NumberFormatException("Scale must be positive");
            }
            if (!editorPanel.hasSvgLoaded()) {
                JOptionPane.showMessageDialog(this, "Load an SVG before setting scale", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            editorPanel.setSvgScale(scale);
            statusLabel.setText(String.format(Locale.US, "Scale set to %.3f", scale));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid scale value", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onLoadSvg(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("SVG files", "svg"));
        if (currentSvgFile != null) {
            chooser.setCurrentDirectory(currentSvgFile.getParentFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                editorPanel.loadSvg(file);
                currentSvgFile = file;
                scaleField.setText(String.format(Locale.US, "%.3f", editorPanel.getSvgScale()));
                statusLabel.setText("Loaded: " + file.getName());
            } catch (IOException ioException) {
                JOptionPane.showMessageDialog(this, "Failed to load SVG: " + ioException.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onSave(ActionEvent e) {
        if (!editorPanel.hasSvgLoaded() || currentSvgFile == null) {
            JOptionPane.showMessageDialog(this, "Load an SVG before saving", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            String svgFileName = currentSvgFile.getName();
            String config = editorPanel.buildConfigurationJson(svgFileName);
            Path targetDir = Paths.get("assets", "blocks");
            Files.createDirectories(targetDir);
            Path svgTarget = targetDir.resolve(svgFileName);
            Files.copy(currentSvgFile.toPath(), svgTarget, StandardCopyOption.REPLACE_EXISTING);
            String jsonName = svgFileName.replaceFirst("\\.svg$", "").concat(".json");
            Path jsonTarget = targetDir.resolve(jsonName);
            Files.write(jsonTarget, config.getBytes(StandardCharsets.UTF_8));
            statusLabel.setText("Saved SVG and config to " + targetDir);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
