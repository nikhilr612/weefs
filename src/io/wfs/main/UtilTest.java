package io.wfs.main;

import io.wfs.ui.util.FileTypeDetector;
import io.wfs.ui.util.FileTypeDetector.FileType;
import io.wfs.ui.util.SwingUtils;

/**
 * Unit tests for UI utility classes:
 * - FileTypeDetector: extension-based file type classification
 * - SwingUtils.formatFileSize: human-readable byte formatting
 */
final class UtilTest {

    private UtilTest() {
    }

    static void run() throws Exception {
        System.out.println("  Running utility tests...");

        // FileTypeDetector tests
        testDetectText();
        testDetectImage();
        testDetectBinary();
        testDetectUnknown();
        testDetectNull();
        testDetectEmpty();
        testDetectCaseInsensitive();
        testMimeDescriptionText();
        testMimeDescriptionImage();
        testMimeDescriptionBinary();
        testMimeDescriptionUnknown();

        // SwingUtils.formatFileSize tests
        testFormatBytes();
        testFormatKilobytes();
        testFormatMegabytes();
        testFormatGigabytes();
        testFormatZeroBytes();
        testFormatBoundaryKB();
        testFormatBoundaryMB();
        testFormatBoundaryGB();

        System.out.println("  All utility tests passed.");
    }

    // ── FileTypeDetector tests ─────────────────────────────────────────

    private static void testDetectText() {
        System.out.println("    [TEST] FileTypeDetector detects text extensions");
        assertType("java", FileType.TEXT, "java");
        assertType("txt", FileType.TEXT, "txt");
        assertType("json", FileType.TEXT, "json");
        assertType("xml", FileType.TEXT, "xml");
        assertType("py", FileType.TEXT, "py");
        assertType("sh", FileType.TEXT, "sh");
        assertType("md", FileType.TEXT, "md");
        assertType("html", FileType.TEXT, "html");
        assertType("sql", FileType.TEXT, "sql");
        System.out.println("    [PASS] FileTypeDetector detects text extensions");
    }

    private static void testDetectImage() {
        System.out.println("    [TEST] FileTypeDetector detects image extensions");
        assertType("png", FileType.IMAGE, "png");
        assertType("jpg", FileType.IMAGE, "jpg");
        assertType("jpeg", FileType.IMAGE, "jpeg");
        assertType("gif", FileType.IMAGE, "gif");
        assertType("svg", FileType.IMAGE, "svg");
        assertType("webp", FileType.IMAGE, "webp");
        System.out.println("    [PASS] FileTypeDetector detects image extensions");
    }

    private static void testDetectBinary() {
        System.out.println("    [TEST] FileTypeDetector detects binary extensions");
        assertType("class", FileType.BINARY, "class");
        assertType("jar", FileType.BINARY, "jar");
        assertType("exe", FileType.BINARY, "exe");
        assertType("pdf", FileType.BINARY, "pdf");
        assertType("zip", FileType.BINARY, "zip");
        assertType("tar", FileType.BINARY, "tar");
        System.out.println("    [PASS] FileTypeDetector detects binary extensions");
    }

    private static void testDetectUnknown() {
        System.out.println("    [TEST] FileTypeDetector returns UNKNOWN for unrecognized");
        assertType("xyz", FileType.UNKNOWN, "xyz");
        assertType("abc123", FileType.UNKNOWN, "abc123");
        System.out.println("    [PASS] FileTypeDetector returns UNKNOWN for unrecognized");
    }

    private static void testDetectNull() {
        System.out.println("    [TEST] FileTypeDetector null → TEXT");
        assertType(null, FileType.TEXT, "null");
        System.out.println("    [PASS] FileTypeDetector null → TEXT");
    }

    private static void testDetectEmpty() {
        System.out.println("    [TEST] FileTypeDetector empty → TEXT");
        assertType("", FileType.TEXT, "empty");
        System.out.println("    [PASS] FileTypeDetector empty → TEXT");
    }

    private static void testDetectCaseInsensitive() {
        System.out.println("    [TEST] FileTypeDetector case-insensitive");
        assertType("JAVA", FileType.TEXT, "JAVA");
        assertType("PNG", FileType.IMAGE, "PNG");
        assertType("JAR", FileType.BINARY, "JAR");
        System.out.println("    [PASS] FileTypeDetector case-insensitive");
    }

    private static void testMimeDescriptionText() {
        System.out.println("    [TEST] FileTypeDetector getMimeDescription text");
        assertEqual("Text File", FileTypeDetector.getMimeDescription("txt"), "txt mime");
        System.out.println("    [PASS] FileTypeDetector getMimeDescription text");
    }

