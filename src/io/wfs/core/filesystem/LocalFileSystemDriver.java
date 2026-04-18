package io.wfs.core.filesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Map;

final class LocalFileSystemDriver implements FileSystemDriver {

    @Override
    public String scheme() {
        return "file";
    }

    @Override
    public FileSystem open(URI uri, Map<String, ?> env) throws IOException {
        if (uri == null || uri.getPath() == null || uri.getPath().isBlank()) {
            throw new IOException("Invalid file URI: " + uri);
        }
        return FileSystems.getDefault();
    }
}
