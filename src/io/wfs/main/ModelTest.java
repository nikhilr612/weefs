package io.wfs.main;

import io.wfs.core.nfs.NfsConnectionConfig;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Tests for the UI model layer:
 * - FileNode creation, comparison, equals, getExtension
 * - ArchiveModel property change events, open/close/NFS states
 */
final class ModelTest {

    private ModelTest() {
    }

    static void run() throws Exception {
        System.out.println("  Running model tests...");

        // FileNode tests
        testFileNodeWithExplicitValues();
        testFileNodeExtension();
        testFileNodeNoExtension();
        testFileNodeDotFile();
        testFileNodeComparable();
        testFileNodeEqualsAndHash();
        testFileNodeToString();

        // ArchiveModel tests
        testModelInitialState();
        testModelOpenCloseLifecycle();
        testModelReadOnlyFlag();
        testModelSelectedFileEvent();
        testModelNfsConfigEvent();
        testModelNfsConfigOnEdt();
        testModelTreeRefreshEvent();
        testModelListChildrenReturnsContent();
        testModelReadFileContent();

        System.out.println("  All model tests passed.");
    }

    // ── FileNode tests ─────────────────────────────────────────────────

    private static void testFileNodeWithExplicitValues() {
        System.out.println("    [TEST] FileNode explicit constructor");
        Path p = Path.of("/tmp/test-filenode");
        FileNode node = new FileNode(p, "test-filenode", true);
        if (!node.isDirectory()) throw fail("Should be directory");
        if (!"test-filenode".equals(node.getDisplayName())) throw fail("Wrong display name");
        if (!p.equals(node.getPath())) throw fail("Wrong path");
        System.out.println("    [PASS] FileNode explicit constructor");
    }

    private static void testFileNodeExtension() {
        System.out.println("    [TEST] FileNode getExtension");
        FileNode node = new FileNode(Path.of("/tmp/doc.pdf"), "doc.pdf", false);
        assertEqual("pdf", node.getExtension(), "extension");

        FileNode tarGz = new FileNode(Path.of("/tmp/data.tar.gz"), "data.tar.gz", false);
        assertEqual("gz", tarGz.getExtension(), "tar.gz extension");

        System.out.println("    [PASS] FileNode getExtension");
    }

    private static void testFileNodeNoExtension() {
        System.out.println("    [TEST] FileNode no extension");
        FileNode node = new FileNode(Path.of("/tmp/Makefile"), "Makefile", false);
        assertEqual("", node.getExtension(), "no-ext");
        System.out.println("    [PASS] FileNode no extension");
    }

    private static void testFileNodeDotFile() {
        System.out.println("    [TEST] FileNode dot-file extension");
        FileNode node = new FileNode(Path.of("/tmp/.gitignore"), ".gitignore", false);
        assertEqual("gitignore", node.getExtension(), "dotfile-ext");
        System.out.println("    [PASS] FileNode dot-file extension");
    }

    private static void testFileNodeComparable() {
        System.out.println("    [TEST] FileNode compareTo (dirs first, then name)");
        FileNode dir = new FileNode(Path.of("/d"), "Alpha", true);
        FileNode file = new FileNode(Path.of("/f"), "Alpha", false);
        FileNode fileB = new FileNode(Path.of("/f2"), "Beta", false);

        if (dir.compareTo(file) >= 0) throw fail("Directory should sort before file");
        if (file.compareTo(dir) <= 0) throw fail("File should sort after directory");
        if (file.compareTo(fileB) >= 0) throw fail("Alpha should sort before Beta");
        System.out.println("    [PASS] FileNode compareTo (dirs first, then name)");
    }

    private static void testFileNodeEqualsAndHash() {
        System.out.println("    [TEST] FileNode equals/hashCode");
        FileNode a = new FileNode(Path.of("/same"), "A", false);
        FileNode b = new FileNode(Path.of("/same"), "B", true);
        FileNode c = new FileNode(Path.of("/other"), "A", false);

        if (!a.equals(b)) throw fail("Same path should be equal");
        if (a.equals(c)) throw fail("Different path should not be equal");
        if (a.hashCode() != b.hashCode()) throw fail("Equal nodes should share hashCode");
        System.out.println("    [PASS] FileNode equals/hashCode");
    }

    private static void testFileNodeToString() {
        System.out.println("    [TEST] FileNode toString = displayName");
        FileNode node = new FileNode(Path.of("/x"), "myfile.txt", false);
        assertEqual("myfile.txt", node.toString(), "toString");
        System.out.println("    [PASS] FileNode toString = displayName");
    }

    // ── ArchiveModel tests ─────────────────────────────────────────────

    private static void testModelInitialState() {
        System.out.println("    [TEST] ArchiveModel initial state");
        ArchiveModel model = new ArchiveModel();
        if (model.isOpen()) throw fail("Model should not be open initially");
        if (model.isReadOnly()) throw fail("Model should not be read-only initially");
        if (model.isNfsMounted()) throw fail("Model should not have NFS initially");
        if (model.getArchivePath() != null) throw fail("Archive path should be null initially");
        if (model.getSelectedFile() != null) throw fail("Selected file should be null initially");
        System.out.println("    [PASS] ArchiveModel initial state");
    }

