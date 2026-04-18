package io.wfs.core.filesystem;

/**
 * Shared environment-key constants for {@link FileSystemDriver} implementations.
 * Avoids duplicating magic strings across providers (DRY).
 */
public final class FsEnvKeys {

    /** Key for the read-only flag passed in the environment map to {@link FileSystemDriver#open}. */
    public static final String READ_ONLY = "readOnly";

    private FsEnvKeys() {}
}
