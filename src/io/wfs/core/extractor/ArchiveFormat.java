package io.wfs.core.extractor;

final class ArchiveFormat {

    private final ArchiveContainerType containerType;
    private final String compressionKey;

    ArchiveFormat(ArchiveContainerType containerType, String compressionKey) {
        this.containerType = containerType;
        this.compressionKey = compressionKey;
    }

    ArchiveContainerType containerType() {
        return containerType;
    }

    String compressionKey() {
        return compressionKey;
    }
}