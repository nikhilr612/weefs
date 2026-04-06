package io.wfs.ui.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Immutable value object representing a single file or directory entry
 * in a mounted archive. Used as the user-object inside JTree nodes.
 */
public final class FileNode implements Comparable<FileNode> {

    private final Path path;
    private final String displayName;
    private final boolean directory;
    private final long size;

    public FileNode(Path path) {
        this.path = Objects.requireNonNull(path);
        Path fileName = path.getFileName();
        this.displayName = (fileName != null) ? fileName.toString() : "/";
        boolean isDir = false;
        long fileSize = 0L;
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            isDir = attrs.isDirectory();
            fileSize = attrs.size();
        } catch (IOException ignored) {
            // Best effort — treat as file with size 0
        }
        this.directory = isDir;
        this.size = fileSize;
    }

    public FileNode(Path path, String displayName, boolean directory) {
        this.path = Objects.requireNonNull(path);
        this.displayName = Objects.requireNonNull(displayName);
        this.directory = directory;
        long fileSize = 0L;
        if (!directory) {
            try {
                fileSize = Files.size(path);
            } catch (IOException ignored) {
                // best effort
            }
        }
        this.size = fileSize;
    }

    public Path getPath() {
        return path;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSize() {
        return size;
    }

    public String getExtension() {
        String name = displayName;
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase();
        }
        return "";
    }

    @Override
    public int compareTo(FileNode other) {
        // Directories first, then alphabetical
        if (this.directory != other.directory) {
            return this.directory ? -1 : 1;
        }
        return this.displayName.compareToIgnoreCase(other.displayName);
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof FileNode))
            return false;
        FileNode other = (FileNode) obj;
        return Objects.equals(path, other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }
}
