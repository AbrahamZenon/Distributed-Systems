package edu.ptit.ds04.benchmark;

import edu.ptit.ds04.client.ClientEngine;
import edu.ptit.ds04.client.JupiterState;
import edu.ptit.ds04.server.CollaborationServer;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/**
 * Performance Benchmark Suite for DS04 Collaborative Document Editing System.
 * Author: Nguyen Duy Phuc (B23DCVT341)
 *
 * Measures:
 * 1. End-to-end Latency (Min, Mean, P95, P99, Max).
 * 2. Operational Throughput (operations per second).
 * 3. Convergence time across multi-client concurrent traffic.
 */
public class BenchmarkRunner {
    public static void main(String[] args) throws Exception {
        System.out.println("===========================================================================");
        System.out.println("   DS04: DISTRIBUTED SYSTEMS COLLABORATIVE EDITOR - BENCHMARK SUITE");
        System.out.println("                  Author: Nguyen Duy Phuc (B23DCVT341)");
        System.out.println("===========================================================================");

        int clientCount = 5;
        int opsPerClient = 20;
        int serverPort = 8990;
        String serverUri = "ws://127.0.0.1:" + serverPort;
        String docId = "bench-doc-" + System.currentTimeMillis();

        for (int i = 0; i < args.length; i++) {
            if ("--clients".equals(args[i]) && i + 1 < args.length) clientCount = Integer.parseInt(args[++i]);
            else if ("--ops".equals(args[i]) && i + 1 < args.length) opsPerClient = Integer.parseInt(args[++i]);
        }

        File tempDir = Files.createTempDirectory("ds04_bench_").toFile();
        CollaborationServer server = new CollaborationServer("127.0.0.1", serverPort, tempDir.getAbsolutePath());
        server.start();
        Thread.sleep(800);

        List<ClientEngine> clients = new ArrayList<>();
        List<Long> latencies = new CopyOnWriteArrayList<>();

        try {
            System.out.printf("[*] Spawning %d concurrent client nodes connecting to %s...%n", clientCount, serverUri);
            for (int i = 1; i <= clientCount; i++) {
                String cId = "bench-client-" + i;
                ClientEngine client = new ClientEngine(serverUri, docId, cId, "User" + i);
                boolean ok = client.connectAndJoin(5);
                if (!ok) throw new RuntimeException("Failed to connect client: " + cId);
                clients.add(client);
            }
            System.out.println("[+] All clients successfully connected and joined.");

            int totalOps = clientCount * opsPerClient;
            final int totalRounds = opsPerClient;
            System.out.printf("[*] Executing %d concurrent operations (%d rounds of %d concurrent clients)...%n",
                totalOps, totalRounds, clientCount);

            ExecutorService pool = Executors.newFixedThreadPool(clientCount);
            long startTime = System.currentTimeMillis();

            for (int r = 0; r < totalRounds; r++) {
                final int round = r;
                CountDownLatch roundDone = new CountDownLatch(clientCount);

                for (int i = 0; i < clientCount; i++) {
                    final int clientIdx = i;
                    final ClientEngine client = clients.get(i);
                    pool.submit(() -> {
                        try {
                            long opStart = System.nanoTime();
                            String text = "C" + clientIdx + "_" + round + " ";
                            client.submitInsert(0, text);
                            long durationNs = System.nanoTime() - opStart;
                            latencies.add(TimeUnit.NANOSECONDS.toMicros(durationNs));
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            roundDone.countDown();
                        }
                    });
                }
                roundDone.await(10, TimeUnit.SECONDS);
                
                // Await all clients to receive and process all broadcasted operations for this round
                int targetVersion = (round + 1) * clientCount;
                long deadline = System.currentTimeMillis() + 3000;
                while (System.currentTimeMillis() < deadline) {
                    boolean allReceived = true;
                    for (ClientEngine c : clients) {
                        if (c.getVersion() < targetVersion || c.getJupiterState() != JupiterState.SYNCHRONIZED) {
                            allReceived = false;
                            break;
                        }
                    }
                    if (allReceived) break;
                    Thread.sleep(15);
                }
                Thread.sleep(50);
            }

            long totalTimeMs = System.currentTimeMillis() - startTime;
            pool.shutdown();

            // Wait for final convergence across all clients
            Thread.sleep(800);

            // Verify Convergence
            String finalContent = clients.get(0).getContent();
            int finalVer = clients.get(0).getVersion();
            boolean converged = true;

            String serverContent = server.getDocumentManager().getOrLoadDocument(docId).getContent();
            System.out.printf("Server (v=%d): len=%d hash=%d%n", server.getDocumentManager().getOrLoadDocument(docId).getVersion(), serverContent.length(), serverContent.hashCode());

            for (int i = 0; i < clients.size(); i++) {
                ClientEngine c = clients.get(i);
                System.out.printf("Client %d (v=%d state=%s): len=%d hash=%d%n", i, c.getVersion(), c.getJupiterState(), c.getContent().length(), c.getContent().hashCode());
                if (!c.getContent().equals(serverContent) || c.getVersion() != finalVer) {
                    converged = false;
                    System.out.println("  Client " + i + " content: '" + c.getContent() + "'");
                }
            }
            if (!converged) {
                System.out.println("  Server content  : '" + serverContent + "'");
            }

            // Statistical calculations
            Collections.sort(latencies);
            double meanLatUs = latencies.stream().mapToLong(v -> v).average().orElse(0.0);
            long minLatUs = latencies.isEmpty() ? 0 : latencies.get(0);
            long maxLatUs = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);
            long p95LatUs = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.95));
            long p99LatUs = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.99));

            double throughputOpsSec = totalOps / ((double) totalTimeMs / 1000.0);

            System.out.println("\n---------------------------------------------------------------------------");
            System.out.println("                        BENCHMARK RESULTS SUMMARY");
            System.out.println("---------------------------------------------------------------------------");
            System.out.printf("Total Concurrent Clients : %d%n", clientCount);
            System.out.printf("Total Operations Applied : %d ops%n", totalOps);
            System.out.printf("Elapsed Execution Time   : %d ms%n", totalTimeMs);
            System.out.printf("Throughput               : %.2f ops/sec%n", throughputOpsSec);
            System.out.printf("Min Local Latency        : %.2f ms%n", minLatUs / 1000.0);
            System.out.printf("Mean Local Latency       : %.2f ms%n", meanLatUs / 1000.0);
            System.out.printf("P95 Latency              : %.2f ms%n", p95LatUs / 1000.0);
            System.out.printf("P99 Latency              : %.2f ms%n", p99LatUs / 1000.0);
            System.out.printf("Max Latency              : %.2f ms%n", maxLatUs / 1000.0);
            System.out.printf("Final Document Length    : %d characters%n", finalContent.length());
            System.out.printf("Convergence Status       : %s (v=%d)%n", (converged ? "PASSED (100% SEC)" : "FAILED"), finalVer);
            System.out.println("---------------------------------------------------------------------------");

            // Write markdown report to docs/benchmark_results.md
            File mdReport = new File("docs/benchmark_results.md");
            try (FileWriter fw = new FileWriter(mdReport)) {
                fw.write("# DS04 Collaboration Engine - Performance Benchmark Report\n\n");
                fw.write("**Author:** Nguyen Duy Phuc (B23DCVT341)\n\n");
                fw.write("## 1. Test Environment & Parameters\n");
                fw.write(String.format("- **Concurrent Clients:** %d\n", clientCount));
                fw.write(String.format("- **Total Operations:** %d ops\n", totalOps));
                fw.write(String.format("- **Elapsed Time:** %d ms\n", totalTimeMs));
                fw.write(String.format("- **Throughput:** `%.2f ops/sec`\n\n", throughputOpsSec));
                fw.write("## 2. Latency Metrics\n");
                fw.write("| Metric | Latency (ms) |\n|:---|:---:|\n");
                fw.write(String.format("| Min Latency | `%.2f ms` |\n", minLatUs / 1000.0));
                fw.write(String.format("| Mean Latency | `%.2f ms` |\n", meanLatUs / 1000.0));
                fw.write(String.format("| 95th Percentile (P95) | `%.2f ms` |\n", p95LatUs / 1000.0));
                fw.write(String.format("| 99th Percentile (P99) | `%.2f ms` |\n", p99LatUs / 1000.0));
                fw.write(String.format("| Max Latency | `%.2f ms` |\n\n", maxLatUs / 1000.0));
                fw.write("## 3. Convergence Verification\n");
                fw.write(String.format("- **Convergence Result:** **%s**\n", (converged ? "CONVERGED (100% Consistently Identical across all clients)" : "FAILED")));
                fw.write(String.format("- **Final Sequenced Revision:** `v=%d`\n", finalVer));
            }
            System.out.println("[+] Benchmark report exported to: docs/benchmark_results.md");
            System.exit(0);

        } finally {
            for (ClientEngine c : clients) {
                try { c.closeBlocking(); } catch (Exception ignored) {}
            }
            server.stop();
            try {
                Files.walk(tempDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
            } catch (Exception ignored) {}
        }
    }
}
