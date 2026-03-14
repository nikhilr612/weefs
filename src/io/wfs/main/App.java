package io.wfs.main;

public class App {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "integration".equalsIgnoreCase(args[0])) {
            ArchiveIntegrationTest.run();
        } else {
            System.out.println("Usage: pass 'integration' to run archive round-trip checks.");
        }
    }
}

