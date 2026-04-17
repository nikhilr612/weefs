package io.wfs.core.filesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Factory that selects a file-system driver from the URI scheme.
 *
 * Supports:
 * - xzip:// (archive file systems)
 * - weefs:// (SFTP-backed remote file systems)
 * - file:// (local default file system)
 */
public final class FileSystemFactory {

    private final ConcurrentMap<String, FileSystemDriver> drivers = new ConcurrentHashMap<>();

    public FileSystemFactory() {
        registerDefaults();
    }

    public void register(FileSystemDriver driver) {
        Objects.requireNonNull(driver, "driver");
        String scheme = normalizeScheme(driver.scheme());
        drivers.put(scheme, driver);
    }

    public FileSystem open(URI uri, Map<String, ?> env) throws IOException {
        Objects.requireNonNull(uri, "uri");
        String scheme = normalizeScheme(uri.getScheme());
        FileSystemDriver driver = drivers.get(scheme);
        if (driver == null) {
            throw new IOException("Unsupported URI scheme: " + uri);
        }
        return driver.open(uri, env == null ? Map.of() : env);
    }

    public List<String> supportedSchemes() {
        return drivers.keySet().stream().sorted().toList();
    }

    private void registerDefaults() {
        register(new ZipFileSystemDriver());
        register(new NfsFileSystemDriver());
        register(new LocalFileSystemDriver());
    }

    private static String normalizeScheme(String scheme) {
        if (scheme == null || scheme.isBlank()) {
            throw new IllegalArgumentException("URI scheme is required");
        }
        return scheme.toLowerCase(Locale.ROOT);
    }
}
