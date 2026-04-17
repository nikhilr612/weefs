package io.wfs.core.nfs;

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

final class NfsFileSystem extends FileSystem {

    private final NfsSftpFsProvider provider;
    private final NfsSftpConfig config;
    private final Path tempRoot;
    private final boolean readOnly;
    private final AtomicBoolean open;
    private volatile Thread shutdownHook;

    NfsFileSystem(NfsSftpFsProvider provider, NfsSftpConfig config, Path tempRoot, boolean readOnly) {
        this.provider = provider;
        this.config = config;
        this.tempRoot = tempRoot;
        this.readOnly = readOnly;
        this.open = new AtomicBoolean(true);
    }

    void installShutdownHook() {
        shutdownHook = new Thread(this::closeQuietly, "weefs-sftp-shutdown-" + config.host());
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down.
        }
    }

    NfsSftpConfig config() {
        return config;
    }

    Path getTempRoot() {
        return tempRoot;
    }

    NfsPath wrap(Path delegate) {
        return new NfsPath(this, delegate);
    }

    void ensureWritable() {
        if (readOnly) {
            throw new UnsupportedOperationException("File system is read-only: " + config.host());
        }
        ensureOpen();
    }

    void ensureWritableFor(Set<? extends OpenOption> options) {
        ensureOpen();
        if (!readOnly || options == null) {
            return;
        }
        for (OpenOption option : options) {
            String name = String.valueOf(option).toUpperCase();
            if (name.contains("WRITE") || name.contains("APPEND") || name.contains("CREATE")
                    || name.contains("TRUNCATE") || name.contains("DELETE")) {
                throw new UnsupportedOperationException("File system is read-only: " + config.host());
            }
        }
    }

    void syncFile(Path localPath) throws IOException {
        ensureOpen();
        NfsSftpFsIO.uploadFile(config, tempRoot, localPath);
    }

    void createRemoteDirectory(Path localPath) throws IOException {
        ensureOpen();
        NfsSftpFsIO.createRemoteDirectory(config, tempRoot, localPath);
    }

    void deleteRemote(Path localPath, boolean wasDirectory) throws IOException {
        ensureOpen();
        NfsSftpFsIO.deleteRemotePath(config, tempRoot, localPath, wasDirectory);
    }

    private void ensureOpen() {
        if (!isOpen()) {
            throw new FileSystemNotFoundException("File system is closed: " + config.host());
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
            if (Files.exists(tempRoot)) {
                try (var walk = Files.walk(tempRoot)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                }
            }
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioEx) {
                failure = ioEx;
            } else {
                failure = new IOException("Failed to cleanup temporary files", ex);
            }
        } catch (IOException ex) {
            failure = ex;
        }

        provider.unregister(this);

        Thread hook = shutdownHook;
        if (hook != null && hook != Thread.currentThread()) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // Shutdown in progress.
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
        return List.of(new NfsPath(this, tempRoot));
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
            throw new IllegalArgumentException("Path escapes mounted root: " + relativeText);
        }

        return new NfsPath(this, delegate);
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
            // Best effort cleanup.
        }
    }
}
