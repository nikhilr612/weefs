package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.nio.channels.SeekableByteChannel;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NFS FileSystemProvider extending Java NIO.2 SPI.
 * Enables mounting NFS file systems as providers accessible via
 * FileSystems.newFileSystem().
 * Follows the Provider pattern and maintains cache of active mounts.
 */
public class NfsFsProvider extends FileSystemProvider {

    private static final String SCHEME = "nfs";
    private final Map<String, NfsFileSystem> mounted = new ConcurrentHashMap<>();

    @Override
    public String getScheme() {
        return SCHEME;
    }

    @Override
    public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
        throw new UnsupportedOperationException("Use URI-based mounting for NFS");
    }

    /**
     * Creates and mounts a new NFS file system.
     * URI format:
     * nfs://hostname:port/export/path?mount=/mount/path&readOnly=false&timeout=30
     */
    @Override
    public FileSystem newFileSystem(java.net.URI uri, Map<String, ?> env) throws IOException {
        Objects.requireNonNull(uri);
        if (!SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("URI scheme must be 'nfs', got: " + uri.getScheme());
        }

        NfsConnectionConfig config = parseUri(uri, env);
        String key = configKey(config);

        // Check if already mounted
        if (mounted.containsKey(key)) {
            throw new FileSystemAlreadyExistsException(
                    "NFS already mounted: " + config.getHost() + ":" + config.getPort() + config.getExportPath());
        }

        try {
            // Verify connection before adding to map
            NfsIO.verifyConnection(config);

            NfsFileSystem fs = new NfsFileSystem(this, config);
            NfsFileSystem previous = mounted.putIfAbsent(key, fs);

            if (previous != null) {
                throw new FileSystemAlreadyExistsException(
                        "NFS already mounted: " + key);
            }

            fs.installShutdownHook();
            return fs;
        } catch (IOException ex) {
            throw ex;
        }
    }

    @Override
    public FileSystem getFileSystem(java.net.URI uri) throws FileSystemNotFoundException {
        NfsConnectionConfig config = parseUri(uri, Collections.emptyMap());
        String key = configKey(config);
        NfsFileSystem fs = mounted.get(key);
        if (fs == null) {
            throw new FileSystemNotFoundException("NFS not mounted: " + config);
        }
        return fs;
    }

    @Override
    public Path getPath(java.net.URI uri) {
        if (!SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("URI scheme must be 'nfs'");
        }
        try {
            FileSystem fs = getFileSystem(uri);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            return fs.getPath(path);
        } catch (FileSystemNotFoundException ex) {
            throw new IllegalArgumentException("NFS file system not found", ex);
        }
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
            FileAttribute<?>... attrs) throws IOException {
        NfsPath nfsPath = castPath(path);
        NfsConnectionConfig config = nfsPath.getFileSystem().getConfig();

        boolean write = options.contains(java.nio.file.StandardOpenOption.WRITE)
                || options.contains(java.nio.file.StandardOpenOption.APPEND);
        boolean create = options.contains(java.nio.file.StandardOpenOption.CREATE)
                || options.contains(java.nio.file.StandardOpenOption.CREATE_NEW);

        if (write || create) {
            if (config.isReadOnly()) {
                throw new IOException("NFS mount is read-only");
            }
            return new NfsWritableByteChannel(config, nfsPath.toString());
        }

        byte[] content = NfsIO.readFile(config, nfsPath.toString());
        return new ByteArraySeekableByteChannel(content);
    }

    /**
     * Writable SeekableByteChannel that flushes to NFS on close.
     */
    private static class NfsWritableByteChannel implements SeekableByteChannel {
        private final NfsConnectionConfig config;
        private final String remotePath;
        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        private boolean closed;
        private int position;

        NfsWritableByteChannel(NfsConnectionConfig config, String remotePath) {
            this.config = config;
            this.remotePath = remotePath;
        }

        @Override
        public int read(ByteBuffer dst) {
            throw new UnsupportedOperationException("Write-only channel");
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (closed)
                throw new IOException("Channel is closed");
            int len = src.remaining();
            byte[] data = new byte[len];
            src.get(data);
            buf.write(data);
            position += len;
            return len;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) {
            position = (int) newPosition;
            return this;
        }

        @Override
        public long size() {
            return buf.size();
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                NfsIO.writeFile(config, remotePath, buf.toByteArray());
            }
        }
    }

    /**
     * Simple SeekableByteChannel implementation for byte arrays.
     * Supports reading but not writing (read-only).
     */
    private static class ByteArraySeekableByteChannel implements SeekableByteChannel {
        private final ByteBuffer buffer;
        private boolean closed;

        ByteArraySeekableByteChannel(byte[] data) {
            this.buffer = ByteBuffer.wrap(data.clone());
            this.closed = false;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (closed)
                throw new IOException("Channel is closed");
            if (!buffer.hasRemaining())
                return -1;
            int bytesToRead = Math.min(dst.remaining(), buffer.remaining());
            dst.put(buffer.array(), buffer.position(), bytesToRead);
            buffer.position(buffer.position() + bytesToRead);
            return bytesToRead;
        }

        @Override
        public int write(ByteBuffer src) {
            throw new UnsupportedOperationException("Write not supported for read-only NFS channel");
        }

        @Override
        public long position() {
            return buffer.position();
        }

        @Override
        public SeekableByteChannel position(long newPosition) {
            if (newPosition < 0)
                throw new IllegalArgumentException("Negative position");
            buffer.position((int) newPosition);
            return this;
        }

        @Override
        public long size() {
            return buffer.array().length;
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException("Truncate not supported for read-only NFS channel");
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        NfsPath nfsPath = castPath(dir);
        NfsConnectionConfig config = nfsPath.getFileSystem().getConfig();

        return new DirectoryStream<Path>() {
            @Override
            public Iterator<Path> iterator() {
                try {
                    return NfsIO.listDirectory(config, nfsPath.toString()).stream()
                            .map(info -> nfsPath.resolve(info.getName()))
                            .filter(p -> {
                                try {
                                    return filter == null || filter.accept(p);
                                } catch (IOException ex) {
                                    return false;
                                }
                            })
                            .iterator();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public void close() {
                // No-op for NFS
            }
        };
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        NfsPath nfsPath = castPath(dir);
        nfsPath.getFileSystem().ensureWritable();
        NfsIO.createDirectory(nfsPath.getFileSystem().getConfig(), nfsPath.toString());
    }

    @Override
    public void delete(Path path) throws IOException {
        NfsPath nfsPath = castPath(path);
        nfsPath.getFileSystem().ensureWritable();
        NfsIO.delete(nfsPath.getFileSystem().getConfig(), nfsPath.toString());
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type,
            LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Attribute reading not implemented for NFS");
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
            throws IOException {
        throw new UnsupportedOperationException("Attribute reading not implemented for NFS");
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
            throws IOException {
        throw new UnsupportedOperationException("Attribute setting not implemented for NFS");
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type,
            LinkOption... options) {
        return null;
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        NfsPath nfsSource = castPath(source);
        nfsSource.getFileSystem().ensureWritable();
        NfsPath nfsTarget = castPath(target);
        NfsIO.copy(nfsSource.getFileSystem().getConfig(), nfsSource.toString(), nfsTarget.toString());
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        NfsPath nfsSource = castPath(source);
        nfsSource.getFileSystem().ensureWritable();
        NfsPath nfsTarget = castPath(target);
        NfsIO.rename(nfsSource.getFileSystem().getConfig(), nfsSource.toString(), nfsTarget.toString());
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        if (!(path instanceof NfsPath) || !(path2 instanceof NfsPath)) {
            return false;
        }
        return path.toString().equals(path2.toString());
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        NfsPath nfsPath = castPath(path);
        String name = nfsPath.getFileName().toString();
        return name.startsWith(".");
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException("FileStore not implemented for NFS");
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        NfsPath nfsPath = castPath(path);
        nfsPath.getFileSystem().ensureOpen();
        if (modes != null) {
            for (AccessMode mode : modes) {
                if (mode == AccessMode.WRITE) {
                    nfsPath.getFileSystem().ensureWritable();
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────

    private static NfsPath castPath(Path path) {
        Objects.requireNonNull(path);
        if (!(path instanceof NfsPath)) {
            throw new ProviderMismatchException("Path is not an NFS path");
        }
        return (NfsPath) path;
    }

    private static NfsConnectionConfig parseUri(java.net.URI uri, Map<String, ?> env) {
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("URI must have hostname");
        }

        int port = uri.getPort();
        if (port <= 0) {
            port = 2049; // Default NFS port
        }

        String exportPath = uri.getPath();
        if (exportPath == null || exportPath.isEmpty()) {
            exportPath = "/";
        }

        String mountPath = (String) (env != null ? env.get("mount") : null);
        if (mountPath == null) {
            mountPath = exportPath;
        }

        boolean readOnly = parseBoolean(env, "readOnly", false);
        int timeout = parseInt(env, "timeout", 30);

        return new NfsConnectionConfig(host, port, exportPath, mountPath, timeout, readOnly);
    }

    private static String configKey(NfsConnectionConfig config) {
        return String.format("%s:%d%s", config.getHost(), config.getPort(), config.getExportPath());
    }

    private static boolean parseBoolean(Map<String, ?> env, String key, boolean defaultValue) {
        if (env == null || !env.containsKey(key)) {
            return defaultValue;
        }
        Object val = env.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return Boolean.parseBoolean(String.valueOf(val));
    }

    private static int parseInt(Map<String, ?> env, String key, int defaultValue) {
        if (env == null || !env.containsKey(key)) {
            return defaultValue;
        }
        Object val = env.get(key);
        if (val instanceof Integer) {
            return (Integer) val;
        }
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
