package io.wfs.core.nfs;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Vector;

final class NfsSftpFsIO {

    @FunctionalInterface
    interface SftpWork<T> {
        T run(ChannelSftp channel) throws SftpException, IOException;
    }

    private NfsSftpFsIO() {
    }

    static void downloadRemoteTree(NfsSftpConfig config, Path localRoot) throws IOException {
        withChannel(config, channel -> {
            SftpATTRS attrs = statOrNull(channel, config.remoteRoot());
            Files.createDirectories(localRoot);

            if (attrs == null) {
                return null;
            }

            if (attrs.isDir()) {
                downloadDirectory(channel, config.remoteRoot(), localRoot);
            } else {
                Path target = localRoot.resolve(fileName(config.remoteRoot()));
                Files.createDirectories(target.getParent());
                channel.get(config.remoteRoot(), target.toString());
            }
            return null;
        });
    }

    static void uploadFile(NfsSftpConfig config, Path localRoot, Path localFile) throws IOException {
        String remotePath = toRemotePath(config.remoteRoot(), localRoot, localFile);
        withChannel(config, channel -> {
            ensureRemoteParentDirectories(channel, remotePath);
            channel.put(localFile.toString(), remotePath, ChannelSftp.OVERWRITE);
            return null;
        });
    }

    static void createRemoteDirectory(NfsSftpConfig config, Path localRoot, Path localDirectory) throws IOException {
        String remotePath = toRemotePath(config.remoteRoot(), localRoot, localDirectory);
        withChannel(config, channel -> {
            mkdirs(channel, remotePath);
            return null;
        });
    }

    static void deleteRemotePath(NfsSftpConfig config, Path localRoot, Path localPath, boolean wasDirectory)
            throws IOException {
        String remotePath = toRemotePath(config.remoteRoot(), localRoot, localPath);
        withChannel(config, channel -> {
            if (wasDirectory) {
                Vector<ChannelSftp.LsEntry> entries = lsOrNull(channel, remotePath);
                if (entries != null) {
                    if (hasChildren(entries)) {
                        throw new DirectoryNotEmptyException(localPath.toString());
                    }
                    channel.rmdir(remotePath);
                }
            } else {
                if (statOrNull(channel, remotePath) != null) {
                    channel.rm(remotePath);
                }
            }
            return null;
        });
    }

    static boolean exists(NfsSftpConfig config, Path localRoot, Path localPath) throws IOException {
        String remotePath = toRemotePath(config.remoteRoot(), localRoot, localPath);
        return withChannel(config, channel -> statOrNull(channel, remotePath) != null);
    }

    static <T> T withChannel(NfsSftpConfig config, SftpWork<T> work) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(work, "work");

        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(config.username(), config.host(), config.port());
            session.setPassword(config.password());
            session.setConfig("StrictHostKeyChecking", "no");
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

    private static void downloadDirectory(ChannelSftp channel, String remoteDir, Path localDir)
            throws SftpException, IOException {
        Files.createDirectories(localDir);
        @SuppressWarnings("unchecked")
        Vector<ChannelSftp.LsEntry> entries = channel.ls(remoteDir);
        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }

            String childRemote = join(remoteDir, name);
            Path childLocal = localDir.resolve(name);
            SftpATTRS attrs = entry.getAttrs();

            if (attrs.isLink()) {
                SftpATTRS targetAttrs = statFollowLinksOrNull(channel, childRemote);
                if (targetAttrs == null) {
                    continue;
                }

                if (targetAttrs.isDir()) {
                    downloadDirectory(channel, childRemote, childLocal);
                } else {
                    Files.createDirectories(childLocal.getParent());
                    channel.get(childRemote, childLocal.toString());
                }
                continue;
            }

            if (attrs.isDir()) {
                downloadDirectory(channel, childRemote, childLocal);
            } else {
                SftpATTRS check = statFollowLinksOrNull(channel, childRemote);
                if (check != null && check.isDir()) {
                    downloadDirectory(channel, childRemote, childLocal);
                    continue;
                }
                Files.createDirectories(childLocal.getParent());
                channel.get(childRemote, childLocal.toString());
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
            SftpATTRS attrs = statOrNull(channel, current);
            if (attrs == null) {
                channel.mkdir(current);
            }
        }
    }

    private static SftpATTRS statOrNull(ChannelSftp channel, String remotePath) throws SftpException {
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

    private static Vector<ChannelSftp.LsEntry> lsOrNull(ChannelSftp channel, String remotePath) throws SftpException {
        try {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channel.ls(remotePath);
            return entries;
        } catch (SftpException ex) {
            if (ex.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null;
            }
            throw ex;
        }
    }

    private static boolean hasChildren(Vector<ChannelSftp.LsEntry> entries) {
        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();
            if (!".".equals(name) && !"..".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String toRemotePath(String remoteRoot, Path localRoot, Path localPath) {
        Path normalizedLocalRoot = localRoot.toAbsolutePath().normalize();
        Path normalizedLocal = localPath.toAbsolutePath().normalize();

        if (!normalizedLocal.startsWith(normalizedLocalRoot)) {
            throw new IllegalArgumentException("Local path escapes mounted root: " + localPath);
        }

        Path relative = normalizedLocalRoot.relativize(normalizedLocal);
        String relativeUnix = relative.toString().replace(java.io.File.separatorChar, '/');

        if (relativeUnix.isBlank()) {
            return normalizeRemotePath(remoteRoot);
        }
        return join(normalizeRemotePath(remoteRoot), relativeUnix);
    }

    private static String join(String base, String child) {
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

    private static String normalizeRemotePath(String raw) {
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

    private static String fileName(String remotePath) {
        String normalized = normalizeRemotePath(remotePath);
        int idx = normalized.lastIndexOf('/');
        if (idx < 0 || idx == normalized.length() - 1) {
            return "remote-file";
        }
        return normalized.substring(idx + 1);
    }
}
