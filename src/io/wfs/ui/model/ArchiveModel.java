package io.wfs.ui.model;

import io.wfs.core.extractor.ExtZipFsProvider;
import io.wfs.core.nfs.NfsConnectionConfig;
import io.wfs.core.nfs.NfsFileInfo;
import io.wfs.core.nfs.NfsIO;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;

/**
 * Central model for the archive/NFS browser.
 * Manages the currently mounted archive FileSystem or NFS connection and fires
 * property-change events so views can react (Observer pattern).
 * Supports both archive (ZIP/TAR) and NFS (Network File System) mounting.
 */
public final class ArchiveModel {

    public static final String PROP_ARCHIVE_PATH = "archivePath";
    public static final String PROP_NFS_CONFIG = "nfsConfig";
    public static final String PROP_OPEN = "open";
    public static final String PROP_READ_ONLY = "readOnly";
    public static final String PROP_SELECTED_FILE = "selectedFile";
    public static final String PROP_TREE_REFRESH = "treeRefresh";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final ExtZipFsProvider provider = new ExtZipFsProvider();

    private Path archivePath;
    private NfsConnectionConfig nfsConfig;
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

        boolean previousReadOnly = this.readOnly;
        this.readOnly = readOnly;
        Path oldPath = this.archivePath;
        this.archivePath = archive;

        URI uri = URI.create("xzip:" + archive.toUri() + "!/");
        Map<String, String> env = readOnly ? Map.of("readOnly", "true") : Map.of();
        this.fileSystem = provider.newFileSystem(uri, env);

        final Path finalOldPath = oldPath;
        final boolean finalPreviousReadOnly = previousReadOnly;
        fireOnEdt(() -> {
            pcs.firePropertyChange(PROP_ARCHIVE_PATH, finalOldPath, archive);
            pcs.firePropertyChange(PROP_OPEN, false, true);
            if (previousReadOnly != readOnly) {
                pcs.firePropertyChange(PROP_READ_ONLY, finalPreviousReadOnly, readOnly);
            }
        });
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
        boolean wasOpen = isOpen();
        if (fileSystem != null && fileSystem.isOpen()) {
            fileSystem.close();
        }
        fileSystem = null;
        Path oldPath = archivePath;
        archivePath = null;
        selectedFile = null;
        if (wasOpen) {
            fireOnEdt(() -> {
                pcs.firePropertyChange(PROP_OPEN, true, false);
                if (oldPath != null) {
                    pcs.firePropertyChange(PROP_ARCHIVE_PATH, oldPath, null);
                }
            });
        }
    }

    /**
     * Sets the NFS configuration for the currently mounted NFS share.
     */
    public void setNfsConfig(NfsConnectionConfig config) throws IOException {
        // Close archive if one is open
        if (fileSystem != null) {
            closeArchive();
        }

        NfsConnectionConfig oldConfig = this.nfsConfig;
        this.nfsConfig = config;
        this.selectedFile = null;

        if (config != null) {
            this.readOnly = config.isReadOnly();
            fireOnEdt(() -> {
                pcs.firePropertyChange(PROP_OPEN, false, true);
                pcs.firePropertyChange(PROP_READ_ONLY, !readOnly, readOnly);
                pcs.firePropertyChange(PROP_NFS_CONFIG, oldConfig, config);
            });
        } else {
            this.readOnly = false;
            fireOnEdt(() -> {
                pcs.firePropertyChange(PROP_OPEN, true, false);
                pcs.firePropertyChange(PROP_NFS_CONFIG, oldConfig, null);
            });
        }
    }

    /**
     * Gets the current NFS configuration.
     */
    public NfsConnectionConfig getNfsConfig() {
        return nfsConfig;
    }

    /**
     * Checks if an NFS share is currently mounted.
     */
    public boolean isNfsMounted() {
        return nfsConfig != null;
    }

    public boolean isOpen() {
        return (fileSystem != null && fileSystem.isOpen()) || isNfsMounted();
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
        if (isNfsMounted()) {
            return Path.of("/");
        }
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

        if (isNfsMounted()) {
            List<NfsFileInfo> nfsChildren = NfsIO.listDirectory(nfsConfig, directory.toString());
            List<FileNode> children = new ArrayList<>();
            for (NfsFileInfo child : nfsChildren) {
                String remotePath = joinRemotePath(directory.toString(), child.getName());
                children.add(new FileNode(Path.of(remotePath), child.getName(), child.isDirectory()));
            }
            Collections.sort(children);
            return children;
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

    /**
     * Returns children for NFS directory.
     */
    public List<NfsFileInfo> listNfsChildren(String remotePath) throws IOException {
        if (!isNfsMounted()) {
            return Collections.emptyList();
        }
        return NfsIO.listDirectory(nfsConfig, remotePath);
    }

    public FileNode getSelectedFile() {
        return selectedFile;
    }

    public void setSelectedFile(FileNode node) {
        FileNode old = this.selectedFile;
        this.selectedFile = node;
        fireOnEdt(() -> pcs.firePropertyChange(PROP_SELECTED_FILE, old, node));
    }

    /**
     * Signals that the tree structure has changed and views should refresh.
     */
    public void fireTreeRefresh() {
        fireOnEdt(() -> pcs.firePropertyChange(PROP_TREE_REFRESH, null, System.currentTimeMillis()));
    }

    /** Dispatches {@code action} on the EDT; runs immediately if already on it. */
    private static void fireOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    /**
     * Reads the text content of a file in the archive.
     */
    public String readFileContent(Path path) throws IOException {
        if (isNfsMounted()) {
            return new String(NfsIO.readFile(nfsConfig, path.toString()), StandardCharsets.UTF_8);
        }
        return Files.readString(path);
    }

    /**
     * Reads the raw bytes of a file in the archive.
     */
    public byte[] readFileBytes(Path path) throws IOException {
        if (isNfsMounted()) {
            return NfsIO.readFile(nfsConfig, path.toString());
        }
        return Files.readAllBytes(path);
    }

    private static String joinRemotePath(String parent, String childName) {
        if (parent == null || parent.isBlank() || "/".equals(parent)) {
            return "/" + childName;
        }
        return parent.endsWith("/") ? parent + childName : parent + "/" + childName;
    }
}
