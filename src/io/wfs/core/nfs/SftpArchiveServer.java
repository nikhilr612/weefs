package io.wfs.core.nfs;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipOutputStream;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.subsystem.SubsystemFactory;

/**
 * Single-file SFTP server that redirects all operations into an xzip-mounted archive.
 *
 * Usage:
 *   sftp-server [archivePath] [port] [username] [password]
 *
 * Defaults:
 *   archivePath=archive.zip, port=2222, username=dev, password=dev
 */
public final class SftpArchiveServer {

    private static final String DEFAULT_ARCHIVE = "archive.zip";
    private static final int DEFAULT_PORT = 8888;
    private static final String DEFAULT_USERNAME = "dev";
    private static final String DEFAULT_PASSWORD = "dev";

    private final Path archivePath;
    private final int port;
    private final String username;
    private final String password;
    private final Path hostKeyPath;

    private FileSystem mountedArchiveFs;
    private SshServer sshServer;

    private SftpArchiveServer(Path archivePath, int port, String username, String password) {
        this.archivePath = archivePath;
        this.port = port;
        this.username = username;
        this.password = password;
        this.hostKeyPath = archivePath.getParent() == null
                ? Paths.get(".weefs-sftp-hostkey.ser")
                : archivePath.getParent().resolve(".weefs-sftp-hostkey.ser");
    }

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);
        SftpArchiveServer server = new SftpArchiveServer(
                parsed.archivePath(),
                parsed.port(),
                parsed.username(),
                parsed.password());
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stopQuietly, "weefs-sftp-server-shutdown"));
        log("LIFECYCLE", "/", "server running; press Ctrl+C to stop");
        new CountDownLatch(1).await();
    }

    private void start() throws Exception {
        ensureArchiveZipExists(archivePath);
        mountedArchiveFs = mountArchive(archivePath);

        sshServer = SshServer.setUpDefaultServer();
        sshServer.setHost("0.0.0.0");
        sshServer.setPort(port);
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKeyPath));
        sshServer.setPasswordAuthenticator(buildAuthenticator());
        sshServer.setFileSystemFactory(buildIsolatedFileSystemFactory(mountedArchiveFs));
        sshServer.setSubsystemFactories(buildSftpSubsystemFactories());

        seedTestFile(mountedArchiveFs);

        sshServer.start();
        log("START", "/", "listening on 0.0.0.0:" + port);
        log("START", "/", "archive=" + archivePath.toAbsolutePath().normalize());
        log("START", "/", "xzip-uri=" + toXzipRootUri(archivePath));
        log("START", "/", "credentials user='" + username + "' password='" + password + "'");
    }

    private static FileSystemFactory buildIsolatedFileSystemFactory(FileSystem archiveFs) {
        try {
            Path tempRoot = extractArchiveTempRoot(archiveFs);
            Class<?> vfsClass = Class.forName("org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory");
            Object vfs = vfsClass.getConstructor(Path.class).newInstance(tempRoot);
            if (vfs instanceof FileSystemFactory fsFactory) {
                log("INIT", tempRoot.toString(), "using VirtualFileSystemFactory root");
                return fsFactory;
            }
        } catch (Exception ex) {
            logError("WARN", "/", ex);
            log("WARN", "/", "falling back to archive-backed FileSystemFactory");
        }
        return new ArchiveFileSystemFactory(archiveFs);
    }

    private static Path extractArchiveTempRoot(FileSystem archiveFs) throws Exception {
        Method tempRootMethod = archiveFs.getClass().getDeclaredMethod("getTempRoot");
        tempRootMethod.setAccessible(true);
        Object value = tempRootMethod.invoke(archiveFs);
        if (!(value instanceof Path path)) {
            throw new IllegalStateException("ExtZip temp root was not a Path");
        }
        return path;
    }

    private static void seedTestFile(FileSystem archiveFs) {
        try {
            Path testFile = archiveFs.getPath("/SFTP_TEST_FILE.txt");
            if (Files.exists(testFile)) {
                log("INIT", testFile.toString(), "test file already exists");
                return;
            }

            String content = "This file was seeded by SftpArchiveServer for put/get testing.\n";
            Files.write(testFile, content.getBytes(StandardCharsets.UTF_8));
            log("INIT", testFile.toString(), "seeded test file in archive.zip");
        } catch (Exception ex) {
            logError("WARN", "/SFTP_TEST_FILE.txt", ex);
        }
    }

    private void stopQuietly() {
        try {
            stop();
        } catch (Exception ex) {
            logError("STOP", "/", ex);
        }
    }

    private void stop() throws Exception {
        if (sshServer != null) {
            try {
                sshServer.stop(true);
                log("STOP", "/", "ssh server stopped");
            } finally {
                sshServer = null;
            }
        }

        if (mountedArchiveFs != null) {
            try {
                mountedArchiveFs.close();
                log("STOP", "/", "archive filesystem closed and persisted");
            } finally {
                mountedArchiveFs = null;
            }
        }
    }

    private PasswordAuthenticator buildAuthenticator() {
        return (candidateUser, candidatePassword, session) -> {
            boolean ok = Objects.equals(username, candidateUser) && Objects.equals(password, candidatePassword);
            log("AUTH", "/", "user='" + candidateUser + "' result=" + (ok ? "OK" : "DENY"));
            return ok;
        };
    }

    private static void ensureArchiveZipExists(Path archive) throws IOException {
        Path normalized = archive.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(normalized)) {
            log("INIT", "/", "using existing archive " + normalized);
            return;
        }

        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(normalized))) {
            // Create a valid empty ZIP container.
        }
        log("INIT", "/", "created new archive " + normalized);
    }

    private static FileSystem mountArchive(Path archive) throws IOException {
        io.wfs.core.filesystem.FileSystemFactory factory = new io.wfs.core.filesystem.FileSystemFactory();
        URI xzipUri = URI.create(toXzipRootUri(archive));
        FileSystem fs = factory.open(xzipUri, Map.of());
        log("INIT", "/", "mounted archive root at " + xzipUri);
        return fs;
    }

    private static String toXzipRootUri(Path archive) {
        return "xzip:" + archive.toAbsolutePath().normalize().toUri() + "!/";
    }

    private static List<SubsystemFactory> buildSftpSubsystemFactories() {
        try {
            Class<?> builderType = Class.forName("org.apache.sshd.sftp.server.SftpSubsystemFactory$Builder");
            Object builder = builderType.getDeclaredConstructor().newInstance();

            attachEventLogging(builder, builderType);

            Method build = builderType.getMethod("build");
            Object factory = build.invoke(builder);
            if (factory instanceof SubsystemFactory subsystemFactory) {
                return List.of(subsystemFactory);
            }
            throw new IllegalStateException("Unexpected SFTP subsystem factory type: " + factory.getClass().getName());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to configure SFTP subsystem", ex);
        }
    }

    private static void attachEventLogging(Object builder, Class<?> builderType) {
        try {
            Class<?> listenerType = Class.forName("org.apache.sshd.sftp.server.SftpEventListener");
            InvocationHandler handler = (proxy, method, args) -> {
                String op = normalizeOperation(method.getName());
                String path = extractPath(args);
                if (isInterestingOperation(op)) {
                    log(op, path, summarize(method.getName(), args));
                    if (op.contains("READ") || op.contains("WRITE") || op.contains("OPEN") || op.contains("CLOSE")) {
                        log("DEBUG", path, "read/write diagnostic marker op=" + op);
                    }
                }
                Throwable error = extractThrowable(args);
                if (error != null) {
                    logError(op, path, error);
                }
                return null;
            };

            Object listener = Proxy.newProxyInstance(
                    SftpArchiveServer.class.getClassLoader(),
                    new Class<?>[] { listenerType },
                    handler);

            for (String methodName : List.of("addSftpEventListener", "withSftpEventListener")) {
                try {
                    Method add = builderType.getMethod(methodName, listenerType);
                    add.invoke(builder, listener);
                    log("INIT", "/", "attached SFTP event listener via " + methodName);
                    return;
                } catch (NoSuchMethodException ignored) {
                    // Try fallback name.
                }
            }
            log("WARN", "/", "SFTP event listener API not found; operation logs limited");
        } catch (Exception ex) {
            logError("WARN", "/", ex);
        }
    }

    private static boolean isInterestingOperation(String op) {
        return op.contains("LIST")
                || op.contains("READDIR")
                || op.contains("OPEN")
                || op.contains("READ")
                || op.contains("WRITE")
                || op.contains("CLOSE")
                || op.contains("REMOVE")
                || op.contains("MKDIR")
                || op.contains("RENAME");
    }

    private static String normalizeOperation(String methodName) {
        return methodName == null ? "UNKNOWN" : methodName.toUpperCase();
    }

    private static String extractPath(Object[] args) {
        if (args == null) {
            return "/";
        }
        for (Object arg : args) {
            if (arg instanceof Path path) {
                return path.toString();
            }
            if (arg instanceof CharSequence text) {
                String value = text.toString();
                if (value.startsWith("/")) {
                    return value;
                }
            }
        }
        return "/";
    }

    private static Throwable extractThrowable(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Throwable throwable) {
                return throwable;
            }
        }
        return null;
    }

    private static String summarize(String methodName, Object[] args) {
        if (args == null || args.length == 0) {
            return methodName;
        }
        return methodName + " args=" + Arrays.toString(args);
    }

    private static void log(String operation, String path, String message) {
        String safePath = (path == null || path.isBlank()) ? "/" : path;
        System.out.printf("[%s] [SFTP:%s] path=%s %s%n", Instant.now(), operation, safePath, message);
    }

    private static void logError(String operation, String path, Throwable error) {
        String type = error == null ? "Unknown" : error.getClass().getSimpleName();
        String msg = error == null ? "" : String.valueOf(error.getMessage());
        log(operation, path, "ERROR type=" + type + " message=" + msg);
    }

    private record Args(Path archivePath, int port, String username, String password) {
        static Args parse(String[] rawArgs) {
            String[] args = rawArgs == null ? new String[0] : rawArgs;
            Path archive = Paths.get(args.length >= 1 ? args[0] : DEFAULT_ARCHIVE).toAbsolutePath().normalize();
            int port = parsePort(args.length >= 2 ? args[1] : String.valueOf(DEFAULT_PORT));
            String username = args.length >= 3 ? args[2] : DEFAULT_USERNAME;
            String password = args.length >= 4 ? args[3] : DEFAULT_PASSWORD;
            return new Args(archive, port, username, password);
        }

        private static int parsePort(String value) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 1 || parsed > 65535) {
                    throw new IllegalArgumentException("Port must be in range 1..65535");
                }
                return parsed;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid port: " + value, ex);
            }
        }
    }

    private static final class ArchiveFileSystemFactory implements FileSystemFactory {

        private final FileSystem shared;

        private ArchiveFileSystemFactory(FileSystem shared) {
            this.shared = shared;
        }

        @Override
        public FileSystem createFileSystem(SessionContext session) {
            String who = session == null ? "unknown" : session.toString();
            log("SESSION", "/", "createFileSystem for " + who);
            return shared;
        }

        @Override
        public Path getUserHomeDir(SessionContext session) {
            Path home = shared.getPath("/").normalize();
            String who = session == null ? "unknown" : session.toString();
            log("SESSION", home.toString(), "getUserHomeDir for " + who);
            return home;
        }
    }
}