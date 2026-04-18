package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Unit tests for package-private NFS core classes:
 * - NfsPath (Path SPI)
 * - NfsFileSystem (FileSystem SPI)
 * - NfsFileInfo (value object)
 */
public final class CoreNfsPathTest {

    private CoreNfsPathTest() {
    }

    public static void run() throws Exception {
        System.out.println("  Running NFS path/filesystem/fileinfo tests...");

        // NfsPath tests
        testPathNormalization();
        testPathIsAbsolute();
        testPathGetRoot();
        testPathGetFileName();
        testPathGetFileNameRoot();
        testPathGetParent();
        testPathGetParentRoot();
        testPathGetNameCount();
        testPathGetName();
        testPathSubpath();
        testPathStartsWith();
        testPathEndsWith();
        testPathResolveAbsolute();
        testPathResolveRelative();
        testPathResolveFromRoot();
        testPathResolveString();
        testPathResolveSibling();
        testPathToAbsolutePath();
        testPathRelativizeThrows();
        testPathToUriThrows();
        testPathToFileThrows();
        testPathIterator();
        testPathCompareTo();
        testPathEqualsAndHashCode();
        testPathToString();

        // NfsFileSystem tests
        testFileSystemIsOpen();
        testFileSystemClose();
        testFileSystemCloseIdempotent();
        testFileSystemIsReadOnly();
        testFileSystemGetSeparator();
        testFileSystemGetRootDirectories();
        testFileSystemGetPath();
        testFileSystemGetPathMultiPart();
        testFileSystemEnsureOpenWhenClosed();
        testFileSystemEnsureWritableWhenReadOnly();
        testFileSystemNewWatchServiceThrows();

        // NfsFileInfo tests
        testFileInfoConstruction();
        testFileInfoGetExtension();
        testFileInfoGetExtensionNoDot();
        testFileInfoGetExtensionDotFile();
        testFileInfoToStringDir();
        testFileInfoToStringFile();
        testFileInfoEqualsAndHash();
        testFileInfoFromPath();

        System.out.println("  All NFS path/filesystem/fileinfo tests passed.");
    }

    // ── NfsPath tests ──────────────────────────────────────────────────

    private static NfsFileSystem createFs(boolean readOnly) {
        NfsConnectionConfig config = new NfsConnectionConfig(
                "testhost", 2049, "/export", "/mnt", 30, readOnly);
        NfsFsProvider provider = new NfsFsProvider();
        return new NfsFileSystem(provider, config);
    }

    private static void testPathNormalization() {
        System.out.println("    [TEST] NfsPath normalization");
        NfsFileSystem fs = createFs(false);
        // Trailing slashes stripped
        NfsPath p = new NfsPath(fs, "/foo/bar/");
        assertEqual("/foo/bar", p.toString(), "trailing slash stripped");
        // Backslashes converted
        NfsPath p2 = new NfsPath(fs, "\\foo\\bar");
        assertEqual("/foo/bar", p2.toString(), "backslash conversion");
        // Null/empty → root
        NfsPath p3 = new NfsPath(fs, "");
        assertEqual("/", p3.toString(), "empty → root");
        NfsPath p4 = new NfsPath(fs, null);
        assertEqual("/", p4.toString(), "null → root");
        System.out.println("    [PASS] NfsPath normalization");
    }

    private static void testPathIsAbsolute() {
        System.out.println("    [TEST] NfsPath isAbsolute");
        NfsFileSystem fs = createFs(false);
        NfsPath abs = new NfsPath(fs, "/foo");
        if (!abs.isAbsolute()) throw fail("Should be absolute");
        NfsPath rel = new NfsPath(fs, "foo");
        if (rel.isAbsolute()) throw fail("Should not be absolute");
        System.out.println("    [PASS] NfsPath isAbsolute");
    }

    private static void testPathGetRoot() {
        System.out.println("    [TEST] NfsPath getRoot");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/foo/bar");
        Path root = p.getRoot();
        assertEqual("/", root.toString(), "root");
        System.out.println("    [PASS] NfsPath getRoot");
    }

    private static void testPathGetFileName() {
        System.out.println("    [TEST] NfsPath getFileName");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/foo/bar.txt");
        Path fn = p.getFileName();
        if (fn == null) throw fail("getFileName should not be null");
        assertEqual("bar.txt", fn.toString(), "fileName");
        System.out.println("    [PASS] NfsPath getFileName");
    }

