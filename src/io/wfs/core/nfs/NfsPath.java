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
    private final Path delegate;

    NfsPath(NfsFileSystem fileSystem, Path delegate) {
        this.fileSystem = fileSystem;
        this.delegate = delegate.normalize();
    }

    NfsFileSystem getNfsFileSystem() {
        return fileSystem;
    }

    Path getDelegate() {
        return delegate;
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

    private boolean isUnderTempRoot() {
        return delegate.isAbsolute() && delegate.startsWith(fileSystem.getTempRoot());
    }

    private String toVirtualString() {
        if (isUnderTempRoot()) {
            Path relative = fileSystem.getTempRoot().relativize(delegate);
            String value = relative.toString().replace(File.separatorChar, '/');
            return value.isEmpty() ? "/" : "/" + value;
        }
        String value = delegate.toString().replace(File.separatorChar, '/');
        return value.isEmpty() ? "/" : value;
    }

    @Override
    public NfsFileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return true;
    }

    @Override
    public Path getRoot() {
        return new NfsPath(fileSystem, fileSystem.getTempRoot());
    }

    @Override
    public Path getFileName() {
        Path fileName = delegate.getFileName();
        if (fileName == null) {
            return null;
        }
        return new NfsPath(fileSystem, fileName);
    }

    @Override
    public Path getParent() {
        Path parent = delegate.getParent();
        if (parent == null) {
            return null;
        }
        return new NfsPath(fileSystem, parent);
    }

    @Override
    public int getNameCount() {
        return delegate.getNameCount();
    }

    @Override
    public Path getName(int index) {
        return new NfsPath(fileSystem, delegate.getName(index));
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        return new NfsPath(fileSystem, delegate.subpath(beginIndex, endIndex));
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
        return new NfsPath(fileSystem, delegate.normalize());
    }

    @Override
    public Path resolve(Path other) {
        NfsPath ext = requireSameProvider(other);
        return new NfsPath(fileSystem, delegate.resolve(ext.delegate));
    }

    @Override
    public Path resolve(String other) {
        return new NfsPath(fileSystem, delegate.resolve(other));
    }

    @Override
    public Path resolveSibling(Path other) {
        NfsPath ext = requireSameProvider(other);
        return new NfsPath(fileSystem, delegate.resolveSibling(ext.delegate));
    }

    @Override
    public Path resolveSibling(String other) {
        return new NfsPath(fileSystem, delegate.resolveSibling(other));
    }

    @Override
    public Path relativize(Path other) {
        NfsPath ext = requireSameProvider(other);
        return new NfsPath(fileSystem, delegate.relativize(ext.delegate));
    }

    @Override
    public URI toUri() {
        NfsSftpConfig cfg = fileSystem.config();
        String internal = toVirtualString();
        String uriPath = cfg.remoteRoot();
        if ("/".equals(internal)) {
            return URI.create("weefs://" + cfg.host() + uriPath + "?auth=" + cfg.authEnvVar());
        }
        String suffix = internal.startsWith("/") ? internal.substring(1) : internal;
        String joined = uriPath.endsWith("/") ? uriPath + suffix : uriPath + "/" + suffix;
        return URI.create("weefs://" + cfg.host() + joined + "?auth=" + cfg.authEnvVar());
    }

    @Override
    public Path toAbsolutePath() {
        return new NfsPath(fileSystem, delegate.toAbsolutePath());
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return new NfsPath(fileSystem, delegate.toRealPath(options));
    }

    @Override
    public File toFile() {
        return delegate.toFile();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
            throws IOException {
        return delegate.register(watcher, events, modifiers);
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
}
