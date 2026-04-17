package io.wfs.core.nfs;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SFTP-backed provider for the weefs:// URI scheme.
 */
public final class NfsSftpFsProvider extends FileSystemProvider {

    private final Map<String, NfsFileSystem> mounted = new ConcurrentHashMap<>();

    @Override
    public String getScheme() {
        return "weefs";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        Objects.requireNonNull(uri, "uri");
        Map<String, ?> safeEnv = env == null ? Collections.emptyMap() : env;

        NfsParsedUri parsed = NfsParsedUri.parse(uri, getScheme());
        String key = toKey(parsed);

        if (mounted.containsKey(key)) {
            throw new FileSystemAlreadyExistsException("Remote location already mounted: " + uri);
        }

        String password = System.getenv(parsed.authEnvVar());
        if (password == null || password.isBlank()) {
            throw new IOException("Environment variable '" + parsed.authEnvVar() + "' is missing or empty");
        }

        Object readOnlyValue = safeEnv.containsKey("readOnly") ? safeEnv.get("readOnly") : "false";
        boolean readOnly = Boolean.parseBoolean(String.valueOf(readOnlyValue));

        NfsSftpConfig config = new NfsSftpConfig(
                parsed.host(),
                parsed.port(),
                parsed.username(),
                password,
                parsed.remotePath(),
                parsed.authEnvVar());

        Path tempRoot = Files.createTempDirectory("weefs-sftp-");
        try {
            NfsSftpFsIO.downloadRemoteTree(config, tempRoot);
            NfsFileSystem fs = new NfsFileSystem(this, config, tempRoot, readOnly);

            NfsFileSystem previous = mounted.putIfAbsent(key, fs);
            if (previous != null) {
                deleteRecursively(tempRoot);
                throw new FileSystemAlreadyExistsException("Remote location already mounted: " + uri);
            }

            fs.installShutdownHook();
            return fs;
        } catch (IOException | RuntimeException ex) {
            deleteRecursively(tempRoot);
            throw ex;
        }
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        NfsParsedUri parsed = NfsParsedUri.parse(uri, getScheme());
        NfsFileSystem fs = mounted.get(toKey(parsed));
        if (fs == null || !fs.isOpen()) {
            throw new FileSystemNotFoundException("No mounted remote file system for URI: " + uri);
        }
        return fs;
    }

