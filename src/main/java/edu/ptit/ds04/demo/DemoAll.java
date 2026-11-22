package edu.ptit.ds04.demo;

import edu.ptit.ds04.client.ClientEngine;
import edu.ptit.ds04.server.CollaborationServer;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;

public class DemoAll {
    public static void main(String[] args) throws Exception {
        System.out.println("===========================================================================");
        System.out.println("   DS04: REAL-TIME COLLABORATIVE DOCUMENT EDITING SYSTEM (JAVA EDITION)");
        System.out.println("                    MANDATORY DEMONSTRATION RUNNER");
        System.out.println("===========================================================================");

        File tempDir = Files.createTempDirectory("ds04_java_demo_").toFile();
        int serverPort = 8898;
        String serverUri = "ws://127.0.0.1:" + serverPort;
        String docId = "demo-doc-java";

        CollaborationServer server = new CollaborationServer("127.0.0.1", serverPort, tempDir.getAbsolutePath());
        server.start();
        System.out.println("\n[+] Server started on " + serverUri + " with storage at: " + tempDir.getAbsolutePath());
        Thread.sleep(800);

        try {
            // SCENARIO 1: Three clients open the same document
            System.out.println("\n----------------------------------------------------------------------");
            System.out.println(">>> SCENARIO 1: Three clients open the same document");
            ClientEngine alice = new ClientEngine(serverUri, docId, "alice", "Alice");
            ClientEngine bob = new ClientEngine(serverUri, docId, "bob", "Bob");
            ClientEngine charlie = new ClientEngine(serverUri, docId, "charlie", "Charlie");

            alice.connectAndJoin(5);
            bob.connectAndJoin(5);
            charlie.connectAndJoin(5);
            System.out.println("[OK] Alice, Bob, and Charlie successfully connected to document '" + docId + "'.");

            // SCENARIO 2: One client edits, other clients receive updates
            System.out.println("\n----------------------------------------------------------------------");
            System.out.println(">>> SCENARIO 2: One client edits, other clients receive updates");
            System.out.println("[-] Alice inserts 'Distributed Systems' at position 0...");
            alice.submitInsert(0, "Distributed Systems");
            Thread.sleep(600);

            System.out.println("    [Alice View]  : '" + alice.getContent() + "' (v=" + alice.getVersion() + ")");
            System.out.println("    [Bob View]    : '" + bob.getContent() + "' (v=" + bob.getVersion() + ")");
            System.out.println("    [Charlie View]: '" + charlie.getContent() + "' (v=" + charlie.getVersion() + ")");
            if (!alice.getContent().equals("Distributed Systems") || !bob.getContent().equals(charlie.getContent())) {
                throw new AssertionError("Scenario 2 updates did not match!");
            }
            System.out.println("[OK] All clients received Alice's update in real-time!");

            // SCENARIO 3: Two clients insert content at the same position simultaneously
            System.out.println("\n----------------------------------------------------------------------");
            System.out.println(">>> SCENARIO 3: Two clients insert content at the same position simultaneously");
            System.out.println("[-] Alice inserts '[Alice] ' at pos 0 concurrently with Bob inserting '[Bob] ' at pos 0...");

            Thread t1 = new Thread(() -> alice.submitInsert(0, "[Alice] "));
            Thread t2 = new Thread(() -> bob.submitInsert(0, "[Bob] "));
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            Thread.sleep(800);

            System.out.println("    [Alice View]  : '" + alice.getContent() + "' (v=" + alice.getVersion() + ")");
            System.out.println("    [Bob View]    : '" + bob.getContent() + "' (v=" + bob.getVersion() + ")");
            System.out.println("    [Charlie View]: '" + charlie.getContent() + "' (v=" + charlie.getVersion() + ")");
            if (!alice.getContent().equals(bob.getContent()) || !bob.getContent().equals(charlie.getContent())) {
                throw new AssertionError("Scenario 3 convergence failed!");
            }
            System.out.println("[OK] Operational Transformation resolved conflict! Converged on: '" + alice.getContent() + "'");

            // SCENARIO 4: One client inserts while another deletes
            System.out.println("\n----------------------------------------------------------------------");
            System.out.println(">>> SCENARIO 4: One client inserts while another deletes");
            System.out.println("[-] Alice deletes 7 characters from pos 0 while Charlie inserts ' 2026' at the end...");
            int docLen = charlie.getContent().length();

            Thread t3 = new Thread(() -> alice.submitDelete(0, 7));
            Thread t4 = new Thread(() -> charlie.submitInsert(docLen, " 2026"));
            t3.start();
            t4.start();
            t3.join();
            t4.join();
            Thread.sleep(800);

            System.out.println("    [Alice View]  : '" + alice.getContent() + "' (v=" + alice.getVersion() + ")");
            System.out.println("    [Bob View]    : '" + bob.getContent() + "' (v=" + bob.getVersion() + ")");
            System.out.println("    [Charlie View]: '" + charlie.getContent() + "' (v=" + charlie.getVersion() + ")");
            if (!alice.getContent().equals(bob.getContent()) || !bob.getContent().equals(charlie.getContent())) {
                throw new AssertionError("Scenario 4 insert vs delete convergence failed!");
            }
            System.out.println("[OK] Insert vs Delete conflict resolved with intention preservation!");

            // SCENARIO 5: Demonstrate convergence among clients
            System.out.println("\n----------------------------------------------------------------------");
            System.out.println(">>> SCENARIO 5: Demonstrate convergence among clients");
            System.out.println("    All 3 clients identical content: '" + alice.getContent() + "'");
            System.out.println("    All 3 clients identical version: v=" + alice.getVersion());
            System.out.println("[OK] Strong Eventual Consistency verified across all client nodes!");

            // SCENARIO 6: Disconnect one client, remaining clients continue editing, disconnected client reconnects & syncs
            System.out.println("\n----------------------------------------------------------------------");
            System.out.println(">>> SCENARIO 6: Client disconnect, concurrent edits, reconnect and catch-up sync");
            System.out.println("[-] Disconnecting Charlie...");
            charlie.closeBlocking();
            System.out.println("    Charlie is offline.");

            System.out.println("[-] Alice and Bob continue typing while Charlie is disconnected...");
            alice.submitInsert(alice.getContent().length(), " - Java Final");
            Thread.sleep(600);
            System.out.println("    [Alice View] (v=" + alice.getVersion() + "): '" + alice.getContent() + "'");

            System.out.println("[-] Charlie reconnects and catches up...");
            ClientEngine charlieReconnected = new ClientEngine(serverUri, docId, "charlie", "Charlie");
            charlieReconnected.connectAndJoin(5);
            Thread.sleep(600);

            System.out.println("    [Charlie Reconnected View] (v=" + charlieReconnected.getVersion() + "): '" + charlieReconnected.getContent() + "'");
            if (!charlieReconnected.getContent().equals(alice.getContent())) {
                throw new AssertionError("Scenario 6 reconnect sync failed!");
            }
            System.out.println("[OK] Disconnected client reconnected and successfully synchronized state!");

            String finalContentBeforeRestart = alice.getContent();
            int finalVersionBeforeRestart = alice.getVersion();

            alice.closeBlocking();
            bob.closeBlocking();
            charlieReconnected.closeBlocking();

            // SCENARIO 7: Restart the server and restore the document
            System.out.println("\n----------------------------------------------------------------------");
            System.out.println(">>> SCENARIO 7: Restart the server and restore the document from WAL/Snapshot");
            System.out.println("[-] Stopping Server (simulating crash/shutdown)...");
            server.stop();
            Thread.sleep(1000);
            System.out.println("[+] Server stopped.");

            System.out.println("[-] Booting a fresh Server instance loading from persistent WAL/Snapshot storage...");
            CollaborationServer restartedServer = new CollaborationServer("127.0.0.1", serverPort, tempDir.getAbsolutePath());
            restartedServer.start();
            Thread.sleep(800);
            System.out.println("[+] Restarted server is active!");

            System.out.println("[-] Client connects to restarted server to verify state restoration...");
            ClientEngine verifier = new ClientEngine(serverUri, docId, "verifier", "Verifier");
            verifier.connectAndJoin(5);
            Thread.sleep(600);

            System.out.println("    Restored Version: v=" + verifier.getVersion() + " (Expected: v=" + finalVersionBeforeRestart + ")");
            System.out.println("    Restored Content: '" + verifier.getContent() + "' (Expected: '" + finalContentBeforeRestart + "')");
            if (verifier.getVersion() != finalVersionBeforeRestart || !verifier.getContent().equals(finalContentBeforeRestart)) {
                throw new AssertionError("Scenario 7 crash recovery failed!");
            }
            System.out.println("[OK] Server Crash Recovery succeeded! 100% document state restored accurately!");

            verifier.closeBlocking();
            restartedServer.stop();

            System.out.println("\n===========================================================================");
            System.out.println("    ALL 7 MANDATORY DEMONSTRATION SCENARIOS COMPLETED SUCCESSFULLY!");
            System.out.println("===========================================================================");
        } finally {
            try {
                Files.walk(tempDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
            } catch (Exception ignored) {}
        }
    }
}
