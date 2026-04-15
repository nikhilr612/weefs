package io.wfs.ui.controller;

import io.wfs.core.nfs.NfsConnectionConfig;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Default Swing implementation of {@link IArchiveController} and {@link INfsController}.
 * Coordinates between the model and views, handling high-level user actions:
 * - Archive operations: open, create, close, save
 * - NFS operations: mount, unmount, file operations
 * Delegates individual file operations to {@link FileOperations} and {@link NfsFileOperations}.
 */
public final class ArchiveController implements IArchiveController, INfsController {

    private final ArchiveModel model;
    private final FileOperations fileOps;
    private final NfsFileOperations nfsFileOps;
    private Component parentComponent;
    private NfsConnectionConfig currentNfsConfig;

    public ArchiveController(ArchiveModel model) {
        this.model = model;
        this.fileOps = new FileOperations(model);
        this.nfsFileOps = new NfsFileOperations(model);
    }

    @Override
    public void setParentComponent(Component parent) {
        this.parentComponent = parent;
    }

    @Override
    public ArchiveModel getModel() {
        return model;
    }

    @Override
    public FileOperations getFileOps() {
        return fileOps;
    }

    // ── Archive-level actions ──────────────────────────────────────────

    @Override
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
                clearNfsIfMounted();
                model.openArchive(selected, readOnly);
            } catch (IOException ex) {
                showError("Open Archive", ex);
            }
        });
    }

    @Override
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
                clearNfsIfMounted();
                model.createArchive(selected);
            } catch (IOException ex) {
                showError("Create Archive", ex);
            }
        });
    }

    @Override
    public void closeArchive() {
        executeInBackground("Closing archive...", () -> {
            try {
                model.closeArchive();
            } catch (IOException ex) {
                showError("Close Archive", ex);
            }
        });
    }

    @Override
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

    @Override
    public void newFile() {
        if (!model.isOpen() || model.isReadOnly())
            return;

        if (isNfsMounted()) {
            FileNode selected = model.getSelectedFile();
            String parentDir = getTargetNfsDirectory(selected);
            if (parentDir == null) {
                return;
            }

            String name = JOptionPane.showInputDialog(parentComponent,
                    "Enter file name:", "New File", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.isBlank()) {
                return;
            }

            nfsFileOps.createFile(currentNfsConfig, joinNfsPath(parentDir, name), "");
            return;
        }

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

    @Override
    public void newDirectory() {
        if (!model.isOpen() || model.isReadOnly())
            return;

        if (isNfsMounted()) {
            FileNode selected = model.getSelectedFile();
            String parentDir = getTargetNfsDirectory(selected);
            if (parentDir == null) {
                return;
            }

            String name = JOptionPane.showInputDialog(parentComponent,
                    "Enter directory name:", "New Directory", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.isBlank()) {
                return;
            }

            nfsFileOps.createDirectory(currentNfsConfig, joinNfsPath(parentDir, name));
            return;
        }

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

    @Override
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
            if (isNfsMounted()) {
                nfsFileOps.delete(currentNfsConfig, selected.getPath().toString());
            } else {
                fileOps.delete(selected);
            }
        }
    }

    @Override
    public void renameSelected() {
        FileNode selected = model.getSelectedFile();
        if (selected == null || !model.isOpen() || model.isReadOnly())
            return;

        String newName = JOptionPane.showInputDialog(parentComponent,
                "Enter new name:", selected.getDisplayName());
        if (newName == null || newName.isBlank())
            return;

        if (isNfsMounted()) {
            String oldPath = selected.getPath().toString();
            String parent = getParentNfsPath(oldPath);
            String newPath = joinNfsPath(parent, newName);
            nfsFileOps.rename(currentNfsConfig, oldPath, newPath);
            return;
        }

        Path oldPath = selected.getPath();
        Path parent = oldPath.getParent();
        if (parent == null)
            return;

        Path newPath = parent.resolve(newName);
        fileOps.rename(oldPath, newPath);
    }

    @Override
    public void extractSelected() {
        if (isNfsMounted()) {
            extractNfsSelected();
            return;
        }

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

    @Override
    public void saveFileContent(Path path, String content) {
        if (isNfsMounted()) {
            nfsFileOps.saveFile(currentNfsConfig, path.toString(), content);
            return;
        }
        fileOps.saveFile(path, content);
    }

    // ── NFS operations (INfsController implementation) ───────────────────

    @Override
    public NfsFileOperations getNfsFileOps() {
        return nfsFileOps;
    }

    @Override
    public void mountNfs() {
        // Show dialog to get NFS connection details
        NfsConnectionDialog dialog = new NfsConnectionDialog(parentComponent);
        NfsConnectionConfig config = dialog.showDialog();
        
        if (config == null) {
            return; // User cancelled
        }

        executeInBackground("Mounting NFS...", () -> {
            try {
                // Here we would typically mount the NFS filesystem
                // For now, we just store the config and update the model
                currentNfsConfig = config;
                model.setNfsConfig(config);
                model.fireTreeRefresh();  // ← FIX: Refresh tree to show NFS contents
                JOptionPane.showMessageDialog(parentComponent,
                        "NFS mounted: " + config.getHost() + ":" + config.getPort() + config.getExportPath(),
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                showError("Mount NFS", ex);
            }
        });
    }

    @Override
    public void unmountNfs() {
        if (currentNfsConfig == null) {
            return;
        }

        executeInBackground("Unmounting NFS...", () -> {
            try {
                // Cleanup NFS connection
                currentNfsConfig = null;
                model.setNfsConfig(null);
                model.fireTreeRefresh();
            } catch (Exception ex) {
                showError("Unmount NFS", ex);
            }
        });
    }

    @Override
    public void extractNfsSelected() {
        if (!isNfsMounted() || model.getSelectedFile() == null || model.getSelectedFile().isDirectory()) {
            return;
        }

        FileNode selected = model.getSelectedFile();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Extract NFS File To...");
        chooser.setSelectedFile(new java.io.File(selected.getDisplayName()));

        if (chooser.showSaveDialog(parentComponent) == JFileChooser.APPROVE_OPTION) {
            Path destination = chooser.getSelectedFile().toPath();
            executeInBackground("Extracting file...", () -> {
                try {
                    nfsFileOps.extractTo(currentNfsConfig, selected.getPath().toString(), destination);
                    JOptionPane.showMessageDialog(parentComponent,
                            "File extracted successfully",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    showError("Extract NFS File", ex);
                }
            });
        }
    }

    @Override
    public NfsConnectionConfig getCurrentNfsConfig() {
        return currentNfsConfig;
    }

    @Override
    public boolean isNfsMounted() {
        return currentNfsConfig != null;
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

    private String getTargetNfsDirectory(FileNode selected) {
        if (selected == null) {
            return "/";
        }
        if (selected.isDirectory()) {
            return selected.getPath().toString();
        }
        return getParentNfsPath(selected.getPath().toString());
    }

    private String getParentNfsPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "/";
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }

    private String joinNfsPath(String parent, String child) {
        if (parent == null || parent.isBlank() || "/".equals(parent)) {
            return "/" + child;
        }
        return parent.endsWith("/") ? parent + child : parent + "/" + child;
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

    private void clearNfsIfMounted() throws IOException {
        if (currentNfsConfig != null || model.isNfsMounted()) {
            currentNfsConfig = null;
            model.setNfsConfig(null);
        }
    }
}
