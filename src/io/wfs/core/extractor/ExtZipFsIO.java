package io.wfs.core.extractor;

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
        ArchiveFormats.extractToDirectory(archiveFile, targetRoot);
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

    static void extractTarToDirectory(Path tarArchive, Path targetRoot) throws IOException {
        if (!Files.exists(tarArchive)) {
            return;
        }

        byte[] buffer = new byte[8192];
        try (InputStream in = new BufferedInputStream(Files.newInputStream(tarArchive));
                TarInputStream tarIn = new TarInputStream(in)) {
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
        ArchiveFormats.writeFromDirectory(sourceRoot, archiveFile);
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
                Files.move(tempArchive, archiveFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.move(tempArchive, archiveFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempArchive);
        }
    }

    static void writeDirectoryToTar(Path sourceRoot, Path archiveFile) throws IOException {
        Path parent = archiveFile.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempArchive = Files.createTempFile(parent, "extzipfs-", ".tar");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempArchive));
                    TarOutputStream tarOut = new TarOutputStream(out);
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
                Files.move(tempArchive, archiveFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
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

}
