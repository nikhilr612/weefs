package io.wfs.ui.model;

import io.wfs.core.nfs.NfsConnectionConfig;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Represents one mounted item (archive, local directory, or NFS share).
 * Each open operation creates a new {@code MountSession} that is added
 * to the {@link ArchiveModel} session list rather than replacing an existing one.
 */
public final class MountSession {

    private final String id = UUID.randomUUID().toString();
    private final String label;

    final FileSystem fileSystem;      // null for NFS-only sessions
    final Path directoryRoot;         // non-null when a local directory is mounted
    final boolean remoteMounted;      // true for weefs:// remote mounts
    final boolean readOnly;
    final Path displayPath;           // human-readable path shown in status bar / title
    NfsConnectionConfig nfsConfig;    // non-null for NFS-only sessions

    /** Constructor for local archive or directory mounts and weefs:// remote mounts. */
    MountSession(String label, FileSystem fs, Path directoryRoot,
                 Path displayPath, boolean readOnly, boolean remoteMounted) {
        this.label = label;
        this.fileSystem = fs;
        this.directoryRoot = directoryRoot;
        this.displayPath = displayPath;
        this.readOnly = readOnly;
        this.remoteMounted = remoteMounted;
    }

    /** Constructor for NFS-only sessions (no local FileSystem). */
    MountSession(String label, NfsConnectionConfig nfsConfig) {
        this.label = label;
        this.fileSystem = null;
        this.directoryRoot = null;
        this.displayPath = null;
        this.readOnly = nfsConfig.isReadOnly();
        this.remoteMounted = false;
        this.nfsConfig = nfsConfig;
    }

    public String getId()             { return id; }
    public String getLabel()          { return label; }
    public boolean isReadOnly()       { return readOnly; }
    public boolean isNfsMounted()     { return nfsConfig != null; }
    public boolean isRemoteMounted()  { return remoteMounted; }
    public NfsConnectionConfig getNfsConfig() { return nfsConfig; }

    /** Returns the browse-root for this session (archive root, directory, or NFS "/"). */
    public Path getRootPath() {
        if (directoryRoot != null) return directoryRoot;
        if (fileSystem != null)    return fileSystem.getPath("/");
        return Path.of("/");
    }

    boolean isSessionOpen() {
        return isNfsMounted() || (fileSystem != null && fileSystem.isOpen());
    }

    /** Closes the underlying FileSystem (skips the JVM default FS which cannot be closed). */
    void close() {
        nfsConfig = null;
        if (fileSystem != null && fileSystem.isOpen()
                && fileSystem != FileSystems.getDefault()) {
            try { fileSystem.close(); } catch (Exception ignored) { }
        }
    }
}
