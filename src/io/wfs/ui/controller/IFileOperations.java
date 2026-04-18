package io.wfs.ui.controller;

import java.nio.file.Path;

/**
 * Unified interface for file-level operations across different
 * file system backends (archive, NFS, future providers).
 * Follows the Strategy pattern — controllers depend on this interface
 * rather than concrete implementations (OCP + DIP).
 */
public interface IFileOperations {

    /**
     * Creates a new file with the given text content.
     *
     * @param path    the path for the new file
     * @param content the initial text content
     * @return true if the file was created successfully
     */
    boolean createFile(Path path, String content);

    /**
     * Creates a new directory.
     *
     * @param path the path for the new directory
     * @return true if the directory was created successfully
     */
    boolean createDirectory(Path path);

    /**
     * Deletes the file or directory at the given path.
     *
     * @param path the path to delete
     * @return true if deletion succeeded
     */
    boolean delete(Path path);

    /**
     * Renames or moves a file from oldPath to newPath.
     *
     * @param oldPath the current path
     * @param newPath the desired new path
     * @return true if the rename succeeded
     */
    boolean rename(Path oldPath, Path newPath);

    /**
     * Writes text content to an existing file.
     *
     * @param path    the file path to write to
     * @param content the new text content
     * @return true if the save succeeded
     */
    boolean saveFile(Path path, String content);

    /**
     * Extracts a file to a local destination path.
     *
     * @param sourcePath  the source file path
     * @param destination the local destination path
     * @return true if extraction succeeded
     */
    boolean extractTo(Path sourcePath, Path destination);

    /**
     * Copies a single file into a target directory.
     *
     * @param sourcePath the source file
     * @param targetDir  the destination directory
     * @return true if the copy succeeded
     */
    boolean copy(Path sourcePath, Path targetDir);
}
