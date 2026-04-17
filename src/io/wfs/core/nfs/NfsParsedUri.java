package io.wfs.core.nfs;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class NfsParsedUri {

    private final String host;
    private final int port;
    private final String remotePath;
    private final String authEnvVar;
    private final String username;

    NfsParsedUri(String host, int port, String remotePath, String authEnvVar, String username) {
        this.host = host;
        this.port = port;
        this.remotePath = remotePath;
        this.authEnvVar = authEnvVar;
        this.username = username;
    }

    String host() {
        return host;
    }

    int port() {
        return port;
    }

    String remotePath() {
        return remotePath;
    }

    String authEnvVar() {
        return authEnvVar;
    }

    String username() {
        return username;
    }

    static NfsParsedUri parse(URI uri, String expectedScheme) {
        if (uri == null) {
            throw new IllegalArgumentException("URI is required");
        }
        if (expectedScheme == null || expectedScheme.isBlank()) {
            throw new IllegalArgumentException("Expected scheme is required");
        }
        if (!expectedScheme.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Unsupported scheme: " + uri);
        }

        Map<String, String> query = parseQuery(uri.getRawQuery());

        String host = trimOrNull(uri.getHost());
        if (host == null) {
            host = trimOrNull(query.get("host"));
        }
        if (host == null) {
            throw new IllegalArgumentException("Host is required in URI: " + uri);
        }

        int port = uri.getPort() > 0 ? uri.getPort() : 22;

        String path = trimOrNull(uri.getPath());
        if (path == null) {
            path = "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        String auth = trimOrNull(query.get("auth"));
        if (auth == null) {
            throw new IllegalArgumentException("Missing required query parameter 'auth' in URI: " + uri);
        }

        String username = parseUsernameFromUserInfo(uri.getUserInfo());
        if (username == null) {
            username = trimOrNull(query.get("user"));
        }
        if (username == null) {
            username = trimOrNull(System.getenv("WEEFS_SFTP_USER"));
        }
        if (username == null) {
            username = trimOrNull(System.getenv("USER"));
        }
        if (username == null) {
            username = trimOrNull(System.getProperty("user.name"));
        }
        if (username == null) {
            throw new IllegalArgumentException("SFTP username could not be resolved from URI or environment");
        }

        return new NfsParsedUri(host, port, path, auth, username);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx < 0 ? pair : pair.substring(0, idx);
            String value = idx < 0 ? "" : pair.substring(idx + 1);
            values.put(decode(key), decode(value));
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String parseUsernameFromUserInfo(String userInfo) {
        String value = trimOrNull(userInfo);
        if (value == null) {
            return null;
        }

        int colon = value.indexOf(':');
        String username = colon >= 0 ? value.substring(0, colon) : value;
        return trimOrNull(username);
    }
}
