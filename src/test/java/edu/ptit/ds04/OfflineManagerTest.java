package edu.ptit.ds04;

import edu.ptit.ds04.client.JupiterClient;
import edu.ptit.ds04.client.OfflineManager;
import edu.ptit.ds04.core.OpType;
import edu.ptit.ds04.core.Operation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying OfflineManager queueing and Catch-up Reconnect Synchronization.
 * Author: Nguyen Duy Phuc (B23DCVT341)
 */
public class OfflineManagerTest {

    @Test
    public void testOfflineQueueAndCatchUpSync() {
        OfflineManager offlineManager = new OfflineManager();
        JupiterClient client = new JupiterClient("charlie", "doc-1", "Hello", 1);

        assertFalse(offlineManager.isOffline());
        offlineManager.setOffline(true);
        assertTrue(offlineManager.isOffline());

        // While offline, Charlie types two edits locally
        Operation op1 = new Operation(OpType.INSERT, 5, " World");
        op1.setClientId("charlie");
        op1.setBaseVersion(1);
        client.handleLocalOperation(op1);
        offlineManager.enqueueOfflineOp(op1);

        Operation op2 = new Operation(OpType.INSERT, 11, "!");
        op2.setClientId("charlie");
        op2.setBaseVersion(1);
        client.handleLocalOperation(op2);
        offlineManager.enqueueOfflineOp(op2);

        assertEquals(2, offlineManager.getOfflineCount());
        assertEquals("Hello World!", client.getContent());

        // Meanwhile on the server, Alice inserted "[PTIT] " at position 0 (version 1 -> 2)
        List<Operation> missedOps = new ArrayList<>();
        Operation missed1 = new Operation(OpType.INSERT, 0, "[PTIT] ");
        missed1.setClientId("alice");
        missed1.setBaseVersion(1);
        missedOps.add(missed1);

        // Charlie reconnects and calls Catch-up reconciliation
        List<Operation> toFlush = offlineManager.reconcileCatchUp(missedOps, 2, client);

        assertFalse(offlineManager.isOffline(), "Offline state should be cleared after catch-up");
        assertEquals(0, offlineManager.getOfflineCount(), "Queue should be empty after reconciliation");
        assertEquals(2, toFlush.size(), "Should produce 2 transformed operations to flush to server");

        // The local document should have transformed both Alice's insert and Charlie's edits:
        // "[PTIT] " at 0 + "Hello World!" -> "[PTIT] Hello World!"
        assertEquals("[PTIT] Hello World!", client.getContent());
        assertEquals(2, client.getVersion());

        // Transformed operations to flush should have updated positions shifted by "[PTIT] ".length() = 7
        assertEquals(12, toFlush.get(0).getPosition(), "op1 pos (5 + 7 = 12)");
        assertEquals(18, toFlush.get(1).getPosition(), "op2 pos (11 + 7 = 18)");
        assertEquals(2, toFlush.get(0).getBaseVersion(), "Base version updated to current server version");
    }
}
