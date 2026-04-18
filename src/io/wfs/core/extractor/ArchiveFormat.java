package io.wfs.core.extractor;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for archive format algorithms.
 */
public interface ArchiveFormat {

    boolean supports(Path archiveFile);

    void extract(Path archiveFile, Path targetRoot) throws IOException;

    void write(Path sourceRoot, Path archiveFile) throws IOException;
}
