package io.wfs.ui.model;

import io.wfs.core.filesystem.FileSystemFactory;
import io.wfs.core.filesystem.FsEnvKeys;
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
 * Central model for the archive browser.
 * Supports multiple simultaneously mounted sessions (archives, directories,
 * NFS shares). Each mount operation adds a new {@link MountSession} rather
 * than replacing the existing one.
 *
 * <p>Property-change events are fired on the EDT so views can react
 * (Observer pattern).</p>
 */
public final class ArchiveModel {

    // ── Property names ─────────────────────────────────────────────────
    public static final String PROP_ARCHIVE_PATH    = "archivePath";
    public static final String PROP_NFS_CONFIG      = "nfsConfig";
    public static final String PROP_OPEN            = "open";
    public static final String PROP_READ_ONLY       = "readOnly";
    public static final String PROP_REMOTE_MOUNTED  = "remoteMounted";
    public static final String PROP_SELECTED_FILE   = "selectedFile";
    public static final String PROP_TREE_REFRESH    = "treeRefresh";
    /** Fired when a new {@link MountSession} is added; new value is the session. */
    public static final String PROP_SESSION_ADDED   = "sessionAdded";
    /** Fired when a session is removed; new value is the removed session's id string. */
    public static final String PROP_SESSION_REMOVED = "sessionRemoved";

    // ── State ──────────────────────────────────────────────────────────
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final FileSystemFactory fileSystemFactory = new FileSystemFactory();

    private final List<MountSession> sessions = new ArrayList<>();
    private MountSession activeSession;
    private FileNode selectedFile;

    // ── Listener registration ──────────────────────────────────────────

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    // ── Session registry ───────────────────────────────────────────────

    /** Returns an unmodifiable snapshot of all current mount sessions. */
    public List<MountSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public MountSession getActiveSession() {
        return activeSession;
    }

    /** Returns the session with the given ID, or {@code null} if not found. */
    public MountSession getSession(String id) {
        for (MountSession s : sessions) {
            if (s.getId().equals(id)) return s;
        }
        return null;
    }

    /**
     * Switches the active session without closing it.
     * Fires {@link #PROP_ARCHIVE_PATH}, {@link #PROP_READ_ONLY}, and
     * {@link #PROP_SELECTED_FILE} (null) so panels update to the new context.
     */
    public void setActiveSession(String id) {
        for (MountSession s : sessions) {
            if (s.getId().equals(id)) {
                MountSession prev = activeSession;
                activeSession = s;
                selectedFile = null;
                final boolean prevRemote = prev != null && prev.remoteMounted;
                final NfsConnectionConfig prevNfs = prev != null ? prev.nfsConfig : null;
                fireOnEdt(() -> {
                    pcs.firePropertyChange(PROP_SELECTED_FILE, null, null);
                    pcs.firePropertyChange(PROP_ARCHIVE_PATH,
                            prev != null ? prev.displayPath : null, s.displayPath);
                    if (prev == null || prev.readOnly != s.readOnly) {
                        pcs.firePropertyChange(PROP_READ_ONLY,
                                prev != null && prev.readOnly, s.readOnly);
                    }
                    if (prevRemote != s.remoteMounted) {
                        pcs.firePropertyChange(PROP_REMOTE_MOUNTED, prevRemote, s.remoteMounted);
                    }
                    if (prevNfs != s.nfsConfig) {
                        pcs.firePropertyChange(PROP_NFS_CONFIG, prevNfs, s.nfsConfig);
                    }
                });
                return;
            }
        }
    }

    /** Closes and removes the session with the given id. */
    public void closeSession(String id) throws IOException {
        MountSession toRemove = null;
        for (MountSession s : sessions) {
            if (s.getId().equals(id)) { toRemove = s; break; }
        }
        if (toRemove == null) return;

        final MountSession removed = toRemove;
        sessions.remove(removed);

        boolean wasActive    = (activeSession == removed);
        boolean wasNfs       = removed.isNfsMounted();
        boolean wasRemote    = removed.remoteMounted;
        // Capture config before close() nulls it
        final NfsConnectionConfig oldNfsConfig = removed.nfsConfig;
        removed.close();

        if (wasActive) {
            activeSession = sessions.isEmpty() ? null : sessions.get(sessions.size() - 1);
        }

        final boolean      nowEmpty   = sessions.isEmpty();
        final MountSession newActive  = activeSession;

        fireOnEdt(() -> {
            pcs.firePropertyChange(PROP_SESSION_REMOVED, removed, id);
            if (wasNfs) {
                pcs.firePropertyChange(PROP_NFS_CONFIG, oldNfsConfig, null);
            }
            if (wasActive) {
                if (nowEmpty) {
                    pcs.firePropertyChange(PROP_OPEN, true, false);
                    pcs.firePropertyChange(PROP_ARCHIVE_PATH, removed.displayPath, null);
                    if (wasRemote) {
                        pcs.firePropertyChange(PROP_REMOTE_MOUNTED, true, false);
                    }
                } else {
                    selectedFile = null;
                    pcs.firePropertyChange(PROP_SELECTED_FILE, null, null);
                    pcs.firePropertyChange(PROP_ARCHIVE_PATH,
                            removed.displayPath, newActive.displayPath);
                    pcs.firePropertyChange(PROP_READ_ONLY,
                            removed.readOnly, newActive.readOnly);
                    // Fire remote/NFS status if it changed when switching active session
                    if (wasRemote != newActive.remoteMounted) {
                        pcs.firePropertyChange(PROP_REMOTE_MOUNTED, wasRemote, newActive.remoteMounted);
                    }
                    if (oldNfsConfig != newActive.nfsConfig) {
                        pcs.firePropertyChange(PROP_NFS_CONFIG, oldNfsConfig, newActive.nfsConfig);
                    }
                }
            }
        });
    }

