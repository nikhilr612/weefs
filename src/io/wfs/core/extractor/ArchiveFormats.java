package io.wfs.core.extractor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Registry of available {@link ArchiveFormat} implementations.
 * Selects the correct format strategy for a given archive file.
 * New formats are added here — no existing code needs modification (OCP).
 */
public final class ArchiveFormats {

    private static final List<ArchiveFormat> FORMATS = List.of(
            new ZipArchiveFormat(),
            new TarArchiveFormat()
    );

    private ArchiveFormats() {
    }

    /**
     * Finds the {@link ArchiveFormat} that supports the given archive path.
     *
     * @param archiveFile the archive to find a handler for
     * @return the matching format
     * @throws IOException if no format supports the file
     */
    public static ArchiveFormat resolve(Path archiveFile) throws IOException {
        for (ArchiveFormat format : FORMATS) {
            if (format.supports(archiveFile)) {
                return format;
            }
        }
        throw new IOException("Unsupported archive format: " + archiveFile.getFileName());
    }

    /**
     * Extracts an archive to a directory using the appropriate format handler.
     *
     * @param archiveFile the archive to extract
     * @param targetRoot  the directory to extract into
     * @throws IOException if extraction fails or the format is unsupported
     */
    public static void extractToDirectory(Path archiveFile, Path targetRoot) throws IOException {
        resolve(archiveFile).extract(archiveFile, targetRoot);
    }

    /**
     * Writes a directory to an archive using the appropriate format handler.
     *
     * @param sourceRoot  the directory to archive
     * @param archiveFile the destination archive path
     * @throws IOException if writing fails or the format is unsupported
     */
    public static void writeFromDirectory(Path sourceRoot, Path archiveFile) throws IOException {
        resolve(archiveFile).write(sourceRoot, archiveFile);
    }
}