    private static void testPathGetFileNameRoot() {
        System.out.println("    [TEST] NfsPath getFileName on root returns null");
        NfsFileSystem fs = createFs(false);
        NfsPath root = new NfsPath(fs, "/");
        Path fn = root.getFileName();
        if (fn != null) throw fail("Root getFileName should be null, got: " + fn);
        System.out.println("    [PASS] NfsPath getFileName on root returns null");
    }

    private static void testPathGetParent() {
        System.out.println("    [TEST] NfsPath getParent");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/a/b/c");
        Path parent = p.getParent();
        if (parent == null) throw fail("getParent should not be null");
        assertEqual("/a/b", parent.toString(), "parent");
        System.out.println("    [PASS] NfsPath getParent");
    }

    private static void testPathGetParentRoot() {
        System.out.println("    [TEST] NfsPath getParent on root returns null");
        NfsFileSystem fs = createFs(false);
        NfsPath root = new NfsPath(fs, "/");
        Path parent = root.getParent();
        if (parent != null) throw fail("Root getParent should be null, got: " + parent);
        System.out.println("    [PASS] NfsPath getParent on root returns null");
    }

    private static void testPathGetNameCount() {
        System.out.println("    [TEST] NfsPath getNameCount");
        NfsFileSystem fs = createFs(false);
        assertEqual(0, new NfsPath(fs, "/").getNameCount(), "root count");
        assertEqual(1, new NfsPath(fs, "/a").getNameCount(), "1 component");
        assertEqual(3, new NfsPath(fs, "/a/b/c").getNameCount(), "3 components");
        System.out.println("    [PASS] NfsPath getNameCount");
    }

    private static void testPathGetName() {
        System.out.println("    [TEST] NfsPath getName");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/alpha/beta/gamma");
        assertEqual("alpha", p.getName(0).toString(), "name(0)");
        assertEqual("beta", p.getName(1).toString(), "name(1)");
        assertEqual("gamma", p.getName(2).toString(), "name(2)");
        // Out of bounds
        expectException(() -> p.getName(-1), IllegalArgumentException.class);
        expectException(() -> p.getName(3), IllegalArgumentException.class);
        System.out.println("    [PASS] NfsPath getName");
    }

    private static void testPathSubpath() {
        System.out.println("    [TEST] NfsPath subpath");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/a/b/c/d");
        Path sub = p.subpath(1, 3);
        assertEqual("b/c", sub.toString(), "subpath(1,3)");
        // Invalid indices
        expectException(() -> p.subpath(-1, 2), IllegalArgumentException.class);
        expectException(() -> p.subpath(2, 2), IllegalArgumentException.class);
        expectException(() -> p.subpath(0, 5), IllegalArgumentException.class);
        System.out.println("    [PASS] NfsPath subpath");
    }

    private static void testPathStartsWith() {
        System.out.println("    [TEST] NfsPath startsWith");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/foo/bar");
        if (!p.startsWith("/foo")) throw fail("Should start with /foo");
        if (!p.startsWith(new NfsPath(fs, "/foo"))) throw fail("Should start with NfsPath /foo");
        if (p.startsWith("/baz")) throw fail("Should not start with /baz");
        System.out.println("    [PASS] NfsPath startsWith");
    }

    private static void testPathEndsWith() {
        System.out.println("    [TEST] NfsPath endsWith");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/foo/bar");
        if (!p.endsWith("bar")) throw fail("Should end with bar");
        if (!p.endsWith(new NfsPath(fs, "bar"))) throw fail("Should end with NfsPath bar");
        if (p.endsWith("baz")) throw fail("Should not end with baz");
        System.out.println("    [PASS] NfsPath endsWith");
    }

    private static void testPathResolveAbsolute() {
        System.out.println("    [TEST] NfsPath resolve absolute replaces");
        NfsFileSystem fs = createFs(false);
        NfsPath base = new NfsPath(fs, "/foo");
        Path resolved = base.resolve(new NfsPath(fs, "/bar"));
        assertEqual("/bar", resolved.toString(), "absolute resolve");
        System.out.println("    [PASS] NfsPath resolve absolute replaces");
    }

    private static void testPathResolveRelative() {
        System.out.println("    [TEST] NfsPath resolve relative appends");
        NfsFileSystem fs = createFs(false);
        NfsPath base = new NfsPath(fs, "/foo");
        Path resolved = base.resolve(new NfsPath(fs, "bar"));
        assertEqual("/foo/bar", resolved.toString(), "relative resolve");
        System.out.println("    [PASS] NfsPath resolve relative appends");
    }

