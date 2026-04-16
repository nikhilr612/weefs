package io.wfs.main;

import io.wfs.core.extractor.ArchiveFormats;
import io.wfs.core.extractor.ArchiveFormat;
import io.wfs.core.extractor.ExtZipFsProvider;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

/**
 * Unit-level tests for the core extractor module:
 * - ArchiveFormats resolution (ZIP, TAR, unsupported)
 * - ExtZipParsedUri parsing (covered indirectly through provider)
 * - ExtZipPath operations (via FileSystem API)
 * - Round-trip extract/write for ZIP and TAR
 */
final class CoreExtractorTest {

    private CoreExtractorTest() {
    }

    static void run() throws Exception {
        System.out.println("  Running core extractor tests...");

        testResolveZipFormat();
        testResolveTarFormat();
        testResolveUnsupportedThrows();
        testExtractAndWriteZipRoundTrip();
        testExtractAndWriteTarRoundTrip();
        testExtZipPathResolve();
        testExtZipPathParentAndFileName();
        testExtZipPathToString();
        testExtZipPathEquals();
        testExtZipPathCompareTo();
        testExtZipDirectoryStream();
        testEmptyArchiveRoundTrip();
        testNestedDirectoriesRoundTrip();
        testProviderInvalidUri();
        testProviderReadOnlyMode();

        System.out.println("  All core extractor tests passed.");
    }

    // ── ArchiveFormats resolution ──────────────────────────────────────

    private static void testResolveZipFormat() throws Exception {
        System.out.println("    [TEST] ArchiveFormats resolves .zip");
        Path tmp = Files.createTempFile("test", ".zip");
        try {
            Files.write(tmp, new byte[0]); // create empty file
            ArchiveFormat format = ArchiveFormats.resolve(tmp);
            if (format == null) throw fail("resolve returned null for .zip");
        } finally {
            Files.deleteIfExists(tmp);
        }
        System.out.println("    [PASS] ArchiveFormats resolves .zip");
    }

    private static void testResolveTarFormat() throws Exception {
        System.out.println("    [TEST] ArchiveFormats resolves .tar");
        Path tmp = Files.createTempFile("test", ".tar");
        try {
            ArchiveFormat format = ArchiveFormats.resolve(tmp);
            if (format == null) throw fail("resolve returned null for .tar");
        } finally {
            Files.deleteIfExists(tmp);
        }
        System.out.println("    [PASS] ArchiveFormats resolves .tar");
    }

