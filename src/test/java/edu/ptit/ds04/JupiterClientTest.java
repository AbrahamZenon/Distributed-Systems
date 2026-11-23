package edu.ptit.ds04;

import edu.ptit.ds04.client.JupiterClient;
import edu.ptit.ds04.client.JupiterState;
import edu.ptit.ds04.core.OpType;
import edu.ptit.ds04.core.Operation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying Jupiter Client State Machine transitions and OT behavior.
 * Author: Nguyen Duy Phuc (B23DCVT341)
 */
public class JupiterClientTest {

    @Test
    public void testJupiterStateTransitions() {
        JupiterClient client = new JupiterClient("alice", "doc-1", "", 0);
        assertEquals(JupiterState.SYNCHRONIZED, client.getState(), "Should start in SYNCHRONIZED state");

        // 1. First local edit -> transitions to AWAITING_ACK
        Operation toSend1 = client.applyLocalInsert(0, "Hello");
        assertNotNull(toSend1, "First operation should be transmitted immediately");
        assertEquals(JupiterState.AWAITING_ACK, client.getState(), "Should transition to AWAITING_ACK");
        assertEquals("Hello", client.getContent());

        // 2. Second local edit before ACK -> transitions to AWAITING_WITH_BUFFER
        Operation toSend2 = client.applyLocalInsert(5, " World");
        assertNull(toSend2, "Subsequent operation should be buffered, not transmitted");
        assertEquals(JupiterState.AWAITING_WITH_BUFFER, client.getState(), "Should transition to AWAITING_WITH_BUFFER");
        assertEquals(1, client.getBufferOps().size());
        assertEquals("Hello World", client.getContent());

        // 3. Receive ACK for first operation -> transitions to AWAITING_ACK (next op sent)
        Operation nextOp = client.handleServerAck(1);
        assertNotNull(nextOp, "Buffered operation should now be transmitted");
        assertEquals(JupiterState.AWAITING_ACK, client.getState(), "Should transition back to AWAITING_ACK");
        assertEquals(0, client.getBufferOps().size());
        assertEquals(1, client.getVersion());

        // 4. Receive ACK for second operation -> transitions to SYNCHRONIZED
        Operation emptyOp = client.handleServerAck(2);
        assertNull(emptyOp, "No more buffered operations to transmit");
        assertEquals(JupiterState.SYNCHRONIZED, client.getState(), "Should return to SYNCHRONIZED state");
        assertEquals(2, client.getVersion());
    }

    @Test
    public void testRemoteOpTransformationWhileAwaitingAck() {
        JupiterClient client = new JupiterClient("alice", "doc-1", "ABC", 0);

        // Alice locally inserts 'X' at position 1 -> "AXBC"
        Operation aliceOp = client.applyLocalInsert(1, "X");
        assertEquals("AXBC", client.getContent());
        assertEquals(JupiterState.AWAITING_ACK, client.getState());

        // While awaiting ACK, Bob concurrently inserted 'Y' at position 0 on base_version 0
        Operation bobOp = new Operation(OpType.INSERT, 0, "Y");
        bobOp.setClientId("bob");
        bobOp.setBaseVersion(0);

        // Alice receives Bob's remote broadcast (server_version 1)
        client.handleRemoteOperation(bobOp, 1);

        // Expected outcome:
        // Bob inserted 'Y' at 0 -> shifts Alice's view to "YAXBC"
        // Alice's pendingOp should have transformed position from 1 to 2
        assertEquals("YAXBC", client.getContent());
        assertEquals(2, client.getPendingOp().getPosition());

        // Alice receives server ACK for her operation at server_version 2
        client.handleServerAck(2);
        assertEquals(JupiterState.SYNCHRONIZED, client.getState());
        assertEquals("YAXBC", client.getContent());
    }

    @Test
    public void testBufferTransformationWithMultipleRemoteOps() {
        JupiterClient client = new JupiterClient("alice", "doc-1", "HELLO", 0);

        // In-flight op: insert '!' at 5
        client.applyLocalInsert(5, "!");
        // Buffered op: insert ' ' at 5
        client.applyLocalInsert(5, " ");
        assertEquals("HELLO !", client.getContent());
        assertEquals(JupiterState.AWAITING_WITH_BUFFER, client.getState());

        // Concurrent remote op from server: insert "SAY " at 0
        Operation remoteOp = new Operation(OpType.INSERT, 0, "SAY ");
        remoteOp.setClientId("bob");
        remoteOp.setBaseVersion(0);

        client.handleRemoteOperation(remoteOp, 1);
        assertEquals("SAY HELLO !", client.getContent());

        // Server ACK first op
        Operation dequeued = client.handleServerAck(2);
        assertNotNull(dequeued);
        // Server ACK second op
        client.handleServerAck(3);
        assertEquals(JupiterState.SYNCHRONIZED, client.getState());
        assertEquals("SAY HELLO !", client.getContent());
    }
}
