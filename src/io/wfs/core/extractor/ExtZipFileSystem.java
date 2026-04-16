package io.wfs.core.extractor;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class ExtZipFileSystem extends FileSystem {

    private final ExtZipFsProvider provider;
    private final Path archivePath;
    private final Path tempRoot;
    private final boolean readOnly;
    private final AtomicBoolean open;
    private volatile Thread shutdownHook;

    ExtZipFileSystem(ExtZipFsProvider provider, Path archivePath, Path tempRoot, boolean readOnly) {
        this.provider = provider;
        this.archivePath = archivePath;
        this.tempRoot = tempRoot;
        this.readOnly = readOnly;
        this.open = new AtomicBoolean(true);
    }

    void installShutdownHook() {
        shutdownHook = new Thread(this::closeQuietly, "extzipfs-shutdown-" + archivePath.getFileName());
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // Shutdown already started.
        }
    }

    Path getArchivePath() {
        return archivePath;
    }

    Path getTempRoot() {
        return tempRoot;
    }

    ExtZipPath wrap(Path delegate) {
        return new ExtZipPath(this, delegate);
    }

    void ensureWritable() {
        if (readOnly) {
            throw new UnsupportedOperationException("File system is read-only: " + archivePath);
        }
        if (!isOpen()) {
            throw new FileSystemNotFoundException("File system is closed: " + archivePath);
        }
    }

    void ensureWritableFor(Set<? extends OpenOption> options) {
        if (!isOpen()) {
            throw new FileSystemNotFoundException("File system is closed: " + archivePath);
        }
        if (readOnly && options != null) {
            for (OpenOption option : options) {
                String name = String.valueOf(option).toUpperCase();
                if (name.contains("WRITE") || name.contains("APPEND") || name.contains("CREATE") || name.contains("TRUNCATE")) {
                    throw new UnsupportedOperationException("File system is read-only: " + archivePath);
                }
            }
        }
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        if (!open.compareAndSet(true, false)) {
            return;
        }

        IOException failure = null;
        try {
            if (!readOnly) {
                ExtZipFsIO.writeDirectoryToArchive(tempRoot, archivePath);
            }
        } catch (Exception ex) {
            failure = asIoException(ex);
        }

        try {
            ExtZipFsIO.deleteRecursively(tempRoot);
        } catch (IOException ex) {
            if (failure == null) {
                failure = ex;
            } else {
                failure.addSuppressed(ex);
            }
        }

        provider.unregister(this);

        Thread hook = shutdownHook;
        if (hook != null && hook != Thread.currentThread()) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // Shutdown already in progress.
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return List.of(new ExtZipPath(this, tempRoot));
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        try {
            return List.of(Files.getFileStore(tempRoot));
        } catch (IOException ex) {
            return List.of();
        }
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return tempRoot.getFileSystem().supportedFileAttributeViews();
    }

    @Override
    public Path getPath(String first, String... more) {
        Path relativePath = Path.of(first, more);
        String relativeText = relativePath.toString().replace(java.io.File.separatorChar, '/');
        String trimmed = relativeText.startsWith("/") ? relativeText.substring(1) : relativeText;
        Path delegate = trimmed.isEmpty() ? tempRoot : tempRoot.resolve(trimmed).normalize();

        if (!delegate.startsWith(tempRoot)) {
            throw new IllegalArgumentException("Path escapes archive root: " + relativeText);
        }

        return new ExtZipPath(this, delegate);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
        return path -> matcher.matches(Path.of(path.toString()));
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return tempRoot.getFileSystem().getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() throws IOException {
        return tempRoot.getFileSystem().newWatchService();
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
            // Best effort on shutdown.
        }
    }

    private IOException asIoException(Exception ex) {
        if (ex instanceof IOException ioEx) {
            return ioEx;
        }
        return new IOException("Failed to persist archive: " + ex.getMessage(), ex);
    }
}
