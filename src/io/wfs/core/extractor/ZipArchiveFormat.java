package io.wfs.core.extractor;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ZIP archive format handler.
 * Delegates to {@link ExtZipFsIO} for the actual I/O operations.
 */
final class ZipArchiveFormat implements ArchiveFormat {

    @Override
    public boolean supports(Path archiveFile) {
        String name = archiveFile.getFileName().toString().toLowerCase();
        return name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".war");
    }

    @Override
    public void extract(Path archiveFile, Path targetRoot) throws IOException {
        ExtZipFsIO.extractZipToDirectory(archiveFile, targetRoot);
    }

    @Override
    public void write(Path sourceRoot, Path archiveFile) throws IOException {
        ExtZipFsIO.writeDirectoryToZip(sourceRoot, archiveFile);
    }
}
