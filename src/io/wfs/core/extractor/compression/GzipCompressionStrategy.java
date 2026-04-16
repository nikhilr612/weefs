package io.wfs.core.extractor.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GzipCompressionStrategy implements ICompressionStrategy {

    @Override
    public InputStream wrapInput(InputStream input) throws IOException {
        return new GZIPInputStream(input);
    }

    @Override
    public OutputStream wrapOutput(OutputStream output) throws IOException {
        return new GZIPOutputStream(output);
    }
}