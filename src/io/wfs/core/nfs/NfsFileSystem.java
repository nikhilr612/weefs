package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NFS-backed FileSystem implementation extending the standard FileSystem SPI.
 * Provides NFS access through Java NIO.2 interfaces (Adapter pattern).
 * Manages connection state and delegates I/O to NfsIO utility class.
 */
final class NfsFileSystem extends FileSystem {

    private final NfsFsProvider provider;
    private final NfsConnectionConfig config;
    private final AtomicBoolean open;
    private volatile Thread shutdownHook;

    NfsFileSystem(NfsFsProvider provider, NfsConnectionConfig config) {
        this.provider = provider;
        this.config = config;
        this.open = new AtomicBoolean(true);
    }

    void installShutdownHook() {
        shutdownHook = new Thread(
                this::closeQuietly,
                "nfs-shutdown-" + config.getHost() + "_" + config.getPort()
        );
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // Shutdown already started
        }
    }

    NfsConnectionConfig getConfig() {
        return config;
    }

    NfsPath wrap(String pathStr) {
        return new NfsPath(this, pathStr);
    }

    void ensureOpen() {
        if (!isOpen()) {
            throw new FileSystemNotFoundException("NFS file system is closed: " + config);
        }
    }

    void ensureWritable() {
        ensureOpen();
        if (isReadOnly()) {
            throw new UnsupportedOperationException("NFS mount is read-only: " + config);
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
        try {
            NfsIO.disconnect(config);
        } finally {
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // Shutdown already in progress
                }
            }
        }
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public boolean isReadOnly() {
        return config.isReadOnly();
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(new NfsPath(this, "/"));
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return Collections.emptyList();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Collections.emptySet();
    }

    @Override
    public Path getPath(String first, String... more) {
        StringBuilder sb = new StringBuilder(first);
        for (String part : more) {
            sb.append("/").append(part);
        }
        return wrap(sb.toString());
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return FileSystems.getDefault().getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("Watch service not supported for NFS");
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
            // Cleanup during shutdown
        }
    }
}
