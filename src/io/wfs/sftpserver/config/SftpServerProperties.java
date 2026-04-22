package io.wfs.sftpserver.config;

import org.springframework.core.env.Environment;

public record SftpServerProperties(
        String host,
        int port,
        String username,
        String password,
        String archivePath,
        boolean readOnly,
        String hostKeyPath) {

    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 2222;
    private static final String DEFAULT_USERNAME = "user";
    private static final String DEFAULT_PASSWORD = "password";
    private static final String DEFAULT_HOST_KEY_PATH = ".weefs-sftp-hostkey.ser";

    public static SftpServerProperties from(Environment environment) {
        String archive = require(environment, "weefs.sftp.archive", "WEEFS_SFTP_ARCHIVE");
        String host = value(environment, "weefs.sftp.host", "WEEFS_SFTP_HOST", DEFAULT_HOST);
        int port = intValue(environment, "weefs.sftp.port", "WEEFS_SFTP_PORT", DEFAULT_PORT);
        String username = value(environment, "weefs.sftp.username", "WEEFS_SFTP_USERNAME", DEFAULT_USERNAME);
        String password = value(environment, "weefs.sftp.password", "WEEFS_SFTP_PASSWORD", DEFAULT_PASSWORD);
        boolean readOnly = boolValue(environment, "weefs.sftp.read-only", "WEEFS_SFTP_READ_ONLY", false);
        String hostKeyPath = value(
                environment,
                "weefs.sftp.host-key-path",
                "WEEFS_SFTP_HOST_KEY_PATH",
                DEFAULT_HOST_KEY_PATH);

        return new SftpServerProperties(host, port, username, password, archive, readOnly, hostKeyPath);
    }

    private static String require(Environment environment, String propertyKey, String envKey) {
        String value = value(environment, propertyKey, envKey, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required configuration: " + propertyKey + " (or env " + envKey + ")");
        }
        return value;
    }

    private static String value(Environment environment, String propertyKey, String envKey, String fallback) {
        String property = environment.getProperty(propertyKey);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }

        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        return fallback;
    }

    private static int intValue(Environment environment, String propertyKey, String envKey, int fallback) {
        String value = value(environment, propertyKey, envKey, Integer.toString(fallback));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer for " + propertyKey + ": " + value, ex);
        }
    }

    private static boolean boolValue(Environment environment, String propertyKey, String envKey, boolean fallback) {
        String value = value(environment, propertyKey, envKey, Boolean.toString(fallback));
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value);
    }
}
