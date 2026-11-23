package edu.ptit.ds04.client;

import edu.ptit.ds04.core.OpType;
import edu.ptit.ds04.core.Operation;
import edu.ptit.ds04.core.OperationalTransformation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core Jupiter Client State Machine implementing the Jupiter collaboration protocol.
 * Author: Nguyen Duy Phuc (B23DCVT341)
 *
 * Coordinates local optimistic updates, operation queuing, and bi-directional
 * Operational Transformation (OT) against concurrent remote operations.
 */
public class JupiterClient {
    private final String clientId;
    private final String docId;
    private String content;
    private int version;

    private Operation pendingOp = null;
    private final List<Operation> bufferOps = new ArrayList<>();
    private final Object lock = new Object();

    public JupiterClient(String clientId, String docId, String initialContent, int initialVersion) {
        this.clientId = clientId;
        this.docId = docId;
        this.content = initialContent != null ? initialContent : "";
        this.version = initialVersion;
    }

    public JupiterClient(String clientId, String docId) {
        this(clientId, docId, "", 0);
    }

    /**
     * Determines current state of the Jupiter state machine.
     */
    public JupiterState getState() {
        synchronized (lock) {
            if (pendingOp == null && bufferOps.isEmpty()) {
                return JupiterState.SYNCHRONIZED;
            } else if (pendingOp != null && bufferOps.isEmpty()) {
                return JupiterState.AWAITING_ACK;
            } else {
                return JupiterState.AWAITING_WITH_BUFFER;
            }
        }
    }

    /**
     * Optimistically applies a local insert operation and updates state.
     * Returns the Operation to be transmitted to the server (if in SYNCHRONIZED state),
     * or null if the operation has been buffered (if in AWAITING_ACK or AWAITING_WITH_BUFFER state).
     */
    public Operation applyLocalInsert(int position, String text) {
        synchronized (lock) {
            Operation op = new Operation(OpType.INSERT, position, text);
            op.setClientId(clientId);
            op.setDocId(docId);
            op.setBaseVersion(this.version);
            return handleLocalOperation(op);
        }
    }

    /**
     * Optimistically applies a local delete operation and updates state.
     */
    public Operation applyLocalDelete(int position, int length) {
        synchronized (lock) {
            Operation op = new Operation(OpType.DELETE, position, length);
            op.setClientId(clientId);
            op.setDocId(docId);
            op.setBaseVersion(this.version);
            return handleLocalOperation(op);
        }
    }

    /**
     * Generic handler for applying any local operation optimistically.
     */
    public Operation handleLocalOperation(Operation op) {
        synchronized (lock) {
            // 1. Optimistic Local Update: Apply immediately to local document view
            this.content = OperationalTransformation.applyOperation(this.content, op);

            // 2. State transition
            if (pendingOp == null) {
                // Transition: SYNCHRONIZED -> AWAITING_ACK
                this.pendingOp = op;
                return op; // Transmit immediately to server
            } else {
                // Transition: AWAITING_ACK -> AWAITING_WITH_BUFFER
                // or remains in AWAITING_WITH_BUFFER
                this.bufferOps.add(op);
                return null; // Buffered, do not transmit yet
            }
        }
    }

    /**
     * Handles OP_ACK message from server when the in-flight pendingOp has been accepted.
     * Returns the next Operation to be transmitted from buffer (if any), or null if buffer is empty.
     */
    public Operation handleServerAck(int serverVersion) {
        synchronized (lock) {
            this.version = Math.max(this.version, serverVersion);
            this.pendingOp = null;

            if (!bufferOps.isEmpty()) {
                // Transition: AWAITING_WITH_BUFFER -> AWAITING_ACK (or remains with smaller buffer)
                Operation nextOp = bufferOps.remove(0);
                nextOp.setBaseVersion(this.version);
                this.pendingOp = nextOp;
                return nextOp;
            } else {
                // Transition: AWAITING_ACK -> SYNCHRONIZED
                return null;
            }
        }
    }

    /**
     * Handles incoming remote operation broadcast from server.
     * Transforms remote operation against pendingOp and all bufferOps,
     * updates local document text and advances document revision.
     */
    public Operation handleRemoteOperation(Operation remoteOp, int remoteVersion) {
        synchronized (lock) {
            this.version = Math.max(this.version, remoteVersion);
            Operation currRemote = remoteOp;

            // 1. Transform against in-flight pending operation (if any)
            if (pendingOp != null) {
                OperationalTransformation.TransformResult tr =
                    OperationalTransformation.transform(currRemote, pendingOp, true);
                currRemote = tr.op1Prime;
                pendingOp = tr.op2Prime;
            }

            // 2. Transform against each buffered operation sequentially
            List<Operation> transformedBuffer = new ArrayList<>();
            for (Operation bOp : bufferOps) {
                OperationalTransformation.TransformResult tr =
                    OperationalTransformation.transform(currRemote, bOp, true);
                currRemote = tr.op1Prime;
                transformedBuffer.add(tr.op2Prime);
            }
            bufferOps.clear();
            bufferOps.addAll(transformedBuffer);

            // 3. Apply transformed remote operation to local document text
            this.content = OperationalTransformation.applyOperation(this.content, currRemote);
            return currRemote;
        }
    }

    /**
     * Reset or synchronize local snapshot state (e.g. upon JOIN_ACK or full reload).
     */
    public void setSnapshot(String newContent, int newVersion) {
        synchronized (lock) {
            this.content = newContent != null ? newContent : "";
            this.version = newVersion;
            this.pendingOp = null;
            this.bufferOps.clear();
        }
    }

    public String getContent() {
        synchronized (lock) {
            return content;
        }
    }

    public int getVersion() {
        synchronized (lock) {
            return version;
        }
    }

    public void setVersion(int version) {
        synchronized (lock) {
            this.version = version;
        }
    }

    public Operation getPendingOp() {
        synchronized (lock) {
            return pendingOp;
        }
    }

    public List<Operation> getBufferOps() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(bufferOps));
        }
    }

    public String getClientId() {
        return clientId;
    }

    public String getDocId() {
        return docId;
    }

    private static boolean hasPriority(Operation op1, Operation op2) {
        if (op1 == null || op2 == null) return true;
        String id1 = op1.getClientId();
        String id2 = op2.getClientId();
        if (id1 != null && id2 != null && !id1.equals(id2)) {
            return id1.compareTo(id2) < 0;
        }
        return true;
    }
}
