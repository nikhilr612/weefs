package io.wfs.ui.controller;

import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;
import io.wfs.ui.model.MountSession;
import io.wfs.ui.util.UIUtils;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * Default Swing implementation of {@link IArchiveController} and
 * {@link INfsController}.
 * Coordinates between the model and views, handling high-level user actions:
 * - Archive operations: open, create, close, save
 * - NFS operations: mount, unmount, file operations
 * Delegates individual file operations to {@link FileOperations} and
 * {@link NfsFileOperations}.
 */
public final class ArchiveController implements IArchiveController, INfsController {

    private static final ArchiveFormatOption[] ARCHIVE_FORMAT_OPTIONS = {
            new ArchiveFormatOption("ZIP (*.zip)", ".zip", "ZIP (*.zip)", "zip"),
            new ArchiveFormatOption("TAR (*.tar)", ".tar", "TAR (*.tar)", "tar"),
            new ArchiveFormatOption("TAR.GZ (*.tar.gz)", ".tar.gz", "TAR.GZ (*.tar.gz, *.tgz)", "gz", "tgz"),
            new ArchiveFormatOption("TAR.BZ2 (*.tar.bz2)", ".tar.bz2", "TAR.BZ2 (*.tar.bz2, *.tbz2)", "bz2", "tbz2"),
            new ArchiveFormatOption("TAR.XZ (*.tar.xz)", ".tar.xz", "TAR.XZ (*.tar.xz, *.txz)", "xz", "txz"),
            new ArchiveFormatOption("TAR.LZMA (*.tar.lzma)", ".tar.lzma", "TAR.LZMA (*.tar.lzma)", "lzma"),
            new ArchiveFormatOption("GZIP single-file (*.gz)", ".gz", "GZIP single-file (*.gz)", "gz"),
            new ArchiveFormatOption("BZIP2 single-file (*.bz2)", ".bz2", "BZIP2 single-file (*.bz2)", "bz2"),
            new ArchiveFormatOption("XZ single-file (*.xz)", ".xz", "XZ single-file (*.xz)", "xz"),
            new ArchiveFormatOption("LZMA single-file (*.lzma)", ".lzma", "LZMA single-file (*.lzma)", "lzma")
    };

    private final ArchiveModel model;
    private final FileOperations fileOps;
    private final NfsFileOperations nfsFileOps;
    private Component parentComponent;

    /** Pending clipboard entry — null when empty. */
    private ClipboardEntry clipboard;

    private record ClipboardEntry(FileNode node, boolean cut) {}

    public ArchiveController(ArchiveModel model) {
        this.model = model;
        this.fileOps = new FileOperations(model);
        this.nfsFileOps = new NfsFileOperations(model);
    }

    @Override
    public void setParentComponent(Component parent) {
        this.parentComponent = parent;
    }

    @Override
    public ArchiveModel getModel() {
        return model;
    }

    @Override
    public IFileOperations getFileOps() {
        boolean nfs = isNfsMounted();
        if (nfs) nfsFileOps.setConfig(model.getNfsConfig());
        return nfs ? nfsFileOps : fileOps;
    }

    // ── Archive-level actions ──────────────────────────────────────────

    @Override
    public void openDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(parentComponent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();

        int mode = JOptionPane.showOptionDialog(parentComponent,
                "Open directory in which mode?",
                "Open Mode",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[] { "Read/Write", "Read Only" },
                "Read/Write");

        if (mode == JOptionPane.CLOSED_OPTION) {
            return;
        }

        boolean readOnly = (mode == 1);

        executeInBackground("Opening directory...", () -> {
            try {
                model.openDirectory(selected, readOnly);
            } catch (IOException ex) {
                showError("Open Directory", ex);
            }
        });
    }

    @Override
    public void openArchive() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Archive");
        configureOpenFilters(chooser);
        chooser.setAcceptAllFileFilterUsed(true);

        if (chooser.showOpenDialog(parentComponent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();

        int mode = JOptionPane.showOptionDialog(parentComponent,
                "Open archive in which mode?",
                "Open Mode",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[] { "Read/Write", "Read Only" },
                "Read/Write");

        if (mode == JOptionPane.CLOSED_OPTION) {
            return;
        }

        boolean readOnly = (mode == 1);

        executeInBackground("Opening archive...", () -> {
            try {
                model.openArchive(selected, readOnly);
            } catch (IOException ex) {
                showError("Open Archive", ex);
            }
        });
    }

    @Override
    public void mountNfs() {
        String value = JOptionPane.showInputDialog(parentComponent,
                "Enter NFS URI (weefs://host/path?auth=ENV_VAR[&user=username]):",
                "Mount NFS",
                JOptionPane.PLAIN_MESSAGE);

        if (value == null || value.isBlank()) {
            return;
        }

        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException ex) {
            showError("Mount NFS", new IOException("Invalid URI: " + ex.getMessage(), ex));
            return;
        }

        int mode = JOptionPane.showOptionDialog(parentComponent,
                "Mount remote file system in which mode?",
                "Mount Mode",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[] { "Read/Write", "Read Only" },
                "Read/Write");

        if (mode == JOptionPane.CLOSED_OPTION) {
            return;
        }

        boolean readOnly = (mode == 1);
        executeInBackground("Mounting remote file system...", () -> {
            try {
                model.openMountUri(uri, readOnly);
            } catch (IOException ex) {
                showError("Mount NFS", ex);
            }
        });
    }

