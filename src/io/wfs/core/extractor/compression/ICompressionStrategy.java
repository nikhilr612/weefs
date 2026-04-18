package io.wfs.core.extractor.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

interface ICompressionStrategy {

    InputStream wrapInput(InputStream input) throws IOException;

    OutputStream wrapOutput(OutputStream output) throws IOException;
}