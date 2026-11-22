package edu.ptit.ds04.common;

public class MessageType {
    public static final String HELLO = "HELLO";
    public static final String JOIN = "JOIN";
    public static final String JOIN_ACK = "JOIN_ACK";
    public static final String OP_SUBMIT = "OP_SUBMIT";
    public static final String OP_ACK = "OP_ACK";
    public static final String OP_BROADCAST = "OP_BROADCAST";
    public static final String SYNC_REQ = "SYNC_REQ";
    public static final String SYNC_RESP = "SYNC_RESP";
    public static final String PRESENCE = "PRESENCE";
    public static final String CURSOR = "CURSOR";
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    public static final String ERROR = "ERROR";

    private MessageType() {}
}
