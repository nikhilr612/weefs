package io.wfs.core.extractor;

import java.nio.file.Path;
import java.util.Iterator;

final class ExtZipPathIterator implements Iterator<Path> {

    private final ExtZipFileSystem fileSystem;
    private final Iterator<Path> delegate;

    ExtZipPathIterator(ExtZipFileSystem fileSystem, Iterator<Path> delegate) {
        this.fileSystem = fileSystem;
        this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public Path next() {
        return new ExtZipPath(fileSystem, delegate.next());
    }
}
