package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
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

    private final FileSystemProvider provider;
    private final NfsSftpFsProvider sftpProvider;
    private final NfsSftpConfig config;
    private final NfsConnectionConfig legacyConfig;
    private final boolean readOnly;
    private final AtomicBoolean open;
    private volatile Thread shutdownHook;

    NfsFileSystem(NfsSftpFsProvider provider, NfsSftpConfig config, boolean readOnly) {
        this.provider = provider;
        this.sftpProvider = provider;
        this.config = config;
        this.legacyConfig = null;
        this.readOnly = readOnly;
        this.open = new AtomicBoolean(true);
    }

    NfsFileSystem(NfsFsProvider provider, NfsConnectionConfig config) {
        this.provider = provider;
        this.sftpProvider = null;
        this.config = null;
        this.legacyConfig = config;
        this.readOnly = config.isReadOnly();
        this.open = new AtomicBoolean(true);
    }

    void installShutdownHook() {
        String host = config != null ? config.host() : legacyConfig.getHost();
        shutdownHook = new Thread(this::closeQuietly, "weefs-sftp-shutdown-" + host);
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down.
        }
    }

    NfsSftpConfig config() {
        if (config == null) {
            throw new IllegalStateException("SFTP config unavailable for legacy NFS file system");
        }
        return config;
    }

    boolean hasSftpConfig() {
        return config != null;
    }

    NfsConnectionConfig getConfig() {
        if (legacyConfig != null) {
            return legacyConfig;
        }
        return new NfsConnectionConfig(
                config.host(),
                config.port(),
                config.remoteRoot(),
                config.remoteRoot(),
                30,
                readOnly);
    }

    String toRemotePath(NfsPath path) {
        if (config == null) {
            return path.toString();
        }
        String virtual = path.toString();
        if ("/".equals(virtual)) {
            return config.remoteRoot();
        }
        String suffix = virtual.startsWith("/") ? virtual.substring(1) : virtual;
        return NfsSftpFsIO.join(config.remoteRoot(), suffix);
    }

    void ensureWritable() {
        if (readOnly) {
            String host = config != null ? config.host() : legacyConfig.getHost();
            throw new UnsupportedOperationException("File system is read-only: " + host);
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
                    String host = config != null ? config.host() : legacyConfig.getHost();
                    throw new UnsupportedOperationException("File system is read-only: " + host);
                }
            }
        }

    void ensureOpen() {
        if (!isOpen()) {
            String host = config != null ? config.host() : legacyConfig.getHost();
            throw new FileSystemNotFoundException("File system is closed: " + host);
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

        if (sftpProvider != null) {
            sftpProvider.unregister(this);
        }
        if (legacyConfig != null) {
            NfsIO.disconnect(legacyConfig);
        }

        Thread hook = shutdownHook;
        if (hook != null && hook != Thread.currentThread()) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // Shutdown in progress.
            }
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
        return List.of(new NfsPath(this, "/"));
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return List.of();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Set.of("basic");
    }

    @Override
    public Path getPath(String first, String... more) {
        String joined = Path.of(first, more).toString().replace('\\', '/');
        if (joined.isBlank() || "/".equals(joined)) {
            return new NfsPath(this, "/");
        }

        String normalized = joined.startsWith("/") ? joined : "/" + joined;
        normalized = NfsSftpFsIO.normalizeRemotePath(normalized);
        return new NfsPath(this, normalized);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
        return path -> matcher.matches(Path.of(path.toString().replace('\\', '/')));
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("User principal lookup is not supported for weefs SFTP");
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("Watch service is not supported for weefs SFTP");
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
            // Best effort cleanup.
        }
    }
}
