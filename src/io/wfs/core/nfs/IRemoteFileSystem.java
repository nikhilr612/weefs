package io.wfs.core.nfs;

import java.nio.file.FileSystem;
import java.nio.file.OpenOption;
import java.util.Set;

/**
 * Abstraction over the dual-mode NFS file system (SFTP and legacy NFS).
 * Makes the two operating modes explicit and testable without exposing
 * the concrete {@link NfsFileSystem} class to external consumers.
 */
interface IRemoteFileSystem {

    /** Returns the SFTP config; throws {@link IllegalStateException} if legacy mode. */
    NfsSftpConfig sftpConfig();

    /** Returns {@code true} if this file system was opened in SFTP mode. */
    boolean hasSftpConfig();

    /** Translates a virtual {@link NfsPath} to its remote path string. */
    String toRemotePath(NfsPath path);

    /** Throws {@link UnsupportedOperationException} if the file system is read-only. */
    void ensureWritable();

    /** Throws if any option in {@code options} implies a write operation on a read-only fs. */
    void ensureWritableFor(Set<? extends OpenOption> options);

    /** Throws {@link java.nio.file.FileSystemNotFoundException} if the file system is closed. */
    void ensureOpen();

    /** Returns the underlying {@link FileSystem} (the implementing object itself). */
    FileSystem fileSystem();
}
