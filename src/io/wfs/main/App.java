package io.wfs.main;

public class App {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "integration".equalsIgnoreCase(args[0])) {
            ArchiveIntegrationTest.run();
        } else if (args.length > 0 && "gui".equalsIgnoreCase(args[0])) {
            io.wfs.ui.WeeFsApp.start();
        } else {
            System.out.println("Usage: pass 'integration' to run archive round-trip checks, or 'gui' to launch the archive explorer.");
        }
    }
}

