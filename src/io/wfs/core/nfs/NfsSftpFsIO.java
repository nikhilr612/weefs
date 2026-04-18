package io.wfs.core.nfs;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;

final class NfsSftpFsIO {

    static final class RemoteEntry {
        private final String name;
        private final boolean directory;
        private final boolean symbolicLink;
        private final long size;
        private final FileTime lastModifiedTime;

        RemoteEntry(String name, boolean directory, boolean symbolicLink, long size, FileTime lastModifiedTime) {
            this.name = name;
            this.directory = directory;
            this.symbolicLink = symbolicLink;
            this.size = size;
            this.lastModifiedTime = lastModifiedTime;
        }

        String name() {
            return name;
        }

        boolean directory() {
            return directory;
        }

        boolean symbolicLink() {
            return symbolicLink;
        }

        long size() {
            return size;
        }

        FileTime lastModifiedTime() {
            return lastModifiedTime;
        }
    }

    static final class RemoteFileStat {
        private final boolean directory;
        private final boolean symbolicLink;
        private final long size;
        private final FileTime lastModifiedTime;

        RemoteFileStat(boolean directory, boolean symbolicLink, long size, FileTime lastModifiedTime) {
            this.directory = directory;
            this.symbolicLink = symbolicLink;
            this.size = size;
            this.lastModifiedTime = lastModifiedTime;
        }

        boolean directory() {
            return directory;
        }

        boolean symbolicLink() {
            return symbolicLink;
        }

        long size() {
            return size;
        }

        FileTime lastModifiedTime() {
            return lastModifiedTime;
        }
    }

    @FunctionalInterface
    interface SftpWork<T> {
        T run(ChannelSftp channel) throws SftpException, IOException;
    }

    private NfsSftpFsIO() {
    }

    static void ensureMountRootExists(NfsSftpConfig config) throws IOException {
        withChannel(config, channel -> {
            SftpATTRS attrs = lstatOrNull(channel, config.remoteRoot());
            if (attrs == null) {
                throw new NoSuchFileException(config.remoteRoot());
            }
            if (!attrs.isDir()) {
                throw new IOException("Mount root is not a directory: " + config.remoteRoot());
            }
            return null;
        });
    }