    private static void testPathResolveFromRoot() {
        System.out.println("    [TEST] NfsPath resolve from root");
        NfsFileSystem fs = createFs(false);
        NfsPath root = new NfsPath(fs, "/");
        Path resolved = root.resolve(new NfsPath(fs, "child"));
        assertEqual("/child", resolved.toString(), "root resolve");
        System.out.println("    [PASS] NfsPath resolve from root");
    }

    private static void testPathResolveString() {
        System.out.println("    [TEST] NfsPath resolve(String)");
        NfsFileSystem fs = createFs(false);
        NfsPath base = new NfsPath(fs, "/dir");
        Path resolved = base.resolve("file.txt");
        assertEqual("/dir/file.txt", resolved.toString(), "resolve string");
        System.out.println("    [PASS] NfsPath resolve(String)");
    }

    private static void testPathResolveSibling() {
        System.out.println("    [TEST] NfsPath resolveSibling");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/dir/old.txt");
        Path sibling = p.resolveSibling("new.txt");
        assertEqual("/dir/new.txt", sibling.toString(), "sibling");
        System.out.println("    [PASS] NfsPath resolveSibling");
    }

    private static void testPathToAbsolutePath() {
        System.out.println("    [TEST] NfsPath toAbsolutePath");
        NfsFileSystem fs = createFs(false);
        NfsPath rel = new NfsPath(fs, "relative");
        Path abs = rel.toAbsolutePath();
        assertEqual("/relative", abs.toString(), "toAbsolutePath");
        // Already absolute returns self
        NfsPath alreadyAbs = new NfsPath(fs, "/abs");
        if (alreadyAbs.toAbsolutePath() != alreadyAbs)
            throw fail("Already-absolute should return same instance");
        System.out.println("    [PASS] NfsPath toAbsolutePath");
    }

    private static void testPathRelativizeThrows() {
        System.out.println("    [TEST] NfsPath relativize throws UnsupportedOperationException");
        NfsFileSystem fs = createFs(false);
        NfsPath a = new NfsPath(fs, "/a");
        NfsPath b = new NfsPath(fs, "/a/b");
        expectException(() -> a.relativize(b), UnsupportedOperationException.class);
        System.out.println("    [PASS] NfsPath relativize throws UnsupportedOperationException");
    }

    private static void testPathToUriThrows() {
        System.out.println("    [TEST] NfsPath toUri throws UnsupportedOperationException");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/foo");
        expectException(p::toUri, UnsupportedOperationException.class);
        System.out.println("    [PASS] NfsPath toUri throws UnsupportedOperationException");
    }

    private static void testPathToFileThrows() {
        System.out.println("    [TEST] NfsPath toFile throws UnsupportedOperationException");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/foo");
        expectException(p::toFile, UnsupportedOperationException.class);
        System.out.println("    [PASS] NfsPath toFile throws UnsupportedOperationException");
    }

    private static void testPathIterator() {
        System.out.println("    [TEST] NfsPath iterator");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/a/b/c");
        List<String> parts = new ArrayList<>();
        for (Path part : p) {
            parts.add(part.toString());
        }
        if (parts.size() != 3) throw fail("Expected 3 parts, got " + parts.size());
        assertEqual("a", parts.get(0), "part[0]");
        assertEqual("b", parts.get(1), "part[1]");
        assertEqual("c", parts.get(2), "part[2]");
        System.out.println("    [PASS] NfsPath iterator");
    }

    private static void testPathCompareTo() {
        System.out.println("    [TEST] NfsPath compareTo");
        NfsFileSystem fs = createFs(false);
        NfsPath a = new NfsPath(fs, "/aaa");
        NfsPath b = new NfsPath(fs, "/bbb");
        if (a.compareTo(b) >= 0) throw fail("aaa should sort before bbb");
        if (b.compareTo(a) <= 0) throw fail("bbb should sort after aaa");
        if (a.compareTo(a) != 0) throw fail("Same path compareTo should be 0");
        System.out.println("    [PASS] NfsPath compareTo");
    }

    private static void testPathEqualsAndHashCode() {
        System.out.println("    [TEST] NfsPath equals/hashCode");
        NfsFileSystem fs = createFs(false);
        NfsPath a = new NfsPath(fs, "/foo/bar");
        NfsPath b = new NfsPath(fs, "/foo/bar");
        NfsPath c = new NfsPath(fs, "/other");
        if (!a.equals(b)) throw fail("Same path should be equal");
        if (a.equals(c)) throw fail("Different paths should not be equal");
        if (a.hashCode() != b.hashCode()) throw fail("Equal paths should have same hashCode");
        // Different filesystem
        NfsFileSystem fs2 = createFs(true);
        NfsPath d = new NfsPath(fs2, "/foo/bar");
        if (a.equals(d)) throw fail("Paths on different filesystems should not be equal");
        System.out.println("    [PASS] NfsPath equals/hashCode");
    }

