package io.wfs.ui.model;

import io.wfs.core.extractor.ExtZipFsProvider;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Central model for the archive browser.
 * Manages the currently mounted archive FileSystem and fires
 * property-change events so views can react (Observer pattern).
 */
public final class ArchiveModel {

    public static final String PROP_ARCHIVE_PATH = "archivePath";
    public static final String PROP_OPEN = "open";
    public static final String PROP_READ_ONLY = "readOnly";
    public static final String PROP_SELECTED_FILE = "selectedFile";
    public static final String PROP_TREE_REFRESH = "treeRefresh";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final ExtZipFsProvider provider = new ExtZipFsProvider();

    private Path archivePath;
    private FileSystem fileSystem;
    private boolean readOnly;
    private FileNode selectedFile;

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Opens an archive (ZIP or TAR) at the given local path.
     * If an archive is already open, it is closed first.
     */
    public void openArchive(Path archive, boolean readOnly) throws IOException {
        closeArchive();

        this.readOnly = readOnly;
        Path oldPath = this.archivePath;
        this.archivePath = archive;

        URI uri = URI.create("xzip:" + archive.toUri() + "!/");
        Map<String, String> env = readOnly ? Map.of("readOnly", "true") : Map.of();
        this.fileSystem = provider.newFileSystem(uri, env);

        pcs.firePropertyChange(PROP_ARCHIVE_PATH, oldPath, archive);
        pcs.firePropertyChange(PROP_OPEN, false, true);
        pcs.firePropertyChange(PROP_READ_ONLY, !readOnly, readOnly);
    }

    /**
     * Creates a new empty archive at the given path and opens it.
     */
    public void createArchive(Path archive) throws IOException {
        closeArchive();
        // Ensure parent exists
        Path parent = archive.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        openArchive(archive, false);
    }

    /**
     * Closes the currently mounted archive.
     */
    public void closeArchive() throws IOException {
        if (fileSystem != null && fileSystem.isOpen()) {
            fileSystem.close();
        }
        fileSystem = null;
        Path oldPath = archivePath;
        archivePath = null;
        selectedFile = null;
        pcs.firePropertyChange(PROP_OPEN, true, false);
        if (oldPath != null) {
            pcs.firePropertyChange(PROP_ARCHIVE_PATH, oldPath, null);
        }
    }

    public boolean isOpen() {
        return fileSystem != null && fileSystem.isOpen();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public Path getArchivePath() {
        return archivePath;
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    /**
     * Returns the root path inside the mounted archive.
     */
    public Path getRootPath() {
        if (!isOpen()) {
            return null;
        }
        return fileSystem.getPath("/");
    }

    /**
     * Returns direct children of the given directory path, sorted.
     */
    public List<FileNode> listChildren(Path directory) throws IOException {
        if (!isOpen()) {
            return Collections.emptyList();
        }
        List<FileNode> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                children.add(new FileNode(entry));
            }
        }
        Collections.sort(children);
        return children;
    }

    public FileNode getSelectedFile() {
        return selectedFile;
    }

    public void setSelectedFile(FileNode node) {
        FileNode old = this.selectedFile;
        this.selectedFile = node;
        pcs.firePropertyChange(PROP_SELECTED_FILE, old, node);
    }

    /**
     * Signals that the tree structure has changed and views should refresh.
     */
    public void fireTreeRefresh() {
        pcs.firePropertyChange(PROP_TREE_REFRESH, null, System.currentTimeMillis());
    }

    /**
     * Reads the text content of a file in the archive.
     */
    public String readFileContent(Path path) throws IOException {
        return Files.readString(path);
    }

    /**
     * Reads the raw bytes of a file in the archive.
     */
    public byte[] readFileBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }
}
