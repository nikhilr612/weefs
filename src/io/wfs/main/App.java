package io.wfs.main;

import io.wfs.ui.MainLauncher;

public class App {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "integration".equalsIgnoreCase(args[0])) {
            ArchiveIntegrationTest.run();
            NFSIntegrationTest.runIfConfigured();
        } else if (args.length > 0 && "gui".equalsIgnoreCase(args[0])) {
            io.wfs.ui.WeeFsApp.start();
        } else {
            System.out.println("Usage: pass 'integration' to run archive checks (plus optional NFS round-trip), or 'gui' to launch the archive explorer.");
        }
        // TODO: add tests for
    }
}
