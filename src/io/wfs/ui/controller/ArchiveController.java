package io.wfs.ui.controller;

import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Main application controller. Coordinates between the model and views.
 * Handles high-level user actions: open, create, close, save archives.
 * Delegates individual file operations to {@link FileOperations}.
 */
public final class ArchiveController {

    private final ArchiveModel model;
    private final FileOperations fileOps;
    private Component parentComponent;

    public ArchiveController(ArchiveModel model) {
        this.model = model;
        this.fileOps = new FileOperations(model);
    }

    public void setParentComponent(Component parent) {
        this.parentComponent = parent;
    }

    public ArchiveModel getModel() {
        return model;
    }

    public FileOperations getFileOps() {
        return fileOps;
    }

    // ── Archive-level actions ──────────────────────────────────────────

    public void openArchive() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Archive");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Archives (*.zip, *.tar)", "zip", "tar"));
        chooser.setAcceptAllFileFilterUsed(true);

        if (chooser.showOpenDialog(parentComponent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();

        int mode = JOptionPane.showOptionDialog(parentComponent,
                "Open archive in which mode?",
                "Open Mode",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[] { "Read/Write", "Read Only" },
                "Read/Write");

        boolean readOnly = (mode == 1);

        executeInBackground("Opening archive...", () -> {
            try {
                model.openArchive(selected, readOnly);
            } catch (IOException ex) {
                showError("Open Archive", ex);
            }
        });
    }

    public void createArchive() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Create New Archive");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Archives (*.zip, *.tar)", "zip", "tar"));

        if (chooser.showSaveDialog(parentComponent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();

        executeInBackground("Creating archive...", () -> {
            try {
                model.createArchive(selected);
            } catch (IOException ex) {
                showError("Create Archive", ex);
            }
        });
    }

    public void closeArchive() {
        executeInBackground("Closing archive...", () -> {
            try {
                model.closeArchive();
            } catch (IOException ex) {
                showError("Close Archive", ex);
            }
        });
    }

    public void saveArchive() {
        if (!model.isOpen() || model.isReadOnly())
            return;
        executeInBackground("Saving archive...", () -> {
            try {
                model.closeArchive();
                // Immediately re-open after save so user can keep working
            } catch (IOException ex) {
                showError("Save Archive", ex);
            }
        });
    }

    // ── File-level actions (prompting UI) ──────────────────────────────

    public void newFile() {
        if (!model.isOpen() || model.isReadOnly())
            return;
        FileNode selected = model.getSelectedFile();
        Path parentDir = getTargetDirectory(selected);
        if (parentDir == null)
            return;

        String name = JOptionPane.showInputDialog(parentComponent,
                "Enter file name:", "New File", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank())
            return;

        Path filePath = parentDir.resolve(name);
        fileOps.createFile(filePath, "");
    }

    public void newDirectory() {
        if (!model.isOpen() || model.isReadOnly())
            return;
        FileNode selected = model.getSelectedFile();
        Path parentDir = getTargetDirectory(selected);
        if (parentDir == null)
            return;

        String name = JOptionPane.showInputDialog(parentComponent,
                "Enter directory name:", "New Directory", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank())
            return;

        Path dirPath = parentDir.resolve(name);
        fileOps.createDirectory(dirPath);
    }

    public void deleteSelected() {
        FileNode selected = model.getSelectedFile();
        if (selected == null || !model.isOpen() || model.isReadOnly())
            return;

        int confirm = JOptionPane.showConfirmDialog(parentComponent,
                "Delete \"" + selected.getDisplayName() + "\"?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            fileOps.delete(selected);
        }
    }

    public void renameSelected() {
        FileNode selected = model.getSelectedFile();
        if (selected == null || !model.isOpen() || model.isReadOnly())
            return;

        String newName = JOptionPane.showInputDialog(parentComponent,
                "Enter new name:", selected.getDisplayName());
        if (newName == null || newName.isBlank())
            return;

        Path oldPath = selected.getPath();
        Path parent = oldPath.getParent();
        if (parent == null)
            return;

        Path newPath = parent.resolve(newName);
        fileOps.rename(oldPath, newPath);
    }

    public void extractSelected() {
        FileNode selected = model.getSelectedFile();
        if (selected == null || selected.isDirectory())
            return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Extract To...");
        chooser.setSelectedFile(new java.io.File(selected.getDisplayName()));

        if (chooser.showSaveDialog(parentComponent) == JFileChooser.APPROVE_OPTION) {
            Path destination = chooser.getSelectedFile().toPath();
            fileOps.extractTo(selected.getPath(), destination);
        }
    }

    public void saveFileContent(Path path, String content) {
        fileOps.saveFile(path, content);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Path getTargetDirectory(FileNode selected) {
        if (selected != null && selected.isDirectory()) {
            return selected.getPath();
        }
        if (selected != null) {
            return selected.getPath().getParent();
        }
        return model.getRootPath();
    }

    private void executeInBackground(String message, Runnable task) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                task.run();
                return null;
            }
        };
        worker.execute();
    }

    private void showError(String operation, Exception ex) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parentComponent,
                operation + " failed:\n" + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE));
    }
}
