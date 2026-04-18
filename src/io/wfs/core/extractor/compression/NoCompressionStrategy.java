package io.wfs.core.extractor.compression;

import java.io.InputStream;
import java.io.OutputStream;

final class NoCompressionStrategy implements ICompressionStrategy {

    @Override
    public InputStream wrapInput(InputStream input) {
        return input;
    }

    @Override
    public OutputStream wrapOutput(OutputStream output) {
        return output;
    }
}