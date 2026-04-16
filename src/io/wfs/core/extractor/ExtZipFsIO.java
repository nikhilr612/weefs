package io.wfs.core.extractor;

import io.wfs.core.extractor.compression.CompressionStrategyFactory;
import io.wfs.core.extractor.compression.ICompressionStrategy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarInputStream;
import org.kamranzafar.jtar.TarOutputStream;

final class ExtZipFsIO {

    private ExtZipFsIO() {
    }

    static void extractArchiveToDirectory(Path archiveFile, Path targetRoot) throws IOException {
        ArchiveFormat format = ArchiveFormatDetector.detect(archiveFile);
        switch (format.containerType()) {
            case ZIP -> extractZipToDirectory(archiveFile, targetRoot);
            case TAR -> extractTarToDirectory(archiveFile, targetRoot, format.compressionKey());
            case SINGLE_FILE -> extractSingleFileArchiveToDirectory(archiveFile, targetRoot, format.compressionKey());
        }
    }

    static void extractZipToDirectory(Path zipArchive, Path targetRoot) throws IOException {
        if (!Files.exists(zipArchive)) {
            return;
        }

        try (InputStream in = new BufferedInputStream(Files.newInputStream(zipArchive));
             ZipInputStream zipIn = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path destination = targetRoot.resolve(entry.getName()).normalize();
                if (!destination.startsWith(targetRoot)) {
                    throw new IOException("Blocked zip-slip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
                        zipIn.transferTo(out);
                    }
                }
                zipIn.closeEntry();
            }
        }
    }

    static void extractTarToDirectory(Path tarArchive, Path targetRoot, String compressionKey) throws IOException {
        if (!Files.exists(tarArchive)) {
            return;
        }

        ICompressionStrategy strategy = CompressionStrategyFactory.forKey(compressionKey);
        byte[] buffer = new byte[8192];
        try (InputStream in = new BufferedInputStream(Files.newInputStream(tarArchive));
             InputStream compressedIn = strategy.wrapInput(in);
             TarInputStream tarIn = new TarInputStream(compressedIn)) {
            TarEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                Path destination = targetRoot.resolve(entry.getName()).normalize();
                if (!destination.startsWith(targetRoot)) {
                    throw new IOException("Blocked tar-slip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }

                Path parent = destination.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
                    int count;
                    while ((count = tarIn.read(buffer, 0, buffer.length)) != -1) {
                        out.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    static void writeDirectoryToArchive(Path sourceRoot, Path archiveFile) throws IOException {
        ArchiveFormat format = ArchiveFormatDetector.detect(archiveFile);
        switch (format.containerType()) {
            case ZIP -> writeDirectoryToZip(sourceRoot, archiveFile);
            case TAR -> writeDirectoryToTar(sourceRoot, archiveFile, format.compressionKey());
            case SINGLE_FILE -> writeDirectoryToSingleFileArchive(sourceRoot, archiveFile, format.compressionKey());
        }
    }

    static void writeDirectoryToZip(Path sourceRoot, Path archiveFile) throws IOException {
        Path parent = archiveFile.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempArchive = Files.createTempFile(parent, "extzipfs-", ".zip");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempArchive));
                ZipOutputStream zipOut = new ZipOutputStream(out);
                Stream<Path> walk = Files.walk(sourceRoot)) {
                List<Path> entries = walk.sorted().collect(Collectors.toList());
                for (Path path : entries) {
                    if (path.equals(sourceRoot)) {
                        continue;
                    }

                    Path relative = sourceRoot.relativize(path);
                    String name = toZipEntryName(relative);

                    if (Files.isDirectory(path)) {
                        ZipEntry dirEntry = new ZipEntry(name + "/");
                        zipOut.putNextEntry(dirEntry);
                        zipOut.closeEntry();
                        continue;
                    }

                    zipOut.putNextEntry(new ZipEntry(name));
                    try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                        in.transferTo(zipOut);
                    }
                    zipOut.closeEntry();
                }
            }

            try {
                Files.move(tempArchive, archiveFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.move(tempArchive, archiveFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempArchive);
        }
    }

    static void writeDirectoryToTar(Path sourceRoot, Path archiveFile, String compressionKey) throws IOException {
        Path parent = archiveFile.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        ICompressionStrategy strategy = CompressionStrategyFactory.forKey(compressionKey);
        String suffix = "none".equals(compressionKey) ? ".tar" : ".tar." + compressionKey;
        Path tempArchive = Files.createTempFile(parent, "extzipfs-", suffix);
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempArchive));
                 OutputStream compressedOut = strategy.wrapOutput(out);
                 TarOutputStream tarOut = new TarOutputStream(compressedOut);
                 Stream<Path> walk = Files.walk(sourceRoot)) {
                List<Path> entries = walk.sorted().collect(Collectors.toList());
                for (Path path : entries) {
                    if (path.equals(sourceRoot)) {
                        continue;
                    }

                    Path relative = sourceRoot.relativize(path);
                    String name = toZipEntryName(relative);
                    if (Files.isDirectory(path)) {
                        name = name + "/";
                    }

                    File file = path.toFile();
                    TarEntry entry = new TarEntry(file, name);
                    tarOut.putNextEntry(entry);

                    if (Files.isRegularFile(path)) {
                        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                            in.transferTo(tarOut);
                        }
                    }
                }
            }

            try {
                Files.move(tempArchive, archiveFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.move(tempArchive, archiveFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempArchive);
        }
    }

    static void extractSingleFileArchiveToDirectory(Path archiveFile, Path targetRoot, String compressionKey) throws IOException {
        if (!Files.exists(archiveFile)) {
            return;
        }

        ICompressionStrategy strategy = CompressionStrategyFactory.forKey(compressionKey);
        String outputFileName = deriveSingleFileName(archiveFile);
        Path destination = targetRoot.resolve(outputFileName).normalize();
        if (!destination.startsWith(targetRoot)) {
            throw new IOException("Blocked path traversal while extracting: " + outputFileName);
        }

        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream in = new BufferedInputStream(Files.newInputStream(archiveFile));
             InputStream decompressedIn = strategy.wrapInput(in);
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
            decompressedIn.transferTo(out);
        }
    }

    static void writeDirectoryToSingleFileArchive(Path sourceRoot, Path archiveFile, String compressionKey) throws IOException {
        Path parent = archiveFile.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path sourceFile = findSingleFileToCompress(sourceRoot);
        ICompressionStrategy strategy = CompressionStrategyFactory.forKey(compressionKey);
        Path tempArchive = Files.createTempFile(parent, "extzipfs-", "." + compressionKey);
        try {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(sourceFile));
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempArchive));
                 OutputStream compressedOut = strategy.wrapOutput(out)) {
                in.transferTo(compressedOut);
            }

            try {
                Files.move(tempArchive, archiveFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.move(tempArchive, archiveFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempArchive);
        }
    }

    static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = new ArrayList<>(walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList()));
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String toZipEntryName(Path relativePath) {
        return relativePath.toString().replace(File.separatorChar, '/');
    }

    private static Path findSingleFileToCompress(Path sourceRoot) throws IOException {
        List<Path> entries;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            entries = walk
                    .filter(path -> !path.equals(sourceRoot))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (entries.size() != 1 || !Files.isRegularFile(entries.get(0))) {
            throw new IOException("Single-file compressed archives require exactly one file at archive root.");
        }
        return entries.get(0);
    }

    private static String deriveSingleFileName(Path archiveFile) {
        String name = archiveFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name + ".out";
    }
}