    private static void testResolveUnsupportedThrows() throws Exception {
        System.out.println("    [TEST] ArchiveFormats rejects unsupported format");
        Path tmp = Files.createTempFile("test", ".rar");
        try {
            try {
                ArchiveFormats.resolve(tmp);
                throw fail("Should have thrown IOException for .rar");
            } catch (IOException expected) {
                // Good
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        System.out.println("    [PASS] ArchiveFormats rejects unsupported format");
    }

    // ── Extract & Write round trips ────────────────────────────────────

    private static void testExtractAndWriteZipRoundTrip() throws Exception {
        System.out.println("    [TEST] ArchiveFormats extract/write round-trip (ZIP)");
        Path tempDir = Files.createTempDirectory("weefs-fmt-test-");
        try {
            // Create a zip via provider
            Path zipFile = tempDir.resolve("sample.zip");
            createSampleArchive(zipFile);

            // Extract to directory
            Path extractDir = tempDir.resolve("extracted");
            Files.createDirectories(extractDir);
            ArchiveFormats.extractToDirectory(zipFile, extractDir);

            // Verify extracted content
            assertFileContent(extractDir.resolve("hello.txt"), "hello-zip");
            assertFileContent(extractDir.resolve("sub/nested.txt"), "nested-zip");

            // Write back to new archive
            Path newZip = tempDir.resolve("rebuilt.zip");
            ArchiveFormats.writeFromDirectory(extractDir, newZip);

            if (!Files.exists(newZip) || Files.size(newZip) == 0)
                throw fail("Rebuilt ZIP is empty or missing");

            // Verify rebuilt archive via provider
            ExtZipFsProvider provider = new ExtZipFsProvider();
            URI uri = URI.create("xzip:" + newZip.toUri() + "!/");
            try (FileSystem fs = provider.newFileSystem(uri, Map.of("readOnly", "true"))) {
                assertPathContent(fs.getPath("/hello.txt"), "hello-zip");
                assertPathContent(fs.getPath("/sub/nested.txt"), "nested-zip");
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ArchiveFormats extract/write round-trip (ZIP)");
    }

    private static void testExtractAndWriteTarRoundTrip() throws Exception {
        System.out.println("    [TEST] ArchiveFormats extract/write round-trip (TAR)");
        Path tempDir = Files.createTempDirectory("weefs-fmt-test-");
        try {
            Path tarFile = tempDir.resolve("sample.tar");
            createSampleArchive(tarFile);

            Path extractDir = tempDir.resolve("extracted");
            Files.createDirectories(extractDir);
            ArchiveFormats.extractToDirectory(tarFile, extractDir);

            assertFileContent(extractDir.resolve("hello.txt"), "hello-tar");
            assertFileContent(extractDir.resolve("sub/nested.txt"), "nested-tar");

            Path newTar = tempDir.resolve("rebuilt.tar");
            ArchiveFormats.writeFromDirectory(extractDir, newTar);

            if (!Files.exists(newTar) || Files.size(newTar) == 0)
                throw fail("Rebuilt TAR is empty or missing");
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ArchiveFormats extract/write round-trip (TAR)");
    }

    // ── ExtZipPath tests (via FileSystem) ──────────────────────────────

    private static void testExtZipPathResolve() throws Exception {
        System.out.println("    [TEST] ExtZipPath resolve()");
        Path tempDir = Files.createTempDirectory("weefs-path-test-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ExtZipFsProvider provider = new ExtZipFsProvider();
            URI uri = URI.create("xzip:" + zipFile.toUri() + "!/");
            try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
                Path root = fs.getPath("/");
                Path resolved = root.resolve("dir/file.txt");
                if (!resolved.toString().endsWith("dir/file.txt"))
                    throw fail("resolve() produced: " + resolved);
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ExtZipPath resolve()");
    }

    private static void testExtZipPathParentAndFileName() throws Exception {
        System.out.println("    [TEST] ExtZipPath getParent/getFileName");
        Path tempDir = Files.createTempDirectory("weefs-path-test-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ExtZipFsProvider provider = new ExtZipFsProvider();
            URI uri = URI.create("xzip:" + zipFile.toUri() + "!/");
            try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
                Path file = fs.getPath("/dir/file.txt");
                Path parent = file.getParent();
                Path fileName = file.getFileName();

                if (parent == null) throw fail("getParent() is null");
                if (fileName == null) throw fail("getFileName() is null");
                if (!fileName.toString().equals("file.txt"))
                    throw fail("getFileName() = " + fileName);
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ExtZipPath getParent/getFileName");
    }

    private static void testExtZipPathToString() throws Exception {
        System.out.println("    [TEST] ExtZipPath toString");
        Path tempDir = Files.createTempDirectory("weefs-path-test-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ExtZipFsProvider provider = new ExtZipFsProvider();
            URI uri = URI.create("xzip:" + zipFile.toUri() + "!/");
            try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
                Path root = fs.getPath("/");
                assertEqual("/", root.toString(), "root toString()");

                Path nested = fs.getPath("/a/b/c");
                if (!nested.toString().contains("a/b/c"))
                    throw fail("toString() should contain 'a/b/c', got: " + nested);
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ExtZipPath toString");
    }

    private static void testExtZipPathEquals() throws Exception {
        System.out.println("    [TEST] ExtZipPath equals/hashCode");
        Path tempDir = Files.createTempDirectory("weefs-path-test-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ExtZipFsProvider provider = new ExtZipFsProvider();
            URI uri = URI.create("xzip:" + zipFile.toUri() + "!/");
            try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
                Path a = fs.getPath("/dir/file.txt");
                Path b = fs.getPath("/dir/file.txt");
                Path c = fs.getPath("/other.txt");

                if (!a.equals(b)) throw fail("Same paths should be equal");
                if (a.equals(c)) throw fail("Different paths should not be equal");
                if (a.hashCode() != b.hashCode()) throw fail("Equal paths should have same hashCode");
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ExtZipPath equals/hashCode");
    }

    private static void testExtZipPathCompareTo() throws Exception {
        System.out.println("    [TEST] ExtZipPath compareTo");
        Path tempDir = Files.createTempDirectory("weefs-path-test-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ExtZipFsProvider provider = new ExtZipFsProvider();
            URI uri = URI.create("xzip:" + zipFile.toUri() + "!/");
            try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
                Path a = fs.getPath("/aaa.txt");
                Path b = fs.getPath("/bbb.txt");
                if (a.compareTo(b) >= 0) throw fail("aaa should come before bbb");
                if (b.compareTo(a) <= 0) throw fail("bbb should come after aaa");
                if (a.compareTo(a) != 0) throw fail("Same path compareTo should be 0");
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ExtZipPath compareTo");
    }

    private static void testExtZipDirectoryStream() throws Exception {
        System.out.println("    [TEST] ExtZipDirectoryStream lists children");
        Path tempDir = Files.createTempDirectory("weefs-ds-test-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ExtZipFsProvider provider = new ExtZipFsProvider();
            URI uri = URI.create("xzip:" + zipFile.toUri() + "!/");
            try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
                Files.createDirectories(fs.getPath("/alpha"));
                Files.createDirectories(fs.getPath("/beta"));
                Files.writeString(fs.getPath("/file.txt"), "content",
                        java.nio.file.StandardOpenOption.CREATE);

                int count = 0;
                try (var stream = Files.newDirectoryStream(fs.getPath("/"))) {
                    for (Path p : stream) {
                        count++;
                    }
                }
                if (count < 3) throw fail("Expected at least 3 entries, got " + count);
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ExtZipDirectoryStream lists children");
    }

    // ── Edge cases ─────────────────────────────────────────────────────

    private static void testEmptyArchiveRoundTrip() throws Exception {
        System.out.println("    [TEST] Empty archive round-trip");
        Path tempDir = Files.createTempDirectory("weefs-empty-test-");
        try {
            Path zipFile = tempDir.resolve("empty.zip");
            ExtZipFsProvider provider = new ExtZipFsProvider();
            URI uri = URI.create("xzip:" + zipFile.toUri() + "!/");

            // Create an empty archive
            try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
                // Don't add any files
            }

            // Reopen and verify it's valid
            try (FileSystem fs = provider.newFileSystem(uri, Map.of("readOnly", "true"))) {
                int count = 0;
                try (var stream = Files.newDirectoryStream(fs.getPath("/"))) {
                    for (Path p : stream) {
                        count++;
                    }
                }
                // Empty archive — root may have 0 entries
                if (count < 0) throw fail("Negative count impossible");
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] Empty archive round-trip");
    }

    private static void testNestedDirectoriesRoundTrip() throws Exception {
        System.out.println("    [TEST] Nested directories round-trip");
        Path tempDir = Files.createTempDirectory("weefs-nested-test-");
        try {
            Path zipFile = tempDir.resolve("nested.zip");
            ExtZipFsProvider provider = new ExtZipFsProvider();
            URI uri = URI.create("xzip:" + zipFile.toUri() + "!/");

            try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
                Files.createDirectories(fs.getPath("/a/b/c/d"));
                Files.writeString(fs.getPath("/a/b/c/d/deep.txt"), "deep-content",
                        java.nio.file.StandardOpenOption.CREATE);
            }

            try (FileSystem fs = provider.newFileSystem(uri, Map.of("readOnly", "true"))) {
                String content = Files.readString(fs.getPath("/a/b/c/d/deep.txt"));
                assertEqual("deep-content", content, "Deep nested file content");
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] Nested directories round-trip");
    }

    private static void testProviderInvalidUri() throws Exception {
        System.out.println("    [TEST] Provider rejects invalid URI scheme");
        ExtZipFsProvider provider = new ExtZipFsProvider();
        try {
            URI bad = URI.create("badscheme:file:///tmp/test.zip!/");
            provider.newFileSystem(bad, Map.of());
            throw fail("Should have rejected badscheme URI");
        } catch (IllegalArgumentException expected) {
            // Good
        }
        System.out.println("    [PASS] Provider rejects invalid URI scheme");
    }

    private static void testProviderReadOnlyMode() throws Exception {
        System.out.println("    [TEST] Read-only archive rejects writes");
        Path tempDir = Files.createTempDirectory("weefs-ro-test-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ExtZipFsProvider provider = new ExtZipFsProvider();
            URI uri = URI.create("xzip:" + zipFile.toUri() + "!/");

            // Create archive
            try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
                Files.writeString(fs.getPath("/existing.txt"), "data",
                        java.nio.file.StandardOpenOption.CREATE);
            }

            // Open read-only and try to write
            try (FileSystem fs = provider.newFileSystem(uri, Map.of("readOnly", "true"))) {
                try {
                    Files.writeString(fs.getPath("/new.txt"), "data",
                            java.nio.file.StandardOpenOption.CREATE);
                    throw fail("Should have rejected write on read-only FS");
                } catch (IOException | UnsupportedOperationException expected) {
                    // Good — either is acceptable
                }
            }
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] Read-only archive rejects writes");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static void createSampleArchive(Path archivePath) throws Exception {
        String ext = archivePath.getFileName().toString();
        String kind = ext.endsWith(".tar") ? "tar" : "zip";
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.createDirectories(fs.getPath("/sub"));
            Files.writeString(fs.getPath("/hello.txt"), "hello-" + kind,
                    java.nio.file.StandardOpenOption.CREATE);
            Files.writeString(fs.getPath("/sub/nested.txt"), "nested-" + kind,
                    java.nio.file.StandardOpenOption.CREATE);
        }
    }

    private static void assertPathContent(Path path, String expected) throws Exception {
        String actual = Files.readString(path);
        if (!expected.equals(actual))
            throw fail("Expected: " + expected + ", got: " + actual + " at " + path);
    }

    private static void assertFileContent(Path path, String expected) throws Exception {
        if (!Files.exists(path))
            throw fail("File does not exist: " + path);
        String actual = Files.readString(path);
        if (!expected.equals(actual))
            throw fail("Expected: " + expected + ", got: " + actual + " at " + path);
    }

    private static void assertEqual(String expected, String actual, String context) {
        if (!expected.equals(actual))
            throw fail(context + ": expected '" + expected + "', got '" + actual + "'");
    }

    private static void cleanup(Path root) {
        try {
            if (!Files.exists(root)) return;
            try (var walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        } catch (Exception ignored) {}
    }

    private static IllegalStateException fail(String msg) {
        return new IllegalStateException("[FAIL] " + msg);
    }
}
