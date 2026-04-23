package io.wfs.sftpserver.config;

import io.wfs.core.filesystem.FileSystemFactory;
import io.wfs.core.filesystem.FsEnvKeys;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ArchiveVfsMount implements AutoCloseable {

    private final FileSystem fileSystem;
    private final Path rootPath;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ArchiveVfsMount(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
        this.rootPath = fileSystem.getPath("/");
    }

    public static ArchiveVfsMount open(SftpServerProperties properties) throws IOException {
        Path archivePath = Path.of(properties.archivePath()).toAbsolutePath().normalize();
        URI archiveUri = URI.create("xzip:" + archivePath.toUri() + "!/");

        FileSystem fileSystem = new FileSystemFactory().open(
                archiveUri,
                Map.of(FsEnvKeys.READ_ONLY, Boolean.toString(properties.readOnly())));

        return new ArchiveVfsMount(fileSystem);
    }

    public FileSystem fileSystem() {
        return fileSystem;
    }

    public Path rootPath() {
        return rootPath;
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            fileSystem.close();
        }
    }
}
