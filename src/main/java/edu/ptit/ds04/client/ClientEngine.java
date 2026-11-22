package edu.ptit.ds04.client;

import edu.ptit.ds04.common.Message;
import edu.ptit.ds04.common.MessageType;
import edu.ptit.ds04.core.OpType;
import edu.ptit.ds04.core.Operation;
import edu.ptit.ds04.core.OperationalTransformation;
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

public class ClientEngine extends WebSocketClient {
    private final String docId;
    private final String clientId;
    private final String userName;
    private final String token;
    private final String color;

    private int version = 0;
    private String content = "";
    private final List<JSONObject> activeUsers = new ArrayList<>();
    private final Object lock = new Object();

    private Operation pendingOp = null;
    private final List<Operation> bufferOps = new ArrayList<>();

    private BiConsumer<String, Integer> onChangeCallback;
    private CountDownLatch joinLatch = new CountDownLatch(1);

    public ClientEngine(URI serverUri, String docId, String clientId, String userName, String token, String color) {
        super(serverUri);
        this.docId = docId;
        this.clientId = clientId;
        this.userName = userName != null ? userName : clientId;
        this.token = token != null ? token : "token-" + clientId;
        this.color = color != null ? color : "#3b82f6";
    }

    public ClientEngine(String serverUri, String docId, String clientId, String userName) throws Exception {
        this(new URI(serverUri), docId, clientId, userName, null, null);
    }

    public void setOnChangeCallback(BiConsumer<String, Integer> callback) {
        this.onChangeCallback = callback;
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

        return joinLatch.await(timeoutSec, TimeUnit.SECONDS);
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
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[CLIENT:" + userName + "] Error: " + (ex != null ? ex.getMessage() : "null"));
    }

    private void handleJoinAck(Message msg) {
        synchronized (lock) {
            this.version = msg.getPayload().optInt("version", 0);
            this.content = msg.getPayload().optString("content", "");
            JSONArray users = msg.getPayload().optJSONArray("active_users");
            this.activeUsers.clear();
            if (users != null) {
                for (int i = 0; i < users.length(); i++) {
                    this.activeUsers.add(users.getJSONObject(i));
                }
            }
            joinLatch.countDown();
            System.out.println("[CLIENT:" + userName + "] Joined doc '" + docId + "' at v=" + version + ". Text: '" + content + "'");
        }
    }

    private void handleOpAck(Message msg) {
        synchronized (lock) {
            int serverVer = msg.getPayload().getInt("server_version");
            this.version = serverVer;
            this.pendingOp = null;

            if (!bufferOps.isEmpty()) {
                Operation nextOp = bufferOps.remove(0);
                nextOp.setBaseVersion(this.version);
                this.pendingOp = nextOp;

                Message submitMsg = new Message(MessageType.OP_SUBMIT, clientId, docId, this.version, nextOp.toJSONObject());
                send(submitMsg.toJSONString());
            }
        }
    }

    private void handleRemoteOp(Message msg) {
        synchronized (lock) {
            Operation remoteOp = Operation.fromJSONObject(msg.getPayload());
            this.version = msg.getVersion();

            Operation currRemote = remoteOp;
            if (pendingOp != null) {
                OperationalTransformation.TransformResult tr =
                    OperationalTransformation.transform(currRemote, pendingOp, true);
                currRemote = tr.op1Prime;
                pendingOp = tr.op2Prime;
            }

            List<Operation> transformedBuffer = new ArrayList<>();
            for (Operation bOp : bufferOps) {
                OperationalTransformation.TransformResult tr =
                    OperationalTransformation.transform(currRemote, bOp, true);
                currRemote = tr.op1Prime;
                transformedBuffer.add(tr.op2Prime);
            }
            bufferOps.clear();
            bufferOps.addAll(transformedBuffer);

            this.content = OperationalTransformation.applyOperation(this.content, currRemote);
            System.out.printf("[CLIENT:%s] Remote op applied (v=%d): '%s'%n", userName, version, content);

            if (onChangeCallback != null) {
                onChangeCallback.accept(content, version);
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
            Operation op = new Operation(OpType.INSERT, position, text);
            op.setClientId(clientId);
            op.setDocId(docId);
            op.setBaseVersion(this.version);

            this.content = OperationalTransformation.applyOperation(this.content, op);
            if (onChangeCallback != null) {
                onChangeCallback.accept(content, version);
            }

            if (pendingOp == null) {
                pendingOp = op;
                Message msg = new Message(MessageType.OP_SUBMIT, clientId, docId, this.version, op.toJSONObject());
                send(msg.toJSONString());
            } else {
                bufferOps.add(op);
            }
        }
    }

    public void submitDelete(int position, int length) {
        synchronized (lock) {
            Operation op = new Operation(OpType.DELETE, position, length);
            op.setClientId(clientId);
            op.setDocId(docId);
            op.setBaseVersion(this.version);

            this.content = OperationalTransformation.applyOperation(this.content, op);
            if (onChangeCallback != null) {
                onChangeCallback.accept(content, version);
            }

            if (pendingOp == null) {
                pendingOp = op;
                Message msg = new Message(MessageType.OP_SUBMIT, clientId, docId, this.version, op.toJSONObject());
                send(msg.toJSONString());
            } else {
                bufferOps.add(op);
            }
        }
    }

    public void sendCursor(int position) {
        JSONObject payload = new JSONObject();
        payload.put("cursor_position", position);
        Message msg = new Message(MessageType.CURSOR, clientId, docId, 0, payload);
        send(msg.toJSONString());
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

    public List<JSONObject> getActiveUsers() {
        synchronized (lock) {
            return new ArrayList<>(activeUsers);
        }
    }
}