    private static void testModelOpenCloseLifecycle() throws Exception {
        System.out.println("    [TEST] ArchiveModel open/close lifecycle");
        Path tempDir = Files.createTempDirectory("weefs-model-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ArchiveModel model = new ArchiveModel();

            model.openArchive(zipFile, false);
            pumpEdt();
            if (!model.isOpen()) throw fail("Model should be open");
            if (model.isReadOnly()) throw fail("Model should be writable");
            if (!zipFile.equals(model.getArchivePath())) throw fail("Wrong archive path");

            model.closeArchive();
            pumpEdt();
            if (model.isOpen()) throw fail("Model should be closed");
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ArchiveModel open/close lifecycle");
    }

    private static void testModelReadOnlyFlag() throws Exception {
        System.out.println("    [TEST] ArchiveModel read-only mode");
        Path tempDir = Files.createTempDirectory("weefs-model-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ArchiveModel model = new ArchiveModel();

            model.openArchive(zipFile, true);
            pumpEdt();
            if (!model.isReadOnly()) throw fail("Model should be read-only");

            model.closeArchive();
            pumpEdt();
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ArchiveModel read-only mode");
    }

    private static void testModelSelectedFileEvent() throws Exception {
        System.out.println("    [TEST] ArchiveModel setSelectedFile fires event");
        ArchiveModel model = new ArchiveModel();
        List<PropertyChangeEvent> events = new ArrayList<>();
        model.addPropertyChangeListener(ArchiveModel.PROP_SELECTED_FILE, events::add);

        FileNode node = new FileNode(Path.of("/test"), "test", false);
        model.setSelectedFile(node);
        pumpEdt();

        if (events.isEmpty()) throw fail("PROP_SELECTED_FILE not fired");
        if (events.get(0).getNewValue() != node) throw fail("Wrong new value");
        System.out.println("    [PASS] ArchiveModel setSelectedFile fires event");
    }

    private static void testModelNfsConfigEvent() throws Exception {
        System.out.println("    [TEST] ArchiveModel setNfsConfig fires events");
        ArchiveModel model = new ArchiveModel();
        List<String> firedProps = new ArrayList<>();
        model.addPropertyChangeListener(evt -> firedProps.add(evt.getPropertyName()));

        NfsConnectionConfig config = new NfsConnectionConfig(
                "localhost", 2049, "/export", "/mnt", 30, false);
        model.setNfsConfig(config);
        pumpEdt();

        if (!firedProps.contains(ArchiveModel.PROP_NFS_CONFIG))
            throw fail("PROP_NFS_CONFIG not fired, got: " + firedProps);
        System.out.println("    [PASS] ArchiveModel setNfsConfig fires events");
    }

    private static void testModelNfsConfigOnEdt() throws Exception {
        System.out.println("    [TEST] ArchiveModel setNfsConfig fires on EDT");
        ArchiveModel model = new ArchiveModel();
        AtomicReference<Boolean> onEdt = new AtomicReference<>(null);
        model.addPropertyChangeListener(ArchiveModel.PROP_NFS_CONFIG,
                evt -> onEdt.set(SwingUtilities.isEventDispatchThread()));

        NfsConnectionConfig config = new NfsConnectionConfig(
                "localhost", 2049, "/export", "/mnt", 30, false);
        model.setNfsConfig(config);
        pumpEdt();

        if (onEdt.get() == null) throw fail("Event was not fired");
        if (!onEdt.get()) throw fail("NFS config event not fired on EDT");
        System.out.println("    [PASS] ArchiveModel setNfsConfig fires on EDT");
    }

    private static void testModelTreeRefreshEvent() throws Exception {
        System.out.println("    [TEST] ArchiveModel fireTreeRefresh");
        ArchiveModel model = new ArchiveModel();
        List<PropertyChangeEvent> events = new ArrayList<>();
        model.addPropertyChangeListener(ArchiveModel.PROP_TREE_REFRESH, events::add);

        model.fireTreeRefresh();
        pumpEdt();

        if (events.isEmpty()) throw fail("PROP_TREE_REFRESH not fired");
        System.out.println("    [PASS] ArchiveModel fireTreeRefresh");
    }

    private static void testModelListChildrenReturnsContent() throws Exception {
        System.out.println("    [TEST] ArchiveModel listChildren returns files");
        Path tempDir = Files.createTempDirectory("weefs-model-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ArchiveModel model = new ArchiveModel();
            model.openArchive(zipFile, false);
            pumpEdt();

            Path root = model.getRootPath();
            Files.writeString(root.resolve("file.txt"), "content",
                    java.nio.file.StandardOpenOption.CREATE);
            Files.createDirectories(root.resolve("subdir"));

            List<FileNode> children = model.listChildren(root);
            if (children.size() < 2) throw fail("Expected at least 2 children, got " + children.size());

            boolean hasFile = children.stream().anyMatch(n -> n.getDisplayName().equals("file.txt"));
            boolean hasDir = children.stream().anyMatch(n -> n.getDisplayName().equals("subdir"));
            if (!hasFile) throw fail("Missing file.txt in listing");
            if (!hasDir) throw fail("Missing subdir in listing");

            model.closeArchive();
            pumpEdt();
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ArchiveModel listChildren returns files");
    }

    private static void testModelReadFileContent() throws Exception {
        System.out.println("    [TEST] ArchiveModel readFileContent");
        Path tempDir = Files.createTempDirectory("weefs-model-");
        try {
            Path zipFile = tempDir.resolve("test.zip");
            ArchiveModel model = new ArchiveModel();
            model.openArchive(zipFile, false);
            pumpEdt();

            Path root = model.getRootPath();
            Files.writeString(root.resolve("readme.md"), "# Title\nContent here",
                    java.nio.file.StandardOpenOption.CREATE);

            String content = model.readFileContent(root.resolve("readme.md"));
            if (content == null || !content.contains("Title"))
                throw fail("readFileContent should return file text, got: " + content);

            model.closeArchive();
            pumpEdt();
        } finally {
            cleanup(tempDir);
        }
        System.out.println("    [PASS] ArchiveModel readFileContent");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static void pumpEdt() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS))
            throw new IllegalStateException("EDT pump timed out");
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
