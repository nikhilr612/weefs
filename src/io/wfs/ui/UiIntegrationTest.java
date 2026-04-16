package io.wfs.ui;

import io.wfs.ui.controller.ArchiveController;
import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Headless integration tests for the UI model and controller layer.
 *
 * These tests exercise the full MVC pipeline (ArchiveModel +
 * ArchiveController + FileOperations) without any visible Swing windows,
 * verifying that:
 * – archives can be opened, mutated and closed through the controller
 * – property-change events are fired on the EDT
 * – the correct old/new event values are reported
 * – saveArchive() re-opens the archive after persisting
 * – closeArchive() does not fire PROP_OPEN when nothing is open
 * – FileOperations rejects directory arguments to copy()
 */
public final class UiIntegrationTest {

    private UiIntegrationTest() {
    }

    // ── Entry point ────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        System.out.println("  Running UI integration tests...");

        testOpenFiresEventsOnEdt();
        testOpenFiresCorrectReadOnlyTransition();
        testCloseNopWhenAlreadyClosed();
        testCloseFiresEventsOnEdt();
        testSaveArchiveReopens();
        testFileOperationsRoundTrip();
        testCopyRejectsDirectory();
        testPropertyChangesOldNewValues();

        System.out.println("  All UI integration tests passed.");
    }

    // ── Tests ──────────────────────────────────────────────────────────

    /** PROP_OPEN and PROP_ARCHIVE_PATH must be delivered on the EDT. */
    private static void testOpenFiresEventsOnEdt() throws Exception {
        System.out.println("    [TEST] openArchive fires events on EDT");
        runWithTempArchive(".zip", (model, archivePath) -> {
            List<Boolean> onEdtFlags = new ArrayList<>();

            model.addPropertyChangeListener(ArchiveModel.PROP_OPEN,
                    evt -> onEdtFlags.add(SwingUtilities.isEventDispatchThread()));
            model.addPropertyChangeListener(ArchiveModel.PROP_ARCHIVE_PATH,
                    evt -> onEdtFlags.add(SwingUtilities.isEventDispatchThread()));

            driveModel(model, m -> m.openArchive(archivePath, false));

            if (onEdtFlags.isEmpty())
                throw assertionFailure("No events fired for PROP_OPEN / PROP_ARCHIVE_PATH");
            for (Boolean flag : onEdtFlags) {
                if (!flag)
                    throw assertionFailure("Property change not fired on EDT");
            }
        });
        System.out.println("    [PASS] openArchive fires events on EDT");
    }

    /** PROP_READ_ONLY must report the correct previous value. */
    private static void testOpenFiresCorrectReadOnlyTransition() throws Exception {
        System.out.println("    [TEST] PROP_READ_ONLY old value is correct");
        runWithTempArchive(".zip", (model, archivePath) -> {
            // Pre-condition: model starts read/write (readOnly == false)
            AtomicReference<Boolean> capturedOld = new AtomicReference<>();
            AtomicReference<Boolean> capturedNew = new AtomicReference<>();

            model.addPropertyChangeListener(ArchiveModel.PROP_READ_ONLY, evt -> {
                capturedOld.set((Boolean) evt.getOldValue());
                capturedNew.set((Boolean) evt.getNewValue());
            });

            // Opening read-only should fire old=false, new=true
            driveModel(model, m -> m.openArchive(archivePath, true));

            if (!Boolean.FALSE.equals(capturedOld.get()))
                throw assertionFailure("PROP_READ_ONLY old should be false, got: " + capturedOld.get());
            if (!Boolean.TRUE.equals(capturedNew.get()))
                throw assertionFailure("PROP_READ_ONLY new should be true, got: " + capturedNew.get());
        });
        System.out.println("    [PASS] PROP_READ_ONLY old value is correct");
    }

    /**
     * closeArchive() on an already-closed model must NOT fire PROP_OPEN.
     */
    private static void testCloseNopWhenAlreadyClosed() throws Exception {
        System.out.println("    [TEST] closeArchive is no-op when nothing is open");
        ArchiveModel model = new ArchiveModel();
        List<Object> events = new ArrayList<>();
        model.addPropertyChangeListener(ArchiveModel.PROP_OPEN, evt -> events.add(evt));

        // Close with nothing mounted — no event expected
        driveModel(model, ArchiveModel::closeArchive);

        if (!events.isEmpty())
            throw assertionFailure("PROP_OPEN fired when nothing was open");
        System.out.println("    [PASS] closeArchive is no-op when nothing is open");
    }

    /** PROP_OPEN must be delivered on the EDT when closing. */
    private static void testCloseFiresEventsOnEdt() throws Exception {
        System.out.println("    [TEST] closeArchive fires PROP_OPEN on EDT");
        runWithTempArchive(".zip", (model, archivePath) -> {
            driveModel(model, m -> m.openArchive(archivePath, false));

            AtomicBoolean onEdt = new AtomicBoolean(true);
            model.addPropertyChangeListener(ArchiveModel.PROP_OPEN,
                    evt -> onEdt.set(SwingUtilities.isEventDispatchThread()));

            driveModel(model, ArchiveModel::closeArchive);

            if (!onEdt.get())
                throw assertionFailure("PROP_OPEN (close) not fired on EDT");
        });
        System.out.println("    [PASS] closeArchive fires PROP_OPEN on EDT");
    }

    /**
     * saveArchive() must persist changes AND leave the archive open
     * afterwards so the session continues.
     */
    private static void testSaveArchiveReopens() throws Exception {
        System.out.println("    [TEST] saveArchive re-opens archive after persisting");
        runWithTempArchive(".zip", (model, archivePath) -> {
            driveModel(model, m -> m.openArchive(archivePath, false));

            // Write a file before saving
            Path filePath = model.getRootPath().resolve("before-save.txt");
            Files.writeString(filePath, "hello", java.nio.file.StandardOpenOption.CREATE);
            model.fireTreeRefresh();

            // Save via controller
            ArchiveController ctrl = new ArchiveController(model);
            runOnEdt(ctrl::saveArchive);
            Thread.sleep(300); // let SwingWorker finish

            // Archive must still be open
            if (!model.isOpen())
                throw assertionFailure("Archive should still be open after saveArchive()");

            // Archive file must exist and be non-empty
            if (!Files.exists(archivePath) || Files.size(archivePath) == 0)
                throw assertionFailure("Archive was not persisted by saveArchive()");
        });
        System.out.println("    [PASS] saveArchive re-opens archive after persisting");
    }

    /** Full round-trip: open → create file → list children → close. */
    private static void testFileOperationsRoundTrip() throws Exception {
        System.out.println("    [TEST] FileOperations round-trip (create/list/delete)");
        runWithTempArchive(".zip", (model, archivePath) -> {
            driveModel(model, m -> m.openArchive(archivePath, false));

            ArchiveController ctrl = new ArchiveController(model);
            Path root = model.getRootPath();

            // Create directory
            boolean dirCreated = ctrl.getFileOps().createDirectory(root.resolve("mydir"));
            if (!dirCreated)
                throw assertionFailure("createDirectory returned false");

            // Create file inside it
            boolean fileCreated = ctrl.getFileOps().createFile(
                    root.resolve("mydir").resolve("hello.txt"), "world");
            if (!fileCreated)
                throw assertionFailure("createFile returned false");

            // List children of root — must include mydir
            List<FileNode> children = model.listChildren(root);
            boolean found = children.stream().anyMatch(n -> n.getDisplayName().equals("mydir"));
            if (!found)
                throw assertionFailure("mydir not found in root listing");

            // Save content
            boolean saved = ctrl.getFileOps().saveFile(
                    root.resolve("mydir").resolve("hello.txt"), "updated");
            if (!saved)
                throw assertionFailure("saveFile returned false");

            // Read back
            String content = model.readFileContent(root.resolve("mydir").resolve("hello.txt"));
            if (!"updated".equals(content))
                throw assertionFailure("Content mismatch after save: " + content);

            driveModel(model, ArchiveModel::closeArchive);
        });
        System.out.println("    [PASS] FileOperations round-trip (create/list/delete)");
    }

    /** copy() must reject directory sources. */
    private static void testCopyRejectsDirectory() throws Exception {
        System.out.println("    [TEST] FileOperations.copy() rejects directories");
        runWithTempArchive(".zip", (model, archivePath) -> {
            driveModel(model, m -> m.openArchive(archivePath, false));
            ArchiveController ctrl = new ArchiveController(model);
            Path root = model.getRootPath();
            ctrl.getFileOps().createDirectory(root.resolve("srcdir"));

            boolean result = ctrl.getFileOps().copy(root.resolve("srcdir"), root);
            if (result)
                throw assertionFailure("copy() should return false for directory source");

            driveModel(model, ArchiveModel::closeArchive);
        });
        System.out.println("    [PASS] FileOperations.copy() rejects directories");
    }

    /**
     * PROP_OPEN old/new values must be true→false on close and false→true on open.
     */
    private static void testPropertyChangesOldNewValues() throws Exception {
        System.out.println("    [TEST] PROP_OPEN old/new values are correct");
        runWithTempArchive(".zip", (model, archivePath) -> {
            AtomicReference<Object> openOld = new AtomicReference<>();
            AtomicReference<Object> openNew = new AtomicReference<>();

            model.addPropertyChangeListener(ArchiveModel.PROP_OPEN, evt -> {
                openOld.set(evt.getOldValue());
                openNew.set(evt.getNewValue());
            });

            driveModel(model, m -> m.openArchive(archivePath, false));
            if (!Boolean.FALSE.equals(openOld.get()) || !Boolean.TRUE.equals(openNew.get()))
                throw assertionFailure("PROP_OPEN open event: expected old=false new=true, got old="
                        + openOld.get() + " new=" + openNew.get());

            driveModel(model, ArchiveModel::closeArchive);
            if (!Boolean.TRUE.equals(openOld.get()) || !Boolean.FALSE.equals(openNew.get()))
                throw assertionFailure("PROP_OPEN close event: expected old=true new=false, got old="
                        + openOld.get() + " new=" + openNew.get());
        });
        System.out.println("    [PASS] PROP_OPEN old/new values are correct");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface ModelAction {
        void run(ArchiveModel model) throws Exception;
    }

    @FunctionalInterface
    private interface TestBody {
        void run(ArchiveModel model, Path archivePath) throws Exception;
    }

    /**
     * Creates a temp archive path, runs the body with a fresh ArchiveModel,
     * and cleans up afterward.
     */
    private static void runWithTempArchive(String suffix, TestBody body) throws Exception {
        Path tempDir = Files.createTempDirectory("weefs-ui-test-");
        Path archivePath = tempDir.resolve("test" + suffix);
        ArchiveModel model = new ArchiveModel();
        try {
            body.run(model, archivePath);
        } finally {
            try {
                if (model.isOpen()) {
                    model.closeArchive();
                }
            } catch (Exception ignored) {
            }
            deleteRecursive(tempDir);
        }
    }

    /**
     * Drives a model mutation and pumps the EDT to flush any pending events.
     * The mutation itself is run on the calling thread (simulating a SwingWorker
     * background thread), then EDT events are drained.
     */
    private static void driveModel(ArchiveModel model, ModelAction action) throws Exception {
        action.run(model);
        pumpEdt();
    }

    /** Blocks until all currently pending EDT tasks have been processed. */
    private static void pumpEdt() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("EDT pump timed out");
        }
    }

    /** Runs action on EDT and waits. */
    private static void runOnEdt(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            action.run();
            latch.countDown();
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("EDT runOnEdt timed out");
        }
    }

    private static void deleteRecursive(Path root) throws IOException {
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
    }

    private static IllegalStateException assertionFailure(String msg) {
        return new IllegalStateException("[FAIL] " + msg);
    }
}
