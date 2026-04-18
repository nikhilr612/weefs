package io.wfs.core.extractor;

import java.nio.file.Path;
import java.util.Locale;

final class ArchiveFormatDetector {

    private ArchiveFormatDetector() {
    }

    static ArchiveFormatInfo detect(Path archiveFile) {
        String name = archiveFile.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return new ArchiveFormatInfo(ArchiveContainerType.TAR, "gz");
        }
        if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2")) {
            return new ArchiveFormatInfo(ArchiveContainerType.TAR, "bz2");
        }
        if (name.endsWith(".tar.xz") || name.endsWith(".txz")) {
            return new ArchiveFormatInfo(ArchiveContainerType.TAR, "xz");
        }
        if (name.endsWith(".tar.lzma")) {
            return new ArchiveFormatInfo(ArchiveContainerType.TAR, "lzma");
        }
        if (name.endsWith(".tar")) {
            return new ArchiveFormatInfo(ArchiveContainerType.TAR, "none");
        }
        if (name.endsWith(".zip")) {
            return new ArchiveFormatInfo(ArchiveContainerType.ZIP, "none");
        }

        if (name.endsWith(".gz")) {
            return new ArchiveFormatInfo(ArchiveContainerType.SINGLE_FILE, "gz");
        }
        if (name.endsWith(".bz2")) {
            return new ArchiveFormatInfo(ArchiveContainerType.SINGLE_FILE, "bz2");
        }
        if (name.endsWith(".xz")) {
            return new ArchiveFormatInfo(ArchiveContainerType.SINGLE_FILE, "xz");
        }
        if (name.endsWith(".lzma")) {
            return new ArchiveFormatInfo(ArchiveContainerType.SINGLE_FILE, "lzma");
        }

        throw new IllegalArgumentException("Unsupported archive format: " + archiveFile);
    }
}