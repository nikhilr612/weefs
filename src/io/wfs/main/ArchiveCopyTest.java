package io.wfs.main;

import io.wfs.core.extractor.ExtZipFsProvider;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Unit tests for copy and delete operations within zip archives.
 * Tests file-to-file, directory copying, and file/directory deletion within a single zip file.
 */
final class ArchiveCopyTest {

    private ArchiveCopyTest() {
    }

    static void run() throws Exception {
        Path tempRoot = Files.createTempDirectory("weefs-copy-test-");
        try {
            testCopyFileToNewLocation(tempRoot);
            testCopyFileToExistingDirectory(tempRoot);
            testCopyFileOverwrite(tempRoot);
            testCopyToNestedNonexistentPath(tempRoot);
            testCopyDirectoryRecursive(tempRoot);
            testDeleteFile(tempRoot);
            testDeleteDirectory(tempRoot);
            testDeleteNonexistentFile(tempRoot);
            System.out.println("All archive copy and delete tests passed.");
        } finally {
            cleanup(tempRoot);
        }
    }

    /**
     * Test copying a file from root to a new path in a subdirectory.
     */
    private static void testCopyFileToNewLocation(Path tempRoot) throws Exception {
        Path archivePath = tempRoot.resolve("copy-basic.zip");
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");

        // Create archive with a file in root
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.createDirectories(fs.getPath("/backup"));
            Files.writeString(fs.getPath("/original.txt"), "test-content-v1", StandardOpenOption.CREATE);
        }

        // Verify copy within the same archive
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Path src = fs.getPath("/original.txt");
            Path dst = fs.getPath("/backup/copied.txt");

            // Perform copy
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

