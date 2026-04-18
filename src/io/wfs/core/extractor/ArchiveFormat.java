package io.wfs.core.extractor;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Strategy interface for reading and writing archive formats.
 * Implementing classes handle format-specific I/O (ZIP, TAR, etc.)
 * without modifying existing code — new formats are added by creating
 * new implementations (Open/Closed Principle).
 */
public interface ArchiveFormat {

    /**
     * Returns {@code true} if this format handles the given archive file,
     * typically based on file extension.
     *
     * @param archiveFile the archive path to test
     * @return true if this format can handle the file
     */
    boolean supports(Path archiveFile);

    /**
     * Extracts the contents of {@code archiveFile} into {@code targetRoot}.
     *
     * @param archiveFile the archive to extract
     * @param targetRoot  the directory to extract into
     * @throws IOException if extraction fails
     */
    void extract(Path archiveFile, Path targetRoot) throws IOException;

    /**
     * Writes the contents of {@code sourceRoot} into {@code archiveFile}.
     *
     * @param sourceRoot  the directory whose contents to archive
     * @param archiveFile the destination archive path
     * @throws IOException if writing fails
     */
    void write(Path sourceRoot, Path archiveFile) throws IOException;
}
