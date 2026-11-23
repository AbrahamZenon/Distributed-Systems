package edu.ptit.ds04.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Operation {
    private String opId;
    private String opType;
    private int position;
    private String text;
    private int length;
    private String clientId;
    private String docId;
    private int baseVersion;
    private Integer serverVersion;
    private long timestamp;
    private List<Operation> subOps;

    public Operation(String opType, int position, String text, int length,
                     String clientId, String docId, int baseVersion, Integer serverVersion,
                     String opId, long timestamp, List<Operation> subOps) {
        this.opId = opId != null ? opId : UUID.randomUUID().toString();
        this.opType = opType;
        this.position = position;
        this.text = text != null ? text : "";
        this.length = length >= 0 ? length : (text != null ? text.length() : 0);
        this.clientId = clientId != null ? clientId : "";
        this.docId = docId != null ? docId : "";
        this.baseVersion = baseVersion;
        this.serverVersion = serverVersion;
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.subOps = subOps;
    }

    public Operation(String opType, int position, String text) {
        this(opType, position, text, text != null ? text.length() : 0, "", "", 0, null, null, 0, null);
    }

    public Operation(String opType, int position, int length) {
        this(opType, position, "", length, "", "", 0, null, null, 0, null);
    }

    public Operation clone() {
        List<Operation> clonedSubs = null;
        if (this.subOps != null) {
            clonedSubs = new ArrayList<>();
            for (Operation s : this.subOps) {
                clonedSubs.add(s.clone());
            }
        }
        return new Operation(opType, position, text, length, clientId, docId, baseVersion, serverVersion, opId, timestamp, clonedSubs);
    }

    public JSONObject toJSONObject() {
        JSONObject json = new JSONObject();
        json.put("op_id", opId);
        json.put("op_type", opType);
        json.put("position", position);
        json.put("text", text);
        json.put("length", length);
        json.put("client_id", clientId);
        json.put("doc_id", docId);
        json.put("base_version", baseVersion);
        if (serverVersion != null) {
            json.put("server_version", serverVersion);
        }
        json.put("timestamp", timestamp);
        if (subOps != null && !subOps.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (Operation sub : subOps) {
                arr.put(sub.toJSONObject());
            }
            json.put("sub_ops", arr);
        }
        return json;
    }

    public static Operation fromJSONObject(JSONObject json) {
        List<Operation> subs = null;
        if (json.has("sub_ops")) {
            JSONArray arr = json.getJSONArray("sub_ops");
            subs = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                subs.add(fromJSONObject(arr.getJSONObject(i)));
            }
        }
        Integer serverVer = json.has("server_version") && !json.isNull("server_version") ? json.getInt("server_version") : null;
        String resolvedOpType = json.optString("op_type", json.optString("type", OpType.NOOP));
        return new Operation(
            resolvedOpType,
            json.optInt("position", 0),
            json.optString("text", ""),
            json.optInt("length", 0),
            json.optString("client_id", ""),
            json.optString("doc_id", ""),
            json.optInt("base_version", 0),
            serverVer,
            json.optString("op_id", UUID.randomUUID().toString()),
            json.optLong("timestamp", System.currentTimeMillis()),
            subs
        );
    }

    public String getOpId() { return opId; }
    public String getOpType() { return opType; }
    public void setOpType(String opType) { this.opType = opType; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public int getBaseVersion() { return baseVersion; }
    public void setBaseVersion(int baseVersion) { this.baseVersion = baseVersion; }
    public Integer getServerVersion() { return serverVersion; }
    public void setServerVersion(Integer serverVersion) { this.serverVersion = serverVersion; }
    public long getTimestamp() { return timestamp; }
    public List<Operation> getSubOps() { return subOps; }
    public void setSubOps(List<Operation> subOps) { this.subOps = subOps; }

    @Override
    public String toString() {
        return String.format("Op(%s @%d len=%d text='%s' v=%d->%s by %s)",
            opType, position, length, text, baseVersion, serverVersion, clientId);
    }
}
