package io.wfs.core.extractor.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public enum CompressionStrategyType {
    NONE("none", new NoCompressionStrategy()),
    GZ("gz", new GzipCompressionStrategy()),
    BZ2("bz2", new Bzip2CompressionStrategy()),
    XZ("xz", new XzCompressionStrategy()),
    LZMA("lzma", new LzmaCompressionStrategy());

    private final String key;
    private final ICompressionStrategy strategy;

    CompressionStrategyType(String key, ICompressionStrategy strategy) {
        this.key = key;
        this.strategy = strategy;
    }

    public static CompressionStrategyType fromKey(String key) {
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        for (CompressionStrategyType type : values()) {
            if (type.key.equals(normalizedKey)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported compression key: " + key);
    }

    public String key() {
        return key;
    }

    public InputStream wrapInput(InputStream input) throws IOException {
        return strategy.wrapInput(input);
    }

    public OutputStream wrapOutput(OutputStream output) throws IOException {
        return strategy.wrapOutput(output);
    }
}