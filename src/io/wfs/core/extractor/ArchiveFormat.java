package io.wfs.core.extractor;

final class ArchiveFormat {

    private final ArchiveContainerType containerType;
    private final io.wfs.core.extractor.compression.CompressionStrategyType compressionType;

    ArchiveFormat(ArchiveContainerType containerType, io.wfs.core.extractor.compression.CompressionStrategyType compressionType) {
        this.containerType = containerType;
        this.compressionType = compressionType;
    }

    ArchiveContainerType containerType() {
        return containerType;
    }

    io.wfs.core.extractor.compression.CompressionStrategyType compressionType() {
        return compressionType;
    }
}
