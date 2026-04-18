package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Encapsulates NFS I/O operations: connect, list, read, write, delete.
 * Uses local FileSystem operations to simulate NFS (production would use jnfs
 * library).
 * Utility class following Pure Fabrication pattern (GRASP).
 */
public final class NfsIO {

    private NfsIO() {
    }

    /**
     * Connects to NFS server and ensures mount is accessible.
     * In production, this would validate NFS protocol connectivity.
     * For now, validates the local cache path exists.
     */
    public static void verifyConnection(NfsConnectionConfig config) throws IOException {
        Path cachePath = getCachePath(config);
        if (!Files.exists(cachePath)) {
            Files.createDirectories(cachePath);
        }
        // In production: attempt RPC connection to host:port
    }

    /**
     * Lists all files in an NFS directory.
     */
    public static List<NfsFileInfo> listDirectory(NfsConnectionConfig config, String remotePath) throws IOException {
        List<NfsFileInfo> result = new ArrayList<>();
        Path cachePath = getCachePath(config).resolve(normalizePath(remotePath));

        if (!Files.exists(cachePath)) {
            return result;
        }

        try (Stream<Path> stream = Files.list(cachePath)) {
            stream.forEach(file -> {
                try {
                    result.add(NfsFileInfo.fromPath(file));
                } catch (IOException ignored) {
                    // Skip unreadable files
                }
            });
        }

        result.sort(Comparator.comparing(NfsFileInfo::isDirectory).reversed()
                .thenComparing(NfsFileInfo::getName));
        return result;
    }

    /**
     * Reads entire file content from NFS.
     */
    public static byte[] readFile(NfsConnectionConfig config, String remotePath) throws IOException {
        Path localPath = getCachePath(config).resolve(normalizePath(remotePath));
        if (!Files.exists(localPath) || Files.isDirectory(localPath)) {
            throw new IOException("File not found or is a directory: " + remotePath);
        }
        return Files.readAllBytes(localPath);
    }

    /**
     * Writes content to NFS file.
     */
    public static void writeFile(NfsConnectionConfig config, String remotePath, byte[] content) throws IOException {
        if (config.isReadOnly()) {
            throw new IOException("NFS mount is read-only");
        }
        Path localPath = getCachePath(config).resolve(normalizePath(remotePath));
        Path parent = localPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(localPath, content);
    }

    /**
     * Creates a directory on NFS.
     */
    public static void createDirectory(NfsConnectionConfig config, String remotePath) throws IOException {
        if (config.isReadOnly()) {
            throw new IOException("NFS mount is read-only");
        }
        Path localPath = getCachePath(config).resolve(normalizePath(remotePath));
        Files.createDirectories(localPath);
    }

    /**
     * Deletes a file or directory from NFS.
     */
    public static void delete(NfsConnectionConfig config, String remotePath) throws IOException {
        if (config.isReadOnly()) {
            throw new IOException("NFS mount is read-only");
        }
        Path localPath = getCachePath(config).resolve(normalizePath(remotePath));
        if (Files.isDirectory(localPath)) {
            deleteRecursively(localPath);
        } else {
            Files.deleteIfExists(localPath);
        }
    }

    /**
     * Renames/moves file or directory on NFS.
     */
    public static void rename(NfsConnectionConfig config, String oldPath, String newPath) throws IOException {
        if (config.isReadOnly()) {
            throw new IOException("NFS mount is read-only");
        }
        Path oldLocalPath = getCachePath(config).resolve(normalizePath(oldPath));
        Path newLocalPath = getCachePath(config).resolve(normalizePath(newPath));
        Files.move(oldLocalPath, newLocalPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Copies file on NFS.
     */
    public static void copy(NfsConnectionConfig config, String sourcePath, String destPath) throws IOException {
        if (config.isReadOnly()) {
            throw new IOException("NFS mount is read-only");
        }
        Path sourceLocal = getCachePath(config).resolve(normalizePath(sourcePath));
        Path destLocal = getCachePath(config).resolve(normalizePath(destPath));
        Path destParent = destLocal.getParent();
        if (destParent != null) {
            Files.createDirectories(destParent);
        }
        Files.copy(sourceLocal, destLocal, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Disconnects from NFS (cleanup if needed).
     */
    public static void disconnect(NfsConnectionConfig config) throws IOException {
        // In production: unmount or close RPC connection
        // For now: no-op or could clean up cache
    }

    // ────────────────────────────────────────────────────────────────

    private static Path getCachePath(NfsConnectionConfig config) {
        Path tempBase = Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("weefs-nfs");
        // Sanitize host and export path to prevent path traversal
        String safeHost = sanitizePathComponent(config.getHost());
        String safeExport = sanitizePathComponent(config.getExportPath());
        String cacheDir = String.format("%s_%d_%s", safeHost, config.getPort(), safeExport);
        return tempBase.resolve(cacheDir);
    }

    /**
     * Sanitizes a string for use as a file path component by removing
     * dangerous characters and path traversal sequences.
     */
    private static String sanitizePathComponent(String input) {
        if (input == null || input.isEmpty()) {
            return "default";
        }
        // Replace path separators, dots sequences, and special characters
        return input.replaceAll("[/\\\\]", "_")
                .replaceAll("\\.\\.", "_")
                .replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return ".";
        }
        // Remove leading slashes and normalize
        String normalized = path.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // Block path traversal attempts
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("Path traversal not allowed: " + path);
        }
        return normalized.isEmpty() ? "." : normalized;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                            // Continue with next
                        }
                    });
        }
    }
}
