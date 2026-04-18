package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Immutable value object representing file metadata from NFS.
 * Similar to FileNode but specific to NFS semantics.
 */
public final class NfsFileInfo {

    private final String name;
    private final String fullPath;
    private final boolean directory;
    private final long size;
    private final long lastModified;

    public NfsFileInfo(String name, String fullPath, boolean directory, long size, long lastModified) {
        this.name = Objects.requireNonNull(name);
        this.fullPath = Objects.requireNonNull(fullPath);
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
    }

    /**
     * Factory method to create NfsFileInfo from a local Path.
     * Used when simulating NFS with local FileSystem.
     */
    static NfsFileInfo fromPath(Path path) throws IOException {
        String name = path.getFileName().toString();
        String fullPath = path.toString();
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        return new NfsFileInfo(
                name,
                fullPath,
                attrs.isDirectory(),
                attrs.size(),
                attrs.lastModifiedTime().toMillis()
        );
    }

    public String getName() {
        return name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getExtension() {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase();
        }
        return "";
    }

    @Override
    public String toString() {
        return name + (directory ? "/" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NfsFileInfo)) return false;
        NfsFileInfo that = (NfsFileInfo) o;
        return Objects.equals(fullPath, that.fullPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullPath);
    }
}
