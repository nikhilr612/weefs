package io.wfs.core.extractor;

import java.net.URI;

/**
 * Unit tests for ExtZipParsedUri (package-private URI parser).
 * Tests parsing edge cases, validation, and URI decoding.
 */
public final class CoreExtractorUriTest {

    private CoreExtractorUriTest() {
    }

    public static void run() throws Exception {
        System.out.println("  Running extractor URI parsing tests...");

        testParseStandardUri();
        testParseUriWithEntryPath();
        testParseUriNoEntry();
        testParseUriBangWithoutSlash();
        testParseUriDoubleSlashStripped();
        testParseNullUriThrows();
        testParseNullSchemeThrows();
        testParseBlankSchemeThrows();
        testParseWrongSchemeThrows();
        testParseEmptyBodyUsesGetPath();
        testParseUrlEncodedPath();
        testZipArchiveFormatSupports();
        testZipArchiveFormatSupportsJar();
        testZipArchiveFormatRejectsNonZip();
        testTarArchiveFormatSupports();
        testTarArchiveFormatRejectsNonTar();
        testArchiveFormatsResolveJar();

        System.out.println("  All extractor URI parsing tests passed.");
    }

    // ── ExtZipParsedUri tests ──────────────────────────────────────────

    private static void testParseStandardUri() {
        System.out.println("    [TEST] Parse standard xzip URI");
        URI uri = URI.create("xzip:file:///tmp/test.zip!/");
        ExtZipParsedUri parsed = ExtZipParsedUri.parse(uri, "xzip");
        if (parsed.archivePart().isEmpty())
            throw fail("archive part should not be empty");
        assertEqual("", parsed.entryPart(), "entry part for root");
        System.out.println("    [PASS] Parse standard xzip URI");
    }

    private static void testParseUriWithEntryPath() {
        System.out.println("    [TEST] Parse URI with entry path");
        URI uri = URI.create("xzip:file:///tmp/test.zip!/dir/file.txt");
        ExtZipParsedUri parsed = ExtZipParsedUri.parse(uri, "xzip");
        assertEqual("dir/file.txt", parsed.entryPart(), "entry path");
        System.out.println("    [PASS] Parse URI with entry path");
    }

    private static void testParseUriNoEntry() {
        System.out.println("    [TEST] Parse URI with no !/ separator");
        URI uri = URI.create("xzip:file:///tmp/test.zip");
        ExtZipParsedUri parsed = ExtZipParsedUri.parse(uri, "xzip");
        if (parsed.archivePart().isEmpty())
            throw fail("archive part should not be empty");
        // Entry should be empty when no !/ separator
        System.out.println("    [PASS] Parse URI with no !/ separator");
    }

    private static void testParseUriBangWithoutSlash() {
        System.out.println("    [TEST] Parse URI ending with ! but no /");
        URI uri = URI.create("xzip:file:///tmp/test.zip!");
        ExtZipParsedUri parsed = ExtZipParsedUri.parse(uri, "xzip");
        if (parsed.archivePart().isEmpty())
            throw fail("archive part should not be empty");
        System.out.println("    [PASS] Parse URI ending with ! but no /");
    }

    private static void testParseUriDoubleSlashStripped() {
        System.out.println("    [TEST] Parse URI with // prefix stripped");
        URI uri = URI.create("xzip:///tmp/test.zip!/");
        ExtZipParsedUri parsed = ExtZipParsedUri.parse(uri, "xzip");
        // The // prefix from the scheme-specific part should be stripped
        if (parsed.archivePart().startsWith("//"))
            throw fail("Double slash should be stripped, got: " + parsed.archivePart());
        System.out.println("    [PASS] Parse URI with // prefix stripped");
    }

