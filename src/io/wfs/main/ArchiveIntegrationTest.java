package io.wfs.main;

import io.wfs.core.extractor.ExtZipFsProvider;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

final class ArchiveIntegrationTest {

    private ArchiveIntegrationTest() {
    }

    static void run() throws Exception {
        Path tempRoot = Files.createTempDirectory("weefs-integration-");
        try {
            roundTrip(tempRoot.resolve("sample.zip"), "zip");
            roundTrip(tempRoot.resolve("sample.tar"), "tar");
            roundTrip(tempRoot.resolve("sample.tar.gz"), "targz");
            roundTrip(tempRoot.resolve("sample.tar.bz2"), "tarbz2");
            roundTrip(tempRoot.resolve("sample.tar.xz"), "tarxz");
            roundTrip(tempRoot.resolve("sample.tar.lzma"), "tarlzma");
            singleFileRoundTrip(tempRoot.resolve("single.gz"), "single-gz");
            singleFileRoundTrip(tempRoot.resolve("single.bz2"), "single-bz2");
            singleFileRoundTrip(tempRoot.resolve("single.xz"), "single-xz");
            singleFileRoundTrip(tempRoot.resolve("single.lzma"), "single-lzma");
            System.out.println("All integration checks passed.");
        } finally {
            cleanup(tempRoot);
        }
    }

    private static void roundTrip(Path archivePath, String kind) throws Exception {
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");

        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.createDirectories(fs.getPath("/dir"));
            Files.writeString(fs.getPath("/hello.txt"),   "root-"   + kind, StandardOpenOption.CREATE);
            Files.writeString(fs.getPath("/dir/nested.txt"), "nested-" + kind, StandardOpenOption.CREATE);
        }

        if (!Files.exists(archivePath) || Files.size(archivePath) == 0L) {
            throw new IllegalStateException("Archive was not written: " + archivePath);
        }

        try (FileSystem fs = provider.newFileSystem(uri, Map.of("readOnly", "true"))) {
            assertContent(fs.getPath("/hello.txt"),      "root-"   + kind);
            assertContent(fs.getPath("/dir/nested.txt"), "nested-" + kind);
        }

        System.out.println("  [PASS] " + kind);
    }

    private static void singleFileRoundTrip(Path archivePath, String contentSeed) throws Exception {
        ExtZipFsProvider provider = new ExtZipFsProvider();
        URI uri = URI.create("xzip:" + archivePath.toUri() + "!/");

        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Files.writeString(fs.getPath("/payload.txt"), contentSeed, StandardOpenOption.CREATE);
        }

        if (!Files.exists(archivePath) || Files.size(archivePath) == 0L) {
            throw new IllegalStateException("Archive was not written: " + archivePath);
        }

        try (FileSystem fs = provider.newFileSystem(uri, Map.of("readOnly", "true"))) {
            assertContent(fs.getPath("/" + deriveOutputName(archivePath)), contentSeed);
        }

        System.out.println("  [PASS] " + archivePath.getFileName());
    }

    private static String deriveOutputName(Path archivePath) {
        String name = archivePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    private static void assertContent(Path path, String expected) throws Exception {
        String actual = Files.readString(path);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Expected: " + expected + ", got: " + actual + " in " + path);
        }
    }

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
