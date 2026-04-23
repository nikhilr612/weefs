package io.wfs.sftpserver;

import io.wfs.sftpserver.adapter.VfsFileSystemFactory;
import io.wfs.sftpserver.adapter.VfsSftpFileSystemView;
import io.wfs.sftpserver.config.SftpServerProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.context.SmartLifecycle;

public final class SftpServerController implements SmartLifecycle {

    private static final Logger LOG = Logger.getLogger(SftpServerController.class.getName());

    private final SftpServerProperties properties;
    private final VfsSftpFileSystemView fileSystemView;
    private final AutoCloseable vfsMount;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile SshServer server;

    public SftpServerController(
            SftpServerProperties properties,
            VfsSftpFileSystemView fileSystemView,
            AutoCloseable vfsMount) {
        this.properties = properties;
        this.fileSystemView = fileSystemView;
        this.vfsMount = vfsMount;
    }

    @Override
    public synchronized void start() {
        if (running.get()) {
            return;
        }

        try {
            SshServer sshd = SshServer.setUpDefaultServer();
            sshd.setHost(properties.host());
            sshd.setPort(properties.port());
            sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Path.of(properties.hostKeyPath())));
            sshd.setPasswordAuthenticator((username, password, session) ->
                    properties.username().equals(username) && properties.password().equals(password));
            sshd.setFileSystemFactory(new VfsFileSystemFactory(fileSystemView));
            sshd.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
            sshd.start();

            server = sshd;
            running.set(true);

            LOG.info(() -> "WEEFS SFTP server started on "
                    + properties.host() + ":" + properties.port()
                    + " serving archive " + properties.archivePath());
        } catch (IOException ex) {
            closeQuietly();
            throw new IllegalStateException("Failed to start SFTP server", ex);
        }
    }

    @Override
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        try {
            if (server != null) {
                server.stop(true);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to stop SFTP server", ex);
        } finally {
            running.set(false);
            server = null;
            closeQuietly();
            LOG.info("WEEFS SFTP server stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    private void closeQuietly() {
        try {
            vfsMount.close();
        } catch (Exception ex) {
            LOG.warning("Failed to close archive VFS mount: " + ex.getMessage());
        }
    }
}
