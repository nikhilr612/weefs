package io.wfs.main;

import io.wfs.ui.MainLauncher;

public class App {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "integration".equalsIgnoreCase(args[0])) {
            ArchiveIntegrationTest.run();
        } else if (args.length > 0 && "gui".equalsIgnoreCase(args[0])) {
            MainLauncher.main(args);
        } else {
            System.out.println("Usage: pass 'integration' to run archive round-trip checks.");
        }
        // TODO: add tests for
    }
}
