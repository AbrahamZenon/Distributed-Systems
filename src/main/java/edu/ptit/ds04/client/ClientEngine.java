package edu.ptit.ds04.client;

import edu.ptit.ds04.common.Message;
import edu.ptit.ds04.common.MessageType;
import edu.ptit.ds04.core.OpType;
import edu.ptit.ds04.core.Operation;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * WebSocket Collaborative Client Engine powered by Jupiter State Machine and Offline Sync.
 * Refactored & Enhanced by: Nguyen Duy Phuc (B23DCVT341)
 */
public class ClientEngine extends WebSocketClient {
    private final String docId;
    private final String clientId;
    private final String userName;
    private final String token;
    private final String color;

    private final JupiterClient jupiter;
    private final OfflineManager offlineManager;
    private final List<JSONObject> activeUsers = new ArrayList<>();
    private final Object lock = new Object();

    private BiConsumer<String, Integer> onChangeCallback;
    private CountDownLatch joinLatch = new CountDownLatch(1);
    private CountDownLatch syncLatch = null;

    public ClientEngine(URI serverUri, String docId, String clientId, String userName, String token, String color) {
        super(serverUri);
        this.docId = docId;
        this.clientId = clientId;
        this.userName = userName != null ? userName : clientId;
        this.token = token != null ? token : "token-" + clientId;
        this.color = color != null ? color : "#3b82f6";
        this.jupiter = new JupiterClient(clientId, docId);
        this.offlineManager = new OfflineManager();
    }

    public ClientEngine(String serverUri, String docId, String clientId, String userName) throws Exception {
        this(new URI(serverUri), docId, clientId, userName, null, null);
    }

    public void setOnChangeCallback(BiConsumer<String, Integer> callback) {
        this.onChangeCallback = callback;
    }

    public JupiterClient getJupiter() {
        return jupiter;
    }

    public OfflineManager getOfflineManager() {
        return offlineManager;
    }

    public JupiterState getJupiterState() {
        return jupiter.getState();
    }

    public void setSimulatedOffline(boolean simulatedOffline) {
        offlineManager.setOffline(simulatedOffline);
        if (!simulatedOffline && isOpen()) {
            requestSync(jupiter.getVersion());
        }
    }

    public boolean connectAndJoin(long timeoutSec) throws InterruptedException {
        boolean connected = connectBlocking(timeoutSec, TimeUnit.SECONDS);
        if (!connected) return false;

        // Send JOIN message
        JSONObject payload = new JSONObject();
        payload.put("token", token);
        payload.put("user_name", userName);
        payload.put("color", color);

        Message joinMsg = new Message(MessageType.JOIN, clientId, docId, 0, payload);
        send(joinMsg.toJSONString());

        boolean ok = joinLatch.await(timeoutSec, TimeUnit.SECONDS);
        if (ok) {
            offlineManager.setOffline(false);
        }
        return ok;
    }

    public void requestSync(int fromVersion) {
        syncLatch = new CountDownLatch(1);
        JSONObject payload = new JSONObject();
        payload.put("from_version", fromVersion);
        Message msg = new Message(MessageType.SYNC_REQ, clientId, docId, fromVersion, payload);
        send(msg.toJSONString());
    }

    public boolean awaitSync(long timeoutSec) throws InterruptedException {
        if (syncLatch != null) {
            return syncLatch.await(timeoutSec, TimeUnit.SECONDS);
        }
        return true;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("[CLIENT:" + userName + "] Connected to server.");
    }

