package io.wfs.main;

import io.wfs.core.nfs.NfsConnectionConfig;
import io.wfs.core.nfs.NfsFileInfo;
import io.wfs.core.nfs.NfsIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Tests for NFS core module:
 * - NfsConnectionConfig validation (host, port, paths, timeout)
 * - NfsIO security (path traversal, read-only enforcement)
 * - NfsIO edge cases (empty dirs, overwrites, non-existent paths)
 */
final class CoreNfsTest {

    private CoreNfsTest() {
    }

    static void run() throws Exception {
        System.out.println("  Running core NFS tests...");

        // NfsConnectionConfig validation
        testValidConfigCreation();
        testNullHostRejected();
        testEmptyHostRejected();
        testHostWithDotsRejected();
        testHostWithSlashRejected();
        testHostWithSemicolonRejected();
        testHostWithPipeRejected();
        testHostWithAmpersandRejected();
        testInvalidPortLow();
        testInvalidPortHigh();
        testExportPathWithDotsRejected();
        testMountPathWithDotsRejected();
        testInvalidTimeoutLow();
        testInvalidTimeoutHigh();
        testConfigEquals();
        testConfigToString();

        // NfsIO security
        testPathTraversalBlocked();
        testReadOnlyWriteRejected();
        testReadOnlyDeleteRejected();
        testReadOnlyCreateDirRejected();
        testReadOnlyRenameRejected();
        testReadOnlyCopyRejected();

        // NfsIO functional
        testWriteReadOverwrite();
        testListEmptyDirectory();
        testDeleteNonExistentIsIdempotent();
        testCreateNestedDirectory();
        testListSortsDirsFirst();
        testCopyFile();
        testVerifyConnection();

        System.out.println("  All core NFS tests passed.");
    }

    // ── NfsConnectionConfig validation ─────────────────────────────────

    private static void testValidConfigCreation() {
        System.out.println("    [TEST] Valid config creation");
        NfsConnectionConfig config = new NfsConnectionConfig(
                "server.local", 2049, "/export", "/mnt/nfs", 30, false);
        assertEqual("server.local", config.getHost(), "host");
        assertEqual(2049, config.getPort(), "port");
        assertEqual("/export", config.getExportPath(), "exportPath");
        assertEqual("/mnt/nfs", config.getMountPath(), "mountPath");
        assertEqual(30, config.getTimeoutSeconds(), "timeout");
        assertEqual(false, config.isReadOnly(), "readOnly");
        System.out.println("    [PASS] Valid config creation");
    }

    private static void testNullHostRejected() {
        System.out.println("    [TEST] Null host rejected");
        expectException(() -> new NfsConnectionConfig(null, 2049, "/e", "/m", 30, false),
                NullPointerException.class);
        System.out.println("    [PASS] Null host rejected");
    }

