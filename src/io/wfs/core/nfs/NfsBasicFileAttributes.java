package io.wfs.core.nfs;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

final class NfsBasicFileAttributes implements BasicFileAttributes {

    private final boolean directory;
    private final boolean symbolicLink;
    private final long size;
    private final FileTime lastModifiedTime;

    NfsBasicFileAttributes(boolean directory, boolean symbolicLink, long size, FileTime lastModifiedTime) {
        this.directory = directory;
        this.symbolicLink = symbolicLink;
        this.size = size;
        this.lastModifiedTime = lastModifiedTime;
    }

    @Override
    public FileTime lastModifiedTime() {
        return lastModifiedTime;
    }

    @Override
    public FileTime lastAccessTime() {
        return lastModifiedTime;
    }

    @Override
    public FileTime creationTime() {
        return lastModifiedTime;
    }

    @Override
    public boolean isRegularFile() {
        return !directory;
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    @Override
    public boolean isSymbolicLink() {
        return symbolicLink;
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public Object fileKey() {
        return null;
    }
}
