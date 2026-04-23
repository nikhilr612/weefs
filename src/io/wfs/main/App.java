package io.wfs.main;

import io.wfs.core.extractor.CoreExtractorUriTest;
import io.wfs.core.nfs.CoreNfsPathTest;
import io.wfs.core.nfs.SftpArchiveServer;

public class App {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            String command = args[0].toLowerCase();
            switch (command) {
                case "integration":
                    ArchiveIntegrationTest.run();
                    NfsIntegrationTest.run();
                    ArchiveCopyTest.run();
                    NFSIntegrationTest.runIfConfigured();
                    break;
                case "nfs-integration":
                    NfsIntegrationTest.run();
                    break;
                case "unit":
                    CoreExtractorTest.run();
                    CoreExtractorUriTest.run();
                    CoreNfsTest.run();
                    CoreNfsPathTest.run();
                    ModelTest.run();
                    UtilTest.run();
                    break;
                case "all-integration":
                    CoreExtractorTest.run();
                    CoreExtractorUriTest.run();
                    CoreNfsTest.run();
                    CoreNfsPathTest.run();
                    ModelTest.run();
                    UtilTest.run();
                    ArchiveIntegrationTest.run();
                    ArchiveCopyTest.run();
                    NfsIntegrationTest.run();
                    break;
                case "gui":
                    io.wfs.ui.WeeFsApp.start();
                    break;
                case "sftp-server":
                    SftpArchiveServer.main(sliceArgs(args, 1));
                    break;
                default:
                    printUsage();
            }
        } else {
            printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  integration       - Run archive integration tests (+ optional remote NFS round-trip)");
        System.out.println("  nfs-integration   - Run local NFS adapter integration tests");
        System.out.println("  unit              - Run unit tests (extractor, NFS, model)");
        System.out.println("  all-integration   - Run unit + integration tests");
        System.out.println("  gui               - Launch GUI application");
        System.out.println("  sftp-server       - Start embedded SFTP server over xzip archive");
    }

    private static String[] sliceArgs(String[] args, int start) {
        if (args == null || start >= args.length) {
            return new String[0];
        }
        String[] out = new String[args.length - start];
        System.arraycopy(args, start, out, 0, out.length);
        return out;
    }
}
