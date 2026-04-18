package io.wfs.core.nfs;

import java.util.Objects;

final class NfsSftpConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String remoteRoot;
    private final String authEnvVar;

    NfsSftpConfig(String host, int port, String username, String password, String remoteRoot, String authEnvVar) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.remoteRoot = Objects.requireNonNull(remoteRoot, "remoteRoot");
        this.authEnvVar = Objects.requireNonNull(authEnvVar, "authEnvVar");
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }

    String remoteRoot() {
        return remoteRoot;
    }

    String authEnvVar() {
        return authEnvVar;
    }
}
