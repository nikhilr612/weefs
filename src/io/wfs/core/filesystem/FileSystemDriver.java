package io.wfs.core.filesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.Map;

/**
 * Strategy contract for opening a URI-backed file system.
 */
public interface FileSystemDriver {

    String scheme();

    FileSystem open(URI uri, Map<String, ?> env) throws IOException;
}
