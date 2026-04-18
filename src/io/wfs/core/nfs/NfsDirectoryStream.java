package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

final class NfsDirectoryStream implements DirectoryStream<Path> {

    private final List<Path> entries;
    private final Filter<? super Path> filter;
    private boolean open = true;

    NfsDirectoryStream(List<Path> entries, Filter<? super Path> filter) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.filter = filter;
    }

    @Override
    public Iterator<Path> iterator() {
        if (!open) {
            throw new IllegalStateException("Directory stream is closed");
        }
        return new NfsDirectoryIterator(entries.iterator(), filter);
    }

    @Override
    public void close() throws IOException {
        open = false;
    }
}
