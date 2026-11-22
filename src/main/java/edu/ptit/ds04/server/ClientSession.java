package edu.ptit.ds04.server;

import org.java_websocket.WebSocket;
import org.json.JSONObject;

public class ClientSession {
    private final String clientId;
    private final String userName;
    private final String docId;
    private final WebSocket webSocket;
    private final String role;
    private final String color;
    private volatile long lastHeartbeat;
    private volatile int cursorPosition;

    public ClientSession(String clientId, String userName, String docId, WebSocket webSocket, String role, String color) {
        this.clientId = clientId;
        this.userName = userName != null ? userName : clientId;
        this.docId = docId;
        this.webSocket = webSocket;
        this.role = role != null ? role : "editor";
        this.color = color != null ? color : "#3b82f6";
        this.lastHeartbeat = System.currentTimeMillis();
        this.cursorPosition = 0;
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public void updateCursor(int position) {
        this.cursorPosition = position;
    }

    public JSONObject toPresenceJSONObject() {
        JSONObject obj = new JSONObject();
        obj.put("client_id", clientId);
        obj.put("user_name", userName);
        obj.put("role", role);
        obj.put("color", color);
        obj.put("cursor_position", cursorPosition);
        obj.put("last_heartbeat", lastHeartbeat);
        return obj;
    }

    public String getClientId() { return clientId; }
    public String getUserName() { return userName; }
    public String getDocId() { return docId; }
    public WebSocket getWebSocket() { return webSocket; }
    public String getRole() { return role; }
    public String getColor() { return color; }
    public long getLastHeartbeat() { return lastHeartbeat; }
    public int getCursorPosition() { return cursorPosition; }
}
