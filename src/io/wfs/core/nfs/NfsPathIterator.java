package io.wfs.core.nfs;

import java.nio.file.Path;
import java.util.Iterator;

final class NfsPathIterator implements Iterator<Path> {

    private final NfsFileSystem fileSystem;
    private final Iterator<Path> delegate;

    NfsPathIterator(NfsFileSystem fileSystem, Iterator<Path> delegate) {
        this.fileSystem = fileSystem;
        this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public Path next() {
        return new NfsPath(fileSystem, delegate.next().toString());
    }
}
