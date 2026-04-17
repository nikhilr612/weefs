package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;

final class NfsDirectoryStream implements DirectoryStream<Path> {

    private final DirectoryStream<Path> delegate;
    private final NfsFileSystem fileSystem;
    private final Filter<? super Path> filter;

    NfsDirectoryStream(DirectoryStream<Path> delegate, NfsFileSystem fileSystem, Filter<? super Path> filter) {
        this.delegate = delegate;
        this.fileSystem = fileSystem;
        this.filter = filter;
    }

    @Override
    public Iterator<Path> iterator() {
        return new NfsDirectoryIterator(delegate.iterator(), fileSystem, filter);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
