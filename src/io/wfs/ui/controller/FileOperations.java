package io.wfs.ui.controller;

import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Encapsulates file-level operations performed on the mounted archive.
 * Follows the Command pattern — each method is an atomic operation.
 * All operations post errors to the EDT via dialogs.
 */
public final class FileOperations {

    private final ArchiveModel model;

    public FileOperations(ArchiveModel model) {
        this.model = model;
    }

    /**
     * Creates a new file at the given path with the supplied text content.
     */
    public boolean createFile(Path path, String content) {
        if (!model.isOpen() || model.isReadOnly())
            return false;
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardOpenOption.CREATE_NEW);
            model.fireTreeRefresh();
            return true;
        } catch (IOException ex) {
            showError("Create File", ex);
            return false;
        }
    }

    /**
     * Creates a new directory at the given path.
     */
    public boolean createDirectory(Path path) {
        if (!model.isOpen() || model.isReadOnly())
            return false;
        try {
            Files.createDirectories(path);
            model.fireTreeRefresh();
            return true;
        } catch (IOException ex) {
            showError("Create Directory", ex);
            return false;
        }
    }

    /**
     * Deletes a file or empty directory.
     */
    public boolean delete(FileNode node) {
        if (!model.isOpen() || model.isReadOnly() || node == null)
            return false;
        try {
            if (node.isDirectory()) {
                deleteRecursive(node.getPath());
            } else {
                Files.deleteIfExists(node.getPath());
            }
            model.setSelectedFile(null);
            model.fireTreeRefresh();
            return true;
        } catch (IOException ex) {
            showError("Delete", ex);
            return false;
        }
    }

    /**
     * Renames/moves a file or directory from oldPath to newPath.
     */
    public boolean rename(Path oldPath, Path newPath) {
        if (!model.isOpen() || model.isReadOnly())
            return false;
        try {
            Files.move(oldPath, newPath);
            model.fireTreeRefresh();
            return true;
        } catch (IOException ex) {
            showError("Rename", ex);
            return false;
        }
    }

    /**
     * Writes text content to an existing file.
     */
    public boolean saveFile(Path path, String content) {
        if (!model.isOpen() || model.isReadOnly())
            return false;
        try {
            Files.writeString(path, content,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException ex) {
            showError("Save File", ex);
            return false;
        }
    }

    /**
     * Copies the file/directory at sourcePath into the targetDir.
     */
    public boolean copy(Path sourcePath, Path targetDir) {
        if (!model.isOpen() || model.isReadOnly())
            return false;
        try {
            Path fileName = sourcePath.getFileName();
            if (fileName == null)
                return false;
            Path target = targetDir.resolve(fileName.toString());
            Files.copy(sourcePath, target);
            model.fireTreeRefresh();
            return true;
        } catch (IOException ex) {
            showError("Copy", ex);
            return false;
        }
    }

    /**
     * Extracts a file from the archive to a local directory.
     */
    public boolean extractTo(Path archiveFilePath, Path localDestination) {
        try {
            byte[] data = Files.readAllBytes(archiveFilePath);
            Files.write(localDestination, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException ex) {
            showError("Extract", ex);
            return false;
        }
    }

    private void deleteRecursive(Path root) throws IOException {
        if (Files.isDirectory(root)) {
            try (var stream = Files.newDirectoryStream(root)) {
                for (Path child : stream) {
                    deleteRecursive(child);
                }
            }
        }
        Files.deleteIfExists(root);
    }

    private void showError(String operation, Exception ex) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                operation + " failed: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE));
    }
}