    private static void testParseNullUriThrows() {
        System.out.println("    [TEST] Parse null URI throws");
        expectException(() -> ExtZipParsedUri.parse(null, "xzip"),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Parse null URI throws");
    }

    private static void testParseNullSchemeThrows() {
        System.out.println("    [TEST] Parse null scheme throws");
        URI uri = URI.create("xzip:file:///tmp/test.zip!/");
        expectException(() -> ExtZipParsedUri.parse(uri, null),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Parse null scheme throws");
    }

    private static void testParseBlankSchemeThrows() {
        System.out.println("    [TEST] Parse blank scheme throws");
        URI uri = URI.create("xzip:file:///tmp/test.zip!/");
        expectException(() -> ExtZipParsedUri.parse(uri, "  "),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Parse blank scheme throws");
    }

    private static void testParseWrongSchemeThrows() {
        System.out.println("    [TEST] Parse wrong scheme throws");
        URI uri = URI.create("badscheme:file:///tmp/test.zip!/");
        expectException(() -> ExtZipParsedUri.parse(uri, "xzip"),
                IllegalArgumentException.class);
        System.out.println("    [PASS] Parse wrong scheme throws");
    }

    private static void testParseEmptyBodyUsesGetPath() {
        System.out.println("    [TEST] Parse URI falls back to getPath()");
        // Opaque URI with path — tests the fallback
        URI uri = URI.create("xzip:/tmp/test.zip!/entry.txt");
        ExtZipParsedUri parsed = ExtZipParsedUri.parse(uri, "xzip");
        if (parsed.archivePart().isEmpty())
            throw fail("Should have extracted archive part from path");
        System.out.println("    [PASS] Parse URI falls back to getPath()");
    }

    private static void testParseUrlEncodedPath() {
        System.out.println("    [TEST] Parse URL-encoded path");
        URI uri = URI.create("xzip:file:///tmp/my%20archive.zip!/");
        ExtZipParsedUri parsed = ExtZipParsedUri.parse(uri, "xzip");
        if (!parsed.archivePart().contains("my archive"))
            throw fail("URL-encoded spaces should be decoded, got: " + parsed.archivePart());
        System.out.println("    [PASS] Parse URL-encoded path");
    }

    // ── ArchiveFormat implementation tests ─────────────────────────────

    private static void testZipArchiveFormatSupports() {
        System.out.println("    [TEST] ZipArchiveFormat supports .zip");
        ZipArchiveFormat fmt = new ZipArchiveFormat();
        if (!fmt.supports(java.nio.file.Path.of("test.zip"))) throw fail("Should support .zip");
        if (!fmt.supports(java.nio.file.Path.of("TEST.ZIP"))) throw fail("Should support .ZIP (case)");
        System.out.println("    [PASS] ZipArchiveFormat supports .zip");
    }

    private static void testZipArchiveFormatSupportsJar() {
        System.out.println("    [TEST] ZipArchiveFormat supports .jar/.war");
        ZipArchiveFormat fmt = new ZipArchiveFormat();
        if (!fmt.supports(java.nio.file.Path.of("app.jar"))) throw fail("Should support .jar");
        if (!fmt.supports(java.nio.file.Path.of("app.war"))) throw fail("Should support .war");
        System.out.println("    [PASS] ZipArchiveFormat supports .jar/.war");
    }

    private static void testZipArchiveFormatRejectsNonZip() {
        System.out.println("    [TEST] ZipArchiveFormat rejects .tar");
        ZipArchiveFormat fmt = new ZipArchiveFormat();
        if (fmt.supports(java.nio.file.Path.of("data.tar"))) throw fail("Should not support .tar");
        if (fmt.supports(java.nio.file.Path.of("data.rar"))) throw fail("Should not support .rar");
        System.out.println("    [PASS] ZipArchiveFormat rejects .tar");
    }

    private static void testTarArchiveFormatSupports() {
        System.out.println("    [TEST] TarArchiveFormat supports .tar");
        TarArchiveFormat fmt = new TarArchiveFormat();
        if (!fmt.supports(java.nio.file.Path.of("data.tar"))) throw fail("Should support .tar");
        if (!fmt.supports(java.nio.file.Path.of("DATA.TAR"))) throw fail("Should support .TAR (case)");
        System.out.println("    [PASS] TarArchiveFormat supports .tar");
    }

    private static void testTarArchiveFormatRejectsNonTar() {
        System.out.println("    [TEST] TarArchiveFormat rejects .zip");
        TarArchiveFormat fmt = new TarArchiveFormat();
        if (fmt.supports(java.nio.file.Path.of("data.zip"))) throw fail("Should not support .zip");
        if (fmt.supports(java.nio.file.Path.of("data.gz"))) throw fail("Should not support .gz");
        System.out.println("    [PASS] TarArchiveFormat rejects .zip");
    }

    private static void testArchiveFormatsResolveJar() throws Exception {
        System.out.println("    [TEST] ArchiveFormats resolves .jar");
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("test", ".jar");
        try {
            ArchiveFormat format = ArchiveFormats.resolve(tmp);
            if (format == null)
                throw fail("resolve returned null for .jar");
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
        System.out.println("    [PASS] ArchiveFormats resolves .jar");
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

    private static IllegalStateException fail(String msg) {
        return new IllegalStateException("[FAIL] " + msg);
    }
}
