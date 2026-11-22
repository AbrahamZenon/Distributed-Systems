package edu.ptit.ds04.common;

import org.json.JSONObject;

import java.util.UUID;

public class Message {
    private String msgId;
    private String type;
    private String clientId;
    private String docId;
    private int version;
    private long timestamp;
    private JSONObject payload;

    public Message(String type, String clientId, String docId, int version, JSONObject payload) {
        this.msgId = UUID.randomUUID().toString();
        this.type = type;
        this.clientId = clientId != null ? clientId : "";
        this.docId = docId != null ? docId : "";
        this.version = version;
        this.timestamp = System.currentTimeMillis();
        this.payload = payload != null ? payload : new JSONObject();
    }

    public Message(String msgId, String type, String clientId, String docId, int version, long timestamp, JSONObject payload) {
        this.msgId = msgId != null ? msgId : UUID.randomUUID().toString();
        this.type = type;
        this.clientId = clientId != null ? clientId : "";
        this.docId = docId != null ? docId : "";
        this.version = version;
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.payload = payload != null ? payload : new JSONObject();
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();
        json.put("msg_id", msgId);
        json.put("type", type);
        json.put("client_id", clientId);
        json.put("doc_id", docId);
        json.put("version", version);
        json.put("timestamp", timestamp);
        json.put("payload", payload);
        return json;
    }

    public String toJSONString() {
        return toJSONObject().toString();
    }

    public static Message fromJSON(String jsonString) {
        JSONObject obj = new JSONObject(jsonString);
        return new Message(
            obj.optString("msg_id", UUID.randomUUID().toString()),
            obj.optString("type", MessageType.ERROR),
            obj.optString("client_id", ""),
            obj.optString("doc_id", ""),
            obj.optInt("version", 0),
            obj.optLong("timestamp", System.currentTimeMillis()),
            obj.optJSONObject("payload") != null ? obj.getJSONObject("payload") : new JSONObject()
        );
    }

    public String getMsgId() { return msgId; }
    public String getType() { return type; }
    public String getClientId() { return clientId; }
    public String getDocId() { return docId; }
    public int getVersion() { return version; }
    public long getTimestamp() { return timestamp; }
    public JSONObject getPayload() { return payload; }

    @Override
    public String toString() {
        return String.format("<Message type=%s client=%s doc=%s v=%d>", type, clientId, docId, version);
    }
}
