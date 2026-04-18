package io.wfs.main;

import io.wfs.core.nfs.NfsSftpFsProvider;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

final class NFSIntegrationTest {

    private static final String ENV_URI = "WEEFS_NFS_TEST_URI";

    private NFSIntegrationTest() {
    }

    static void runIfConfigured() throws Exception {
        String uriText = System.getenv(ENV_URI);
        if (uriText == null || uriText.isBlank()) {
            System.out.println("NFS integration test skipped (set " + ENV_URI + ").");
            return;
        }

        URI uri = URI.create(uriText);
        NfsSftpFsProvider provider = new NfsSftpFsProvider();

        String fileName = "weefs-integration-" + System.currentTimeMillis() + ".txt";
        try (FileSystem fs = provider.newFileSystem(uri, Map.of())) {
            Path testFile = fs.getPath("/").resolve(fileName);
            String content = "nfs-roundtrip-" + System.nanoTime();

            Files.writeString(testFile, content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            String readBack = Files.readString(testFile);
            if (!content.equals(readBack)) {
                throw new IllegalStateException("NFS read mismatch: expected=" + content + " actual=" + readBack);
            }

            Files.delete(testFile);
            if (Files.exists(testFile)) {
                throw new IllegalStateException("NFS delete failed for: " + testFile);
            }
        }

        System.out.println("  [PASS] nfs");
    }
}
