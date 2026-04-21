package io.wfs.ui.controller;

import io.wfs.ui.model.ArchiveModel;

import java.awt.*;
import java.nio.file.Path;

/**
 * Contract for the UI controller component in the MVC triad.
 *
 * <p>
 * Views depend on this interface — never on the concrete
 * {@link ArchiveController} — so alternative implementations
 * (headless, test doubles, remote delegates) can be substituted
 * without touching any view code.
 * </p>
 *
 * <p>
 * Responsibilities encoded here:
 * </p>
 * <ul>
 * <li>Archive lifecycle — open, create, close, save</li>
 * <li>File-level mutations — new file/dir, rename, delete, extract, save
 * content</li>
 * <li>Infrastructure — parent-component registration, model access</li>
 * </ul>
 */
public interface IArchiveController {

    // ── Infrastructure ────────────────────────────────────────────────

    /**
     * Registers the Swing component that owns dialogs produced by this
     * controller (used as the {@code parentComponent} in every
     * {@link javax.swing.JOptionPane} / {@link javax.swing.JFileChooser} call).
     *
     * @param parent the owning component; may be {@code null}
     */
    void setParentComponent(Component parent);

    /**
     * Returns the shared model this controller operates on.
     *
     * @return the {@link ArchiveModel}; never {@code null}
     */
    ArchiveModel getModel();

    /**
     * Returns the file-operations delegate used for low-level mutations.
     *
     * @return the {@link IFileOperations} instance; never {@code null}
     */
    IFileOperations getFileOps();

    // ── Archive lifecycle ─────────────────────────────────────────────

    /**
     * Prompts the user to choose a local directory and mounts it
     * as a browsable (read-write or read-only) file system.
     */
    void openDirectory();

    /**
     * Prompts the user to choose an archive file and an access mode
     * (read-write / read-only), then mounts it via the model.
     */
    void openArchive();

    /**
     * Prompts the user for a weefs:// URI and mounts an SFTP-backed
     * remote file system.
     */
    void mountNfs();

    /**
     * Prompts the user to choose a destination path and format,
     * then creates and mounts a new empty archive.
     */
    void createArchive();

    /**
     * Marks the currently selected file/directory for copying (non-destructive).
     * No-op if nothing is selected.
     */
    void copySelected();

    /**
     * Marks the currently selected file/directory for moving (cut).
     * No-op if nothing is selected.
     */
    void cutSelected();

    /**
     * Pastes the clipboard entry into the target directory of the current selection
     * (or the session root if nothing is selected).
     * No-op if the clipboard is empty or the destination is read-only.
     */
    void pasteSelected();

    /**
     * Returns {@code true} if there is a pending clipboard entry (copy or cut).
     */
    boolean hasClipboard();

    /**
     * Closes and removes the mount session with the given ID.
     * No-op if the ID is not found.
     *
     * @param sessionId the UUID of the session to close
     */
    void closeSession(String sessionId);

    /**
     * Saves (flushes) the archive session with the given ID by closing its
     * underlying filesystem (which writes the archive to disk) and reopening it.
     * No-op for directory mounts, NFS sessions, read-only sessions, or unknown IDs.
     *
     * @param sessionId the UUID of the session to save
     */
    void saveSession(String sessionId);

    /**
     * Flushes and unmounts the currently active archive session.
     * No-op if no archive is mounted.
     */
    void closeArchive();

    /**
     * Persists the current in-memory archive state to disk.
     * No-op if no archive is open or the archive is read-only.
     */
    void saveArchive();

    // ── File-level mutations (UI-prompted) ────────────────────────────

    /**
     * Prompts the user for a file name and creates an empty file under
     * the currently selected directory (or the archive root if nothing
     * is selected). No-op when the archive is closed or read-only.
     */
    void newFile();

    /**
     * Prompts the user for a directory name and creates it under the
     * currently selected directory (or the archive root). No-op when
     * the archive is closed or read-only.
     */
    void newDirectory();

    /**
     * Prompts for confirmation then deletes the currently selected
     * file or directory (recursively). No-op when nothing is selected,
     * the archive is closed, or the archive is read-only.
     */
    void deleteSelected();

    /**
     * Prompts the user for a new name and renames the currently
     * selected entry. No-op when nothing is selected, the archive is
     * closed, or the archive is read-only.
     */
    void renameSelected();

    /**
     * Prompts the user for a local destination and extracts the
     * currently selected file to it. No-op when a directory or nothing
     * is selected.
     */
    void extractSelected();

    /**
     * Writes {@code content} to the file at {@code path} inside the
     * archive. Called by the content-viewer panel when the user saves
     * an open editor buffer.
     *
     * @param path    the archive path of the file to overwrite
     * @param content the new text content
     */
    void saveFileContent(Path path, String content);
}
