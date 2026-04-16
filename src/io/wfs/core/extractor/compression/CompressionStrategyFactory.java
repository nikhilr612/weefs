package io.wfs.core.extractor.compression;

public final class CompressionStrategyFactory {

    private CompressionStrategyFactory() {
    }

    public static ICompressionStrategy forKey(String key) {
        return switch (key.toLowerCase()) {
            case "none" -> new NoCompressionStrategy();
            case "gz" -> new GzipCompressionStrategy();
            case "bz2" -> new Bzip2CompressionStrategy();
            case "xz" -> new XzCompressionStrategy();
            case "lzma" -> new LzmaCompressionStrategy();
            default -> throw new IllegalArgumentException("Unsupported compression key: " + key);
        };
    }
}