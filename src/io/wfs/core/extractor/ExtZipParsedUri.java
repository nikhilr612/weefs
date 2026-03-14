package io.wfs.core.extractor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class ExtZipParsedUri {

    private final String archivePart;
    private final String entryPart;

    ExtZipParsedUri(String archivePart, String entryPart) {
        this.archivePart = archivePart;
        this.entryPart = entryPart;
    }

    String archivePart() {
        return archivePart;
    }

    String entryPart() {
        return entryPart;
    }

    static ExtZipParsedUri parse(URI uri, String expectedScheme) {
        if (uri == null) {
            throw new IllegalArgumentException("URI is required");
        }
        if (expectedScheme == null || expectedScheme.isBlank()) {
            throw new IllegalArgumentException("Expected scheme is required");
        }
        if (!expectedScheme.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Unsupported scheme: " + uri);
        }

        String raw = uri.getSchemeSpecificPart();
        if (raw == null || raw.isBlank()) {
            raw = uri.getPath();
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing archive location: " + uri);
        }

        String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
        int split = decoded.indexOf("!/");
        if (split < 0 && decoded.endsWith("!")) {
            split = decoded.length() - 1;
        }

        String archive = split < 0 ? decoded : decoded.substring(0, split);
        String entry = split < 0 ? "" : decoded.substring(Math.min(split + 2, decoded.length()));
        archive = archive.trim();
        entry = entry.trim();

        if (archive.startsWith("//")) {
            archive = archive.substring(2);
        }

        return new ExtZipParsedUri(archive, entry);
    }
}
