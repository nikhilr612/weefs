package io.wfs.main;

import io.wfs.ui.MainLauncher;
import io.wfs.core.nfs.CoreNfsPathTest;
import io.wfs.core.extractor.CoreExtractorUriTest;

public class App {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            String command = args[0].toLowerCase();
            switch (command) {
                case "integration":
                    System.out.println("Running archive integration tests...");
                    ArchiveIntegrationTest.run();
                    break;
                case "nfs-integration":
                    System.out.println("Running NFS integration tests...");
                    NfsIntegrationTest.run();
                    break;
                case "unit":
                    System.out.println("Running unit tests...");
                    CoreExtractorTest.run();
                    CoreExtractorUriTest.run();
                    CoreNfsTest.run();
                    CoreNfsPathTest.run();
                    ModelTest.run();
                    UtilTest.run();
                    break;
                case "all-integration":
                    System.out.println("Running all tests...");
                    CoreExtractorTest.run();
                    CoreExtractorUriTest.run();
                    CoreNfsTest.run();
                    CoreNfsPathTest.run();
                    ModelTest.run();
                    UtilTest.run();
                    ArchiveIntegrationTest.run();
                    NfsIntegrationTest.run();
                    break;
                case "gui":
                    MainLauncher.main(args);
                    break;
                default:
                    printUsage();
            }
        } else {
            printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("WeEFS - Web File System");
        System.out.println("Usage:");
        System.out.println("  integration       - Run archive integration tests");
        System.out.println("  nfs-integration   - Run NFS integration tests");
        System.out.println("  unit              - Run unit tests (extractor, NFS, model)");
        System.out.println("  all-integration   - Run all tests (unit + integration)");
        System.out.println("  gui               - Launch GUI application");
    }
}
