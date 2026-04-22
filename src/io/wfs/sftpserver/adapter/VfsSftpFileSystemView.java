package io.wfs.sftpserver.adapter;

import io.wfs.sftpserver.config.ArchiveVfsMount;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

public final class VfsSftpFileSystemView {

    private final ArchiveVfsMount mount;

    public VfsSftpFileSystemView(ArchiveVfsMount mount) {
        this.mount = mount;
    }

    public FileSystem fileSystem() {
        return mount.fileSystem();
    }

    public Path userHomeDirectory(String username) {
        return mount.rootPath();
    }

    public VfsSftpFile resolve(String virtualPath) throws IOException {
        String normalized = normalize(virtualPath);
        Path resolved = mount.rootPath().resolve(normalized).normalize();
        if (!resolved.startsWith(mount.rootPath())) {
            throw new IOException("Path escapes archive root: " + virtualPath);
        }
        return new VfsSftpFile(resolved);
    }

    private static String normalize(String virtualPath) {
        if (virtualPath == null || virtualPath.isBlank() || "/".equals(virtualPath)) {
            return "";
        }
        String trimmed = virtualPath.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }
}
