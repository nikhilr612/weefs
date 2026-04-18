package io.wfs.core.nfs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.Objects;

final class NfsPath implements Path {

    private final NfsFileSystem fileSystem;
    private final String virtualPath;
    private final Path delegate;

    NfsPath(NfsFileSystem fileSystem, String virtualPath) {
        this.fileSystem = fileSystem;
        this.virtualPath = normalizeVirtualPath(virtualPath);
        this.delegate = Path.of(this.virtualPath);
    }

    NfsFileSystem getNfsFileSystem() {
        return fileSystem;
    }

    String virtualPath() {
        return virtualPath;
    }

    private NfsPath requireSameProvider(Path path) {
        if (!(path instanceof NfsPath)) {
            throw new ProviderMismatchException("Path is not a weefs path: " + path);
        }
        NfsPath other = (NfsPath) path;
        if (fileSystem != other.fileSystem) {
            throw new ProviderMismatchException("Paths belong to different weefs file systems");
        }
        return other;
    }

    private String toVirtualString() {
        return virtualPath;
    }

    @Override
    public NfsFileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return delegate.isAbsolute();
    }

    @Override
    public Path getRoot() {
        Path root = delegate.getRoot();
        return root == null ? null : new NfsPath(fileSystem, root.toString());
    }

    @Override
    public Path getFileName() {
        Path fileName = delegate.getFileName();
        if (fileName == null) {
            return null;
        }
        return new NfsPath(fileSystem, fileName.toString());
    }

    @Override
    public Path getParent() {
        Path parent = delegate.getParent();
        if (parent == null) {
            return null;
        }
        return new NfsPath(fileSystem, parent.toString());
    }

    @Override
    public int getNameCount() {
        return delegate.getNameCount();
    }

    @Override
    public Path getName(int index) {
        return new NfsPath(fileSystem, delegate.getName(index).toString());
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        return new NfsPath(fileSystem, delegate.subpath(beginIndex, endIndex).toString());
    }

    @Override
    public boolean startsWith(Path other) {
        NfsPath ext = requireSameProvider(other);
        return delegate.startsWith(ext.delegate);
    }

    @Override
    public boolean startsWith(String other) {
        return toVirtualString().startsWith(other.replace(File.separatorChar, '/'));
    }

    @Override
    public boolean endsWith(Path other) {
        NfsPath ext = requireSameProvider(other);
        return delegate.endsWith(ext.delegate);
    }

    @Override
    public boolean endsWith(String other) {
        return toVirtualString().endsWith(other.replace(File.separatorChar, '/'));
    }

    @Override
    public Path normalize() {
        return new NfsPath(fileSystem, delegate.normalize().toString());
    }

    @Override
    public Path resolve(Path other) {
        NfsPath ext = requireSameProvider(other);
        return new NfsPath(fileSystem, delegate.resolve(ext.delegate).toString());
    }

    @Override
    public Path resolve(String other) {
        return new NfsPath(fileSystem, delegate.resolve(other).toString());
    }

    @Override
    public Path resolveSibling(Path other) {
        NfsPath ext = requireSameProvider(other);
        return new NfsPath(fileSystem, delegate.resolveSibling(ext.delegate).toString());
    }

    @Override
    public Path resolveSibling(String other) {
        return new NfsPath(fileSystem, delegate.resolveSibling(other).toString());
    }

    @Override
    public Path relativize(Path other) {
        if (!fileSystem.hasSftpConfig()) {
            throw new UnsupportedOperationException("Relativization not supported for legacy NFS paths");
        }
        NfsPath ext = requireSameProvider(other);
        return new NfsPath(fileSystem, delegate.relativize(ext.delegate).toString());
    }

    @Override
    public URI toUri() {
        if (!fileSystem.hasSftpConfig()) {
            throw new UnsupportedOperationException("URI conversion not supported for legacy NFS paths");
        }
        NfsSftpConfig cfg = fileSystem.config();
        String internal = toVirtualString();
        String uriPath = cfg.remoteRoot();
        String authority = cfg.username() + "@" + cfg.host();
        if ("/".equals(internal)) {
            return URI.create("weefs://" + authority + uriPath + "?auth=" + cfg.authEnvVar());
        }
        String suffix = internal.startsWith("/") ? internal.substring(1) : internal;
        String joined = uriPath.endsWith("/") ? uriPath + suffix : uriPath + "/" + suffix;
        return URI.create("weefs://" + authority + joined + "?auth=" + cfg.authEnvVar());
    }

    @Override
    public Path toAbsolutePath() {
        return isAbsolute() ? this : new NfsPath(fileSystem, "/" + virtualPath);
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return toAbsolutePath();
    }

    @Override
    public File toFile() {
        throw new UnsupportedOperationException("NFS paths do not map to local files");
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
            throws IOException {
        throw new UnsupportedOperationException("Watch service is not supported for weefs SFTP");
    }

    @Override
    public Iterator<Path> iterator() {
        Iterator<Path> iterator = delegate.iterator();
        return new NfsPathIterator(fileSystem, iterator);
    }

    @Override
    public int compareTo(Path other) {
        NfsPath ext = requireSameProvider(other);
        return delegate.compareTo(ext.delegate);
    }

    @Override
    public String toString() {
        return toVirtualString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NfsPath)) {
            return false;
        }
        NfsPath other = (NfsPath) obj;
        return fileSystem == other.fileSystem && Objects.equals(delegate, other.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(fileSystem), delegate);
    }

    private static String normalizeVirtualPath(String raw) {
        String value = raw == null ? "" : raw.replace('\\', '/').trim();
        if (value.isBlank()) {
            return "/";
        }

        boolean absolute = value.startsWith("/");
        String[] parts = value.split("/+");
        String[] normalized = new String[parts.length];
        int count = 0;

        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (count > 0 && !"..".equals(normalized[count - 1])) {
                    count--;
                } else if (!absolute) {
                    normalized[count++] = part;
                }
                continue;
            }
            normalized[count++] = part;
        }

        StringBuilder result = new StringBuilder();
        if (absolute) {
            result.append('/');
        }
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                result.append('/');
            }
            result.append(normalized[i]);
        }

        if (result.length() == 0) {
            return absolute ? "/" : "";
        }
        return result.toString();
    }
}
