package io.wfs.ui.controller;

import io.wfs.ui.model.ArchiveModel;
import io.wfs.ui.model.FileNode;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Default Swing implementation of {@link IArchiveController}.
 * Coordinates between the model and views, handling high-level user
 * actions: open, create, close, save archives.
 * Delegates individual file operations to {@link FileOperations}.
 */
public final class ArchiveController implements IArchiveController {

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
    private Component parentComponent;

    public ArchiveController(ArchiveModel model) {
        this.model = model;
        this.fileOps = new FileOperations(model);
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
    public FileOperations getFileOps() {
        return fileOps;
    }

    // ── Archive-level actions ──────────────────────────────────────────

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
        executeInBackground("Closing archive...", () -> {
            try {
                model.closeArchive();
            } catch (IOException ex) {
                showError("Close Archive", ex);
            }
        });
    }

    @Override
    public void saveArchive() {
        if (!model.isOpen() || model.isReadOnly())
            return;
        executeInBackground("Saving archive...", () -> {
            try {
                Path archiveToReopen = model.getArchivePath();
                model.closeArchive();
                if (archiveToReopen != null) {
                    model.openArchive(archiveToReopen, false);
                }
            } catch (IOException ex) {
                showError("Save Archive", ex);
            }
        });
    }

    // ── File-level actions (prompting UI) ──────────────────────────────

    @Override
    public void newFile() {
        if (!model.isOpen() || model.isReadOnly())
            return;
        FileNode selected = model.getSelectedFile();
        Path parentDir = getTargetDirectory(selected);
        if (parentDir == null)
            return;

        String name = JOptionPane.showInputDialog(parentComponent,
                "Enter file name:", "New File", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank())
            return;

        Path filePath = parentDir.resolve(name);
        fileOps.createFile(filePath, "");
    }

    @Override
    public void newDirectory() {
        if (!model.isOpen() || model.isReadOnly())
            return;
        FileNode selected = model.getSelectedFile();
        Path parentDir = getTargetDirectory(selected);
        if (parentDir == null)
            return;

        String name = JOptionPane.showInputDialog(parentComponent,
                "Enter directory name:", "New Directory", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank())
            return;

        Path dirPath = parentDir.resolve(name);
        fileOps.createDirectory(dirPath);
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
            fileOps.delete(selected);
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

        Path newPath = parent.resolve(newName);
        fileOps.rename(oldPath, newPath);
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
            fileOps.extractTo(selected.getPath(), destination);
        }
    }

    @Override
    public void saveFileContent(Path path, String content) {
        fileOps.saveFile(path, content);
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

        private ArchiveFormatOption(String label, String requiredExtension, String filterDescription, String... extensions) {
            this.label = label;
            this.requiredExtension = requiredExtension;
            this.filterDescription = filterDescription;
            this.extensions = extensions;
        }
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
            }
        };
        worker.execute();
    }

    private void showError(String operation, Exception ex) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parentComponent,
                operation + " failed:\n" + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE));
    }
}