    private static void testEmptyHostRejected() {
        System.out.println("    [TEST] Empty host rejected");
        expectException(() -> new NfsConnectionConfig("  ", 2049, "/e", "/m", 30, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Empty host rejected");
    }

    private static void testHostWithDotsRejected() {
        System.out.println("    [TEST] Host with '..' rejected");
        expectException(() -> new NfsConnectionConfig("../evil", 2049, "/e", "/m", 30, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Host with '..' rejected");
    }

    private static void testHostWithSlashRejected() {
        System.out.println("    [TEST] Host with '/' rejected");
        expectException(() -> new NfsConnectionConfig("host/path", 2049, "/e", "/m", 30, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Host with '/' rejected");
    }

    private static void testHostWithSemicolonRejected() {
        System.out.println("    [TEST] Host with ';' rejected");
        expectException(() -> new NfsConnectionConfig("host;cmd", 2049, "/e", "/m", 30, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Host with ';' rejected");
    }

    private static void testHostWithPipeRejected() {
        System.out.println("    [TEST] Host with '|' rejected");
        expectException(() -> new NfsConnectionConfig("host|evil", 2049, "/e", "/m", 30, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Host with '|' rejected");
    }

    private static void testHostWithAmpersandRejected() {
        System.out.println("    [TEST] Host with '&' rejected");
        expectException(() -> new NfsConnectionConfig("host&evil", 2049, "/e", "/m", 30, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Host with '&' rejected");
    }

    private static void testInvalidPortLow() {
        System.out.println("    [TEST] Port 0 rejected");
        expectException(() -> new NfsConnectionConfig("host", 0, "/e", "/m", 30, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Port 0 rejected");
    }

    private static void testInvalidPortHigh() {
        System.out.println("    [TEST] Port 65536 rejected");
        expectException(() -> new NfsConnectionConfig("host", 65536, "/e", "/m", 30, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Port 65536 rejected");
    }

    private static void testExportPathWithDotsRejected() {
        System.out.println("    [TEST] Export path with '..' rejected");
        expectException(() -> new NfsConnectionConfig("host", 2049, "/export/../etc", "/m", 30, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Export path with '..' rejected");
    }

    private static void testMountPathWithDotsRejected() {
        System.out.println("    [TEST] Mount path with '..' rejected");
        expectException(() -> new NfsConnectionConfig("host", 2049, "/e", "/mnt/../etc", 30, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Mount path with '..' rejected");
    }

    private static void testInvalidTimeoutLow() {
        System.out.println("    [TEST] Timeout 0 rejected");
        expectException(() -> new NfsConnectionConfig("host", 2049, "/e", "/m", 0, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Timeout 0 rejected");
    }

    private static void testInvalidTimeoutHigh() {
        System.out.println("    [TEST] Timeout 3601 rejected");
        expectException(() -> new NfsConnectionConfig("host", 2049, "/e", "/m", 3601, false),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Timeout 3601 rejected");
    }

    private static void testConfigEquals() {
        System.out.println("    [TEST] Config equals/hashCode");
        NfsConnectionConfig a = new NfsConnectionConfig("h", 2049, "/e", "/m", 30, false);
        NfsConnectionConfig b = new NfsConnectionConfig("h", 2049, "/e", "/m", 30, true);
        NfsConnectionConfig c = new NfsConnectionConfig("other", 2049, "/e", "/m", 30, false);

        // equals ignores readOnly (checks host, port, export, mount only)
        if (!a.equals(b))
            throw fail("Same host/port/paths should be equal regardless of readOnly");
        if (a.equals(c))
            throw fail("Different host should not be equal");
        if (a.hashCode() != b.hashCode())
            throw fail("Equal configs should have same hashCode");
        System.out.println("    [PASS] Config equals/hashCode");
    }

    private static void testConfigToString() {
        System.out.println("    [TEST] Config toString");
        NfsConnectionConfig config = new NfsConnectionConfig("myhost", 2049, "/share", "/mnt", 30, true);
        String str = config.toString();
        if (!str.contains("myhost"))
            throw fail("toString should contain host");
        if (!str.contains("2049"))
            throw fail("toString should contain port");
        if (!str.contains("/share"))
            throw fail("toString should contain export");
        System.out.println("    [PASS] Config toString");
    }

    // ── NfsIO security tests ───────────────────────────────────────────

    private static void testPathTraversalBlocked() throws Exception {
        System.out.println("    [TEST] NfsIO blocks '..' path traversal");
        Path tempDir = Files.createTempDirectory("weefs-nfs-sec-");
        try {
            NfsConnectionConfig config = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);

            try {
                NfsIO.readFile(config, "/../../../etc/passwd");
                throw fail("Path traversal should be blocked");
            } catch (IllegalArgumentException expected) {
                // Good — normalizePath blocks ".."
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] NfsIO blocks '..' path traversal");
    }

    private static void testReadOnlyWriteRejected() throws Exception {
        System.out.println("    [TEST] Read-only config rejects write");
        Path tempDir = Files.createTempDirectory("weefs-nfs-ro-");
        try {
            NfsConnectionConfig roConfig = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, true);

            try {
                NfsIO.writeFile(roConfig, "/test.txt", "data".getBytes());
                throw fail("Write should be rejected on read-only");
            } catch (IOException expected) {
                // Good
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] Read-only config rejects write");
    }

    private static void testReadOnlyDeleteRejected() throws Exception {
        System.out.println("    [TEST] Read-only config rejects delete");
        Path tempDir = Files.createTempDirectory("weefs-nfs-ro-");
        try {
            NfsConnectionConfig rwConfig = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);
            NfsIO.writeFile(rwConfig, "/todelete.txt", "data".getBytes());

            NfsConnectionConfig roConfig = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, true);
            try {
                NfsIO.delete(roConfig, "/todelete.txt");
                throw fail("Delete should be rejected on read-only");
            } catch (IOException expected) {
                // Good
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] Read-only config rejects delete");
    }

    private static void testReadOnlyCreateDirRejected() throws Exception {
        System.out.println("    [TEST] Read-only config rejects createDirectory");
        Path tempDir = Files.createTempDirectory("weefs-nfs-ro-");
        try {
            NfsConnectionConfig roConfig = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, true);
            try {
                NfsIO.createDirectory(roConfig, "/newdir");
                throw fail("createDirectory should be rejected on read-only");
            } catch (IOException expected) {
                // Good
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] Read-only config rejects createDirectory");
    }

    private static void testReadOnlyRenameRejected() throws Exception {
        System.out.println("    [TEST] Read-only config rejects rename");
        Path tempDir = Files.createTempDirectory("weefs-nfs-ro-");
        try {
            NfsConnectionConfig rwConfig = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);
            NfsIO.writeFile(rwConfig, "/old.txt", "data".getBytes());

            NfsConnectionConfig roConfig = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, true);
            try {
                NfsIO.rename(roConfig, "/old.txt", "/new.txt");
                throw fail("Rename should be rejected on read-only");
            } catch (IOException expected) {
                // Good
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] Read-only config rejects rename");
    }

    // ── NfsIO functional tests ─────────────────────────────────────────

    private static void testWriteReadOverwrite() throws Exception {
        System.out.println("    [TEST] NfsIO write/read/overwrite");
        Path tempDir = Files.createTempDirectory("weefs-nfs-func-");
        try {
            NfsConnectionConfig config = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);

            NfsIO.writeFile(config, "/data.txt", "initial".getBytes());
            byte[] read1 = NfsIO.readFile(config, "/data.txt");
            assertEqual("initial", new String(read1), "first read");

            NfsIO.writeFile(config, "/data.txt", "overwritten".getBytes());
            byte[] read2 = NfsIO.readFile(config, "/data.txt");
            assertEqual("overwritten", new String(read2), "after overwrite");
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] NfsIO write/read/overwrite");
    }

    private static void testListEmptyDirectory() throws Exception {
        System.out.println("    [TEST] NfsIO listDirectory on empty dir");
        Path tempDir = Files.createTempDirectory("weefs-nfs-func-");
        try {
            NfsConnectionConfig config = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);
            NfsIO.createDirectory(config, "/emptydir");

            List<NfsFileInfo> list = NfsIO.listDirectory(config, "/emptydir");
            if (!list.isEmpty())
                throw fail("Expected empty list for empty dir, got " + list.size());
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] NfsIO listDirectory on empty dir");
    }

    private static void testDeleteNonExistentIsIdempotent() throws Exception {
        System.out.println("    [TEST] NfsIO delete non-existent is idempotent");
        Path tempDir = Files.createTempDirectory("weefs-nfs-func-");
        try {
            NfsConnectionConfig config = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);
            // Should not throw — deleteIfExists semantics
            NfsIO.delete(config, "/does-not-exist.txt");
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] NfsIO delete non-existent is idempotent");
    }

    private static void testCreateNestedDirectory() throws Exception {
        System.out.println("    [TEST] NfsIO createDirectory nested");
        Path tempDir = Files.createTempDirectory("weefs-nfs-func-");
        try {
            NfsConnectionConfig config = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);
            NfsIO.createDirectory(config, "/a/b/c");

            // Verify directory was created by writing a file in it
            NfsIO.writeFile(config, "/a/b/c/file.txt", "deep".getBytes());
            byte[] data = NfsIO.readFile(config, "/a/b/c/file.txt");
            assertEqual("deep", new String(data), "file in nested dir");
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] NfsIO createDirectory nested");
    }

    private static void testListSortsDirsFirst() throws Exception {
        System.out.println("    [TEST] NfsIO listDirectory sorts dirs first");
        Path tempDir = Files.createTempDirectory("weefs-nfs-func-");
        try {
            NfsConnectionConfig config = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);

            NfsIO.writeFile(config, "/zzz-file.txt", "f".getBytes());
            NfsIO.createDirectory(config, "/aaa-dir");
            NfsIO.writeFile(config, "/aaa-file.txt", "f".getBytes());

            List<NfsFileInfo> list = NfsIO.listDirectory(config, "/");
            if (list.size() < 3)
                throw fail("Expected at least 3 items, got " + list.size());

            // First entry should be a directory
            if (!list.get(0).isDirectory())
                throw fail("First entry should be directory, got: " + list.get(0).getName());
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] NfsIO listDirectory sorts dirs first");
    }

    private static void testReadOnlyCopyRejected() throws Exception {
        System.out.println("    [TEST] Read-only config rejects copy");
        Path tempDir = Files.createTempDirectory("weefs-nfs-ro-");
        try {
            NfsConnectionConfig rwConfig = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);
            NfsIO.writeFile(rwConfig, "/source.txt", "data".getBytes());

            NfsConnectionConfig roConfig = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, true);
            try {
                NfsIO.copy(roConfig, "/source.txt", "/dest.txt");
                throw fail("Copy should be rejected on read-only");
            } catch (IOException expected) {
                // Good
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] Read-only config rejects copy");
    }

    private static void testCopyFile() throws Exception {
        System.out.println("    [TEST] NfsIO copy file");
        Path tempDir = Files.createTempDirectory("weefs-nfs-func-");
        try {
            NfsConnectionConfig config = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);

            NfsIO.writeFile(config, "/original.txt", "copy-me".getBytes());
            NfsIO.copy(config, "/original.txt", "/copied.txt");

            byte[] data = NfsIO.readFile(config, "/copied.txt");
            assertEqual("copy-me", new String(data), "copied content");
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] NfsIO copy file");
    }

    private static void testVerifyConnection() throws Exception {
        System.out.println("    [TEST] NfsIO verifyConnection creates cache dir");
        Path tempDir = Files.createTempDirectory("weefs-nfs-func-");
        try {
            NfsConnectionConfig config = new NfsConnectionConfig(
                    "localhost", 2049, "/export", tempDir.toString(), 30, false);
            NfsIO.verifyConnection(config);
            // Should not throw — cache directory should be created/exist
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] NfsIO verifyConnection creates cache dir");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static void expectException(Runnable action, Class<? extends Throwable> expected) {
        try {
            action.run();
            throw fail("Expected " + expected.getSimpleName() + " but no exception was thrown");
        } catch (Throwable t) {
            if (!expected.isInstance(t)) {
                throw fail("Expected " + expected.getSimpleName() + " but got "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private static void assertEqual(String expected, String actual, String context) {
        if (!expected.equals(actual))
            throw fail(context + ": expected '" + expected + "', got '" + actual + "'");
    }

    private static void assertEqual(int expected, int actual, String context) {
        if (expected != actual)
            throw fail(context + ": expected " + expected + ", got " + actual);
    }

    private static void assertEqual(boolean expected, boolean actual, String context) {
        if (expected != actual)
            throw fail(context + ": expected " + expected + ", got " + actual);
    }

    private static void cleanup(Path root) {
        try {
            if (!Files.exists(root))
                return;
            try (var walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    private static IllegalStateException fail(String msg) {
        return new IllegalStateException("[FAIL] " + msg);
    }
}