    static List<RemoteEntry> listDirectory(NfsSftpConfig config, String remoteDirectory) throws IOException {
        withChannel(config, channel -> {
            SftpATTRS attrs = statFollowLinksOrNull(channel, remoteDirectory);
            if (attrs == null || !attrs.isDir()) {
                throw new NoSuchFileException(remoteDirectory);
            }
            return null;
        });

        return withChannel(config, channel -> {
            List<ChannelSftp.LsEntry> entries = safeList(channel, remoteDirectory);
            List<RemoteEntry> result = new ArrayList<>(entries.size());
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }

                SftpATTRS listed = entry.getAttrs();
                boolean link = listed.isLink();
                SftpATTRS effective = link ? statFollowLinksOrNull(channel, join(remoteDirectory, name)) : listed;
                if (effective == null) {
                    continue;
                }

                result.add(new RemoteEntry(
                        name,
                        effective.isDir(),
                        link,
                        effective.getSize(),
                        toFileTime(effective)));
            }
            return result;
        });
    }

    static RemoteFileStat stat(NfsSftpConfig config, String remotePath) throws IOException {
        return withChannel(config, channel -> {
            SftpATTRS attrs = statFollowLinksOrNull(channel, remotePath);
            if (attrs == null) {
                throw new NoSuchFileException(remotePath);
            }
            SftpATTRS linkAttrs = lstatOrNull(channel, remotePath);
            boolean symbolicLink = linkAttrs != null && linkAttrs.isLink();
            return new RemoteFileStat(attrs.isDir(), symbolicLink, attrs.getSize(), toFileTime(attrs));
        });
    }

    static RemoteFileStat lstat(NfsSftpConfig config, String remotePath) throws IOException {
        return withChannel(config, channel -> {
            SftpATTRS attrs = lstatOrNull(channel, remotePath);
            if (attrs == null) {
                throw new NoSuchFileException(remotePath);
            }
            return new RemoteFileStat(attrs.isDir(), attrs.isLink(), attrs.getSize(), toFileTime(attrs));
        });
    }

    static boolean exists(NfsSftpConfig config, String remotePath) throws IOException {
        return withChannel(config, channel -> lstatOrNull(channel, remotePath) != null);
    }

    static byte[] readFile(NfsSftpConfig config, String remotePath) throws IOException {
        return withChannel(config, channel -> {
            SftpATTRS attrs = statFollowLinksOrNull(channel, remotePath);
            if (attrs == null) {
                throw new NoSuchFileException(remotePath);
            }
            if (attrs.isDir()) {
                throw new IOException("Cannot read a directory: " + remotePath);
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                channel.get(remotePath, out);
                return out.toByteArray();
            }
        });
    }

    static void writeFile(NfsSftpConfig config, String remotePath, byte[] content, boolean createParents) throws IOException {
        byte[] safeContent = content == null ? new byte[0] : content;
        withChannel(config, channel -> {
            if (createParents) {
                ensureRemoteParentDirectories(channel, remotePath);
            }
            try (ByteArrayInputStream in = new ByteArrayInputStream(safeContent)) {
                channel.put(in, remotePath, ChannelSftp.OVERWRITE);
            }
            return null;
        });
    }

    static void createFile(NfsSftpConfig config, String remotePath, boolean failIfExists) throws IOException {
        withChannel(config, channel -> {
            SftpATTRS existing = lstatOrNull(channel, remotePath);
            if (existing != null && failIfExists) {
                throw new FileAlreadyExistsException(remotePath);
            }
            ensureRemoteParentDirectories(channel, remotePath);
            try (ByteArrayInputStream in = new ByteArrayInputStream(new byte[0])) {
                channel.put(in, remotePath, ChannelSftp.OVERWRITE);
            }
            return null;
        });
    }

    static void createDirectory(NfsSftpConfig config, String remoteDirectory) throws IOException {
        withChannel(config, channel -> {
            SftpATTRS attrs = lstatOrNull(channel, remoteDirectory);
            if (attrs != null) {
                if (!attrs.isDir()) {
                    throw new FileAlreadyExistsException(remoteDirectory);
                }
                return null;
            }
            mkdirs(channel, remoteDirectory);
            return null;
        });
    }

    static void deleteFile(NfsSftpConfig config, String remoteFile) throws IOException {
        withChannel(config, channel -> {
            SftpATTRS attrs = statFollowLinksOrNull(channel, remoteFile);
            if (attrs == null) {
                throw new NoSuchFileException(remoteFile);
            }
            if (attrs.isDir()) {
                throw new IOException("Path is a directory: " + remoteFile);
            }
            channel.rm(remoteFile);
            return null;
        });
    }

    static void deleteDirectory(NfsSftpConfig config, String remoteDirectory) throws IOException {
        withChannel(config, channel -> {
            SftpATTRS attrs = statFollowLinksOrNull(channel, remoteDirectory);
            if (attrs == null) {
                throw new NoSuchFileException(remoteDirectory);
            }
            if (!attrs.isDir()) {
                throw new IOException("Path is not a directory: " + remoteDirectory);
            }

            List<ChannelSftp.LsEntry> entries = safeList(channel, remoteDirectory);
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (!".".equals(name) && !"..".equals(name)) {
                    throw new java.nio.file.DirectoryNotEmptyException(remoteDirectory);
                }
            }

            channel.rmdir(remoteDirectory);
            return null;
        });
    }

    static void move(NfsSftpConfig config, String sourcePath, String targetPath) throws IOException {
        withChannel(config, channel -> {
            ensureRemoteParentDirectories(channel, targetPath);
            channel.rename(sourcePath, targetPath);
            return null;
        });
    }

    static void copyFile(NfsSftpConfig config, String sourcePath, String targetPath) throws IOException {
        byte[] bytes = readFile(config, sourcePath);
        writeFile(config, targetPath, bytes, true);
    }

    static Map<String, Object> readBasicAttributesMap(NfsSftpConfig config, String remotePath) throws IOException {
        RemoteFileStat stat = stat(config, remotePath);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("size", stat.size());
        values.put("isDirectory", stat.directory());
        values.put("isRegularFile", !stat.directory());
        values.put("isSymbolicLink", stat.symbolicLink());
        values.put("isOther", false);
        values.put("fileKey", null);
        values.put("lastModifiedTime", stat.lastModifiedTime());
        values.put("lastAccessTime", stat.lastModifiedTime());
        values.put("creationTime", stat.lastModifiedTime());
        return values;
    }

    static <T> T withChannel(NfsSftpConfig config, SftpWork<T> work) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(work, "work");

        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            configureKnownHosts(jsch);
            session = jsch.getSession(config.username(), config.host(), config.port());
            session.setPassword(config.password());
            session.setConfig("StrictHostKeyChecking", resolveStrictHostKeyChecking());
            session.connect(15_000);

            Channel raw = session.openChannel("sftp");
            raw.connect(15_000);
            channel = (ChannelSftp) raw;

            return work.run(channel);
        } catch (SftpException ex) {
            throw mapSftpException(ex);
        } catch (JSchException ex) {
            throw new IOException("SFTP connection failed: " + ex.getMessage(), ex);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private static IOException mapSftpException(SftpException ex) {
        if (ex.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
            return new FileNotFoundException(ex.getMessage());
        }
        return new IOException("SFTP error: " + ex.getMessage(), ex);
    }

    private static void ensureRemoteParentDirectories(ChannelSftp channel, String remotePath) throws SftpException {
        int idx = remotePath.lastIndexOf('/');
        if (idx <= 0) {
            return;
        }
        String parent = remotePath.substring(0, idx);
        mkdirs(channel, parent);
    }

    private static void mkdirs(ChannelSftp channel, String absolutePath) throws SftpException {
        String normalized = normalizeRemotePath(absolutePath);
        if ("/".equals(normalized)) {
            return;
        }

        String[] parts = normalized.substring(1).split("/");
        String current = "";
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            current = current + "/" + part;
            SftpATTRS attrs = lstatOrNull(channel, current);
            if (attrs == null) {
                channel.mkdir(current);
            }
        }
    }

    private static SftpATTRS lstatOrNull(ChannelSftp channel, String remotePath) throws SftpException {
        try {
            return channel.lstat(remotePath);
        } catch (SftpException ex) {
            if (ex.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null;
            }
            throw ex;
        }
    }

    private static SftpATTRS statFollowLinksOrNull(ChannelSftp channel, String remotePath) throws SftpException {
        try {
            return channel.stat(remotePath);
        } catch (SftpException ex) {
            if (ex.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null;
            }
            throw ex;
        }
    }

    private static List<ChannelSftp.LsEntry> safeList(ChannelSftp channel, String path) throws SftpException {
        @SuppressWarnings("unchecked")
        // JSch returns a raw Vector; per API contract, elements are ChannelSftp.LsEntry.
        Vector<ChannelSftp.LsEntry> vec = channel.ls(path);
        return new ArrayList<>(vec);
    }

    static String join(String base, String child) {
        String left = normalizeRemotePath(base);
        String right = child == null ? "" : child.replace('\\', '/');
        while (right.startsWith("/")) {
            right = right.substring(1);
        }
        if (right.isBlank()) {
            return left;
        }
        return "/".equals(left) ? "/" + right : left + "/" + right;
    }

    static String normalizeRemotePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "/";
        }

        String value = raw.replace('\\', '/').trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static FileTime toFileTime(SftpATTRS attrs) {
        return FileTime.fromMillis(attrs.getMTime() * 1000L);
    }

    private static String resolveStrictHostKeyChecking() {
        String raw = System.getenv("WEEFS_SFTP_STRICT_HOST_KEY_CHECKING");
        if (raw == null || raw.isBlank()) {
            return "yes";
        }

        String normalized = raw.trim().toLowerCase();
        if ("no".equals(normalized) || "off".equals(normalized) || "false".equals(normalized) || "0".equals(normalized)) {
            return "no";
        }
        return "yes";
    }

    private static void configureKnownHosts(JSch jsch) throws IOException {
        String strict = resolveStrictHostKeyChecking();
        if (!"yes".equalsIgnoreCase(strict)) {
            return;
        }

        Path knownHosts = Path.of(System.getProperty("user.home"), ".ssh", "known_hosts");
        if (!Files.exists(knownHosts)) {
            throw new IOException("Strict host key checking is enabled, but known_hosts is missing at: " + knownHosts);
        }

        try {
            jsch.setKnownHosts(knownHosts.toString());
        } catch (JSchException ex) {
            throw new IOException("Failed to load known_hosts from: " + knownHosts, ex);
        }
    }
}
