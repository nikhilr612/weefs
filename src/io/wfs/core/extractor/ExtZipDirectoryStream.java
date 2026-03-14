package io.wfs.core.extractor;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;

final class ExtZipDirectoryStream implements DirectoryStream<Path> {

    private final DirectoryStream<Path> delegate;
    private final ExtZipFileSystem fileSystem;
    private final Filter<? super Path> filter;

    ExtZipDirectoryStream(DirectoryStream<Path> delegate, ExtZipFileSystem fileSystem, Filter<? super Path> filter) {
        this.delegate = delegate;
        this.fileSystem = fileSystem;
        this.filter = filter;
    }

    @Override
    public Iterator<Path> iterator() {
        return new ExtZipDirectoryIterator(delegate.iterator(), fileSystem, filter);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
