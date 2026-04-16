package io.wfs.ui.controller;

import io.wfs.core.nfs.NfsConnectionConfig;
import io.wfs.core.nfs.NfsIO;
import io.wfs.ui.model.ArchiveModel;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Encapsulates NFS file-level operations.
 * Implements {@link IFileOperations} for polymorphic use by controllers.
 * Requires an active {@link NfsConnectionConfig} to perform operations.
 */
public final class NfsFileOperations implements IFileOperations {

    private final ArchiveModel model;
    private volatile NfsConnectionConfig config;

    public NfsFileOperations(ArchiveModel model) {
        this.model = model;
    }

    /**
     * Sets the active NFS connection configuration.
     *
     * @param config the NFS config, or null to clear
     */
    public void setConfig(NfsConnectionConfig config) {
        this.config = config;
    }

    /**
     * Returns the active NFS connection configuration.
     *
     * @return the config, or null if not connected
     */
    public NfsConnectionConfig getConfig() {
        return config;
    }

    @Override
    public boolean createFile(Path path, String content) {
        NfsConnectionConfig cfg = this.config;
        if (cfg == null) return false;
        return createFile(cfg, path.toString(), content);
    }

    @Override
    public boolean createDirectory(Path path) {
        NfsConnectionConfig cfg = this.config;
        if (cfg == null) return false;
        return createDirectory(cfg, path.toString());
    }

    @Override
    public boolean delete(Path path) {
        NfsConnectionConfig cfg = this.config;
        if (cfg == null) return false;
        return delete(cfg, path.toString());
    }

    @Override
    public boolean rename(Path oldPath, Path newPath) {
        NfsConnectionConfig cfg = this.config;
        if (cfg == null) return false;
        return rename(cfg, oldPath.toString(), newPath.toString());
    }

    @Override
    public boolean saveFile(Path path, String content) {
        NfsConnectionConfig cfg = this.config;
        if (cfg == null) return false;
        return saveFile(cfg, path.toString(), content);
    }

    @Override
    public boolean extractTo(Path sourcePath, Path destination) {
        NfsConnectionConfig cfg = this.config;
        if (cfg == null) return false;
        return extractTo(cfg, sourcePath.toString(), destination);
    }

    @Override
    public boolean copy(Path sourcePath, Path targetDir) {
        NfsConnectionConfig cfg = this.config;
        if (cfg == null) return false;
        return copy(cfg, sourcePath.toString(), targetDir.toString());
    }

    /**
     * Creates a new file on the NFS mount with supplied text content.
     */
    public boolean createFile(NfsConnectionConfig config, String remotePath, String content) {
        if (config.isReadOnly()) {
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
        if (config.isReadOnly()) {
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
        if (config.isReadOnly()) {
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
        if (config.isReadOnly()) {
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
        if (config.isReadOnly()) {
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
        if (config.isReadOnly()) {
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
