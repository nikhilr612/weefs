package io.wfs.core.extractor;

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

final class ExtZipPath implements Path {

    private final ExtZipFileSystem fileSystem;
    private final Path delegate;

    ExtZipPath(ExtZipFileSystem fileSystem, Path delegate) {
        this.fileSystem = fileSystem;
        this.delegate = delegate.normalize();
    }

    ExtZipFileSystem getExtZipFileSystem() {
        return fileSystem;
    }

    Path getDelegate() {
        return delegate;
    }

    private ExtZipPath requireSameProvider(Path path) {
        if (!(path instanceof ExtZipPath)) {
            throw new ProviderMismatchException("Path is not an xzip path: " + path);
        }
        ExtZipPath other = (ExtZipPath) path;
        if (fileSystem != other.fileSystem) {
            throw new ProviderMismatchException("Paths belong to different xzip file systems");
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
    public ExtZipFileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return true;
    }

    @Override
    public Path getRoot() {
        return new ExtZipPath(fileSystem, fileSystem.getTempRoot());
    }

    @Override
    public Path getFileName() {
        Path fileName = delegate.getFileName();
        if (fileName == null) {
            return null;
        }
        return new ExtZipPath(fileSystem, fileName);
    }

    @Override
    public Path getParent() {
        Path parent = delegate.getParent();
        if (parent == null) {
            return null;
        }
        return new ExtZipPath(fileSystem, parent);
    }

    @Override
    public int getNameCount() {
        return delegate.getNameCount();
    }

    @Override
    public Path getName(int index) {
        return new ExtZipPath(fileSystem, delegate.getName(index));
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        return new ExtZipPath(fileSystem, delegate.subpath(beginIndex, endIndex));
    }

    @Override
    public boolean startsWith(Path other) {
        ExtZipPath ext = requireSameProvider(other);
        return delegate.startsWith(ext.delegate);
    }

    @Override
    public boolean startsWith(String other) {
        return toVirtualString().startsWith(other.replace(File.separatorChar, '/'));
    }

    @Override
    public boolean endsWith(Path other) {
        ExtZipPath ext = requireSameProvider(other);
        return delegate.endsWith(ext.delegate);
    }

    @Override
    public boolean endsWith(String other) {
        return toVirtualString().endsWith(other.replace(File.separatorChar, '/'));
    }

    @Override
    public Path normalize() {
        return new ExtZipPath(fileSystem, delegate.normalize());
    }

    @Override
    public Path resolve(Path other) {
        ExtZipPath ext = requireSameProvider(other);
        return new ExtZipPath(fileSystem, delegate.resolve(ext.delegate));
    }

    @Override
    public Path resolve(String other) {
        return new ExtZipPath(fileSystem, delegate.resolve(other));
    }

    @Override
    public Path resolveSibling(Path other) {
        ExtZipPath ext = requireSameProvider(other);
        return new ExtZipPath(fileSystem, delegate.resolveSibling(ext.delegate));
    }

    @Override
    public Path resolveSibling(String other) {
        return new ExtZipPath(fileSystem, delegate.resolveSibling(other));
    }

    @Override
    public Path relativize(Path other) {
        ExtZipPath ext = requireSameProvider(other);
        return new ExtZipPath(fileSystem, delegate.relativize(ext.delegate));
    }

    @Override
    public URI toUri() {
        String archive = fileSystem.getArchivePath().toUri().toString();
        String internal = toVirtualString();
        if ("/".equals(internal)) {
            return URI.create("xzip:" + archive + "!/");
        }
        return URI.create("xzip:" + archive + "!" + internal);
    }

    @Override
    public Path toAbsolutePath() {
        return new ExtZipPath(fileSystem, delegate.toAbsolutePath());
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return new ExtZipPath(fileSystem, delegate.toRealPath(options));
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
        return new ExtZipPathIterator(fileSystem, iterator);
    }

    @Override
    public int compareTo(Path other) {
        ExtZipPath ext = requireSameProvider(other);
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
        if (!(obj instanceof ExtZipPath)) {
            return false;
        }
        ExtZipPath other = (ExtZipPath) obj;
        return fileSystem == other.fileSystem && Objects.equals(delegate, other.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(fileSystem), delegate);
    }
}
