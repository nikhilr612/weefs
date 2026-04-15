package io.wfs.main;

import io.wfs.core.nfs.NfsConnectionConfig;
import io.wfs.core.nfs.NfsFileInfo;
import io.wfs.core.nfs.NfsIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Integration tests for NFS operations.
 * Tests file creation, listing, reading, writing, deletion, renaming, and copying.
 * Uses a local temp directory to simulate NFS mount.
 */
final class NfsIntegrationTest {

    private NfsIntegrationTest() {
    }

    static void run() throws Exception {
        Path tempRoot = Files.createTempDirectory("weefs-nfs-integration-");
        try {
            testCreateAndList(tempRoot);
            testReadWriteFile(tempRoot);
            testDeleteFile(tempRoot);
            testRenameFile(tempRoot);
            testCopyFile(tempRoot);
            testCreateDirectory(tempRoot);
            testReadOnlyMode(tempRoot);
            System.out.println("All NFS integration checks passed.");
        } finally {
            cleanup(tempRoot);
        }
    }

    /**
     * Test creating files and listing directory contents.
     */
    private static void testCreateAndList(Path tempRoot) throws Exception {
        Path nfsMount = tempRoot.resolve("test-mount");
        Files.createDirectories(nfsMount);

        NfsConnectionConfig config = new NfsConnectionConfig(
                "localhost", 2049, "/export", nfsMount.toString(), 30, false);

        // Write test files
        NfsIO.writeFile(config, "/file1.txt", "content1".getBytes());
        NfsIO.writeFile(config, "/file2.txt", "content2".getBytes());

        // List directory
        List<NfsFileInfo> files = NfsIO.listDirectory(config, "/");
        if (files.size() < 2) {
            throw new IllegalStateException("Expected at least 2 files, got: " + files.size());
        }

        boolean hasFile1 = files.stream().anyMatch(f -> f.getName().equals("file1.txt"));
        boolean hasFile2 = files.stream().anyMatch(f -> f.getName().equals("file2.txt"));
        if (!hasFile1 || !hasFile2) {
            throw new IllegalStateException("Missing expected files in listing");
        }

        System.out.println("  [PASS] Create and List");
    }

    /**
     * Test reading and writing file content.
     */
    private static void testReadWriteFile(Path tempRoot) throws Exception {
        Path nfsMount = tempRoot.resolve("test-readwrite");
        Files.createDirectories(nfsMount);

        NfsConnectionConfig config = new NfsConnectionConfig(
                "localhost", 2049, "/export", nfsMount.toString(), 30, false);

        String testContent = "Hello NFS World";
        NfsIO.writeFile(config, "/readwrite.txt", testContent.getBytes());

        byte[] readContent = NfsIO.readFile(config, "/readwrite.txt");
        String actual = new String(readContent);
        if (!testContent.equals(actual)) {
            throw new IllegalStateException("Content mismatch. Expected: " + testContent + ", got: " + actual);
        }

        System.out.println("  [PASS] Read/Write File");
    }

    /**
     * Test file deletion.
     */
    private static void testDeleteFile(Path tempRoot) throws Exception {
        Path nfsMount = tempRoot.resolve("test-delete");
        Files.createDirectories(nfsMount);

        NfsConnectionConfig config = new NfsConnectionConfig(
                "localhost", 2049, "/export", nfsMount.toString(), 30, false);

        NfsIO.writeFile(config, "/todelete.txt", "content".getBytes());
        List<NfsFileInfo> before = NfsIO.listDirectory(config, "/");
        int sizeBefore = before.size();

        NfsIO.delete(config, "/todelete.txt");
        List<NfsFileInfo> after = NfsIO.listDirectory(config, "/");

        if (after.size() >= sizeBefore) {
            throw new IllegalStateException("File was not deleted. Before: " + sizeBefore + ", After: " + after.size());
        }

        System.out.println("  [PASS] Delete File");
    }

    /**
     * Test file renaming.
     */
    private static void testRenameFile(Path tempRoot) throws Exception {
        Path nfsMount = tempRoot.resolve("test-rename");
        Files.createDirectories(nfsMount);

        NfsConnectionConfig config = new NfsConnectionConfig(
                "localhost", 2049, "/export", nfsMount.toString(), 30, false);

        NfsIO.writeFile(config, "/oldname.txt", "content".getBytes());
        NfsIO.rename(config, "/oldname.txt", "/newname.txt");

        List<NfsFileInfo> files = NfsIO.listDirectory(config, "/");
        boolean hasNewName = files.stream().anyMatch(f -> f.getName().equals("newname.txt"));
        boolean hasOldName = files.stream().anyMatch(f -> f.getName().equals("oldname.txt"));

        if (!hasNewName || hasOldName) {
            throw new IllegalStateException("Rename failed. New name exists: " + hasNewName + ", Old name exists: " + hasOldName);
        }

        System.out.println("  [PASS] Rename File");
    }

