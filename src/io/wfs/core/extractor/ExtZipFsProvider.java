package io.wfs.core.extractor;

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

public class ExtZipFsProvider extends FileSystemProvider {

    private final Map<Path, ExtZipFileSystem> mounted = new ConcurrentHashMap<>();

    @Override
    public String getScheme() {
        return "xzip";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        Objects.requireNonNull(uri, "uri");
        Map<String, ?> safeEnv = env == null ? Collections.emptyMap() : env;

        ExtZipParsedUri parsed = ExtZipParsedUri.parse(uri, getScheme());
        Path archive = resolveArchivePath(parsed.archivePart(), safeEnv);
        Path key = toKey(archive);

        if (mounted.containsKey(key)) {
            throw new FileSystemAlreadyExistsException("Archive already mounted: " + archive);
        }

        Path tempRoot = Files.createTempDirectory("extzipfs-");
        try {
            if (Files.exists(archive)) {
                ExtZipFsIO.extractArchiveToDirectory(archive, tempRoot);
            }

            Object readOnlyValue = safeEnv.containsKey("readOnly") ? safeEnv.get("readOnly") : "false";
            boolean readOnly = Boolean.parseBoolean(String.valueOf(readOnlyValue));
            ExtZipFileSystem fs = new ExtZipFileSystem(this, archive, tempRoot, readOnly);

            ExtZipFileSystem previous = mounted.putIfAbsent(key, fs);
            if (previous != null) {
                ExtZipFsIO.deleteRecursively(tempRoot);
                throw new FileSystemAlreadyExistsException("Archive already mounted: " + archive);
            }

            fs.installShutdownHook();
            return fs;
        } catch (IOException | RuntimeException ex) {
            ExtZipFsIO.deleteRecursively(tempRoot);
            throw ex;
        }
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        ExtZipParsedUri parsed = ExtZipParsedUri.parse(uri, getScheme());
        Path key = toKey(resolveArchivePath(parsed.archivePart(), Collections.emptyMap()));

        ExtZipFileSystem fs = mounted.get(key);
        if (fs == null || !fs.isOpen()) {
            throw new FileSystemNotFoundException("No mounted file system for: " + key);
        }
        return fs;
    }

    @Override
    public Path getPath(URI uri) {
        ExtZipParsedUri parsed = ExtZipParsedUri.parse(uri, getScheme());
        Path key = toKey(resolveArchivePath(parsed.archivePart(), Collections.emptyMap()));
        ExtZipFileSystem fs = mounted.get(key);

        if (fs == null || !fs.isOpen()) {
            try {
                fs = (ExtZipFileSystem) newFileSystem(uri, Collections.emptyMap());
            } catch (FileSystemAlreadyExistsException ex) {
                fs = mounted.get(key);
            } catch (IOException ioEx) {
                throw new IllegalArgumentException("Unable to create file system for URI: " + uri, ioEx);
            }
        }

        String entry = parsed.entryPart();
        if (entry == null || entry.isEmpty()) {
            return fs.getPath("/");
        }
        return fs.getPath("/" + entry);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        ExtZipPath extPath = requireExtPath(path);
        extPath.getExtZipFileSystem().ensureWritableFor(options);
        return Files.newByteChannel(extPath.getDelegate(), options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        ExtZipPath extDir = requireExtPath(dir);
        DirectoryStream<Path> delegate = Files.newDirectoryStream(extDir.getDelegate());
        DirectoryStream.Filter<? super Path> safeFilter = filter == null ? path -> true : filter;
        return new ExtZipDirectoryStream(delegate, extDir.getExtZipFileSystem(), safeFilter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        ExtZipPath extDir = requireExtPath(dir);
        extDir.getExtZipFileSystem().ensureWritable();
        Files.createDirectory(extDir.getDelegate(), attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        ExtZipPath extPath = requireExtPath(path);
        extPath.getExtZipFileSystem().ensureWritable();
        if (Files.isDirectory(extPath.getDelegate())) {
            try (var stream = Files.list(extPath.getDelegate())) {
                if (stream.findAny().isPresent()) {
                    throw new DirectoryNotEmptyException(extPath.toString());
                }
            }
        }
        Files.delete(extPath.getDelegate());
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        ExtZipPath src = requireExtPath(source);
        ExtZipPath dst = requireExtPath(target);
        dst.getExtZipFileSystem().ensureWritable();
        Files.copy(src.getDelegate(), dst.getDelegate(), options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        ExtZipPath src = requireExtPath(source);
        ExtZipPath dst = requireExtPath(target);
        src.getExtZipFileSystem().ensureWritable();
        dst.getExtZipFileSystem().ensureWritable();
        Files.move(src.getDelegate(), dst.getDelegate(), options);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        ExtZipPath left = requireExtPath(path);
        ExtZipPath right = requireExtPath(path2);
        return Files.isSameFile(left.getDelegate(), right.getDelegate());
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return Files.isHidden(requireExtPath(path).getDelegate());
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return Files.getFileStore(requireExtPath(path).getDelegate());
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        ExtZipPath extPath = requireExtPath(path);
        if (!Files.exists(extPath.getDelegate())) {
            throw new NoSuchFileException(extPath.toString());
        }

        if (modes == null) {
            return;
        }

        for (AccessMode mode : modes) {
            if (mode == AccessMode.READ && !Files.isReadable(extPath.getDelegate())) {
                throw new IOException("Read access denied: " + extPath);
            }
            if (mode == AccessMode.WRITE) {
                extPath.getExtZipFileSystem().ensureWritable();
                if (!Files.isWritable(extPath.getDelegate())) {
                    throw new IOException("Write access denied: " + extPath);
                }
            }
            if (mode == AccessMode.EXECUTE && !Files.isExecutable(extPath.getDelegate())) {
                throw new IOException("Execute access denied: " + extPath);
            }
        }
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return Files.getFileAttributeView(requireExtPath(path).getDelegate(), type, options);
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
            throws IOException {
        return Files.readAttributes(requireExtPath(path).getDelegate(), type, options);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return Files.readAttributes(requireExtPath(path).getDelegate(), attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        ExtZipPath extPath = requireExtPath(path);
        extPath.getExtZipFileSystem().ensureWritable();
        Files.setAttribute(extPath.getDelegate(), attribute, value, options);
    }

    Path resolveArchivePath(String archivePart, Map<String, ?> env) {
        Object envArchive = env == null ? null : env.get("archive");
        String raw = archivePart;
        if ((raw == null || raw.isBlank()) && envArchive != null) {
            raw = String.valueOf(envArchive);
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Archive path is required");
        }

        raw = raw.trim();
        if (raw.startsWith("file:")) {
            return Path.of(URI.create(raw)).toAbsolutePath().normalize();
        }
        if (raw.matches("^/[a-zA-Z]:/.*")) {
            raw = raw.substring(1);
        }
        return Path.of(raw).toAbsolutePath().normalize();
    }

    Path toKey(Path archive) {
        return archive.toAbsolutePath().normalize();
    }

    ExtZipPath requireExtPath(Path path) {
        if (!(path instanceof ExtZipPath)) {
            throw new ProviderMismatchException("Path is not managed by xzip provider: " + path);
        }
        ExtZipPath ext = (ExtZipPath) path;
        if (!ext.getExtZipFileSystem().isOpen()) {
            throw new FileSystemNotFoundException("File system closed for path: " + path);
        }
        return ext;
    }

    void unregister(ExtZipFileSystem fileSystem) {
        mounted.remove(toKey(fileSystem.getArchivePath()), fileSystem);
    }
}
