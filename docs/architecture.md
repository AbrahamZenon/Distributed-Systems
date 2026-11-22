# Architecture Specification: DS04 Real-Time Collaborative Document Editing System (Java Edition)

## 1. System Overview
DS04 simulates the collaborative document editing mechanisms of Google Docs using a centralized, ordered Operational Transformation (OT) server written in **Java** and multi-client event-driven WebSocket connections.

## 2. Distributed Properties
- **Decoupled Nodes:** Server and multiple independent Java client processes communicate strictly over TCP/WebSocket without shared memory or shared files.
- **Node Identities:**
  - Client ID: `client_id` (e.g. `alice`, `bob`, `charlie`)
  - Document ID: `doc_id`
  - Message ID: UUID v4
  - Operation ID: UUID v4
- **Monotonic Versioning:** Authoritative sequencer assigning consecutive revision integers ($0, 1, 2, \dots$).
- **Consistency Model:** Strong Eventual Consistency (SEC) + Intention Preservation.

## 3. Communication Protocol
JSON framing over WebSocket (`org.java_websocket`):
- `HELLO / JOIN`: Client registers, authenticates token, receives initial state snapshot and presence list.
- `OP_SUBMIT`: Client sends operation with `base_version`.
- `OP_ACK`: Server returns monotonically assigned `server_version`.
- `OP_BROADCAST`: Server broadcasts transformed operation to all other connected clients.
- `SYNC_REQ / SYNC_RESP`: Catch-up synchronization for reconnected or lagging clients.
- `CURSOR`: Broadcasts cursor position and user presence.
- `PING / PONG`: Application-level heartbeat for detecting network partitions.

## 4. Operational Transformation (OT) Mathematics
Given initial document text $S$, and two concurrent operations $Op_1$ and $Op_2$:
Transformation function $T(Op_1, Op_2) \to (Op_1', Op_2')$ satisfies:
$$S \circ Op_1 \circ Op_2' = S \circ Op_2 \circ Op_1'$$

Implemented in `edu.ptit.ds04.core.OperationalTransformation` with deterministic priority tie-breaking.

## 5. Storage & Crash Recovery
- **Write-Ahead Log (WAL):** Every operation accepted by the sequencer is written append-only to `wal.log` with `fos.getFD().sync()`.
- **Snapshots:** Periodic checkpoints saved as atomic JSON files (`snapshot.json`).
- **Recovery:** On crash/restart, `StorageEngine` loads latest snapshot and replays subsequent WAL operations.