    @Override
    public Path getPath(URI uri) {
        NfsParsedUri parsed = NfsParsedUri.parse(uri, getScheme());
        String key = toKey(parsed);
        NfsFileSystem fs = mounted.get(key);

        if (fs == null || !fs.isOpen()) {
            try {
                fs = (NfsFileSystem) newFileSystem(uri, Collections.emptyMap());
            } catch (FileSystemAlreadyExistsException ex) {
                fs = mounted.get(key);
            } catch (IOException ioEx) {
                throw new IllegalArgumentException("Unable to create file system for URI: " + uri, ioEx);
            }
        }

        String root = parsed.remotePath();
        if (root == null || root.isBlank() || "/".equals(root)) {
            return fs.getPath("/");
        }
        return fs.getPath("/");
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        NfsPath nfsPath = requireNfsPath(path);
        nfsPath.getNfsFileSystem().ensureWritableFor(options);

        SeekableByteChannel channel = Files.newByteChannel(nfsPath.getDelegate(), options, attrs);
        if (isWriteOperation(options)) {
            return new NfsSyncingByteChannel(channel, nfsPath.getNfsFileSystem(), nfsPath.getDelegate());
        }
        return channel;
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        NfsPath nfsDir = requireNfsPath(dir);
        DirectoryStream<Path> delegate = Files.newDirectoryStream(nfsDir.getDelegate());
        DirectoryStream.Filter<? super Path> safeFilter = filter == null ? path -> true : filter;
        return new NfsDirectoryStream(delegate, nfsDir.getNfsFileSystem(), safeFilter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        NfsPath nfsDir = requireNfsPath(dir);
        nfsDir.getNfsFileSystem().ensureWritable();
        Files.createDirectory(nfsDir.getDelegate(), attrs);
        nfsDir.getNfsFileSystem().createRemoteDirectory(nfsDir.getDelegate());
    }

    @Override
    public void delete(Path path) throws IOException {
        NfsPath nfsPath = requireNfsPath(path);
        nfsPath.getNfsFileSystem().ensureWritable();

        boolean isDirectory = Files.isDirectory(nfsPath.getDelegate());
        if (isDirectory) {
            try (var stream = Files.list(nfsPath.getDelegate())) {
                if (stream.findAny().isPresent()) {
                    throw new DirectoryNotEmptyException(nfsPath.toString());
                }
            }
        }

        Files.delete(nfsPath.getDelegate());
        nfsPath.getNfsFileSystem().deleteRemote(nfsPath.getDelegate(), isDirectory);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        NfsPath src = requireNfsPath(source);
        NfsPath dst = requireNfsPath(target);
        dst.getNfsFileSystem().ensureWritable();

        Files.copy(src.getDelegate(), dst.getDelegate(), options);
        if (Files.isDirectory(dst.getDelegate())) {
            dst.getNfsFileSystem().createRemoteDirectory(dst.getDelegate());
        } else {
            dst.getNfsFileSystem().syncFile(dst.getDelegate());
        }
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        NfsPath src = requireNfsPath(source);
        NfsPath dst = requireNfsPath(target);
        src.getNfsFileSystem().ensureWritable();
        dst.getNfsFileSystem().ensureWritable();

        boolean srcWasDirectory = Files.isDirectory(src.getDelegate());
        Files.move(src.getDelegate(), dst.getDelegate(), options);
        src.getNfsFileSystem().deleteRemote(src.getDelegate(), srcWasDirectory);

        if (Files.isDirectory(dst.getDelegate())) {
            dst.getNfsFileSystem().createRemoteDirectory(dst.getDelegate());
        } else {
            dst.getNfsFileSystem().syncFile(dst.getDelegate());
        }
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        NfsPath left = requireNfsPath(path);
        NfsPath right = requireNfsPath(path2);
        return Files.isSameFile(left.getDelegate(), right.getDelegate());
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return Files.isHidden(requireNfsPath(path).getDelegate());
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return Files.getFileStore(requireNfsPath(path).getDelegate());
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        NfsPath nfsPath = requireNfsPath(path);
        if (!Files.exists(nfsPath.getDelegate())) {
            throw new NoSuchFileException(nfsPath.toString());
        }

        if (modes == null) {
            return;
        }

        for (AccessMode mode : modes) {
            if (mode == AccessMode.READ && !Files.isReadable(nfsPath.getDelegate())) {
                throw new IOException("Read access denied: " + nfsPath);
            }
            if (mode == AccessMode.WRITE) {
                nfsPath.getNfsFileSystem().ensureWritable();
                if (!Files.isWritable(nfsPath.getDelegate())) {
                    throw new IOException("Write access denied: " + nfsPath);
                }
            }
            if (mode == AccessMode.EXECUTE && !Files.isExecutable(nfsPath.getDelegate())) {
                throw new IOException("Execute access denied: " + nfsPath);
            }
        }
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return Files.getFileAttributeView(requireNfsPath(path).getDelegate(), type, options);
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
            throws IOException {
        return Files.readAttributes(requireNfsPath(path).getDelegate(), type, options);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return Files.readAttributes(requireNfsPath(path).getDelegate(), attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        NfsPath nfsPath = requireNfsPath(path);
        nfsPath.getNfsFileSystem().ensureWritable();
        Files.setAttribute(nfsPath.getDelegate(), attribute, value, options);
        if (Files.isDirectory(nfsPath.getDelegate())) {
            nfsPath.getNfsFileSystem().createRemoteDirectory(nfsPath.getDelegate());
        } else {
            nfsPath.getNfsFileSystem().syncFile(nfsPath.getDelegate());
        }
    }

    void unregister(NfsFileSystem fs) {
        mounted.entrySet().removeIf(entry -> entry.getValue() == fs);
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private String toKey(NfsParsedUri parsed) {
        return parsed.username() + "@" + parsed.host() + ":" + parsed.port() + parsed.remotePath();
    }

    private static boolean isWriteOperation(Set<? extends OpenOption> options) {
        if (options == null || options.isEmpty()) {
            return false;
        }

        for (OpenOption option : options) {
            String name = String.valueOf(option).toUpperCase();
            if (name.contains("WRITE") || name.contains("APPEND") || name.contains("CREATE")
                    || name.contains("TRUNCATE") || name.contains("DELETE")) {
                return true;
            }
        }
        return false;
    }

    private static NfsPath requireNfsPath(Path path) {
        if (!(path instanceof NfsPath)) {
            throw new ProviderMismatchException("Path is not managed by weefs provider: " + path);
        }
        return (NfsPath) path;
    }
}
