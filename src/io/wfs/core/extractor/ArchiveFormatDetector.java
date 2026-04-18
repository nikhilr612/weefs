package io.wfs.core.extractor;

import java.nio.file.Path;
import java.util.Locale;

final class ArchiveFormatDetector {

    private ArchiveFormatDetector() {
    }

    static ArchiveFormat detect(Path archiveFile) {
        String name = archiveFile.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return new ArchiveFormat(ArchiveContainerType.TAR, "gz");
        }
        if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2")) {
            return new ArchiveFormat(ArchiveContainerType.TAR, "bz2");
        }
        if (name.endsWith(".tar.xz") || name.endsWith(".txz")) {
            return new ArchiveFormat(ArchiveContainerType.TAR, "xz");
        }
        if (name.endsWith(".tar.lzma")) {
            return new ArchiveFormat(ArchiveContainerType.TAR, "lzma");
        }
        if (name.endsWith(".tar")) {
            return new ArchiveFormat(ArchiveContainerType.TAR, "none");
        }
        if (name.endsWith(".zip")) {
            return new ArchiveFormat(ArchiveContainerType.ZIP, "none");
        }

        if (name.endsWith(".gz")) {
            return new ArchiveFormat(ArchiveContainerType.SINGLE_FILE, "gz");
        }
        if (name.endsWith(".bz2")) {
            return new ArchiveFormat(ArchiveContainerType.SINGLE_FILE, "bz2");
        }
        if (name.endsWith(".xz")) {
            return new ArchiveFormat(ArchiveContainerType.SINGLE_FILE, "xz");
        }
        if (name.endsWith(".lzma")) {
            return new ArchiveFormat(ArchiveContainerType.SINGLE_FILE, "lzma");
        }

        throw new IllegalArgumentException("Unsupported archive format: " + archiveFile);
    }
}