package io.wfs.core.extractor;

import io.wfs.core.extractor.compression.CompressionStrategyType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class ArchiveFormatDetector {

    private ArchiveFormatDetector() {
    }

    static ArchiveFormat detect(Path archiveFile) {
        String name = archiveFile.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return new ArchiveFormat(ArchiveContainerType.TAR, CompressionStrategyType.GZ);
        }
        if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2")) {
            return new ArchiveFormat(ArchiveContainerType.TAR, CompressionStrategyType.BZ2);
        }
        if (name.endsWith(".tar.xz") || name.endsWith(".txz")) {
            return new ArchiveFormat(ArchiveContainerType.TAR, CompressionStrategyType.XZ);
        }
        if (name.endsWith(".tar.lzma")) {
            return new ArchiveFormat(ArchiveContainerType.TAR, CompressionStrategyType.LZMA);
        }
        if (name.endsWith(".tar")) {
            return new ArchiveFormat(ArchiveContainerType.TAR, CompressionStrategyType.NONE);
        }
        if (name.endsWith(".zip")) {
            return new ArchiveFormat(ArchiveContainerType.ZIP, CompressionStrategyType.NONE);
        }

        if (name.endsWith(".gz")) {
            return new ArchiveFormat(ArchiveContainerType.SINGLE_FILE, CompressionStrategyType.GZ);
        }
        if (name.endsWith(".bz2")) {
            return new ArchiveFormat(ArchiveContainerType.SINGLE_FILE, CompressionStrategyType.BZ2);
        }
        if (name.endsWith(".xz")) {
            return new ArchiveFormat(ArchiveContainerType.SINGLE_FILE, CompressionStrategyType.XZ);
        }
        if (name.endsWith(".lzma")) {
            return new ArchiveFormat(ArchiveContainerType.SINGLE_FILE, CompressionStrategyType.LZMA);
        }

        if (looksLikeZipArchive(archiveFile)) {
            return new ArchiveFormat(ArchiveContainerType.ZIP, CompressionStrategyType.NONE);
        }

        throw new IllegalArgumentException("Unsupported archive format: " + archiveFile);
    }

    private static boolean looksLikeZipArchive(Path archiveFile) {
        if (!Files.isRegularFile(archiveFile)) {
            return false;
        }

        byte[] signature = new byte[4];
        try (InputStream in = Files.newInputStream(archiveFile)) {
            int read = in.read(signature);
            if (read < 4) {
                return false;
            }
        } catch (IOException ignored) {
            return false;
        }

        return signature[0] == 'P' && signature[1] == 'K'
                && ((signature[2] == 3 && signature[3] == 4)
                || (signature[2] == 5 && signature[3] == 6)
                || (signature[2] == 7 && signature[3] == 8));
    }
}