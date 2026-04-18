package io.wfs.core.filesystem;

import io.wfs.core.nfs.NfsSftpFsProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.Map;

final class NfsFileSystemDriver implements FileSystemDriver {

    private final NfsSftpFsProvider provider;

    NfsFileSystemDriver() {
        this(new NfsSftpFsProvider());
    }

    NfsFileSystemDriver(NfsSftpFsProvider provider) {
        this.provider = provider;
    }

    @Override
    public String scheme() {
        return "weefs";
    }

    @Override
    public FileSystem open(URI uri, Map<String, ?> env) throws IOException {
        return provider.newFileSystem(uri, env);
    }
}
