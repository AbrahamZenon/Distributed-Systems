package edu.ptit.ds04.client;

import edu.ptit.ds04.core.Operation;
import edu.ptit.ds04.core.OperationalTransformation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages client offline state, operation queuing, and Catch-up Reconnect Synchronization.
 * Author: Nguyen Duy Phuc (B23DCVT341)
 *
 * When network disconnects, local edits are held in an append-only offline queue.
 * Upon reconnection, the manager requests missed server operations via SYNC_REQ,
 * transforms local offline operations against the missed remote operations,
 * and flushes them to the server sequentially.
 */
public class OfflineManager {
    private volatile boolean offline = false;
    private final List<Operation> offlineQueue = new ArrayList<>();
    private final Object lock = new Object();
    private Consumer<Boolean> onConnectionStateChangeListener;

    public OfflineManager() {}

    public boolean isOffline() {
        return offline;
    }

    public void setOffline(boolean isOffline) {
        synchronized (lock) {
            boolean changed = (this.offline != isOffline);
            this.offline = isOffline;
            if (changed && onConnectionStateChangeListener != null) {
                onConnectionStateChangeListener.accept(isOffline);
            }
        }
    }

    public void setOnConnectionStateChangeListener(Consumer<Boolean> listener) {
        this.onConnectionStateChangeListener = listener;
    }

    /**
     * Enqueues an operation generated while in offline mode.
     */
    public void enqueueOfflineOp(Operation op) {
        synchronized (lock) {
            offlineQueue.add(op);
        }
    }

    public List<Operation> getOfflineQueue() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(offlineQueue));
        }
    }

    public int getOfflineCount() {
        synchronized (lock) {
            return offlineQueue.size();
        }
    }

    public void clear() {
        synchronized (lock) {
            offlineQueue.clear();
        }
    }

    /**
     * Executes Catch-Up Synchronization when reconnecting.
     * Transforms local offline queue operations against missed server operations,
     * updates the Jupiter client's document text, and returns the re-based operations
     * ready to be sent to the server.
     *
     * @param missedOps Operations applied on server during the offline period
     * @param serverVersion Latest authoritative version returned by server
     * @param jupiterClient The local Jupiter client instance
     * @return List of transformed offline operations to transmit to server
     */
    public List<Operation> reconcileCatchUp(List<Operation> missedOps, int serverVersion, JupiterClient jupiterClient) {
        synchronized (lock) {
            if (missedOps != null && !missedOps.isEmpty()) {
                for (Operation missedOp : missedOps) {
                    Operation currMissed = missedOp;
                    List<Operation> transformedOffline = new ArrayList<>();

                    for (Operation offlineOp : offlineQueue) {
                        OperationalTransformation.TransformResult tr =
                            OperationalTransformation.transform(currMissed, offlineOp, true);
                        currMissed = tr.op1Prime;
                        transformedOffline.add(tr.op2Prime);
                    }

                    offlineQueue.clear();
                    offlineQueue.addAll(transformedOffline);

                    // Apply transformed remote operation to local document content
                    jupiterClient.handleRemoteOperation(currMissed, currMissed.getBaseVersion() + 1);
                }
            }

            // Align base version with server version
            jupiterClient.setVersion(serverVersion);

            // Re-base all offline operations to the synchronized server version
            List<Operation> toFlush = new ArrayList<>();
            for (Operation op : offlineQueue) {
                op.setBaseVersion(serverVersion);
                toFlush.add(op);
            }

            offlineQueue.clear();
            this.offline = false;
            return toFlush;
        }
    }
}
