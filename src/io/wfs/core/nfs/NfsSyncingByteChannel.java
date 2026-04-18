package io.wfs.core.nfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.util.Arrays;

final class NfsSyncingByteChannel implements SeekableByteChannel {

    private final NfsFileSystem fileSystem;
    private final NfsPath path;
    private final boolean readable;
    private final boolean writable;
    private byte[] buffer;
    private int size;
    private int position;
    private boolean dirty;
    private boolean open = true;

    NfsSyncingByteChannel(NfsFileSystem fileSystem, NfsPath path, boolean readable, boolean writable,
            byte[] initialContent, boolean appendMode, boolean initiallyDirty) {
        this.fileSystem = fileSystem;
        this.path = path;
        this.readable = readable;
        this.writable = writable;
        this.buffer = initialContent == null ? new byte[0] : Arrays.copyOf(initialContent, initialContent.length);
        this.size = this.buffer.length;
        this.position = appendMode ? this.size : 0;
        this.dirty = initiallyDirty;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (!readable) {
            throw new NonReadableChannelException();
        }
        if (position >= size) {
            return -1;
        }
        int count = Math.min(dst.remaining(), size - position);
        dst.put(buffer, position, count);
        position += count;
        return count;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ensureOpen();
        if (!writable) {
            throw new NonWritableChannelException();
        }

        int count = src.remaining();
        ensureCapacity(position + count);
        src.get(buffer, position, count);
        position += count;
        if (position > size) {
            size = position;
        }
        dirty = true;
        return count;
    }

    @Override
    public long position() throws IOException {
        ensureOpen();
        return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        ensureOpen();
        if (newPosition < 0 || newPosition > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid channel position: " + newPosition);
        }
        position = (int) newPosition;
        return this;
    }

    @Override
    public long size() throws IOException {
        ensureOpen();
        return size;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        ensureOpen();
        if (!writable) {
            throw new NonWritableChannelException();
        }
        if (size < 0 || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid truncate size: " + size);
        }
        int newSize = (int) size;
        if (newSize < this.size) {
            this.size = newSize;
            if (position > this.size) {
                position = this.size;
            }
            dirty = true;
        }
        return this;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() throws IOException {
        if (!open) {
            return;
        }
        open = false;

        if (writable && dirty) {
            fileSystem.ensureWritable();
            byte[] payload = Arrays.copyOf(buffer, size);
            NfsSftpFsIO.writeFile(fileSystem.config(), fileSystem.toRemotePath(path), payload, true);
        }
    }

    private void ensureOpen() throws IOException {
        if (!open) {
            throw new IOException("Channel is closed");
        }
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= buffer.length) {
            return;
        }
        int newCapacity = Math.max(minCapacity, Math.max(16, buffer.length * 2));
        buffer = Arrays.copyOf(buffer, newCapacity);
    }
}