    @Override
    public void createArchive() {
        String[] labels = buildCreateFormatLabels();
        String selectedLabel = (String) JOptionPane.showInputDialog(
                parentComponent,
                "Choose archive format:",
                "Archive Type",
                JOptionPane.QUESTION_MESSAGE,
                null,
                labels,
                labels[0]);

        if (selectedLabel == null) {
            return;
        }

        ArchiveFormatOption selectedFormat = null;
        for (ArchiveFormatOption option : ARCHIVE_FORMAT_OPTIONS) {
            if (option.label.equals(selectedLabel)) {
                selectedFormat = option;
                break;
            }
        }
        if (selectedFormat == null) {
            selectedFormat = ARCHIVE_FORMAT_OPTIONS[0];
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Create New Archive");
        configureCreateFilter(chooser, selectedFormat);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showSaveDialog(parentComponent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        Path selectedWithExtension = ensureArchiveExtension(selected, selectedFormat.requiredExtension);

        executeInBackground("Creating archive...", () -> {
            try {
                model.createArchive(selectedWithExtension);
            } catch (IOException ex) {
                showError("Create Archive", ex);
            }
        });
    }

    @Override
    public void closeArchive() {
        MountSession active = model.getActiveSession();
        if (active == null) return;
        closeSession(active.getId());
    }

    @Override
    public void closeSession(String sessionId) {
        executeInBackground("Closing...", () -> {
            try {
                model.closeSession(sessionId);
            } catch (IOException ex) {
                showError("Close", ex);
            }
        });
    }

    @Override
    public void saveArchive() {
        if (!model.isOpen() || model.isReadOnly()) return;
        executeInBackground("Saving archive...", () -> {
            try {
                MountSession active = model.getActiveSession();
                if (active == null || active.isReadOnly()) return;
                Path archiveToReopen = model.getArchivePath();
                model.closeSession(active.getId());
                if (archiveToReopen != null) {
                    model.openArchive(archiveToReopen, false);
                }
            } catch (IOException ex) {
                showError("Save Archive", ex);
            }
        });
    }

    @Override
    public void saveSession(String sessionId) {
        MountSession session = model.getSession(sessionId);
        if (session == null || !session.isSaveable()) return;
        Path archivePath = session.getDisplayPath();
        executeInBackground("Saving...", () -> {
            try {
                model.closeSession(sessionId);
                if (archivePath != null) {
                    model.openArchive(archivePath, false);
                }
            } catch (IOException ex) {
                showError("Save", ex);
            }
        });
    }

    // ── File-level actions (prompting UI) ──────────────────────────────

    @Override
    public void newFile() {
        if (!model.isOpen() || model.isReadOnly())
            return;

        Path parentDir = resolveTargetDirectory();
        if (parentDir == null)
            return;

        String name = JOptionPane.showInputDialog(parentComponent,
                "Enter file name:", "New File", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank())
            return;

        getFileOps().createFile(parentDir.resolve(name), "");
    }

    @Override
    public void newDirectory() {
        if (!model.isOpen() || model.isReadOnly())
            return;

        Path parentDir = resolveTargetDirectory();
        if (parentDir == null)
            return;

        String name = JOptionPane.showInputDialog(parentComponent,
                "Enter directory name:", "New Directory", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank())
            return;

        getFileOps().createDirectory(parentDir.resolve(name));
    }

    @Override
    public void deleteSelected() {
        FileNode selected = model.getSelectedFile();
        if (selected == null || !model.isOpen() || model.isReadOnly())
            return;

        int confirm = JOptionPane.showConfirmDialog(parentComponent,
                "Delete \"" + selected.getDisplayName() + "\"?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            getFileOps().delete(selected.getPath());
        }
    }

    @Override
    public void renameSelected() {
        FileNode selected = model.getSelectedFile();
        if (selected == null || !model.isOpen() || model.isReadOnly())
            return;

        String newName = JOptionPane.showInputDialog(parentComponent,
                "Enter new name:", selected.getDisplayName());
        if (newName == null || newName.isBlank())
            return;

        Path oldPath = selected.getPath();
        Path parent = oldPath.getParent();
        if (parent == null)
            return;

        getFileOps().rename(oldPath, parent.resolve(newName));
    }

    @Override
    public void extractSelected() {
        FileNode selected = model.getSelectedFile();
        if (selected == null || selected.isDirectory())
            return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Extract To...");
        chooser.setSelectedFile(new java.io.File(selected.getDisplayName()));

        if (chooser.showSaveDialog(parentComponent) == JFileChooser.APPROVE_OPTION) {
            Path destination = chooser.getSelectedFile().toPath();
            getFileOps().extractTo(selected.getPath(), destination);
        }
    }

    @Override
    public void saveAs() {
        FileNode selected = model.getSelectedFile();
        if (selected == null || selected.isDirectory())
            return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save As...");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setSelectedFile(new java.io.File(selected.getDisplayName()));

        if (chooser.showSaveDialog(parentComponent) == JFileChooser.APPROVE_OPTION) {
            java.nio.file.Path destination = chooser.getSelectedFile().toPath();
            getFileOps().extractTo(selected.getPath(), destination);
        }
    }

    @Override
    public void saveFileContent(Path path, String content) {
        getFileOps().saveFile(path, content);
    }

    // ── Clipboard operations ───────────────────────────────────────────

    @Override
    public void copySelected() {
        FileNode sel = model.getSelectedFile();
        if (sel != null) clipboard = new ClipboardEntry(sel, false);
    }

    @Override
    public void cutSelected() {
        FileNode sel = model.getSelectedFile();
        if (sel != null) clipboard = new ClipboardEntry(sel, true);
    }

    @Override
    public boolean hasClipboard() {
        return clipboard != null;
    }

    @Override
    public void pasteSelected() {
        if (clipboard == null || !model.isOpen() || model.isReadOnly()) return;
        Path targetDir = resolveTargetDirectory();
        if (targetDir == null) return;

        final ClipboardEntry entry = clipboard;
        if (entry.cut()) clipboard = null; // consume cut immediately

        executeInBackground("Pasting...", () -> {
            try {
                doPaste(entry, targetDir);
                model.fireTreeRefresh();
            } catch (IOException ex) {
                showError("Paste", ex);
            }
        });
    }

    private void doPaste(ClipboardEntry entry, Path targetDir) throws IOException {
        FileNode src = entry.node();
        String name = src.getPath().getFileName() != null
                ? src.getPath().getFileName().toString()
                : src.getDisplayName();
        Path dest = targetDir.resolve(name);

        boolean sameFs = src.getPath().getFileSystem() == dest.getFileSystem();

        if (src.isDirectory()) {
            copyDirectoryRecursive(src.getPath(), dest, sameFs, entry.cut());
        } else {
            copySingleFile(src.getPath(), dest, sameFs, entry.cut());
        }
    }

    private void copySingleFile(Path src, Path dest, boolean sameFs, boolean deleteSource)
            throws IOException {
        if (sameFs) {
            if (deleteSource) {
                java.nio.file.Files.move(src, dest,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                java.nio.file.Files.copy(src, dest,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            // Cross-filesystem: read bytes from source, write to destination
            byte[] data = model.readFileBytes(src);
            java.nio.file.Files.write(dest, data,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            if (deleteSource) java.nio.file.Files.deleteIfExists(src);
        }
    }

    private void copyDirectoryRecursive(Path srcDir, Path destDir, boolean sameFs, boolean deleteSource)
            throws IOException {
        java.nio.file.Files.createDirectories(destDir);
        for (io.wfs.ui.model.FileNode child : model.listChildren(srcDir)) {
            Path childDest = destDir.resolve(child.getPath().getFileName().toString());
            if (child.isDirectory()) {
                copyDirectoryRecursive(child.getPath(), childDest, sameFs, deleteSource);
            } else {
                copySingleFile(child.getPath(), childDest, sameFs, deleteSource);
            }
        }
        if (deleteSource && sameFs) java.nio.file.Files.deleteIfExists(srcDir);
    }

    // ── NFS operations (INfsController implementation) ───────────────────

    @Override
    public IFileOperations getNfsFileOps() {
        return nfsFileOps;
    }

    @Override
    public void unmountNfs() {
        executeInBackground("Unmounting...", () -> {
            try {
                for (MountSession s : model.getSessions()) {
                    if (s.isNfsMounted() || s.isRemoteMounted()) {
                        if (s.isNfsMounted()) nfsFileOps.setConfig(null);
                        model.closeSession(s.getId());
                        break;
                    }
                }
            } catch (Exception ex) {
                showError("Unmount", ex);
            }
        });
    }

    @Override
    public void extractNfsSelected() {
        if (!isNfsMounted() || model.getSelectedFile() == null || model.getSelectedFile().isDirectory()) {
            return;
        }

        FileNode selected = model.getSelectedFile();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Extract NFS File To...");
        chooser.setSelectedFile(new java.io.File(selected.getDisplayName()));

        if (chooser.showSaveDialog(parentComponent) == JFileChooser.APPROVE_OPTION) {
            Path destination = chooser.getSelectedFile().toPath();
            executeInBackground("Extracting file...", () -> {
                try {
                    boolean success = fileOps.extractTo(selected.getPath(), destination);
                    if (success) {
                        JOptionPane.showMessageDialog(parentComponent,
                                "File extracted successfully",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    showError("Extract NFS File", ex);
                }
            });
        }
    }

    @Override
    public boolean isNfsMounted() {
        return model.isRemoteMounted() || model.isNfsMounted();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Path getTargetDirectory(FileNode selected) {
        if (selected != null && selected.isDirectory()) {
            return selected.getPath();
        }
        if (selected != null) {
            return selected.getPath().getParent();
        }
        return model.getRootPath();
    }

    private Path ensureArchiveExtension(Path selected, String requiredExtension) {
        String filename = selected.getFileName().toString();
        String normalized = stripSupportedArchiveExtension(filename);
        if (!normalized.endsWith(requiredExtension)) {
            normalized = normalized + requiredExtension;
        }
        return selected.resolveSibling(normalized);
    }

    private String stripSupportedArchiveExtension(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".tar.gz")) {
            return filename.substring(0, filename.length() - 7);
        }
        if (lower.endsWith(".tar.bz2")) {
            return filename.substring(0, filename.length() - 8);
        }
        if (lower.endsWith(".tar.xz")) {
            return filename.substring(0, filename.length() - 7);
        }
        if (lower.endsWith(".tar.lzma")) {
            return filename.substring(0, filename.length() - 9);
        }
        if (lower.endsWith(".tgz") || lower.endsWith(".tbz2") || lower.endsWith(".txz")) {
            return filename.substring(0, filename.lastIndexOf('.'));
        }
        if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz")
                || lower.endsWith(".bz2") || lower.endsWith(".xz") || lower.endsWith(".lzma")) {
            return filename.substring(0, filename.lastIndexOf('.'));
        }
        return filename;
    }

    private void configureCreateFilter(JFileChooser chooser, ArchiveFormatOption formatOption) {
        chooser.setFileFilter(new FileNameExtensionFilter(
                formatOption.filterDescription,
                formatOption.extensions));
    }

    private void configureOpenFilters(JFileChooser chooser) {
        chooser.resetChoosableFileFilters();
        FileNameExtensionFilter defaultFilter = null;
        for (ArchiveFormatOption option : ARCHIVE_FORMAT_OPTIONS) {
            FileNameExtensionFilter filter = new FileNameExtensionFilter(option.filterDescription, option.extensions);
            chooser.addChoosableFileFilter(filter);
            if (defaultFilter == null) {
                defaultFilter = filter;
            }
        }

        if (defaultFilter != null) {
            chooser.setFileFilter(defaultFilter);
        }
    }

    private String[] buildCreateFormatLabels() {
        String[] labels = new String[ARCHIVE_FORMAT_OPTIONS.length];
        for (int i = 0; i < ARCHIVE_FORMAT_OPTIONS.length; i++) {
            labels[i] = ARCHIVE_FORMAT_OPTIONS[i].label;
        }
        return labels;
    }

    private static final class ArchiveFormatOption {
        private final String label;
        private final String requiredExtension;
        private final String filterDescription;
        private final String[] extensions;

        private ArchiveFormatOption(String label, String requiredExtension, String filterDescription,
                String... extensions) {
            this.label = label;
            this.requiredExtension = requiredExtension;
            this.filterDescription = filterDescription;
            this.extensions = extensions;
        }
    }

    private Path resolveTargetDirectory() {
        FileNode selected = model.getSelectedFile();
        return getTargetDirectory(selected);
    }

    private void executeInBackground(String message, Runnable task) {
        if (parentComponent != null) {
            parentComponent.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        }
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                task.run();
                return null;
            }

            @Override
            protected void done() {
                if (parentComponent != null) {
                    parentComponent.setCursor(java.awt.Cursor.getDefaultCursor());
                }
                try {
                    get(); // Surface any exceptions from doInBackground
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof Exception) {
                        showError(message, (Exception) cause);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        worker.execute();
    }

    private void showError(String operation, Exception ex) {
        UIUtils.showError(parentComponent, operation, ex);
    }
}
