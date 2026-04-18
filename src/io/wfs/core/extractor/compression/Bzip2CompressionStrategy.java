package io.wfs.core.extractor.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

public final class Bzip2CompressionStrategy implements ICompressionStrategy {

    @Override
    public InputStream wrapInput(InputStream input) throws IOException {
        return new BZip2CompressorInputStream(input);
    }

    @Override
    public OutputStream wrapOutput(OutputStream output) throws IOException {
        return new BZip2CompressorOutputStream(output);
    }
}