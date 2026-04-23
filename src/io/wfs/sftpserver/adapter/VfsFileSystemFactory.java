package io.wfs.sftpserver.adapter;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.session.SessionContext;

public final class VfsFileSystemFactory implements FileSystemFactory {

    private final VfsSftpFileSystemView view;

    public VfsFileSystemFactory(VfsSftpFileSystemView view) {
        this.view = view;
    }

    @Override
    public Path getUserHomeDir(SessionContext session) throws IOException {
        return view.userHomeDirectory(session.getUsername());
    }

    @Override
    public FileSystem createFileSystem(SessionContext session) throws IOException {
        return view.fileSystem();
    }
}