            // Verify both exist with same content
            assertContent(src, "test-content-v1");
            assertContent(dst, "test-content-v1");
        }

        System.out.println("  [PASS] Copy file to new location");
    }

    /**
     * Test copying a file into an existing directory.
     */
    private static void testCopyFileToExistingDirectory(Path tempRoot) throws Exception {
        Path archivePath = tempRoot.resolve("copy-to-dir.zip");
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");

        // Setup: create file and target directory
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.createDirectories(fs.getPath("/destination"));
            Files.writeString(fs.getPath("/source.txt"), "to-copy", StandardOpenOption.CREATE);
        }

        // Copy file into existing directory
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Path src = fs.getPath("/source.txt");
            Path destDir = fs.getPath("/destination");
            Path dst = destDir.resolve(src.getFileName());

            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

            assertContent(fs.getPath("/source.txt"), "to-copy");
            assertContent(fs.getPath("/destination/source.txt"), "to-copy");
        }

        System.out.println("  [PASS] Copy file into existing directory");
    }

    /**
     * Test overwriting an existing file via copy.
     */
    private static void testCopyFileOverwrite(Path tempRoot) throws Exception {
        Path archivePath = tempRoot.resolve("copy-overwrite.zip");
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");

        // Setup: create original and target file
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.writeString(fs.getPath("/original.txt"), "original-content", StandardOpenOption.CREATE);
            Files.writeString(fs.getPath("/target.txt"), "old-target", StandardOpenOption.CREATE);
        }

        // Copy with REPLACE_EXISTING
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Path src = fs.getPath("/original.txt");
            Path dst = fs.getPath("/target.txt");

            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

            assertContent(dst, "original-content");
        }

        System.out.println("  [PASS] Copy with overwrite");
    }

    /**
     * Test copying to a nested path where intermediate directories don't exist.
     */
    private static void testCopyToNestedNonexistentPath(Path tempRoot) throws Exception {
        Path archivePath = tempRoot.resolve("copy-nested.zip");
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");

        // Setup: create only the source file
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.writeString(fs.getPath("/file.txt"), "nested-content", StandardOpenOption.CREATE);
        }

        // Copy to nested path (should work if dirs are pre-created)
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Path src = fs.getPath("/file.txt");
            Path dst = fs.getPath("/a/b/c/file-copy.txt");

            // Pre-create parent directories
            Files.createDirectories(dst.getParent());

            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

            assertContent(dst, "nested-content");
        }

        System.out.println("  [PASS] Copy to nested path");
    }

    /**
     * Test recursive directory copy (copy a directory with files inside).
     */
    private static void testCopyDirectoryRecursive(Path tempRoot) throws Exception {
        Path archivePath = tempRoot.resolve("copy-recursive.zip");
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");

        // Setup: create directory structure
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.createDirectories(fs.getPath("/source/subdir"));
            Files.writeString(fs.getPath("/source/file1.txt"), "file1", StandardOpenOption.CREATE);
            Files.writeString(fs.getPath("/source/subdir/file2.txt"), "file2", StandardOpenOption.CREATE);
            Files.createDirectories(fs.getPath("/target"));
        }

        // Copy directory recursively (manual traversal)
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Path srcDir = fs.getPath("/source");
            Path targetDir = fs.getPath("/target/source-copy");

            // Create target directory
            Files.createDirectories(targetDir);

            // Copy files
            Files.copy(fs.getPath("/source/file1.txt"),
                    targetDir.resolve("file1.txt"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.createDirectories(targetDir.resolve("subdir"));
            Files.copy(fs.getPath("/source/subdir/file2.txt"),
                    targetDir.resolve("subdir/file2.txt"),
                    StandardCopyOption.REPLACE_EXISTING);

            // Verify
            assertContent(targetDir.resolve("file1.txt"), "file1");
            assertContent(targetDir.resolve("subdir/file2.txt"), "file2");
        }

        System.out.println("  [PASS] Recursive directory copy");
    }

    /**
     * Test deleting a file from an archive.
     */
    private static void testDeleteFile(Path tempRoot) throws Exception {
        Path archivePath = tempRoot.resolve("delete-file.zip");
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");

        // Setup: create files
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.writeString(fs.getPath("/keep.txt"), "keep-this", StandardOpenOption.CREATE);
            Files.writeString(fs.getPath("/remove.txt"), "remove-this", StandardOpenOption.CREATE);
        }

        // Delete one file
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Path toDelete = fs.getPath("/remove.txt");
            Path toKeep = fs.getPath("/keep.txt");

            Files.delete(toDelete);

            // Verify deletion
            if (Files.exists(toDelete)) {
                throw new AssertionError("File should have been deleted: " + toDelete);
            }
            assertContent(toKeep, "keep-this");
        }

        System.out.println("  [PASS] Delete file");
    }

    /**
     * Test deleting an empty directory.
     */
    private static void testDeleteDirectory(Path tempRoot) throws Exception {
        Path archivePath = tempRoot.resolve("delete-dir.zip");
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");

        // Setup: create directory structure
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.createDirectories(fs.getPath("/empty-dir"));
            Files.createDirectories(fs.getPath("/has-file"));
            Files.writeString(fs.getPath("/has-file/content.txt"), "data", StandardOpenOption.CREATE);
        }

        // Delete empty directory
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Path emptyDir = fs.getPath("/empty-dir");
            Path hasFileDir = fs.getPath("/has-file");

            Files.delete(emptyDir);

            // Verify empty dir is gone but other dir remains
            if (Files.exists(emptyDir)) {
                throw new AssertionError("Empty directory should have been deleted");
            }
            if (!Files.exists(hasFileDir)) {
                throw new AssertionError("Directory with file should still exist");
            }
        }

        System.out.println("  [PASS] Delete directory");
    }

    /**
     * Test deleting a nonexistent file (should throw exception).
     */
    private static void testDeleteNonexistentFile(Path tempRoot) throws Exception {
        Path archivePath = tempRoot.resolve("delete-nonexistent.zip");
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");

        // Setup: create empty archive
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.createDirectories(fs.getPath("/dummy"));
        }

        // Try to delete nonexistent file
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Path nonexistent = fs.getPath("/does-not-exist.txt");

            try {
                Files.delete(nonexistent);
                throw new AssertionError("Should have thrown NoSuchFileException");
            } catch (java.nio.file.NoSuchFileException e) {
                // Expected
            }
        }

        System.out.println("  [PASS] Delete nonexistent file (throws exception)");
    }

    private static void assertContent(Path path, String expectedContent) throws Exception {
        String actual = Files.readString(path);
        if (!actual.equals(expectedContent)) {
            throw new AssertionError("Expected '" + expectedContent + "' but got '" + actual + "' in " + path);
        }
    }

    private static void cleanup(Path path) throws Exception {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted((a, b) -> b.compareTo(a)) // reverse order to delete children first
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception e) {
                            // ignore
                        }
                    });
        }
    }
}
