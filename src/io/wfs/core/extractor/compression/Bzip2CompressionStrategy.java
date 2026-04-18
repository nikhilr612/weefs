package io.wfs.core.extractor.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.tools.bzip2.CBZip2InputStream;
import org.apache.tools.bzip2.CBZip2OutputStream;

final class Bzip2CompressionStrategy implements ICompressionStrategy {

    @Override
    public InputStream wrapInput(InputStream input) throws IOException {
        int magic1 = input.read();
        int magic2 = input.read();
        if (magic1 != 'B' || magic2 != 'Z') {
            throw new IOException("Invalid BZip2 header");
        }
        return new CBZip2InputStream(input);
    }

    @Override
    public OutputStream wrapOutput(OutputStream output) throws IOException {
        output.write('B');
        output.write('Z');
        return new CBZip2OutputStream(output);
    }
}