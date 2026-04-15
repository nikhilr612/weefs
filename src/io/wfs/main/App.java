package io.wfs.main;

import io.wfs.ui.MainLauncher;

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
                case "all-integration":
                    System.out.println("Running all integration tests...");
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
        System.out.println("  all-integration   - Run all integration tests");
        System.out.println("  gui               - Launch GUI application");
    }
}
