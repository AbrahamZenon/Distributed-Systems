package edu.ptit.ds04.server;

import edu.ptit.ds04.common.Config;
import edu.ptit.ds04.common.Message;
import edu.ptit.ds04.common.MessageType;
import edu.ptit.ds04.common.SecurityUtil;
import edu.ptit.ds04.core.Operation;
import edu.ptit.ds04.storage.StorageEngine;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CollaborationServer extends WebSocketServer {
    private final DocumentManager docManager;
    private final SessionManager sessionManager;
    private final Map<WebSocket, String> connToClientId = new ConcurrentHashMap<>();
    private final Map<WebSocket, String> connToDocId = new ConcurrentHashMap<>();
    private Thread heartbeatThread;
    private com.sun.net.httpserver.HttpServer httpServer;
    private volatile boolean running = false;

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public CollaborationServer(String host, int port, String storageDir) {
        super(new InetSocketAddress(host, port));
        setReuseAddr(true);
        StorageEngine storage = new StorageEngine(storageDir);
        this.docManager = new DocumentManager(storage);
        this.sessionManager = new SessionManager();
    }

    public CollaborationServer() {
        this(Config.SERVER_HOST, Config.SERVER_PORT, Config.STORAGE_DIR);
    }

    private static void log(String eventType, String msg) {
        System.out.printf("[NODE:SERVER | %s | %s] %s%n",
            LocalDateTime.now().format(LOG_TIME), eventType, msg);
    }

    @Override
    public void onStart() {
        this.running = true;
        log("SERVER_STARTED", "Listening on ws://" + getAddress().getHostString() + ":" + getPort());
        startHeartbeatSweep();
        startHttpServer();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String remote = conn.getRemoteSocketAddress() != null ? conn.getRemoteSocketAddress().toString() : "unknown";
        log("EVENT:CONNECT", "Inbound connection from " + remote);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String clientId = connToClientId.remove(conn);
        String docId = connToDocId.remove(conn);
        if (clientId != null) {
            sessionManager.unregister(clientId);
            log("EVENT:DISCONNECT", "Client disconnected: " + clientId + " (code=" + code + ")");
            if (docId != null) {
                broadcastPresence(docId, "Client " + clientId + " disconnected", clientId);
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (!SecurityUtil.validatePayloadSize(message)) {
            log("EVENT:SECURITY_REJECT", "Payload exceeded maximum allowed size");
            sendError(conn, "PAYLOAD_TOO_LARGE", "Message payload exceeded maximum allowed size");
            return;
        }

        Message msg;
        try {
            msg = Message.fromJSON(message);
        } catch (Exception e) {
            log("EVENT:PROTOCOL_ERROR", "Malformed JSON: " + e.getMessage());
            sendError(conn, "MALFORMED_JSON", e.getMessage());
            return;
        }

        switch (msg.getType()) {
            case MessageType.JOIN:
                handleJoin(conn, msg);
                break;
            case MessageType.OP_SUBMIT:
                handleOpSubmit(conn, msg);
                break;
            case MessageType.SYNC_REQ:
                handleSyncReq(conn, msg);
                break;
            case MessageType.CURSOR:
                handleCursor(conn, msg);
                break;
            case MessageType.PING:
                handlePing(conn, msg);
                break;
            default:
                log("EVENT:UNKNOWN_MSG", "Unrecognized message type: " + msg.getType());
                sendError(conn, "UNKNOWN_TYPE", "Unrecognized message type: " + msg.getType());
                break;
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log("EVENT:ERROR", "WebSocket error: " + (ex != null ? ex.getMessage() : "null"));
    }

    private void handleJoin(WebSocket conn, Message msg) {
        String docId = msg.getDocId();
        JSONObject payload = msg.getPayload();
        String token = payload.optString("token", "");
        String userName = payload.optString("user_name", msg.getClientId());
        String color = payload.optString("color", "#3b82f6");

        if (docId == null || docId.trim().isEmpty()) {
            sendError(conn, "INVALID_DOC_ID", "Document ID is required");
            return;
        }

        SecurityUtil.AuthResult auth = SecurityUtil.verifyToken(token, Config.AUTH_TOKEN_SECRET);
        if (!auth.valid) {
            log("EVENT:AUTH_FAIL", "Unauthorized join attempt for client: " + msg.getClientId());
            sendError(conn, "AUTH_FAILED", "Invalid authentication token");
            return;
        }

        String clientId = !msg.getClientId().isEmpty() ? msg.getClientId() : auth.clientId;
        ClientSession session = new ClientSession(clientId, userName, docId, conn, auth.role, color);
        sessionManager.register(session);
        connToClientId.put(conn, clientId);
        connToDocId.put(conn, docId);

        DocumentState doc = docManager.getOrLoadDocument(docId);
        log("EVENT:USER_JOIN", String.format("client_id='%s' joined doc_id='%s' role='%s' current_v=%d",
            clientId, docId, auth.role, doc.getVersion()));

        // Reply JOIN_ACK
        JSONArray activeUsers = sessionManager.getPresenceList(docId);
        JSONObject ackPayload = new JSONObject();
        ackPayload.put("content", doc.getContent());
        ackPayload.put("version", doc.getVersion());
        ackPayload.put("role", auth.role);
        ackPayload.put("active_users", activeUsers);

        Message ack = new Message(MessageType.JOIN_ACK, clientId, docId, doc.getVersion(), ackPayload);
        safeSend(conn, ack.toJSONString());

        // Broadcast PRESENCE to all other clients in document
        broadcastPresence(docId, "User '" + userName + "' joined", clientId);
    }

    private void handleOpSubmit(WebSocket conn, Message msg) {
        String clientId = msg.getClientId();
        String docId = msg.getDocId();
        int baseVersion = msg.getVersion();
        JSONObject payload = msg.getPayload();

        ClientSession session = sessionManager.getSession(clientId);
        if (session == null || "viewer".equalsIgnoreCase(session.getRole())) {
            log("EVENT:PERMISSION_DENIED", "Client '" + clientId + "' does not have editor permission");
            sendError(conn, "PERMISSION_DENIED", "Only editors may submit operations");
            return;
        }
        session.updateHeartbeat();

        if (!SecurityUtil.sanitizeOperationPayload(payload)) {
            log("EVENT:INVALID_OP", "Client '" + clientId + "' submitted invalid operation payload");
            sendError(conn, "INVALID_OPERATION", "Operation payload failed schema validation");
            return;
        }

        Operation op = Operation.fromJSONObject(payload);
        op.setClientId(clientId);
        op.setDocId(docId);
        op.setBaseVersion(baseVersion);

        DocumentState doc = docManager.getOrLoadDocument(docId);
        synchronized (doc) {
            DocumentManager.ApplyOpResult result = docManager.applyClientOp(docId, clientId, baseVersion, op);

            log("EVENT:OP_APPLIED", String.format("doc_id='%s' client_id='%s' base_v=%d->server_v=%d op=%s pos=%d len=%d",
                docId, clientId, baseVersion, result.newVersion, result.transformedOp.getOpType(),
                result.transformedOp.getPosition(), result.updatedContent.length()));

            // Send OP_ACK to author
            JSONObject ackPayload = new JSONObject();
            ackPayload.put("op_id", op.getOpId());
            ackPayload.put("server_version", result.newVersion);
            Message ack = new Message(MessageType.OP_ACK, clientId, docId, result.newVersion, ackPayload);
            safeSend(conn, ack.toJSONString());

            // Broadcast OP_BROADCAST to other editors
            Message broadcast = new Message(MessageType.OP_BROADCAST, clientId, docId, result.newVersion, result.transformedOp.toJSONObject());
            broadcastToOthers(docId, clientId, broadcast.toJSONString());
        }
    }

    private void handleSyncReq(WebSocket conn, Message msg) {
        String docId = msg.getDocId();
        int fromVersion = msg.getPayload().optInt("from_version", 0);
        String clientId = msg.getClientId();

        DocumentManager.HistorySyncResult res = docManager.getHistorySince(docId, fromVersion);
        log("EVENT:SYNC_SERVED", String.format("client_id='%s' doc_id='%s' from_v=%d served %d ops up to v=%d",
            clientId, docId, fromVersion, res.operations.size(), res.currentVersion));

        JSONArray opsArr = new JSONArray();
        for (Operation o : res.operations) {
            opsArr.put(o.toJSONObject());
        }

        JSONObject respPayload = new JSONObject();
        respPayload.put("from_version", fromVersion);
        respPayload.put("current_version", res.currentVersion);
        respPayload.put("content", res.content);
        respPayload.put("operations", opsArr);

        Message resp = new Message(MessageType.SYNC_RESP, clientId, docId, res.currentVersion, respPayload);
        safeSend(conn, resp.toJSONString());
    }

    private void handleCursor(WebSocket conn, Message msg) {
        String clientId = msg.getClientId();
        String docId = msg.getDocId();
        int pos = msg.getPayload().optInt("cursor_position", 0);

        ClientSession session = sessionManager.getSession(clientId);
        if (session != null) {
            session.updateCursor(pos);
            session.updateHeartbeat();

            JSONObject curPayload = new JSONObject();
            curPayload.put("client_id", clientId);
            curPayload.put("user_name", session.getUserName());
            curPayload.put("color", session.getColor());
            curPayload.put("cursor_position", pos);

            Message curMsg = new Message(MessageType.CURSOR, clientId, docId, 0, curPayload);
            broadcastToOthers(docId, clientId, curMsg.toJSONString());
        }
    }

    private void handlePing(WebSocket conn, Message msg) {
        String clientId = msg.getClientId();
        ClientSession session = sessionManager.getSession(clientId);
        if (session != null) {
            session.updateHeartbeat();
        }
        JSONObject pongPayload = new JSONObject();
        pongPayload.put("server_time", System.currentTimeMillis());
        Message pong = new Message(MessageType.PONG, clientId, msg.getDocId(), 0, pongPayload);
        safeSend(conn, pong.toJSONString());
    }

    private void broadcastToOthers(String docId, String senderClientId, String jsonText) {
        List<ClientSession> subscribers = sessionManager.getSubscribers(docId);
        for (ClientSession s : subscribers) {
            if (!s.getClientId().equals(senderClientId)) {
                safeSend(s.getWebSocket(), jsonText);
            }
        }
    }

    private void broadcastPresence(String docId, String reason, String excludeClientId) {
        JSONArray users = sessionManager.getPresenceList(docId);
        JSONObject payload = new JSONObject();
        payload.put("users", users);
        payload.put("reason", reason != null ? reason : "");

        Message msg = new Message(MessageType.PRESENCE, "", docId, 0, payload);
        String jsonText = msg.toJSONString();

        List<ClientSession> subscribers = sessionManager.getSubscribers(docId);
        for (ClientSession s : subscribers) {
            if (excludeClientId == null || !s.getClientId().equals(excludeClientId)) {
                safeSend(s.getWebSocket(), jsonText);
            }
        }
    }

    private void sendError(WebSocket conn, String code, String message) {
        JSONObject payload = new JSONObject();
        payload.put("code", code);
        payload.put("error", message);
        Message err = new Message(MessageType.ERROR, "", "", 0, payload);
        safeSend(conn, err.toJSONString());
    }

    private void safeSend(WebSocket conn, String text) {
        if (conn != null && conn.isOpen()) {
            try {
                conn.send(text);
            } catch (Exception ignored) {}
        }
    }

    private void startHeartbeatSweep() {
        heartbeatThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(Config.HEARTBEAT_INTERVAL_MS);
                    List<ClientSession> timedOut = sessionManager.checkTimeouts(Config.HEARTBEAT_TIMEOUT_MS);
                    for (ClientSession s : timedOut) {
                        log("EVENT:HEARTBEAT_TIMEOUT", "Client timed out: " + s.getClientId());
                        sessionManager.unregister(s.getClientId());
                        try {
                            s.getWebSocket().close(1000, "Heartbeat timeout");
                        } catch (Exception ignored) {}
                        broadcastPresence(s.getDocId(), "Client " + s.getClientId() + " timed out", null);
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    log("EVENT:SWEEP_ERROR", "Error in heartbeat sweep: " + e.getMessage());
                }
            }
        }, "HeartbeatSweepThread");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void startHttpServer() {
        int[] ports = {8080, 8766, 8888};
        for (int p : ports) {
            try {
                httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(p), 0);
                httpServer.createContext("/", exchange -> {
                    String path = exchange.getRequestURI().getPath();
                    if ("/".equals(path) || path == null || path.isEmpty()) path = "/index.html";
                    java.io.File file = new java.io.File("src/main/resources/web" + path);
                    if (!file.exists()) {
                        byte[] notFound = "404 Not Found".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(404, notFound.length);
                        exchange.getResponseBody().write(notFound);
                        exchange.getResponseBody().close();
                        return;
                    }
                    String contentType = "text/html";
                    if (path.endsWith(".css")) contentType = "text/css";
                    else if (path.endsWith(".js")) contentType = "application/javascript";
                    else if (path.endsWith(".svg")) contentType = "image/svg+xml";

                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                    exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.getResponseBody().close();
                });
                httpServer.start();
                log("WEB_UI", "Interactive Web UI available at: http://localhost:" + p + "/?user=Phuc");
                break;
            } catch (Exception ignored) {}
        }
    }

    public DocumentManager getDocumentManager() {
        return docManager;
    }

    @Override
    public void stop() throws InterruptedException {
        this.running = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        if (httpServer != null) {
            try {
                httpServer.stop(0);
            } catch (Exception ignored) {}
        }
        super.stop(1000);
        log("SERVER_STOPPED", "Collaboration server stopped cleanly.");
    }

    public static void main(String[] args) {
        CollaborationServer server = new CollaborationServer();
        server.start();
        log("MAIN", "Press Ctrl+C to terminate server.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception ignored) {}
        }));
    }
}