    @Override
    public void onMessage(String message) {
        try {
            Message msg = Message.fromJSON(message);
            switch (msg.getType()) {
                case MessageType.JOIN_ACK:
                    handleJoinAck(msg);
                    break;
                case MessageType.OP_ACK:
                    handleOpAck(msg);
                    break;
                case MessageType.OP_BROADCAST:
                    handleRemoteOp(msg);
                    break;
                case MessageType.SYNC_RESP:
                    handleSyncResp(msg);
                    break;
                case MessageType.PRESENCE:
                    handlePresence(msg);
                    break;
                case MessageType.CURSOR:
                    break;
                case MessageType.ERROR:
                    System.err.println("[CLIENT:" + userName + "] ERROR: " + msg.getPayload().optString("error"));
                    break;
            }
        } catch (Exception e) {
            System.err.println("[CLIENT:" + userName + "] Message error: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[CLIENT:" + userName + "] Connection closed (code=" + code + ")");
        offlineManager.setOffline(true);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[CLIENT:" + userName + "] Error: " + (ex != null ? ex.getMessage() : "null"));
    }

    private void handleJoinAck(Message msg) {
        synchronized (lock) {
            int ver = msg.getPayload().optInt("version", 0);
            String txt = msg.getPayload().optString("content", "");
            jupiter.setSnapshot(txt, ver);

            JSONArray users = msg.getPayload().optJSONArray("active_users");
            this.activeUsers.clear();
            if (users != null) {
                for (int i = 0; i < users.length(); i++) {
                    this.activeUsers.add(users.getJSONObject(i));
                }
            }
            joinLatch.countDown();
            System.out.println("[CLIENT:" + userName + "] Joined doc '" + docId + "' at v=" + ver + ". Text: '" + txt + "'");

            if (onChangeCallback != null) {
                onChangeCallback.accept(jupiter.getContent(), jupiter.getVersion());
            }
        }
    }

    private void handleOpAck(Message msg) {
        synchronized (lock) {
            int serverVer = msg.getPayload().getInt("server_version");
            Operation nextOp = jupiter.handleServerAck(serverVer);

            if (nextOp != null) {
                Message submitMsg = new Message(MessageType.OP_SUBMIT, clientId, docId, jupiter.getVersion(), nextOp.toJSONObject());
                send(submitMsg.toJSONString());
            }

            if (onChangeCallback != null) {
                onChangeCallback.accept(jupiter.getContent(), jupiter.getVersion());
            }
        }
    }

    private void handleRemoteOp(Message msg) {
        synchronized (lock) {
            Operation remoteOp = Operation.fromJSONObject(msg.getPayload());
            jupiter.handleRemoteOperation(remoteOp, msg.getVersion());
            System.out.printf("[CLIENT:%s] Remote op applied (v=%d): '%s'%n",
                userName, jupiter.getVersion(), jupiter.getContent());

            if (onChangeCallback != null) {
                onChangeCallback.accept(jupiter.getContent(), jupiter.getVersion());
            }
        }
    }

    private void handleSyncResp(Message msg) {
        synchronized (lock) {
            JSONObject payload = msg.getPayload();
            int currentVer = payload.optInt("current_version", msg.getVersion());
            JSONArray opsArr = payload.optJSONArray("operations");
            List<Operation> missedOps = new ArrayList<>();
            if (opsArr != null) {
                for (int i = 0; i < opsArr.length(); i++) {
                    missedOps.add(Operation.fromJSONObject(opsArr.getJSONObject(i)));
                }
            }

            List<Operation> toFlush = offlineManager.reconcileCatchUp(missedOps, currentVer, jupiter);
            System.out.printf("[CLIENT:%s] Catch-up sync completed! Reconciled %d missed ops. Flushing %d offline ops...%n",
                userName, missedOps.size(), toFlush.size());

            // Flush offline ops sequentially through Jupiter
            for (Operation offOp : toFlush) {
                Operation toSend = jupiter.handleLocalOperation(offOp);
                if (toSend != null) {
                    Message submitMsg = new Message(MessageType.OP_SUBMIT, clientId, docId, jupiter.getVersion(), toSend.toJSONObject());
                    send(submitMsg.toJSONString());
                }
            }

            if (syncLatch != null) {
                syncLatch.countDown();
            }

            if (onChangeCallback != null) {
                onChangeCallback.accept(jupiter.getContent(), jupiter.getVersion());
            }
        }
    }

    private void handlePresence(Message msg) {
        synchronized (lock) {
            JSONArray users = msg.getPayload().optJSONArray("users");
            activeUsers.clear();
            if (users != null) {
                for (int i = 0; i < users.length(); i++) {
                    activeUsers.add(users.getJSONObject(i));
                }
            }
        }
    }

    public void submitInsert(int position, String text) {
        synchronized (lock) {
            if (offlineManager.isOffline() || !isOpen()) {
                Operation op = new Operation(OpType.INSERT, position, text);
                op.setClientId(clientId);
                op.setDocId(docId);
                op.setBaseVersion(jupiter.getVersion());

                // Optimistically apply locally
                jupiter.handleLocalOperation(op);
                offlineManager.enqueueOfflineOp(op);

                System.out.printf("[CLIENT:%s][OFFLINE] Insert queued (buffer=%d): '%s'%n",
                    userName, offlineManager.getOfflineCount(), jupiter.getContent());
                if (onChangeCallback != null) {
                    onChangeCallback.accept(jupiter.getContent(), jupiter.getVersion());
                }
            } else {
                Operation toSend = jupiter.applyLocalInsert(position, text);
                if (toSend != null) {
                    Message msg = new Message(MessageType.OP_SUBMIT, clientId, docId, jupiter.getVersion(), toSend.toJSONObject());
                    send(msg.toJSONString());
                }
                if (onChangeCallback != null) {
                    onChangeCallback.accept(jupiter.getContent(), jupiter.getVersion());
                }
            }
        }
    }

    public void submitDelete(int position, int length) {
        synchronized (lock) {
            if (offlineManager.isOffline() || !isOpen()) {
                Operation op = new Operation(OpType.DELETE, position, length);
                op.setClientId(clientId);
                op.setDocId(docId);
                op.setBaseVersion(jupiter.getVersion());

                jupiter.handleLocalOperation(op);
                offlineManager.enqueueOfflineOp(op);

                System.out.printf("[CLIENT:%s][OFFLINE] Delete queued (buffer=%d): '%s'%n",
                    userName, offlineManager.getOfflineCount(), jupiter.getContent());
                if (onChangeCallback != null) {
                    onChangeCallback.accept(jupiter.getContent(), jupiter.getVersion());
                }
            } else {
                Operation toSend = jupiter.applyLocalDelete(position, length);
                if (toSend != null) {
                    Message msg = new Message(MessageType.OP_SUBMIT, clientId, docId, jupiter.getVersion(), toSend.toJSONObject());
                    send(msg.toJSONString());
                }
                if (onChangeCallback != null) {
                    onChangeCallback.accept(jupiter.getContent(), jupiter.getVersion());
                }
            }
        }
    }

    public void sendCursor(int position) {
        if (isOpen()) {
            JSONObject payload = new JSONObject();
            payload.put("cursor_position", position);
            Message msg = new Message(MessageType.CURSOR, clientId, docId, 0, payload);
            send(msg.toJSONString());
        }
    }

    public String getContent() {
        return jupiter.getContent();
    }

    public int getVersion() {
        return jupiter.getVersion();
    }

    public List<JSONObject> getActiveUsers() {
        synchronized (lock) {
            return new ArrayList<>(activeUsers);
        }
    }
}