    private static void testPathToString() {
        System.out.println("    [TEST] NfsPath toString");
        NfsFileSystem fs = createFs(false);
        NfsPath p = new NfsPath(fs, "/hello/world");
        assertEqual("/hello/world", p.toString(), "toString");
        System.out.println("    [PASS] NfsPath toString");
    }

    // ── NfsFileSystem tests ────────────────────────────────────────────

    private static void testFileSystemIsOpen() {
        System.out.println("    [TEST] NfsFileSystem isOpen after creation");
        NfsFileSystem fs = createFs(false);
        if (!fs.isOpen()) throw fail("New FS should be open");
        System.out.println("    [PASS] NfsFileSystem isOpen after creation");
    }

    private static void testFileSystemClose() throws Exception {
        System.out.println("    [TEST] NfsFileSystem close");
        NfsFileSystem fs = createFs(false);
        fs.close();
        if (fs.isOpen()) throw fail("FS should be closed after close()");
        System.out.println("    [PASS] NfsFileSystem close");
    }

    private static void testFileSystemCloseIdempotent() throws Exception {
        System.out.println("    [TEST] NfsFileSystem close is idempotent");
        NfsFileSystem fs = createFs(false);
        fs.close();
        fs.close(); // Should not throw
        if (fs.isOpen()) throw fail("FS should still be closed");
        System.out.println("    [PASS] NfsFileSystem close is idempotent");
    }

    private static void testFileSystemIsReadOnly() {
        System.out.println("    [TEST] NfsFileSystem isReadOnly");
        NfsFileSystem rw = createFs(false);
        if (rw.isReadOnly()) throw fail("Should not be read-only");
        NfsFileSystem ro = createFs(true);
        if (!ro.isReadOnly()) throw fail("Should be read-only");
        System.out.println("    [PASS] NfsFileSystem isReadOnly");
    }

    private static void testFileSystemGetSeparator() {
        System.out.println("    [TEST] NfsFileSystem getSeparator");
        NfsFileSystem fs = createFs(false);
        assertEqual("/", fs.getSeparator(), "separator");
        System.out.println("    [PASS] NfsFileSystem getSeparator");
    }

    private static void testFileSystemGetRootDirectories() {
        System.out.println("    [TEST] NfsFileSystem getRootDirectories");
        NfsFileSystem fs = createFs(false);
        List<Path> roots = new ArrayList<>();
        fs.getRootDirectories().forEach(roots::add);
        if (roots.size() != 1) throw fail("Expected 1 root, got " + roots.size());
        assertEqual("/", roots.get(0).toString(), "root path");
        System.out.println("    [PASS] NfsFileSystem getRootDirectories");
    }

    private static void testFileSystemGetPath() {
        System.out.println("    [TEST] NfsFileSystem getPath single");
        NfsFileSystem fs = createFs(false);
        Path p = fs.getPath("/hello/world");
        assertEqual("/hello/world", p.toString(), "getPath");
        System.out.println("    [PASS] NfsFileSystem getPath single");
    }

    private static void testFileSystemGetPathMultiPart() {
        System.out.println("    [TEST] NfsFileSystem getPath multi-part");
        NfsFileSystem fs = createFs(false);
        Path p = fs.getPath("/a", "b", "c");
        // Should concatenate with /
        String result = p.toString();
        if (!result.contains("a") || !result.contains("b") || !result.contains("c"))
            throw fail("getPath multi-part should contain all parts, got: " + result);
        System.out.println("    [PASS] NfsFileSystem getPath multi-part");
    }

    private static void testFileSystemEnsureOpenWhenClosed() throws Exception {
        System.out.println("    [TEST] NfsFileSystem ensureOpen throws when closed");
        NfsFileSystem fs = createFs(false);
        fs.close();
        expectException(fs::ensureOpen,
                java.nio.file.FileSystemNotFoundException.class);
        System.out.println("    [PASS] NfsFileSystem ensureOpen throws when closed");
    }

    private static void testFileSystemEnsureWritableWhenReadOnly() {
        System.out.println("    [TEST] NfsFileSystem ensureWritable throws when read-only");
        NfsFileSystem fs = createFs(true);
        expectException(fs::ensureWritable, UnsupportedOperationException.class);
        System.out.println("    [PASS] NfsFileSystem ensureWritable throws when read-only");
    }

