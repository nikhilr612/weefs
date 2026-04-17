package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

final class NfsSyncingByteChannel implements SeekableByteChannel {

    private final SeekableByteChannel delegate;
    private final NfsFileSystem fileSystem;
    private final Path localPath;
    private boolean open = true;

    NfsSyncingByteChannel(SeekableByteChannel delegate, NfsFileSystem fileSystem, Path localPath) {
        this.delegate = delegate;
        this.fileSystem = fileSystem;
        this.localPath = localPath;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return delegate.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return delegate.write(src);
    }

    @Override
    public long position() throws IOException {
        return delegate.position();
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        delegate.position(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return delegate.size();
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        delegate.truncate(size);
        return this;
    }

    @Override
    public boolean isOpen() {
        return open && delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (!open) {
            return;
        }
        open = false;

        IOException failure = null;
        try {
            delegate.close();
        } catch (IOException ex) {
            failure = ex;
        }

        try {
            fileSystem.syncFile(localPath);
        } catch (IOException ex) {
            if (failure == null) {
                failure = ex;
            } else {
                failure.addSuppressed(ex);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }
}
