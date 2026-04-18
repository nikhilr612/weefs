package io.wfs.core.extractor.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

final class XzCompressionStrategy implements ICompressionStrategy {

    @Override
    public InputStream wrapInput(InputStream input) throws IOException {
        return new XZInputStream(input);
    }

    @Override
    public OutputStream wrapOutput(OutputStream output) throws IOException {
        return new XZOutputStream(output, new LZMA2Options());
    }
}