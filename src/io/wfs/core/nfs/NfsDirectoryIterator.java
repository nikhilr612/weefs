package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

final class NfsDirectoryIterator implements Iterator<Path> {

    private final Iterator<Path> delegate;
    private final DirectoryStream.Filter<? super Path> filter;
    private Path next;
    private boolean prepared;

    NfsDirectoryIterator(Iterator<Path> delegate, DirectoryStream.Filter<? super Path> filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public boolean hasNext() {
        if (prepared) {
            return next != null;
        }

        prepared = true;
        while (delegate.hasNext()) {
            Path candidate = delegate.next();
            try {
                if (filter.accept(candidate)) {
                    next = candidate;
                    return true;
                }
            } catch (IOException ex) {
                throw new DirectoryIteratorException(ex);
            }
        }

        next = null;
        return false;
    }

    @Override
    public Path next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        prepared = false;
        return next;
    }
}
