package io.wfs.core.extractor.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.LZMAOutputStream;

final class LzmaCompressionStrategy implements ICompressionStrategy {

    @Override
    public InputStream wrapInput(InputStream input) throws IOException {
        return new LZMAInputStream(input);
    }

    @Override
    public OutputStream wrapOutput(OutputStream output) throws IOException {
        return new LZMAOutputStream(output, new LZMA2Options(), -1L);
    }
}