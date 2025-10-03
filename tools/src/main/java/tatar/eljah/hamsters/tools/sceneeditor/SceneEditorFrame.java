package tatar.eljah.hamsters.tools.sceneeditor;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class SceneEditorFrame extends JFrame {
    private final SceneEditorPanel editorPanel;
    private final DefaultListModel<BlockDefinition> blockListModel;
    private final JList<BlockDefinition> blockList;
    private final JTextField sceneNameField;
    private final javax.swing.JLabel statusLabel;
    private final LinerGuides linerGuides;
    private final Map<String, BlockDefinition> definitionsByName = new HashMap<>();
    private File currentSceneFile;

    SceneEditorFrame() {
        super("Scene Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        try {
            linerGuides = LinerGuides.load();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load liner assets", e);
        }

        statusLabel = new javax.swing.JLabel("Ready", SwingConstants.LEFT);

        editorPanel = new SceneEditorPanel(linerGuides);
        editorPanel.setSelectionListener(instance -> {
            if (instance == null) {
                statusLabel.setText("No block selected");
            } else {
                BlockDefinition definition = instance.getDefinition();
                if (definition != null) {
                    statusLabel.setText(String.format(Locale.US, "Selected %s at (%.1f, %.1f)",
                            definition.getDisplayName(), instance.getX(), instance.getY()));
                } else {
                    statusLabel.setText("Selected block with missing definition");
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(editorPanel);
        scrollPane.setPreferredSize(new Dimension(1024, 640));
        add(scrollPane, BorderLayout.CENTER);

        blockListModel = new DefaultListModel<>();
        blockList = new JList<>(blockListModel);
        blockList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(blockList);
        listScroll.setPreferredSize(new Dimension(200, 300));

        JPanel leftPanel = new JPanel(new BorderLayout());
        javax.swing.JLabel listLabel = new javax.swing.JLabel("Blocks", SwingConstants.CENTER);
        leftPanel.add(listLabel, BorderLayout.NORTH);
        leftPanel.add(listScroll, BorderLayout.CENTER);

        JPanel leftButtons = new JPanel(new GridLayout(0, 1, 4, 4));
        JButton addButton = new JButton("Add to Scene");
        addButton.addActionListener(this::onAddBlock);
        leftButtons.add(addButton);

        JButton refreshButton = new JButton("Refresh Blocks");
        refreshButton.addActionListener(e -> reloadBlockDefinitions());
        leftButtons.add(refreshButton);

        JButton removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(e -> {
            if (editorPanel.removeSelectedInstance()) {
                statusLabel.setText("Removed block from scene");
            } else {
                statusLabel.setText("Nothing to remove");
            }
        });
        leftButtons.add(removeButton);

        JButton moveUpButton = new JButton("Move Up");
        moveUpButton.addActionListener(e -> {
            if (editorPanel.moveSelectedInstanceUp()) {
                BlockInstance selected = editorPanel.getSelectedInstance();
                if (selected != null && selected.getDefinition() != null) {
                    statusLabel.setText(String.format(Locale.US, "Moved %s to (%.1f, %.1f)",
                            selected.getDefinition().getDisplayName(), selected.getX(), selected.getY()));
                } else {
                    statusLabel.setText("Moved block up");
                }
            } else {
                statusLabel.setText("Cannot move block up");
            }
        });
        leftButtons.add(moveUpButton);

        JButton moveDownButton = new JButton("Move Down");
        moveDownButton.addActionListener(e -> {
            if (editorPanel.moveSelectedInstanceDown()) {
                BlockInstance selected = editorPanel.getSelectedInstance();
                if (selected != null && selected.getDefinition() != null) {
                    statusLabel.setText(String.format(Locale.US, "Moved %s to (%.1f, %.1f)",
                            selected.getDefinition().getDisplayName(), selected.getX(), selected.getY()));
                } else {
                    statusLabel.setText("Moved block down");
                }
            } else {
                statusLabel.setText("Cannot move block down");
            }
        });
        leftButtons.add(moveDownButton);

        JButton clearButton = new JButton("Clear Scene");
        clearButton.addActionListener(e -> {
            editorPanel.clearScene();
            statusLabel.setText("Scene cleared");
        });
        leftButtons.add(clearButton);

        leftPanel.add(leftButtons, BorderLayout.SOUTH);
        add(leftPanel, BorderLayout.WEST);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new javax.swing.JLabel("Scene name:"));
        sceneNameField = new JTextField("scene", 20);
        topPanel.add(sceneNameField);

        JButton loadButton = new JButton("Load Scene");
        loadButton.addActionListener(this::onLoadScene);
        topPanel.add(loadButton);

        JButton saveButton = new JButton("Save Scene");
        saveButton.addActionListener(this::onSaveScene);
        topPanel.add(saveButton);

        add(topPanel, BorderLayout.NORTH);

        add(statusLabel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);

        reloadBlockDefinitions();
    }

    private void onAddBlock(ActionEvent event) {
        BlockDefinition selected = blockList.getSelectedValue();
        if (selected == null) {
            statusLabel.setText("Select a block to add");
            return;
        }
        editorPanel.addBlock(selected);
        statusLabel.setText("Added block " + selected.getDisplayName());
    }

    private void onSaveScene(ActionEvent event) {
        List<BlockInstance> instances = editorPanel.getInstances();
        if (instances.isEmpty()) {
            int result = JOptionPane.showConfirmDialog(this, "Scene is empty. Save anyway?", "Confirm",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        String sceneName = sceneNameField.getText().trim();
        if (sceneName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter scene name", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!sceneName.endsWith(".json")) {
            sceneName = sceneName + ".json";
        }
        Path targetDir = Paths.get("assets", "scenes");
        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(sceneName);
            String json = buildSceneJson(instances);
            Files.write(target, json.getBytes(StandardCharsets.UTF_8));
            currentSceneFile = target.toFile();
            statusLabel.setText("Scene saved to " + target.toAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save scene: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onLoadScene(ActionEvent event) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Scene JSON", "json"));
        Path scenesDir = Paths.get("assets", "scenes");
        if (Files.isDirectory(scenesDir)) {
            chooser.setCurrentDirectory(scenesDir.toFile());
        }
        if (currentSceneFile != null) {
            chooser.setSelectedFile(currentSceneFile);
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                loadScene(file);
                currentSceneFile = file;
                sceneNameField.setText(stripExtension(file.getName()));
                statusLabel.setText("Loaded scene " + file.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to load scene: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadScene(File file) throws IOException {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(new FileHandle(file));
        JsonValue blocks = root.get("blocks");
        editorPanel.clearScene();
        if (blocks == null || !blocks.isArray()) {
            return;
        }
        for (JsonValue item : blocks) {
            String blockName = item.getString("block");
            BlockDefinition definition = definitionsByName.get(blockName);
            if (definition == null) {
                JOptionPane.showMessageDialog(this,
                        "Block " + blockName + " is missing. Skipping.",
                        "Warning",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            double x = item.getDouble("x", 0.0);
            double y = item.getDouble("y", 0.0);
            editorPanel.addBlock(definition, x, y, false);
        }
        statusLabel.setText(String.format(Locale.US, "Loaded %d blocks", editorPanel.getInstances().size()));
    }

    private String buildSceneJson(List<BlockInstance> instances) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format(Locale.US, "  \"background\": \"%s\",\n", linerGuides.getBackgroundAssetName()));
        sb.append(String.format(Locale.US, "  \"canvasWidth\": %d,\n", editorPanel.getCanvasWidth()));
        sb.append(String.format(Locale.US, "  \"canvasHeight\": %d,\n", editorPanel.getCanvasHeight()));
        if (instances.isEmpty()) {
            sb.append("  \"blocks\": []\n");
        } else {
            sb.append("  \"blocks\": [\n");
            for (int i = 0; i < instances.size(); i++) {
                BlockInstance instance = instances.get(i);
                sb.append(String.format(Locale.US,
                        "    {\"block\": \"%s\", \"x\": %.3f, \"y\": %.3f}",
                        instance.getBlockReference(),
                        instance.getX(),
                        instance.getY()));
                if (i < instances.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append("  ]\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private void reloadBlockDefinitions() {
        Path blocksDir = Paths.get("assets", "blocks");
        List<BlockDefinition> loaded = new ArrayList<>();
        definitionsByName.clear();
        if (Files.isDirectory(blocksDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(blocksDir, "*.json")) {
                for (Path json : stream) {
                    try {
                        BlockDefinition definition = BlockDefinition.load(json.toFile());
                        loaded.add(definition);
                        definitionsByName.put(definition.getJsonFileName(), definition);
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(this,
                                "Failed to load block " + json.getFileName() + ": " + e.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to read blocks directory: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        loaded.sort(Comparator.comparing(BlockDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        blockListModel.clear();
        for (BlockDefinition definition : loaded) {
            blockListModel.addElement(definition);
        }
        if (!loaded.isEmpty()) {
            blockList.setSelectedIndex(0);
            statusLabel.setText(String.format(Locale.US, "Loaded %d blocks", loaded.size()));
        } else {
            statusLabel.setText("No blocks found in assets/blocks");
        }
        editorPanel.remapDefinitions(definitionsByName);
    }

    private static String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx > 0) {
            return name.substring(0, idx);
        }
        return name;
    }
}
