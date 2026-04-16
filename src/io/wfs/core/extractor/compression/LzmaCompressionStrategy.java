package io.wfs.core.extractor.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;

public final class LzmaCompressionStrategy implements ICompressionStrategy {

    @Override
    public InputStream wrapInput(InputStream input) throws IOException {
        return new LZMACompressorInputStream(input);
    }

    @Override
    public OutputStream wrapOutput(OutputStream output) throws IOException {
        return new LZMACompressorOutputStream(output);
    }
}