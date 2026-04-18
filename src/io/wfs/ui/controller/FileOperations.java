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
 * Implements {@link IFileOperations} for polymorphic use by controllers.
 * All operations post errors to the EDT via dialogs.
 */
public final class FileOperations implements IFileOperations {

    private final ArchiveModel model;

    public FileOperations(ArchiveModel model) {
        this.model = model;
    }

    /**
     * Creates a new file at the given path with the supplied text content.
     */
    @Override
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
    @Override
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
     * Deletes a file or directory at the given path.
     */
    @Override
    public boolean delete(Path path) {
        if (!model.isOpen() || model.isReadOnly() || path == null)
            return false;
        try {
            if (Files.isDirectory(path)) {
                deleteRecursive(path);
            } else {
                Files.deleteIfExists(path);
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
     * Deletes a file or empty directory represented by a FileNode.
     */
    public boolean delete(FileNode node) {
        if (node == null)
            return false;
        return delete(node.getPath());
    }

    /**
     * Renames/moves a file or directory from oldPath to newPath.
     */
    @Override
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
    @Override
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
     * Copies a single file at {@code sourcePath} into {@code targetDir}.
     * Directories are not supported; callers should iterate children manually.
     */
    @Override
    public boolean copy(Path sourcePath, Path targetDir) {
        if (!model.isOpen() || model.isReadOnly())
            return false;
        if (Files.isDirectory(sourcePath)) {
            showError("Copy", new UnsupportedOperationException(
                    "Recursive directory copy is not supported; select individual files."));
            return false;
        }
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
    @Override
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
