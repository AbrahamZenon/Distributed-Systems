/**
 * DS04 Real-Time Collaborative Web Client (Simulating Google Docs)
 * Author: Nguyen Duy Phuc (B23DCVT341)
 */

(function () {
  const SERVER_HOST = window.location.hostname || "127.0.0.1";
  const WS_URI = `ws://${SERVER_HOST}:8765`;
  const DOC_ID = new URLSearchParams(window.location.search).get("doc") || "doc-1";
  const USER_NAME = new URLSearchParams(window.location.search).get("user") || "Phuc";
  const CLIENT_ID = (USER_NAME.toLowerCase() + "-" + Math.floor(Math.random() * 1000));
  const CLIENT_COLOR = "#1a73e8";

  // State
  let ws = null;
  let docVersion = 0;
  let docContent = "";
  let pendingOp = null;
  const bufferOps = [];
  const offlineQueue = [];
  let isOfflineSimulated = false;
  let opCount = 0;

  // DOM Elements
  const editor = document.getElementById("collabEditor");
  const versionBadge = document.getElementById("versionBadge");
  const jupiterBadge = document.getElementById("jupiterBadge");
  const cloudStatusText = document.getElementById("cloudStatusText");
  const activeUsersContainer = document.getElementById("activeUsersContainer");
  const btnOfflineToggle = document.getElementById("btnOfflineToggle");
  const btnOfflineText = document.getElementById("btnOfflineText");
  const offlineQueueCounter = document.getElementById("offlineQueueCounter");
  const indicatorDot = document.querySelector(".indicator-dot");
  const otLogList = document.getElementById("otLogList");
  const otLogDrawer = document.getElementById("otLogDrawer");
  const btnToggleLog = document.getElementById("btnToggleLog");
  const btnCloseDrawer = document.getElementById("btnCloseDrawer");
  const btnClearLog = document.getElementById("btnClearLog");
  const statOpsCount = document.getElementById("statOpsCount");
  const footerServerUri = document.getElementById("footerServerUri");
  const footerDocId = document.getElementById("footerDocId");
  const footerUser = document.getElementById("footerUser");
  const footerStats = document.getElementById("footerStats");

  footerServerUri.textContent = WS_URI;
  footerDocId.textContent = DOC_ID;
  footerUser.textContent = `${USER_NAME} (${CLIENT_ID})`;

  // ==========================================
  // JUPITER STATE MACHINE HELPERS
  // ==========================================
  function getJupiterState() {
    if (isOfflineSimulated || !ws || ws.readyState !== WebSocket.OPEN) {
      return "OFFLINE";
    }
    if (pendingOp === null && bufferOps.length === 0) {
      return "SYNCHRONIZED";
    } else if (pendingOp !== null && bufferOps.length === 0) {
      return "AWAITING_ACK";
    } else {
      return "AWAITING_WITH_BUFFER";
    }
  }

  function updateStateUI() {
    const state = getJupiterState();
    jupiterBadge.textContent = state;
    jupiterBadge.className = "jupiter-tag " + state.toLowerCase();

    versionBadge.textContent = `v=${docVersion}`;

    if (state === "OFFLINE") {
      cloudStatusText.textContent = "Offline (Edits queued)";
      indicatorDot.className = "indicator-dot offline";
      btnOfflineText.textContent = "Go Online";
    } else if (pendingOp !== null || bufferOps.length > 0) {
      cloudStatusText.textContent = "Saving...";
      indicatorDot.className = "indicator-dot online";
      btnOfflineText.textContent = "Go Offline";
    } else {
      cloudStatusText.textContent = "Saved to Server";
      indicatorDot.className = "indicator-dot online";
      btnOfflineText.textContent = "Go Offline";
    }

    if (offlineQueue.length > 0) {
      offlineQueueCounter.style.display = "inline-block";
      offlineQueueCounter.textContent = `${offlineQueue.length} queued`;
    } else {
      offlineQueueCounter.style.display = "none";
    }

    // Word and character stats
    const chars = docContent.length;
    const words = docContent.trim() ? docContent.trim().split(/\s+/).length : 0;
    footerStats.textContent = `Chars: ${chars} | Words: ${words} | Buffer: ${bufferOps.length}`;
  }

  // ==========================================
  // OPERATIONAL TRANSFORMATION (OT) ENGINE (JS)
  // ==========================================
  function transform(op1, op2, priority) {
    if (!op1 || op1.type === "NOOP" || !op2 || op2.type === "NOOP") {
      return { op1Prime: op1, op2Prime: op2 };
    }

    // INSERT vs INSERT
    if (op1.type === "INSERT" && op2.type === "INSERT") {
      if (op1.position < op2.position) {
        return {
          op1Prime: { ...op1 },
          op2Prime: { ...op2, position: op2.position + op1.text.length }
        };
      } else if (op1.position > op2.position) {
        return {
          op1Prime: { ...op1, position: op1.position + op2.text.length },
          op2Prime: { ...op2 }
        };
      } else {
        // Deterministic Tie-Breaking
        if (priority) {
          return {
            op1Prime: { ...op1 },
            op2Prime: { ...op2, position: op2.position + op1.text.length }
          };
        } else {
          return {
            op1Prime: { ...op1, position: op1.position + op2.text.length },
            op2Prime: { ...op2 }
          };
        }
      }
    }

    // INSERT vs DELETE
    if (op1.type === "INSERT" && op2.type === "DELETE") {
      const insPos = op1.position;
      const delStart = op2.position;
      const delEnd = op2.position + op2.length;

      if (insPos <= delStart) {
        return {
          op1Prime: { ...op1 },
          op2Prime: { ...op2, position: op2.position + op1.text.length }
        };
      } else if (insPos >= delEnd) {
        return {
          op1Prime: { ...op1, position: op1.position - op2.length },
          op2Prime: { ...op2 }
        };
      } else {
        // Intention preservation: insert inside deletion range
        return {
          op1Prime: { ...op1, position: delStart },
          op2Prime: { ...op2, length: op2.length + op1.text.length }
        };
      }
    }

    // DELETE vs INSERT
    if (op1.type === "DELETE" && op2.type === "INSERT") {
      const res = transform(op2, op1, !priority);
      return { op1Prime: res.op2Prime, op2Prime: res.op1Prime };
    }

    // DELETE vs DELETE
    if (op1.type === "DELETE" && op2.type === "DELETE") {
      const s1 = op1.position;
      const e1 = op1.position + op1.length;
      const s2 = op2.position;
      const e2 = op2.position + op2.length;

      if (e1 <= s2) {
        return {
          op1Prime: { ...op1 },
          op2Prime: { ...op2, position: op2.position - op1.length }
        };
      } else if (e2 <= s1) {
        return {
          op1Prime: { ...op1, position: op1.position - op2.length },
          op2Prime: { ...op2 }
        };
      } else {
        // Overlapping delete
        return {
          op1Prime: { ...op1, length: Math.max(0, op1.length - op2.length) },
          op2Prime: { ...op2, length: Math.max(0, op2.length - op1.length) }
        };
      }
    }

    return { op1Prime: op1, op2Prime: op2 };
  }

  function applyOpToText(text, op) {
    if (!op || op.type === "NOOP") return text;
    if (op.type === "INSERT") {
      const pos = Math.min(Math.max(0, op.position), text.length);
      return text.slice(0, pos) + op.text + text.slice(pos);
    } else if (op.type === "DELETE") {
      const pos = Math.min(Math.max(0, op.position), text.length);
      const end = Math.min(pos + op.length, text.length);
      return text.slice(0, pos) + text.slice(end);
    }
    return text;
  }

  // ==========================================
  // LOGGING DRAWER
  // ==========================================
  function logEvent(title, detail, type = "info") {
    opCount++;
    statOpsCount.textContent = `${opCount} operations`;

    const empty = otLogList.querySelector(".log-empty-msg");
    if (empty) empty.remove();

    const entry = document.createElement("div");
    entry.className = `log-entry op-${type}`;
    const time = new Date().toLocaleTimeString();
    entry.innerHTML = `<strong>[${time}] ${title}</strong>: ${detail}`;
    otLogList.prepend(entry);
  }

  // ==========================================
  // WEBSOCKET COLLABORATION ENGINE
  // ==========================================
  function connect() {
    if (isOfflineSimulated) return;

    ws = new WebSocket(WS_URI);

    ws.onopen = () => {
      logEvent("Connected", `Connected to ${WS_URI}`, "sync");
      // Send JOIN message
      const joinMsg = {
        type: "JOIN",
        client_id: CLIENT_ID,
        doc_id: DOC_ID,
        version: docVersion,
        payload: {
          token: "token-" + CLIENT_ID,
          user_name: USER_NAME,
          color: CLIENT_COLOR
        }
      };
      ws.send(JSON.stringify(joinMsg));
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        handleMessage(msg);
      } catch (err) {
        console.error("Malformed message:", err);
      }
    };

    ws.onclose = () => {
      logEvent("Disconnected", "WebSocket connection closed.", "sync");
      updateStateUI();
    };

    ws.onerror = (err) => {
      console.error("WebSocket error:", err);
    };
  }

  function handleMessage(msg) {
    switch (msg.type) {
      case "JOIN_ACK": {
        docVersion = msg.payload.version || 0;
        docContent = msg.payload.content || "";
        editor.value = docContent;
        renderActiveUsers(msg.payload.active_users || []);
        logEvent("Joined Document", `Doc '${DOC_ID}' at v=${docVersion}`, "sync");
        updateStateUI();

        // If we had queued offline ops, request sync
        if (offlineQueue.length > 0) {
          sendSyncReq();
        }
        break;
      }

      case "OP_ACK": {
        const serverVer = msg.payload.server_version;
        docVersion = serverVer;
        pendingOp = null;
        logEvent("OP_ACK", `Server confirmed operation at v=${serverVer}`, "insert");

        if (bufferOps.length > 0) {
          const nextOp = bufferOps.shift();
          nextOp.base_version = docVersion;
          pendingOp = nextOp;
          sendOpSubmit(nextOp);
        }
        updateStateUI();
        break;
      }

      case "OP_BROADCAST": {
        const remoteOp = msg.payload;
        docVersion = msg.version;
        logEvent("OP_BROADCAST", `Remote ${remoteOp.type} from ${msg.client_id} at v=${docVersion}`, remoteOp.type.toLowerCase());

        let currRemote = remoteOp;
        if (pendingOp) {
          const tr = transform(currRemote, pendingOp, true);
          currRemote = tr.op1Prime;
          pendingOp = tr.op2Prime;
        }

        const newBuffer = [];
        for (const bOp of bufferOps) {
          const tr = transform(currRemote, bOp, true);
          currRemote = tr.op1Prime;
          newBuffer.push(tr.op2Prime);
        }
        bufferOps.length = 0;
        bufferOps.push(...newBuffer);

        // Preserve local cursor selection
        const selStart = editor.selectionStart;
        const selEnd = editor.selectionEnd;

        docContent = applyOpToText(docContent, currRemote);
        editor.value = docContent;

        // Shift cursor if needed
        if (currRemote.type === "INSERT" && currRemote.position <= selStart) {
          editor.setSelectionRange(selStart + currRemote.text.length, selEnd + currRemote.text.length);
        } else if (currRemote.type === "DELETE" && currRemote.position < selStart) {
          editor.setSelectionRange(Math.max(0, selStart - currRemote.length), Math.max(0, selEnd - currRemote.length));
        }

        updateStateUI();
        break;
      }

      case "SYNC_RESP": {
        const missedOps = msg.payload.operations || [];
        const currentVer = msg.payload.current_version;
        logEvent("SYNC_RESP", `Catch-up sync received ${missedOps.length} missed operations`, "sync");

        // Reconcile missed server ops against offline queue
        for (const missed of missedOps) {
          let currMissed = missed;
          const newOffline = [];
          for (const offOp of offlineQueue) {
            const tr = transform(currMissed, offOp, true);
            currMissed = tr.op1Prime;
            newOffline.push(tr.op2Prime);
          }
          offlineQueue.length = 0;
          offlineQueue.push(...newOffline);
          docContent = applyOpToText(docContent, currMissed);
        }

        docVersion = currentVer;
        editor.value = docContent;

        // Flush offline queue
        const toFlush = [...offlineQueue];
        offlineQueue.length = 0;
        for (const op of toFlush) {
          submitOperation(op);
        }

        updateStateUI();
        break;
      }

      case "PRESENCE": {
        renderActiveUsers(msg.payload.users || []);
        break;
      }

      case "CURSOR": {
        renderRemoteCursor(msg.payload);
        break;
      }
    }
  }

  function sendOpSubmit(op) {
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    const msg = {
      type: "OP_SUBMIT",
      client_id: CLIENT_ID,
      doc_id: DOC_ID,
      version: docVersion,
      payload: op
    };
    ws.send(JSON.stringify(msg));
  }

  function sendSyncReq() {
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    const msg = {
      type: "SYNC_REQ",
      client_id: CLIENT_ID,
      doc_id: DOC_ID,
      version: docVersion,
      payload: { from_version: docVersion }
    };
    ws.send(JSON.stringify(msg));
    logEvent("SYNC_REQ", `Requested history sync from v=${docVersion}`, "sync");
  }

  function submitOperation(op) {
    if (isOfflineSimulated || !ws || ws.readyState !== WebSocket.OPEN) {
      // Offline mode: queue operation
      offlineQueue.push(op);
      logEvent("Offline Queued", `${op.type} buffered (${offlineQueue.length} queued)`, "sync");
      updateStateUI();
      return;
    }

    if (pendingOp === null) {
      pendingOp = op;
      sendOpSubmit(op);
      logEvent("OP_SUBMIT", `Sent ${op.type} at pos ${op.position}`, op.type.toLowerCase());
    } else {
      bufferOps.push(op);
      logEvent("Buffered", `${op.type} added to Jupiter buffer (${bufferOps.length})`, "sync");
    }
    updateStateUI();
  }

  // ==========================================
  // TEXTAREA DIFFING & INPUT DISPATCH
  // ==========================================
  let prevText = "";
  editor.addEventListener("input", () => {
    const newText = editor.value;
    const oldText = docContent;

    // Fast Single-Edit Diffing
    let start = 0;
    while (start < oldText.length && start < newText.length && oldText[start] === newText[start]) {
      start++;
    }

    let endOld = oldText.length - 1;
    let endNew = newText.length - 1;
    while (endOld >= start && endNew >= start && oldText[endOld] === newText[endNew]) {
      endOld--;
      endNew--;
    }

    const delLen = endOld - start + 1;
    const insText = newText.slice(start, endNew + 1);

    if (delLen > 0) {
      const delOp = {
        op_id: "op-" + Math.random().toString(36).substr(2, 9),
        op_type: "DELETE",
        type: "DELETE",
        position: start,
        length: delLen,
        text: "",
        client_id: CLIENT_ID,
        doc_id: DOC_ID,
        base_version: docVersion
      };
      docContent = applyOpToText(docContent, delOp);
      submitOperation(delOp);
    }

    if (insText.length > 0) {
      const insOp = {
        op_id: "op-" + Math.random().toString(36).substr(2, 9),
        op_type: "INSERT",
        type: "INSERT",
        position: start,
        length: 0,
        text: insText,
        client_id: CLIENT_ID,
        doc_id: DOC_ID,
        base_version: docVersion
      };
      docContent = applyOpToText(docContent, insOp);
      submitOperation(insOp);
    }

    updateStateUI();
  });

  // Cursor broadcast
  editor.addEventListener("keyup", () => {
    broadcastCursor(editor.selectionStart);
  });
  editor.addEventListener("click", () => {
    broadcastCursor(editor.selectionStart);
  });

  function broadcastCursor(pos) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: "CURSOR",
        client_id: CLIENT_ID,
        doc_id: DOC_ID,
        version: 0,
        payload: { cursor_position: pos }
      }));
    }
  }

  // ==========================================
  // PRESENCE & CURSOR UI
  // ==========================================
  function renderActiveUsers(users) {
    activeUsersContainer.innerHTML = "";
    users.forEach(u => {
      const avatar = document.createElement("div");
      avatar.className = "user-avatar";
      avatar.style.backgroundColor = u.color || "#3b82f6";
      avatar.textContent = (u.user_name || u.client_id || "U").charAt(0).toUpperCase();
      avatar.title = `${u.user_name} (${u.role})`;
      activeUsersContainer.appendChild(avatar);
    });
  }

  function renderRemoteCursor(cursorData) {
    // remote cursor display placeholder
  }

  // ==========================================
  // EVENT LISTENERS & CONTROLS
  // ==========================================
  btnOfflineToggle.addEventListener("click", () => {
    isOfflineSimulated = !isOfflineSimulated;
    if (isOfflineSimulated) {
      if (ws) ws.close();
      logEvent("Offline Mode", "Disconnected network. Edits will queue locally.", "sync");
    } else {
      logEvent("Online Mode", "Reconnecting to server and synchronizing...", "sync");
      connect();
    }
    updateStateUI();
  });

  btnToggleLog.addEventListener("click", () => {
    otLogDrawer.classList.toggle("open");
  });

  btnCloseDrawer.addEventListener("click", () => {
    otLogDrawer.classList.remove("open");
  });

  btnClearLog.addEventListener("click", () => {
    otLogList.innerHTML = '<div class="log-empty-msg">Log cleared.</div>';
  });

  // Start connection
  connect();
})();