    private static void testMimeDescriptionImage() {
        System.out.println("    [TEST] FileTypeDetector getMimeDescription image");
        assertEqual("Image File", FileTypeDetector.getMimeDescription("png"), "png mime");
        System.out.println("    [PASS] FileTypeDetector getMimeDescription image");
    }

    private static void testMimeDescriptionBinary() {
        System.out.println("    [TEST] FileTypeDetector getMimeDescription binary");
        assertEqual("Binary File", FileTypeDetector.getMimeDescription("jar"), "jar mime");
        System.out.println("    [PASS] FileTypeDetector getMimeDescription binary");
    }

    private static void testMimeDescriptionUnknown() {
        System.out.println("    [TEST] FileTypeDetector getMimeDescription unknown");
        assertEqual("Unknown File", FileTypeDetector.getMimeDescription("xyz"), "xyz mime");
        System.out.println("    [PASS] FileTypeDetector getMimeDescription unknown");
    }

    // ── SwingUtils.formatFileSize tests ────────────────────────────────

    private static void testFormatBytes() {
        System.out.println("    [TEST] formatFileSize bytes");
        assertEqual("0 B", SwingUtils.formatFileSize(0), "0 bytes");
        assertEqual("1 B", SwingUtils.formatFileSize(1), "1 byte");
        assertEqual("512 B", SwingUtils.formatFileSize(512), "512 bytes");
        assertEqual("1023 B", SwingUtils.formatFileSize(1023), "1023 bytes");
        System.out.println("    [PASS] formatFileSize bytes");
    }

    private static void testFormatKilobytes() {
        System.out.println("    [TEST] formatFileSize KB");
        String result = SwingUtils.formatFileSize(1024);
        if (!result.contains("KB")) throw fail("1024 should show KB, got: " + result);
        String r2 = SwingUtils.formatFileSize(1536);
        if (!r2.contains("KB")) throw fail("1536 should show KB, got: " + r2);
        System.out.println("    [PASS] formatFileSize KB");
    }

    private static void testFormatMegabytes() {
        System.out.println("    [TEST] formatFileSize MB");
        String result = SwingUtils.formatFileSize(1024 * 1024);
        if (!result.contains("MB")) throw fail("1MB should show MB, got: " + result);
        String r2 = SwingUtils.formatFileSize(5 * 1024 * 1024);
        if (!r2.contains("MB")) throw fail("5MB should show MB, got: " + r2);
        System.out.println("    [PASS] formatFileSize MB");
    }

    private static void testFormatGigabytes() {
        System.out.println("    [TEST] formatFileSize GB");
        String result = SwingUtils.formatFileSize(1024L * 1024 * 1024);
        if (!result.contains("GB")) throw fail("1GB should show GB, got: " + result);
        String r2 = SwingUtils.formatFileSize(10L * 1024 * 1024 * 1024);
        if (!r2.contains("GB")) throw fail("10GB should show GB, got: " + r2);
        System.out.println("    [PASS] formatFileSize GB");
    }

    private static void testFormatZeroBytes() {
        System.out.println("    [TEST] formatFileSize zero");
        assertEqual("0 B", SwingUtils.formatFileSize(0), "zero");
        System.out.println("    [PASS] formatFileSize zero");
    }

    private static void testFormatBoundaryKB() {
        System.out.println("    [TEST] formatFileSize boundary KB");
        assertEqual("1.0 KB", SwingUtils.formatFileSize(1024), "1024 bytes");
        System.out.println("    [PASS] formatFileSize boundary KB");
    }

    private static void testFormatBoundaryMB() {
        System.out.println("    [TEST] formatFileSize boundary MB");
        assertEqual("1.0 MB", SwingUtils.formatFileSize(1024 * 1024), "1MB");
        System.out.println("    [PASS] formatFileSize boundary MB");
    }

    private static void testFormatBoundaryGB() {
        System.out.println("    [TEST] formatFileSize boundary GB");
        assertEqual("1.0 GB", SwingUtils.formatFileSize(1024L * 1024 * 1024), "1GB");
        System.out.println("    [PASS] formatFileSize boundary GB");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static void assertType(String ext, FileType expected, String label) {
        FileType actual = FileTypeDetector.detect(ext);
        if (actual != expected)
            throw fail(label + ": expected " + expected + ", got " + actual);
    }

    private static void assertEqual(String expected, String actual, String context) {
        if (!expected.equals(actual))
            throw fail(context + ": expected '" + expected + "', got '" + actual + "'");
    }

    private static IllegalStateException fail(String msg) {
        return new IllegalStateException("[FAIL] " + msg);
    }
}
