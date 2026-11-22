package edu.ptit.ds04.client;

/**
 * Represents the three canonical states of the Jupiter Collaboration Protocol State Machine:
 * 1. SYNCHRONIZED: Client has no outgoing operations pending server acknowledgment.
 * 2. AWAITING_ACK: Client has sent one operation to the server and is awaiting OP_ACK.
 * 3. AWAITING_WITH_BUFFER: Client is awaiting OP_ACK for an in-flight operation, while
 *                          subsequent local operations have been queued in a local buffer.
 */
public enum JupiterState {
    SYNCHRONIZED,
    AWAITING_ACK,
    AWAITING_WITH_BUFFER
}