    private static void testFileSystemNewWatchServiceThrows() {
        System.out.println("    [TEST] NfsFileSystem newWatchService throws");
        NfsFileSystem fs = createFs(false);
        expectException(() -> {
            try { fs.newWatchService(); } catch (IOException e) { throw new RuntimeException(e); }
        }, RuntimeException.class);
        System.out.println("    [PASS] NfsFileSystem newWatchService throws");
    }

    // ── NfsFileInfo tests ──────────────────────────────────────────────

    private static void testFileInfoConstruction() {
        System.out.println("    [TEST] NfsFileInfo construction");
        NfsFileInfo info = new NfsFileInfo("test.txt", "/path/test.txt", false, 1024, 1000L);
        assertEqual("test.txt", info.getName(), "name");
        assertEqual("/path/test.txt", info.getFullPath(), "fullPath");
        if (info.isDirectory()) throw fail("Should not be directory");
        if (info.getSize() != 1024) throw fail("Wrong size: " + info.getSize());
        if (info.getLastModified() != 1000L) throw fail("Wrong lastModified: " + info.getLastModified());
        System.out.println("    [PASS] NfsFileInfo construction");
    }

    private static void testFileInfoGetExtension() {
        System.out.println("    [TEST] NfsFileInfo getExtension");
        NfsFileInfo info = new NfsFileInfo("report.PDF", "/report.PDF", false, 0, 0);
        assertEqual("pdf", info.getExtension(), "extension lowercased");
        System.out.println("    [PASS] NfsFileInfo getExtension");
    }

    private static void testFileInfoGetExtensionNoDot() {
        System.out.println("    [TEST] NfsFileInfo getExtension no dot");
        NfsFileInfo info = new NfsFileInfo("Makefile", "/Makefile", false, 0, 0);
        assertEqual("", info.getExtension(), "no-dot extension");
        System.out.println("    [PASS] NfsFileInfo getExtension no dot");
    }

    private static void testFileInfoGetExtensionDotFile() {
        System.out.println("    [TEST] NfsFileInfo getExtension dot-file");
        NfsFileInfo info = new NfsFileInfo(".gitignore", "/.gitignore", false, 0, 0);
        assertEqual("gitignore", info.getExtension(), "dotfile extension");
        System.out.println("    [PASS] NfsFileInfo getExtension dot-file");
    }

    private static void testFileInfoToStringDir() {
        System.out.println("    [TEST] NfsFileInfo toString directory");
        NfsFileInfo info = new NfsFileInfo("docs", "/docs", true, 0, 0);
        assertEqual("docs/", info.toString(), "dir toString");
        System.out.println("    [PASS] NfsFileInfo toString directory");
    }

    private static void testFileInfoToStringFile() {
        System.out.println("    [TEST] NfsFileInfo toString file");
        NfsFileInfo info = new NfsFileInfo("readme.md", "/readme.md", false, 200, 0);
        assertEqual("readme.md", info.toString(), "file toString");
        System.out.println("    [PASS] NfsFileInfo toString file");
    }

    private static void testFileInfoEqualsAndHash() {
        System.out.println("    [TEST] NfsFileInfo equals/hashCode");
        NfsFileInfo a = new NfsFileInfo("a.txt", "/dir/a.txt", false, 100, 0);
        NfsFileInfo b = new NfsFileInfo("a.txt", "/dir/a.txt", false, 200, 0);
        NfsFileInfo c = new NfsFileInfo("a.txt", "/other/a.txt", false, 100, 0);
        if (!a.equals(b)) throw fail("Same fullPath should be equal");
        if (a.equals(c)) throw fail("Different fullPath should not be equal");
        if (a.hashCode() != b.hashCode()) throw fail("Equal infos should have same hashCode");
        System.out.println("    [PASS] NfsFileInfo equals/hashCode");
    }

    private static void testFileInfoFromPath() throws Exception {
        System.out.println("    [TEST] NfsFileInfo fromPath");
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("nfsinfo-test-", ".txt");
        try {
            java.nio.file.Files.writeString(tempFile, "hello");
            NfsFileInfo info = NfsFileInfo.fromPath(tempFile);
            if (info.isDirectory()) throw fail("Should not be directory");
            if (info.getSize() != 5) throw fail("Size should be 5, got " + info.getSize());
            if (info.getName().isEmpty()) throw fail("Name should not be empty");
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
        System.out.println("    [PASS] NfsFileInfo fromPath");
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

    private static IllegalStateException fail(String msg) {
        return new IllegalStateException("[FAIL] " + msg);
    }
}
