package io.wfs.core.nfs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * NFS path implementation compliant with java.nio.file.Path interface.
 * Represents a path within an NFS-mounted file system.
 * Immutable and thread-safe.
 */
final class NfsPath implements Path {

    private final NfsFileSystem fileSystem;
    private final String path;

    NfsPath(NfsFileSystem fileSystem, String path) {
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.path = normalizePath(path);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        // Normalize separators
        String normalized = path.replace("\\", "/");
        // Remove trailing slashes except for root
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Override
    public NfsFileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return path.startsWith("/");
    }

    @Override
    public Path getRoot() {
        return new NfsPath(fileSystem, "/");
    }

    @Override
    public Path getFileName() {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return null;
        }
        return new NfsPath(fileSystem, path.substring(lastSlash + 1));
    }

    @Override
    public Path getParent() {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return null;
        }
        return new NfsPath(fileSystem, path.substring(0, lastSlash));
    }

    @Override
    public int getNameCount() {
        if (path.equals("/")) {
            return 0;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (normalized.isEmpty()) {
            return 0;
        }
        return normalized.split("/").length;
    }

    @Override
    public Path getName(int index) {
        if (index < 0 || index >= getNameCount()) {
            throw new IllegalArgumentException("Invalid name index: " + index);
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = normalized.split("/");
        return new NfsPath(fileSystem, parts[index]);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        int count = getNameCount();
        if (beginIndex < 0 || endIndex > count || beginIndex >= endIndex) {
            throw new IllegalArgumentException("Invalid indices");
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = normalized.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = beginIndex; i < endIndex; i++) {
            if (i > beginIndex) sb.append("/");
            sb.append(parts[i]);
        }
        return new NfsPath(fileSystem, sb.toString());
    }

    @Override
    public boolean startsWith(Path other) {
        if (!(other instanceof NfsPath)) {
            return false;
        }
        return path.startsWith(((NfsPath) other).path);
    }

    @Override
    public boolean startsWith(String other) {
        return path.startsWith(normalizePath(other));
    }

    @Override
    public boolean endsWith(Path other) {
        if (!(other instanceof NfsPath)) {
            return false;
        }
        return path.endsWith(((NfsPath) other).path);
    }

    @Override
    public boolean endsWith(String other) {
        return path.endsWith(normalizePath(other));
    }

    @Override
    public Path normalize() {
        return this; // Already normalized
    }

    @Override
    public Path resolve(Path other) {
        if (!(other instanceof NfsPath)) {
            throw new IllegalArgumentException("Cannot resolve non-NFS path");
        }
        String otherPath = ((NfsPath) other).path;
        if (otherPath.startsWith("/")) {
            return new NfsPath(fileSystem, otherPath);
        }
        if (path.equals("/")) {
            return new NfsPath(fileSystem, "/" + otherPath);
        }
        return new NfsPath(fileSystem, path + "/" + otherPath);
    }

    @Override
    public Path resolve(String other) {
        return resolve(new NfsPath(fileSystem, other));
    }

    @Override
    public Path resolveSibling(Path other) {
        Path parent = getParent();
        if (parent == null) {
            return other;
        }
        return parent.resolve(other);
    }

    @Override
    public Path resolveSibling(String other) {
        return resolveSibling(new NfsPath(fileSystem, other));
    }

    @Override
    public Path relativize(Path other) {
        throw new UnsupportedOperationException("Relativization not supported for NFS paths");
    }

    @Override
    public URI toUri() {
        throw new UnsupportedOperationException("URI conversion not supported for NFS paths");
    }

    @Override
    public Path toAbsolutePath() {
        return isAbsolute() ? this : new NfsPath(fileSystem, "/" + path);
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        // NFS doesn't support symbolic links in this implementation
        return toAbsolutePath();
    }

    @Override
    public File toFile() {
        throw new UnsupportedOperationException("File conversion not supported for NFS paths");
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events,
                             WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("Watch service not supported for NFS paths");
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        throw new UnsupportedOperationException("Watch service not supported for NFS paths");
    }

    @Override
    public Iterator<Path> iterator() {
        List<Path> parts = new ArrayList<>();
        Path current = this;
        while (current != null && !current.toString().equals("/")) {
            parts.add(0, current.getFileName());
            current = current.getParent();
        }
        return parts.iterator();
    }

    @Override
    public int compareTo(Path other) {
        if (!(other instanceof NfsPath)) {
            throw new ClassCastException("Cannot compare with non-NFS path");
        }
        return path.compareTo(((NfsPath) other).path);
    }

    @Override
    public String toString() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NfsPath)) return false;
        NfsPath nfsPath = (NfsPath) o;
        return Objects.equals(path, nfsPath.path) &&
                Objects.equals(fileSystem, nfsPath.fileSystem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, fileSystem);
    }
}
