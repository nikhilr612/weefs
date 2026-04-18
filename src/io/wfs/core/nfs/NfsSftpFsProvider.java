package io.wfs.core.nfs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
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

        NfsSftpFsIO.ensureMountRootExists(config);
        NfsFileSystem fs = new NfsFileSystem(this, config, readOnly);

        NfsFileSystem previous = mounted.putIfAbsent(key, fs);
        if (previous != null) {
            throw new FileSystemAlreadyExistsException("Remote location already mounted: " + uri);
        }

        fs.installShutdownHook();
        return fs;
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

        return fs.getPath("/");
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        NfsPath nfsPath = requireNfsPath(path);
        NfsFileSystem fs = nfsPath.getNfsFileSystem();
        Set<StandardOpenOption> resolved = resolveOpenOptions(options);

        boolean readable = resolved.contains(StandardOpenOption.READ);
        boolean writable = resolved.contains(StandardOpenOption.WRITE) || resolved.contains(StandardOpenOption.APPEND);
        boolean append = resolved.contains(StandardOpenOption.APPEND);
        boolean truncate = resolved.contains(StandardOpenOption.TRUNCATE_EXISTING) && writable;
        boolean create = resolved.contains(StandardOpenOption.CREATE);
        boolean createNew = resolved.contains(StandardOpenOption.CREATE_NEW);

        fs.ensureWritableFor(resolved);

        String remotePath = fs.toRemotePath(nfsPath);
        boolean exists = NfsSftpFsIO.exists(fs.config(), remotePath);

        if (createNew && exists) {
            throw new java.nio.file.FileAlreadyExistsException(nfsPath.toString());
        }

        if (!exists && !create && !createNew) {
            throw new FileNotFoundException(nfsPath.toString());
        }

        byte[] initial = new byte[0];
        boolean initialDirty = false;
        if (exists) {
            if (!truncate) {
                NfsSftpFsIO.RemoteFileStat stat = NfsSftpFsIO.stat(fs.config(), remotePath);
                if (stat.directory()) {
                    throw new IOException("Cannot open directory as file: " + nfsPath);
                }
                if (readable || writable || append) {
                    initial = NfsSftpFsIO.readFile(fs.config(), remotePath);
                }
            } else {
                initialDirty = true;
            }
        } else {
            initialDirty = create || createNew;
        }

        return new NfsSyncingByteChannel(fs, nfsPath, readable, writable, initial, append, initialDirty);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        NfsPath nfsDir = requireNfsPath(dir);
        NfsFileSystem fs = nfsDir.getNfsFileSystem();
        String remoteDir = fs.toRemotePath(nfsDir);

        List<NfsSftpFsIO.RemoteEntry> entries = NfsSftpFsIO.listDirectory(fs.config(), remoteDir);
        List<Path> children = new ArrayList<>(entries.size());
        for (NfsSftpFsIO.RemoteEntry entry : entries) {
            children.add(fs.getPath(joinVirtual(nfsDir.toString(), entry.name())));
        }

        DirectoryStream.Filter<? super Path> safeFilter = filter == null ? path -> true : filter;
        return new NfsDirectoryStream(children, safeFilter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        NfsPath nfsDir = requireNfsPath(dir);
        NfsFileSystem fs = nfsDir.getNfsFileSystem();
        fs.ensureWritable();
        NfsSftpFsIO.createDirectory(fs.config(), fs.toRemotePath(nfsDir));
    }

    @Override
    public void delete(Path path) throws IOException {
        NfsPath nfsPath = requireNfsPath(path);
        NfsFileSystem fs = nfsPath.getNfsFileSystem();
        fs.ensureWritable();

        String remotePath = fs.toRemotePath(nfsPath);
        NfsSftpFsIO.RemoteFileStat stat = NfsSftpFsIO.stat(fs.config(), remotePath);
        if (stat.directory()) {
            NfsSftpFsIO.deleteDirectory(fs.config(), remotePath);
        } else {
            NfsSftpFsIO.deleteFile(fs.config(), remotePath);
        }
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        NfsPath src = requireNfsPath(source);
        NfsPath dst = requireNfsPath(target);
        NfsFileSystem fs = dst.getNfsFileSystem();
        fs.ensureWritable();

        String srcRemote = src.getNfsFileSystem().toRemotePath(src);
        String dstRemote = dst.getNfsFileSystem().toRemotePath(dst);
        NfsSftpFsIO.RemoteFileStat stat = NfsSftpFsIO.stat(src.getNfsFileSystem().config(), srcRemote);

        if (stat.directory()) {
            throw new UnsupportedOperationException("Directory copy is not supported");
        }
        NfsSftpFsIO.copyFile(src.getNfsFileSystem().config(), srcRemote, dstRemote);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        NfsPath src = requireNfsPath(source);
        NfsPath dst = requireNfsPath(target);
        NfsFileSystem fs = dst.getNfsFileSystem();
        fs.ensureWritable();

        String srcRemote = src.getNfsFileSystem().toRemotePath(src);
        String dstRemote = dst.getNfsFileSystem().toRemotePath(dst);
        NfsSftpFsIO.move(src.getNfsFileSystem().config(), srcRemote, dstRemote);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) {
        NfsPath left = requireNfsPath(path);
        NfsPath right = requireNfsPath(path2);
        return left.getNfsFileSystem() == right.getNfsFileSystem()
                && left.toString().equals(right.toString());
    }

    @Override
    public boolean isHidden(Path path) {
        NfsPath nfsPath = requireNfsPath(path);
        Path fileName = Path.of(nfsPath.toString()).getFileName();
        return fileName != null && fileName.toString().startsWith(".");
    }

    @Override
    public FileStore getFileStore(Path path) {
        throw new UnsupportedOperationException("FileStore is not supported for weefs SFTP");
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        NfsPath nfsPath = requireNfsPath(path);
        NfsFileSystem fs = nfsPath.getNfsFileSystem();
        String remotePath = fs.toRemotePath(nfsPath);

        NfsSftpFsIO.RemoteFileStat stat = NfsSftpFsIO.stat(fs.config(), remotePath);
        if (modes == null) {
            return;
        }

        for (AccessMode mode : modes) {
            if (mode == AccessMode.WRITE) {
                fs.ensureWritable();
            }
            if (mode == AccessMode.EXECUTE && stat.directory()) {
                continue;
            }
        }
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
            throws IOException {
        if (!BasicFileAttributes.class.isAssignableFrom(type)) {
            throw new UnsupportedOperationException("Only basic attributes are supported");
        }
        NfsPath nfsPath = requireNfsPath(path);
        NfsFileSystem fs = nfsPath.getNfsFileSystem();
        NfsSftpFsIO.RemoteFileStat stat = NfsSftpFsIO.stat(fs.config(), fs.toRemotePath(nfsPath));

        NfsBasicFileAttributes attrs = new NfsBasicFileAttributes(
                stat.directory(),
                stat.symbolicLink(),
                stat.size(),
                stat.lastModifiedTime());
        return type.cast(attrs);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        NfsPath nfsPath = requireNfsPath(path);
        NfsFileSystem fs = nfsPath.getNfsFileSystem();
        return NfsSftpFsIO.readBasicAttributesMap(fs.config(), fs.toRemotePath(nfsPath));
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
        throw new UnsupportedOperationException("setAttribute is not supported for weefs SFTP");
    }

    void unregister(NfsFileSystem fs) {
        mounted.entrySet().removeIf(entry -> entry.getValue() == fs);
    }

    private String toKey(NfsParsedUri parsed) {
        return parsed.username() + "@" + parsed.host() + ":" + parsed.port() + parsed.remotePath();
    }

    private static String joinVirtual(String parent, String child) {
        if ("/".equals(parent)) {
            return "/" + child;
        }
        return parent + "/" + child;
    }

    private static NfsPath requireNfsPath(Path path) {
        if (!(path instanceof NfsPath)) {
            throw new ProviderMismatchException("Path is not managed by weefs provider: " + path);
        }
        return (NfsPath) path;
    }

    private static Set<StandardOpenOption> resolveOpenOptions(Set<? extends OpenOption> options) {
        EnumSet<StandardOpenOption> resolved = EnumSet.noneOf(StandardOpenOption.class);
        if (options != null) {
            for (OpenOption option : options) {
                if (option instanceof StandardOpenOption standard) {
                    resolved.add(standard);
                }
            }
        }

        if (!resolved.contains(StandardOpenOption.READ)
                && !resolved.contains(StandardOpenOption.WRITE)
                && !resolved.contains(StandardOpenOption.APPEND)) {
            resolved.add(StandardOpenOption.READ);
        }
        if (resolved.contains(StandardOpenOption.APPEND)) {
            resolved.add(StandardOpenOption.WRITE);
        }
        return resolved;
    }
}