    // ── Open operations ────────────────────────────────────────────────

    /**
     * Mounts a local directory as a browsable session without closing
     * any existing sessions.
     */
    public void openDirectory(Path dir, boolean readOnly) throws IOException {
        URI uri = dir.toUri();
        Map<String, String> env = readOnly ? Map.of(FsEnvKeys.READ_ONLY, "true") : Map.of();
        FileSystem fs = fileSystemFactory.open(uri, env);
        String name = dir.getFileName() != null ? dir.getFileName().toString() : dir.toString();
        String label = readOnly ? name + " [Read Only]" : name;
        addSession(new MountSession(label, fs, dir, dir, readOnly, false));
    }

    /**
     * Opens an archive at the given local path as a new session.
     */
    public void openArchive(Path archive, boolean readOnly) throws IOException {
        URI uri = URI.create("xzip:" + archive.toUri() + "!/");
        openMountUri(uri, readOnly, archive);
    }

    /**
     * Mounts a URI-backed file system (weefs:// remote or xzip:// archive)
     * as a new session.
     */
    public void openMountUri(URI uri, boolean readOnly) throws IOException {
        Path displayPath = Path.of(uri.getHost() == null
                ? uri.toString()
                : uri.getHost() + uri.getPath());
        openMountUri(uri, readOnly, displayPath);
    }

    private void openMountUri(URI uri, boolean readOnly, Path displayPath) throws IOException {
        boolean isRemote = "weefs".equalsIgnoreCase(uri.getScheme());
        Map<String, String> env = readOnly ? Map.of(FsEnvKeys.READ_ONLY, "true") : Map.of();
        FileSystem fs = fileSystemFactory.open(uri, env);

        String name = displayPath.getFileName() != null
                ? displayPath.getFileName().toString()
                : displayPath.toString();
        String label = readOnly ? name + " [Read Only]" : name;
        addSession(new MountSession(label, fs, null, displayPath, readOnly, isRemote));
    }

    /**
     * Creates a new empty archive and opens it as a new session.
     */
    public void createArchive(Path archive) throws IOException {
        Path parent = archive.getParent();
        if (parent != null) Files.createDirectories(parent);
        openArchive(archive, false);
    }

    /**
     * Adds or removes an NFS-only session.
     * Passing {@code null} closes the currently active NFS session (if any).
     */
    public void setNfsConfig(NfsConnectionConfig config) throws IOException {
        if (config != null) {
            String label = config.getHost() + ":" + config.getPort() + config.getExportPath();
            if (config.isReadOnly()) label += " [Read Only]";
            MountSession session = new MountSession(label, config);
            addSession(session);
            final NfsConnectionConfig cfg = config;
            fireOnEdt(() -> pcs.firePropertyChange(PROP_NFS_CONFIG, null, cfg));
        } else {
            // Close the first NFS session found
            String nfsId = null;
            if (activeSession != null && activeSession.isNfsMounted()) {
                nfsId = activeSession.getId();
            } else {
                for (MountSession s : sessions) {
                    if (s.isNfsMounted()) { nfsId = s.getId(); break; }
                }
            }
            if (nfsId != null) closeSession(nfsId);
            // closeSession already fires PROP_NFS_CONFIG (oldConfig→null); no extra fire needed.
        }
    }

    private void addSession(MountSession session) {
        boolean wasEmpty = sessions.isEmpty();
        sessions.add(session);
        MountSession prev = activeSession;
        activeSession = session;

        fireOnEdt(() -> {
            pcs.firePropertyChange(PROP_SESSION_ADDED, null, session);
            if (wasEmpty) {
                pcs.firePropertyChange(PROP_OPEN, false, true);
            }
            // Switching active → update status bar and mode indicator
            pcs.firePropertyChange(PROP_ARCHIVE_PATH,
                    prev != null ? prev.displayPath : null, session.displayPath);
            pcs.firePropertyChange(PROP_READ_ONLY,
                    prev != null && prev.readOnly, session.readOnly);
            pcs.firePropertyChange(PROP_SELECTED_FILE, null, null);
            if (session.remoteMounted) {
                pcs.firePropertyChange(PROP_REMOTE_MOUNTED, false, true);
            }
        });
    }

    // ── Close operations ───────────────────────────────────────────────