    /**
     * Test file copying.
     */
    private static void testCopyFile(Path tempRoot) throws Exception {
        Path nfsMount = tempRoot.resolve("test-copy");
        Files.createDirectories(nfsMount);

        NfsConnectionConfig config = new NfsConnectionConfig(
                "localhost", 2049, "/export", nfsMount.toString(), 30, false);

        String content = "copy test content";
        NfsIO.writeFile(config, "/original.txt", content.getBytes());
        NfsIO.copy(config, "/original.txt", "/copied.txt");

        byte[] original = NfsIO.readFile(config, "/original.txt");
        byte[] copied = NfsIO.readFile(config, "/copied.txt");

        if (!java.util.Arrays.equals(original, copied)) {
            throw new IllegalStateException("Copied file content does not match original");
        }

        System.out.println("  [PASS] Copy File");
    }

    /**
     * Test directory creation and nested file operations.
     */
    private static void testCreateDirectory(Path tempRoot) throws Exception {
        Path nfsMount = tempRoot.resolve("test-mkdir");
        Files.createDirectories(nfsMount);

        NfsConnectionConfig config = new NfsConnectionConfig(
                "localhost", 2049, "/export", nfsMount.toString(), 30, false);

        NfsIO.createDirectory(config, "/newdir");
        NfsIO.writeFile(config, "/newdir/nested.txt", "nested content".getBytes());

        List<NfsFileInfo> rootFiles = NfsIO.listDirectory(config, "/");
        boolean hasDir = rootFiles.stream().anyMatch(f -> f.isDirectory() && f.getName().equals("newdir"));
        if (!hasDir) {
            throw new IllegalStateException("Directory creation failed");
        }

        List<NfsFileInfo> nestedFiles = NfsIO.listDirectory(config, "/newdir");
        boolean hasNestedFile = nestedFiles.stream().anyMatch(f -> f.getName().equals("nested.txt"));
        if (!hasNestedFile) {
            throw new IllegalStateException("Nested file not found");
        }

        System.out.println("  [PASS] Create Directory");
    }

    /**
     * Test read-only mode restrictions.
     */
    private static void testReadOnlyMode(Path tempRoot) throws Exception {
        Path nfsMount = tempRoot.resolve("test-readonly");
        Files.createDirectories(nfsMount);

        NfsConnectionConfig configRw = new NfsConnectionConfig(
                "localhost", 2049, "/export", nfsMount.toString(), 30, false);
        NfsConnectionConfig configRo = new NfsConnectionConfig(
                "localhost", 2049, "/export", nfsMount.toString(), 30, true);

        // Create file in read-write mode
        NfsIO.writeFile(configRw, "/readonly-test.txt", "content".getBytes());

        // Try to write in read-only mode - should fail
        boolean writeFailedAsExpected = false;
        try {
            NfsIO.writeFile(configRo, "/readonly-test2.txt", "content".getBytes());
        } catch (IOException e) {
            if (e.getMessage().contains("read-only")) {
                writeFailedAsExpected = true;
            }
        }

        if (!writeFailedAsExpected) {
            throw new IllegalStateException("Read-only mode did not prevent write operation");
        }

        // Try to delete in read-only mode - should fail
        boolean deleteFailedAsExpected = false;
        try {
            NfsIO.delete(configRo, "/readonly-test.txt");
        } catch (IOException e) {
            if (e.getMessage().contains("read-only")) {
                deleteFailedAsExpected = true;
            }
        }

        if (!deleteFailedAsExpected) {
            throw new IllegalStateException("Read-only mode did not prevent delete operation");
        }

        // Try to create directory in read-only mode - should fail
        boolean mkdirFailedAsExpected = false;
        try {
            NfsIO.createDirectory(configRo, "/readonly-dir");
        } catch (IOException e) {
            if (e.getMessage().contains("read-only")) {
                mkdirFailedAsExpected = true;
            }
        }

        if (!mkdirFailedAsExpected) {
            throw new IllegalStateException("Read-only mode did not prevent mkdir operation");
        }

        System.out.println("  [PASS] Read-Only Mode");
    }

    /**
     * Recursively clean up temp directory.
     */
    private static void cleanup(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (Exception ex) { throw new RuntimeException(ex); }
                });
        }
    }
}
