package edu.ptit.ds04;

import edu.ptit.ds04.client.ClientEngine;
import edu.ptit.ds04.server.CollaborationServer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerIntegrationTest {

    @Test
    public void testServerMultiClientAndOTConcurrency() throws Exception {
        File tempDir = Files.createTempDirectory("ds04_server_it_").toFile();
        int port = 8792;
        String uri = "ws://127.0.0.1:" + port;
        String docId = "test-doc-integration";

        CollaborationServer server = new CollaborationServer("127.0.0.1", port, tempDir.getAbsolutePath());
        server.start();
        Thread.sleep(800);

        try {
            ClientEngine client1 = new ClientEngine(uri, docId, "alice", "Alice");
            ClientEngine client2 = new ClientEngine(uri, docId, "bob", "Bob");

            assertTrue(client1.connectAndJoin(5));
            assertTrue(client2.connectAndJoin(5));

            assertEquals(0, client1.getVersion());
            assertEquals("", client1.getContent());

            // Alice inserts "Hello"
            client1.submitInsert(0, "Hello");
            Thread.sleep(600);

            assertEquals("Hello", client1.getContent());
            assertEquals("Hello", client2.getContent());
            assertEquals(1, client1.getVersion());
            assertEquals(1, client2.getVersion());

            // Concurrent typing:
            // Alice inserts " A" at pos 5
            // Bob inserts " B" at pos 5
            Thread t1 = new Thread(() -> client1.submitInsert(5, " A"));
            Thread t2 = new Thread(() -> client2.submitInsert(5, " B"));
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            Thread.sleep(800);

            // Both clients should converge to identical content!
            assertEquals(client1.getContent(), client2.getContent());
            assertEquals(3, client1.getVersion());
            assertEquals(3, client2.getVersion());
            assertTrue(client1.getContent().equals("Hello A B") || client1.getContent().equals("Hello B A"));

            client1.closeBlocking();
            client2.closeBlocking();
        } finally {
            server.stop();
            Files.walk(tempDir.toPath())
                .sorted(Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(File::delete);
        }
    }
}