    /**
     * Closes all sessions — used on application shutdown.
     */
    public void closeArchive() throws IOException {
        List<String> ids = new ArrayList<>();
        for (MountSession s : sessions) ids.add(s.getId());
        for (String id : ids) closeSession(id);
    }

    // ── State queries ──────────────────────────────────────────────────

    public boolean isOpen()      { return !sessions.isEmpty(); }
    public boolean isReadOnly()  { return activeSession != null && activeSession.readOnly; }

    public Path getArchivePath() {
        return activeSession != null ? activeSession.displayPath : null;
    }

    public FileSystem getFileSystem() {
        return activeSession != null ? activeSession.fileSystem : null;
    }

    public boolean isRemoteMounted() {
        return activeSession != null && activeSession.remoteMounted;
    }

    public boolean isNfsMounted() {
        if (activeSession != null && activeSession.isNfsMounted()) return true;
        for (MountSession s : sessions) {
            if (s.isNfsMounted()) return true;
        }
        return false;
    }

    public NfsConnectionConfig getNfsConfig() {
        if (activeSession != null && activeSession.isNfsMounted())
            return activeSession.nfsConfig;
        for (MountSession s : sessions) {
            if (s.isNfsMounted()) return s.nfsConfig;
        }
        return null;
    }

    public Path getRootPath() {
        return activeSession != null ? activeSession.getRootPath() : null;
    }

    // ── File listing / content ─────────────────────────────────────────

    /**
     * Lists the children of {@code directory} using the session that owns
     * that path. Does not change the active session.
     */
    public List<FileNode> listChildren(Path directory) throws IOException {
        if (!isOpen()) return Collections.emptyList();
        MountSession session = findSessionForPath(directory);
        return listChildrenInSession(session, directory);
    }

    /**
     * Lists the children of {@code directory} within a specific session,
     * without changing the active session. Used by the tree panel during
     * lazy node expansion.
     */
    public List<FileNode> listChildrenForSession(String sessionId, Path directory) throws IOException {
        MountSession session = findSessionById(sessionId);
        if (session == null) return Collections.emptyList();
        return listChildrenInSession(session, directory);
    }

    private List<FileNode> listChildrenInSession(MountSession session, Path directory)
            throws IOException {
        if (session == null) return Collections.emptyList();

        if (session.isNfsMounted()) {
            NfsConnectionConfig cfg = session.nfsConfig;
            List<NfsFileInfo> nfsChildren = NfsIO.listDirectory(cfg, directory.toString());
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
            for (Path entry : stream) children.add(new FileNode(entry));
        }
        Collections.sort(children);
        return children;
    }

    public String readFileContent(Path path) throws IOException {
        MountSession session = findSessionForPath(path);
        if (session != null && session.isNfsMounted()) {
            return new String(NfsIO.readFile(session.nfsConfig, path.toString()),
                    StandardCharsets.UTF_8);
        }
        return Files.readString(path);
    }

    public byte[] readFileBytes(Path path) throws IOException {
        MountSession session = findSessionForPath(path);
        if (session != null && session.isNfsMounted()) {
            return NfsIO.readFile(session.nfsConfig, path.toString());
        }
        return Files.readAllBytes(path);
    }

    // ── Selection ──────────────────────────────────────────────────────

    public FileNode getSelectedFile() { return selectedFile; }

    public void setSelectedFile(FileNode node) {
        FileNode old = this.selectedFile;
        this.selectedFile = node;
        fireOnEdt(() -> pcs.firePropertyChange(PROP_SELECTED_FILE, old, node));
    }

    public void fireTreeRefresh() {
        fireOnEdt(() -> pcs.firePropertyChange(PROP_TREE_REFRESH, null,
                System.currentTimeMillis()));
    }

    // ── Internal helpers ───────────────────────────────────────────────

    private MountSession findSessionById(String id) {
        for (MountSession s : sessions) {
            if (s.getId().equals(id)) return s;
        }
        return null;
    }

    /**
     * Finds the session that "owns" the given path, without changing the
     * active session. Routing order:
     * <ol>
     *   <li>Archive sessions: path's FileSystem matches the session's FileSystem</li>
     *   <li>Directory sessions: path starts with the session's directoryRoot</li>
     *   <li>Active session (fallback, covers NFS whose paths are default-FS paths)</li>
     * </ol>
     */
    private MountSession findSessionForPath(Path path) {
        if (path == null) return activeSession;
        // Archive / remote sessions: path lives inside that session's FS
        for (MountSession s : sessions) {
            if (s.fileSystem != null && s.fileSystem == path.getFileSystem()) return s;
        }
        // Directory sessions: real-FS paths rooted under directoryRoot
        for (MountSession s : sessions) {
            if (s.directoryRoot != null && path.startsWith(s.directoryRoot)) return s;
        }
        // Fallback (NFS or unrooted paths)
        return activeSession;
    }

    private static void fireOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private static String joinRemotePath(String parent, String childName) {
        if (parent == null || parent.isBlank() || "/".equals(parent))
            return "/" + childName;
        return parent.endsWith("/") ? parent + childName : parent + "/" + childName;
    }
}
