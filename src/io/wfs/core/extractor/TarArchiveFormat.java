package io.wfs.core.extractor;

import java.io.IOException;
import java.nio.file.Path;

/**
 * TAR archive format handler.
 * Delegates to {@link ExtZipFsIO} for the actual I/O operations.
 */
final class TarArchiveFormat implements ArchiveFormat {

    @Override
    public boolean supports(Path archiveFile) {
        String name = archiveFile.getFileName().toString().toLowerCase();
        return name.endsWith(".tar");
    }

    @Override
    public void extract(Path archiveFile, Path targetRoot) throws IOException {
        ExtZipFsIO.extractTarToDirectory(archiveFile, targetRoot, "none");
    }

    @Override
    public void write(Path sourceRoot, Path archiveFile) throws IOException {
        ExtZipFsIO.writeDirectoryToTar(sourceRoot, archiveFile, "none");
    }
}
