package io.wfs.core.nfs;

import java.util.Objects;

/**
 * Immutable configuration for an NFS connection.
 * Encapsulates host, port, export path, and mount options (Factory pattern material).
 */
public final class NfsConnectionConfig {

    private final String host;
    private final int port;
    private final String exportPath;
    private final String mountPath;
    private final int timeoutSeconds;
    private final boolean readOnly;

    public NfsConnectionConfig(
            String host,
            int port,
            String exportPath,
            String mountPath,
            int timeoutSeconds,
            boolean readOnly) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = validatePort(port);
        this.exportPath = Objects.requireNonNull(exportPath, "exportPath");
        this.mountPath = Objects.requireNonNull(mountPath, "mountPath");
        this.timeoutSeconds = validateTimeout(timeoutSeconds);
        this.readOnly = readOnly;
    }

    private static int validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be 1-65535, got: " + port);
        }
        return port;
    }

    private static int validateTimeout(int timeout) {
        if (timeout < 1 || timeout > 3600) {
            throw new IllegalArgumentException("Timeout must be 1-3600 seconds, got: " + timeout);
        }
        return timeout;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getExportPath() {
        return exportPath;
    }

    public String getMountPath() {
        return mountPath;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public String toString() {
        return String.format("%s:%d%s(%s)[ro=%b]", host, port, exportPath, mountPath, readOnly);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NfsConnectionConfig)) return false;
        NfsConnectionConfig that = (NfsConnectionConfig) o;
        return port == that.port &&
                Objects.equals(host, that.host) &&
                Objects.equals(exportPath, that.exportPath) &&
                Objects.equals(mountPath, that.mountPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, exportPath, mountPath);
    }
}
