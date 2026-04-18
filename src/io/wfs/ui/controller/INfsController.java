package io.wfs.ui.controller;

import io.wfs.core.nfs.NfsConnectionConfig;

/**
 * Extension interface for NFS control operations.
 * Complements IArchiveController with NFS-specific methods.
 * Views can use this interface to control NFS mounts via the controller.
 * Interface Segregation principle (SOLID) — only NFS-relevant methods here.
 */
public interface INfsController {

    /**
     * Returns the NFS file operations delegate.
     * @return the {@link NfsFileOperations} instance; never {@code null}
     */
    NfsFileOperations getNfsFileOps();

    /**
     * Prompts user to enter NFS connection details and mounts the NFS share.
     * Updates model with the mounted file system.
     */
    void mountNfs();

    /**
     * Unmounts the currently active NFS share and closes the file system.
     * No-op if no NFS share is currently mounted.
     */
    void unmountNfs();

    /**
     * Opens file browser dialog to extract a file from NFS to local filesystem.
     * Only for files, not directories.
     */
    void extractNfsSelected();

    /**
     * Gets the current NFS connection configuration if one is mounted.
     * @return the configuration, or null if not mounted
     */
    NfsConnectionConfig getCurrentNfsConfig();

    /**
     * Checks if an NFS share is currently mounted.
     * @return true if NFS is active, false otherwise
     */
    boolean isNfsMounted();
}
