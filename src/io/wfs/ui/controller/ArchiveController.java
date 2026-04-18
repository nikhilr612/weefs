package io.wfs.ui.controller;

import io.wfs.core.nfs.NfsConnectionConfig;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * Default Swing implementation of {@link IArchiveController} and
 * {@link INfsController}.
 * Coordinates between the model and views, handling high-level user actions:
 * - Archive operations: open, create, close, save
 * - NFS operations: mount, unmount, file operations
 * Delegates individual file operations to {@link FileOperations} and
 * {@link NfsFileOperations}.
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
    public IFileOperations getFileOps() {
        return isNfsMounted() ? nfsFileOps : fileOps;
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

        if (mode == JOptionPane.CLOSED_OPTION) {
            return;
        }

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
    public void mountNfs() {
        String value = JOptionPane.showInputDialog(parentComponent,
                "Enter NFS URI (weefs://host/path?auth=ENV_VAR[&user=username]):",
                "Mount NFS",
                JOptionPane.PLAIN_MESSAGE);

        if (value == null || value.isBlank()) {
            return;
        }

        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException ex) {
            showError("Mount NFS", new IOException("Invalid URI: " + ex.getMessage(), ex));
            return;
        }

        int mode = JOptionPane.showOptionDialog(parentComponent,
                "Mount remote file system in which mode?",
                "Mount Mode",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[] { "Read/Write", "Read Only" },
                "Read/Write");

        if (mode == JOptionPane.CLOSED_OPTION) {
            return;
        }

        boolean readOnly = (mode == 1);
        executeInBackground("Mounting remote file system...", () -> {
            try {
                model.openMountUri(uri, readOnly);
            } catch (IOException ex) {
                showError("Mount NFS", ex);
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
        Path archivePath = model.getArchivePath();
        boolean wasReadOnly = model.isReadOnly();
        executeInBackground("Saving archive...", () -> {
            try {
                model.closeArchive();
                model.openArchive(archivePath, wasReadOnly);
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

        Path parentDir = resolveTargetDirectory();
        if (parentDir == null)
            return;

        String name = JOptionPane.showInputDialog(parentComponent,
                "Enter file name:", "New File", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank())
            return;

        getFileOps().createFile(parentDir.resolve(name), "");
    }

    @Override
    public void newDirectory() {
        if (!model.isOpen() || model.isReadOnly())
            return;

        Path parentDir = resolveTargetDirectory();
        if (parentDir == null)
            return;

        String name = JOptionPane.showInputDialog(parentComponent,
                "Enter directory name:", "New Directory", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank())
            return;

        getFileOps().createDirectory(parentDir.resolve(name));
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
            getFileOps().delete(selected.getPath());
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

        Path oldPath = selected.getPath();
        Path parent = oldPath.getParent();
        if (parent == null)
            return;

        getFileOps().rename(oldPath, parent.resolve(newName));
    }

    @Override
    public void extractSelected() {
        FileNode selected = model.getSelectedFile();
        if (selected == null || selected.isDirectory())
            return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Extract To...");
        chooser.setSelectedFile(new java.io.File(selected.getDisplayName()));

        if (chooser.showSaveDialog(parentComponent) == JFileChooser.APPROVE_OPTION) {
            Path destination = chooser.getSelectedFile().toPath();
            getFileOps().extractTo(selected.getPath(), destination);
        }
    }

    @Override
    public void saveFileContent(Path path, String content) {
        getFileOps().saveFile(path, content);
    }

    // ── NFS operations (INfsController implementation) ───────────────────

    @Override
    public NfsFileOperations getNfsFileOps() {
        return nfsFileOps;
    }

    @Override
    public void unmountNfs() {
        if (currentNfsConfig == null) {
            return;
        }

        executeInBackground("Unmounting NFS...", () -> {
            try {
                currentNfsConfig = null;
                nfsFileOps.setConfig(null);
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

    private Path resolveTargetDirectory() {
        FileNode selected = model.getSelectedFile();
        return getTargetDirectory(selected);
    }

    private void executeInBackground(String message, Runnable task) {
        if (parentComponent != null) {
            parentComponent.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        }
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                task.run();
                return null;
            }

            @Override
            protected void done() {
                if (parentComponent != null) {
                    parentComponent.setCursor(java.awt.Cursor.getDefaultCursor());
                }
                try {
                    get(); // Surface any exceptions from doInBackground
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof Exception) {
                        showError(message, (Exception) cause);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
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
            nfsFileOps.setConfig(null);
            model.setNfsConfig(null);
        }
    }
}
