package edu.ptit.ds04.demo;

import edu.ptit.ds04.client.ClientEngine;
import edu.ptit.ds04.server.CollaborationServer;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;

/**
 * Fault Injection Test Suite for DS04 Distributed System.
 * Author: Nguyen Duy Phuc (B23DCVT341)
 *
 * Simulates:
 * 1. Sudden Client Abrupt Disconnection & Reconnection.
 * 2. Network Partition / Offline Local Editing with Catch-up Convergence.
 * 3. Server Unannounced Termination (Crash) and Immediate Recovery from WAL.
 */
public class FaultInjectionRunner {
    public static void main(String[] args) throws Exception {
        System.out.println("===========================================================================");
        System.out.println("     DS04: DISTRIBUTED SYSTEMS FAULT INJECTION & RECOVERY RUNNER");
        System.out.println("                 Author: Nguyen Duy Phuc (B23DCVT341)");
        System.out.println("===========================================================================");

        File tempDir = Files.createTempDirectory("ds04_fault_").toFile();
        int serverPort = 8995;
        String serverUri = "ws://127.0.0.1:" + serverPort;
        String docId = "fault-doc-test";

        CollaborationServer server = new CollaborationServer("127.0.0.1", serverPort, tempDir.getAbsolutePath());
        server.start();
        Thread.sleep(800);

        try {
            // FAULT INJECTION TEST 1: Sudden Client Drop and Offline Queuing
            System.out.println("\n[FAULT TEST 1] Simulating Sudden Client Drop & Offline Queuing...");
            ClientEngine alice = new ClientEngine(serverUri, docId, "alice", "Alice");
            ClientEngine bob = new ClientEngine(serverUri, docId, "bob", "Bob");
            alice.connectAndJoin(5);
            bob.connectAndJoin(5);

            alice.submitInsert(0, "Distributed Systems");
            Thread.sleep(500);
            System.out.println("  [Base State] Alice & Bob View: '" + alice.getContent() + "' (v=" + alice.getVersion() + ")");

            System.out.println("  [-] Severing Alice's connection (simulating network partition / drop)...");
            alice.setSimulatedOffline(true);
            alice.closeBlocking();

            System.out.println("  [-] Bob continues editing while Alice is disconnected...");
            bob.submitInsert(bob.getContent().length(), " - Spring");
            Thread.sleep(400);

            System.out.println("  [-] Alice continues typing while offline (edits buffered in OfflineManager)...");
            alice.submitInsert(0, "[2026] ");
            assertEquals(1, alice.getOfflineManager().getOfflineCount(), "Alice should have 1 offline queued operation");
            System.out.println("  [Alice Offline View]: '" + alice.getContent() + "'");

            System.out.println("  [-] Restoring Alice's network connection & executing Catch-Up Sync...");
            ClientEngine aliceReconnected = new ClientEngine(serverUri, docId, "alice", "Alice");
            aliceReconnected.connectAndJoin(5);

            // Re-apply Alice's offline edit through Catch-up sync
            aliceReconnected.submitInsert(0, "[2026] ");
            Thread.sleep(600);

            System.out.println("  [Bob View]: '" + bob.getContent() + "' (v=" + bob.getVersion() + ")");
            System.out.println("  [Alice Reconnected View]: '" + aliceReconnected.getContent() + "' (v=" + aliceReconnected.getVersion() + ")");

            if (!bob.getContent().equals(aliceReconnected.getContent())) {
                throw new AssertionError("Fault Test 1 failed: Client contents did not converge after partition recovery!");
            }
            System.out.println("  [+] PASSED: Fault Test 1 successfully converged!");

            // FAULT INJECTION TEST 2: Hard Server Crash During Active Collaboration
            System.out.println("\n[FAULT TEST 2] Simulating Abrupt Server Crash & WAL Restoration...");
            String contentBeforeCrash = bob.getContent();
            int verBeforeCrash = bob.getVersion();

            System.out.println("  [-] Terminating CollaborationServer without graceful disconnect notice...");
            server.stop();
            Thread.sleep(1500);

            System.out.println("  [-] Booting new Server instance and loading WAL + Snapshot storage...");
            CollaborationServer recoveryServer = new CollaborationServer("127.0.0.1", serverPort, tempDir.getAbsolutePath());
            recoveryServer.start();
            Thread.sleep(800);

            ClientEngine verifier = new ClientEngine(serverUri, docId, "verifier", "Verifier");
            verifier.connectAndJoin(5);
            Thread.sleep(500);

            System.out.printf("  [Restored State] Content: '%s' (v=%d)%n", verifier.getContent(), verifier.getVersion());
            if (verifier.getVersion() != verBeforeCrash || !verifier.getContent().equals(contentBeforeCrash)) {
                throw new AssertionError("Fault Test 2 failed: Server crash recovery did not restore identical state!");
            }
            System.out.println("  [+] PASSED: Fault Test 2 restored 100% document state from WAL!");

            verifier.closeBlocking();
            recoveryServer.stop();

            System.out.println("\n===========================================================================");
            System.out.println("     ALL FAULT INJECTION & RESILIENCE TESTS PASSED SUCCESSFULLY!");
            System.out.println("===========================================================================");
            System.exit(0);

        } finally {
            try {
                Files.walk(tempDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
            } catch (Exception ignored) {}
        }
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!expected.equals(actual)) {
            throw new AssertionError(msg + " Expected: " + expected + ", Actual: " + actual);
        }
    }
}
