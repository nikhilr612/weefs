package io.wfs.ui.controller;

import io.wfs.core.nfs.NfsConnectionConfig;
import io.wfs.core.nfs.NfsIO;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Encapsulates NFS file-level operations.
 * Mirrors FileOperations but works with NFS file systems.
 * Following the Command pattern (GRASP) — each method is an atomic operation.
 */
public final class NfsFileOperations {

    private final ArchiveModel model;

    public NfsFileOperations(ArchiveModel model) {
        this.model = model;
    }

    /**
     * Creates a new file on the NFS mount with supplied text content.
     */
    public boolean createFile(NfsConnectionConfig config, String remotePath, String content) {
        if (!model.isOpen() || model.isReadOnly()) {
            return false;
        }
        try {
            NfsIO.writeFile(config, remotePath, content.getBytes());
            model.fireTreeRefresh();
            return true;
        } catch (IOException ex) {
            showError("Create File", ex);
            return false;
        }
    }

    /**
     * Creates a new directory on the NFS mount.
     */
    public boolean createDirectory(NfsConnectionConfig config, String remotePath) {
        if (!model.isOpen() || model.isReadOnly()) {
            return false;
        }
        try {
            NfsIO.createDirectory(config, remotePath);
            model.fireTreeRefresh();
            return true;
        } catch (IOException ex) {
            showError("Create Directory", ex);
            return false;
        }
    }

    /**
     * Deletes a file or directory from the NFS mount.
     */
    public boolean delete(NfsConnectionConfig config, String remotePath) {
        if (!model.isOpen() || model.isReadOnly()) {
            return false;
        }
        try {
            NfsIO.delete(config, remotePath);
            model.setSelectedFile(null);
            model.fireTreeRefresh();
            return true;
        } catch (IOException ex) {
            showError("Delete", ex);
            return false;
        }
    }

    /**
     * Renames/moves a file or directory on the NFS mount.
     */
    public boolean rename(NfsConnectionConfig config, String oldPath, String newPath) {
        if (!model.isOpen() || model.isReadOnly()) {
            return false;
        }
        try {
            NfsIO.rename(config, oldPath, newPath);
            model.fireTreeRefresh();
            return true;
        } catch (IOException ex) {
            showError("Rename", ex);
            return false;
        }
    }

    /**
     * Writes text content to an existing file on NFS.
     */
    public boolean saveFile(NfsConnectionConfig config, String remotePath, String content) {
        if (!model.isOpen() || model.isReadOnly()) {
            return false;
        }
        try {
            NfsIO.writeFile(config, remotePath, content.getBytes());
            return true;
        } catch (IOException ex) {
            showError("Save File", ex);
            return false;
        }
    }

    /**
     * Copies a file on the NFS mount.
     */
    public boolean copy(NfsConnectionConfig config, String sourcePath, String destPath) {
        if (!model.isOpen() || model.isReadOnly()) {
            return false;
        }
        try {
            NfsIO.copy(config, sourcePath, destPath);
            model.fireTreeRefresh();
            return true;
        } catch (IOException ex) {
            showError("Copy", ex);
            return false;
        }
    }

    /**
     * Extracts a file from NFS to the local filesystem.
     */
    public boolean extractTo(NfsConnectionConfig config, String nfsPath, Path localDestination) {
        try {
            byte[] data = NfsIO.readFile(config, nfsPath);
            java.nio.file.Files.write(localDestination, data,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException ex) {
            showError("Extract", ex);
            return false;
        }
    }

    private void showError(String title, IOException ex) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null,
                        title + " failed:\n" + ex.getMessage(),
                        title,
                        JOptionPane.ERROR_MESSAGE)
        );
    }
}
