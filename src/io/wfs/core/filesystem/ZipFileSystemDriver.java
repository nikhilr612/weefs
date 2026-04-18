package io.wfs.core.filesystem;

import io.wfs.core.extractor.ExtZipFsProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.Map;

final class ZipFileSystemDriver implements FileSystemDriver {

    private final ExtZipFsProvider provider;

    ZipFileSystemDriver() {
        this(new ExtZipFsProvider());
    }

    ZipFileSystemDriver(ExtZipFsProvider provider) {
        this.provider = provider;
    }

    @Override
    public String scheme() {
        return "xzip";
    }

    @Override
    public FileSystem open(URI uri, Map<String, ?> env) throws IOException {
        return provider.newFileSystem(uri, env);
    }
}
