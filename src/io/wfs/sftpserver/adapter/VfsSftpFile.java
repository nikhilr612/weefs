package io.wfs.sftpserver.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class VfsSftpFile {

    private final Path path;

    public VfsSftpFile(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    public boolean isDirectory() throws IOException {
        return Files.isDirectory(path);
    }

    public List<Path> list() throws IOException {
        try (var stream = Files.list(path)) {
            return stream.toList();
        }
    }

    public byte[] read() throws IOException {
        return Files.readAllBytes(path);
    }

    public void write(byte[] data, boolean truncate) throws IOException {
        if (truncate) {
            Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return;
        }
        Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    public void delete() throws IOException {
        Files.delete(path);
    }

    public void renameTo(VfsSftpFile destination, boolean replaceExisting) throws IOException {
        if (replaceExisting) {
            Files.move(path, destination.path, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        Files.move(path, destination.path);
    }
}
