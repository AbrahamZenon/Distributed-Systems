# DS04: Hệ Thống Soạn Thảo Văn Bản Cộng Tác Thời Gian Thực (Mô Phỏng Google Docs) - Java Edition
> **Môn học:** Hệ phân tán (Distributed Systems)  
> **Mã đề tài:** DS04 - Collaborative document editing system simulating Google Docs  
> **Ngôn ngữ triển khai:** **Java (JDK 17+)**  
> **Công cụ quản lý & đóng gói:** **Apache Maven**  
> **Phiên bản:** `v1.0.0`  

---

## 1. Thông Tin Nhóm & Phân Công Công Việc (Task Assignment)

### 1.1. Danh sách thành viên
| STT | Mã Sinh Viên | Họ và Tên | Vai trò | Email liên hệ |
|:---:|:---:|:---|:---:|:---|
| 1 | **B23DCVT315** | **Nguyễn Tất Khôi Nguyên** | Nhóm trưởng | B23DCVT315@stu.ptit.edu.vn |
| 2 | **B23DCVT341** | **Nguyễn Duy Phúc** | Thành viên | B23DCVT341@stu.ptit.edu.vn |

---

### 1.2. Bảng phân công nhiệm vụ chi tiết (Task-Assignment Table)

Theo yêu cầu đặc tả đồ án môn Hệ phân tán, toàn bộ hệ thống được xây dựng hoàn toàn bằng ngôn ngữ **Java**, phân chia công việc độc lập, rõ ràng giữa 2 thành viên:

| Thành viên phụ trách | Module / Thành phần đảm nhiệm (Java) | Nội dung công việc chi tiết | Package / File phụ trách | Trạng thái |
|:---|:---|:---|:---|:---:|
| **Nguyễn Tất Khôi Nguyên**<br>*(B23DCVT315)*<br>**[Thành viên 1]** | **1. Core OT & Concurrency Engine** | - Thiết kế cấu trúc dữ liệu `Operation`, `OpType` (INSERT, DELETE, NOOP).<br>- Hiện thực thuật toán Operational Transformation (`OperationalTransformation.java`).<br>- Hàm biến đổi `transform(op1, op2, priority)` xử lý mọi trường hợp: Insert-Insert (deterministic priority tie-breaking), Insert-Delete, Delete-Delete.<br>- Chứng minh tính chất hội tụ TP1 và bảo toàn ý định người dùng (Intention Preservation). | `edu.ptit.ds04.core.OpType`<br>`edu.ptit.ds04.core.Operation`<br>`edu.ptit.ds04.core.OperationalTransformation` | **Đã hoàn thành (100%)** |
| | **2. WebSocket Collaboration Server** | - Xây dựng máy chủ WebSocket đa luồng hiệu năng cao bằng Java (`CollaborationServer.java`).<br>- Quản lý phiên đa người dùng (`SessionManager.java`), danh sách người dùng trực tuyến (Active Presence).<br>- Bộ tuần tự hóa phiên bản đơn điệu (Monotonic Revision Sequencer) và khóa đồng thời tài liệu (`ReentrantLock`).<br>- Phát hiện mất kết nối qua cơ chế Heartbeat Ping/Pong lease. | `edu.ptit.ds04.server.CollaborationServer`<br>`edu.ptit.ds04.server.SessionManager`<br>`edu.ptit.ds04.server.ClientSession`<br>`edu.ptit.ds04.server.DocumentManager` | **Đã hoàn thành (100%)** |
| | **3. Persistence & Crash Recovery Engine** | - Xây dựng cơ chế Write-Ahead Log (`StorageEngine.java`) ghi nhật ký append-only mọi thao tác xuống đĩa (`wal.log`).<br>- Checkpointing snapshot định kỳ lưu toàn vẹn trạng thái văn bản (`snapshot.json`).<br>- Phục hồi dữ liệu tự động khi server crash/restart bằng cách nạp snapshot và replay WAL. | `edu.ptit.ds04.storage.StorageEngine`<br>`data/documents/` | **Đã hoàn thành (100%)** |
| | **4. Protocol & Distributed Security** | - Định nghĩa chuẩn cấu trúc thông điệp JSON Schema (`Message.java`, `MessageType.java`).<br>- Tầng bảo mật: Xác thực HMAC Token, phân quyền người dùng (Editor/Viewer), chống giả mạo Client ID, xác thực và làm sạch dữ liệu đầu vào (Input Sanitization).<br>- Cấu hình tham số môi trường (`Config.java`). | `edu.ptit.ds04.common.Message`<br>`edu.ptit.ds04.common.MessageType`<br>`edu.ptit.ds04.common.SecurityUtil`<br>`edu.ptit.ds04.common.Config` | **Đã hoàn thành (100%)** |
| | **5. Core Tests & Maven Build System** | - Cấu hình `pom.xml`, Maven Wrapper (`mvnw`), và container hóa Docker (`deploy/Dockerfile`).<br>- Viết bộ Unit test tự động JUnit 5 cho OT Engine, Crash Recovery và WebSocket Server. | `pom.xml`<br>`deploy/Dockerfile`<br>`deploy/docker-compose.yml`<br>`src/test/java/edu/ptit/ds04/` | **Đã hoàn thành (100%)** |
| **Nguyễn Duy Phúc**<br>*(B23DCVT341)*<br>**[Thành viên 2]** | **1. Client Engine & State Machine** | - Xây dựng máy trạng thái Jupiter Client (3 trạng thái: `SYNCHRONIZED`, `AWAITING_ACK`, `AWAITING_WITH_BUFFER`).<br>- Hiện thực Optimistic Local Update (hiển thị ký tự tức thì trên client trước khi nhận server ACK).<br>- Đệm thao tác và biến đổi buffer khi nhận remote operation. | `edu.ptit.ds04.client.JupiterClient`<br>`edu.ptit.ds04.client.JupiterState`<br>`edu.ptit.ds04.client.ClientEngine` | **Đã hoàn thành (100%)** |
| | **2. Offline Editing & Catch-up Sync** | - Quản lý hàng đợi thao tác ngoại tuyến khi mất kết nối mạng (`OfflineManager`).<br>- Cơ chế Reconnect Handshake & Catch-up Sync: tự động gửi `SYNC_REQ` để lấy các thao tác bị bỏ lỡ từ server và reconcile. | `edu.ptit.ds04.client.OfflineManager` | **Đã hoàn thành (100%)** |
| | **3. Giao diện Người dùng Tương tác (CLI & Web UI)** | - Xây dựng Interactive Terminal Client cho phép gõ lệnh trực tiếp, xem live text, hiển thị con trỏ, danh sách người dùng, toggle offline.<br>- Xây dựng Web UI giao diện trực quan mô phỏng Google Docs với kết nối WebSocket trực tiếp và máy chủ HTTP mini nhúng. | `edu.ptit.ds04.client.InteractiveCLI`<br>`src/main/resources/web/` | **Đã hoàn thành (100%)** |
| | **4. Kịch bản Demo Tự động & Fault Injection** | - Viết chương trình Java thực hiện trọn vẹn 7 kịch bản biểu diễn (Demonstration Scenarios) theo mục 8.4 đặc tả môn học.<br>- Công cụ tiêm lỗi mạng (Fault Injection): giả lập độ trễ mạng, ngắt kết nối client/server đột ngột (`FaultInjectionRunner`). | `edu.ptit.ds04.demo.DemoAll`<br>`edu.ptit.ds04.demo.FaultInjectionRunner`<br>`scripts/fault_injection.sh` | **Đã hoàn thành (100%)** |
| | **5. Đánh giá Hiệu năng & Báo cáo** | - Đo đạc độ trễ (latency), thông lượng (throughput), thời gian hội tụ (convergence speed) qua Benchmark Runner.<br>- Viết tài liệu báo cáo kỹ thuật (Report 20-30 trang) và Slide thuyết trình. | `edu.ptit.ds04.benchmark.BenchmarkRunner`<br>`scripts/benchmark.sh`<br>`docs/report.pdf`<br>`docs/slides.pdf`<br>`docs/REPORT.md`<br>`docs/SLIDES.md` | **Đã hoàn thành (100%)** |

---

## 2. Giới Thiệu Dự Án & Bản Chất Phân Tán (Project Overview)

Hệ thống **DS04 - Real-Time Collaborative Document Editing System** là hệ thống phân tán được phát triển bằng ngôn ngữ **Java**, cho phép nhiều tiến trình client độc lập cùng mở, soạn thảo và quan sát các thay đổi của một văn bản trong thời gian thực (near real-time).

### Các vấn đề hệ phân tán được giải quyết triệt để:
1. **Kiểm soát đồng thời & Xung đột dữ liệu (Concurrency & Conflict Resolution):**
   - Không sử dụng giải pháp Last-Write-Wins (LWW).
   - Áp dụng thuật toán **Operational Transformation (OT)** chuẩn Jupiter giúp bảo toàn ý định người dùng (Intention Preservation) và đảm bảo tính nhất quán hội tụ mạnh (Strong Eventual Consistency - SEC).
2. **Thứ tự nhân quả & Phiên bản tuần tự (Causality & Version Management):**
   - Máy chủ đóng vai trò Sequencer gán số phiên bản tuần tự tăng đơn điệu (`version = 0, 1, 2...`).
   - Mọi thao tác gửi lên đều mang `base_version`. Server tự động biến đổi thao tác đối chiếu với các thao tác đồng thời diễn ra giữa `base_version` và `current_version`.
3. **Phát hiện nút mạng & Quản lý trạng thái (Peer Discovery & Presence):**
   - Giao thức Heartbeat Ping-Pong định kỳ phát hiện client mất kết nối.
   - Cập nhật danh sách người dùng đang hoạt động (Active Users) và toạ độ con trỏ (Real-time Cursors).
4. **Dung lỗi & Khôi phục sự cố (Fault Tolerance & Recovery):**
   - **Phía Server:** Cơ chế lưu trữ kết hợp Snapshot + Write-Ahead Log (WAL) lưu trữ append-only. Khi server gặp sự cố crash đột ngột, lúc khởi động lại hệ thống sẽ nạp snapshot và replay WAL để khôi phục chính xác 100% nội dung và revision.
   - **Phía Client:** Khi rớt mạng (Network partition/disconnect), client chuyển sang chế độ Offline, các thao tác gõ phím được lưu tạm vào hàng đợi. Khi kết nối lại, client thực hiện bắt tay tái đồng bộ (Catch-up sync) để hội tụ với server.
5. **Bảo mật phân tán (Distributed Security):**
   - Xác thực HMAC Token định danh client.
   - Phân quyền (Editor/Viewer).
   - Kiểm duyệt giới hạn payload và xác thực tính hợp lệ của index nhằm ngăn chặn tràn bộ nhớ hoặc crash hệ thống.

---

## 3. Kiến Trúc Hệ Thống (Architecture & Communication)

```
+-----------------------------------------------------------------------------------+
|                                  CLIENTS LAYER                                    |
|                                                                                   |
|  +------------------------+  +------------------------+  +---------------------+  |
|  |   Client 1 (Alice)     |  |   Client 2 (Bob)       |  |  Client 3 (Charlie) |  |
|  |  - Jupiter State Mach. |  |  - Jupiter State Mach. |  |  - Interactive CLI  |  |
|  |  - Optimistic Buffer   |  |  - Optimistic Buffer   |  |  - Offline Queue   |  |
|  +-----------+------------+  +-----------+------------+  +----------+----------+  |
+--------------|---------------------------|--------------------------|-------------+
               |                           |                          |
               | WebSocket (JSON Protocol) |                          |
               +---------------------------+--------------------------+
                                           |
+------------------------------------------v----------------------------------------+
|                            COLLABORATION SERVER                                   |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                     Network & Session Manager (WebSocket)                   |  |
|  | - Client Authentication & Access Control  - Heartbeat & Presence Monitor    |  |
|  +-----------------------------------------------------------------------------+  |
|                                          |                                        |
|  +---------------------------------------v-------------------------------------+  |
|  |               Document Manager & Concurrency Control Lock                   |  |
|  | - Monotonic Revision Sequencer            - ReentrantLock per Document      |  |
|  +-----------------------------------------------------------------------------+  |
|                                          |                                        |
|  +---------------------------------------v-------------------------------------+  |
|  |                   Operational Transformation (OT) Engine                    |  |
|  | - transform(incoming_op, concurrent_ops)                                    |  |
|  | - Deterministic Tie-breaking (Client Priority)                              |  |
|  +---------------------------------------+-------------------------------------+  |
|                                          |                                        |
|  +---------------------------------------v-------------------------------------+  |
|  |                     Persistence & Recovery Engine (WAL)                     |  |
|  | - Append-only Write-Ahead Log (wal.log)   - Periodic Checkpoint Snapshots   |  |
|  | - Automatic Crash Replay on Startup                                         |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 4. Thuật Toán Đồng Thời & Nhất Quán (Operational Transformation)

### 4.1. Hàm biến đổi cốt lõi $T(Op_1, Op_2)$
Khi hai client thực hiện hai thao tác $Op_1$ và $Op_2$ trên cùng một trạng thái tài liệu $S$:
- $Op_1$ được biến đổi thành $Op_1'$ để áp dụng sau $Op_2$.
- $Op_2$ được biến đổi thành $Op_2'$ để áp dụng sau $Op_1$.
- **Điều kiện hội tụ (Transformation Property 1 - TP1):**
  $$S \circ Op_1 \circ Op_2' \equiv S \circ Op_2 \circ Op_1'$$

Các trường hợp biến đổi được hiện thực trong `OperationalTransformation.java`:
1. **Insert vs Insert:**
   - Nếu $p_1 < p_2$: $p_2' = p_2 + len(text_1)$, $p_1' = p_1$.
   - Nếu $p_1 > p_2$: $p_1' = p_1 + len(text_2)$, $p_2' = p_2$.
   - Nếu $p_1 == p_2$: Deterministic tie-breaking theo `priority` (boolean flag dựa trên client_id).
2. **Insert vs Delete:**
   - Nếu vị trí Insert $\le$ vị trí Delete: Vị trí Delete dời về sau một đoạn bằng độ dài Insert.
   - Nếu vị trí Insert $\ge$ cuối khoảng Delete: Vị trí Insert dời về trước một đoạn bằng độ dài Delete.
   - Nếu vị trí Insert nằm bên trong khoảng Delete: Giữ nguyên văn bản được chèn, tách thao tác Delete để bảo toàn ý định người dùng (Intention Preservation).
3. **Delete vs Delete:**
   - Hai khoảng rời nhau: Dịch chuyển chỉ số của thao tác phía sau.
   - Hai khoảng giao nhau: Loại bỏ phần giao nhau để tránh xóa trùng lặp ký tự (Idempotent Deletion).

---

## 5. Lưu Trữ Bền Vững & Khôi Phục Sự Cố (Storage & Recovery)

1. **Write-Ahead Log (WAL):** Mỗi khi Server tiếp nhận và áp dụng một operation thành công, record đó được ghi ngay lập tức vào `data/documents/<doc_id>/wal.log` kèm `flush()` và `getFD().sync()`.
2. **Snapshots:** Định kỳ sau mỗi $N$ operations (mặc định $N=50$), hệ thống tạo một bản snapshot JSON ghi lại toàn bộ trạng thái nội dung và revision hiện thời.
3. **Crash Recovery Flow:** Khi Server khởi động lại, `StorageEngine` nạp snapshot gần nhất (nếu có), sau đó đọc và replay toàn bộ các record trong `wal.log` có phiên bản lớn hơn snapshot version.

---

## 6. Kịch Bản Kiểm Thử & Trình Diễn Bắt Buộc (Demonstration Scenarios)

Hệ thống được thiết kế để vượt qua đầy đủ 7 kịch bản bắt buộc theo mục 8.4 đặc tả môn học:
1. **Kịch bản 1: Ba client cùng mở một tài liệu:** 3 tiến trình client Java độc lập kết nối đến Server và nạp cùng một Document ID.
2. **Kịch bản 2: Một client chỉnh sửa, các client khác nhận cập nhật tức thời:** Client 1 gửi thao tác chèn, Client 2 và 3 nhận thông điệp qua WebSocket và cập nhật nội dung đồng bộ.
3. **Kịch bản 3: Hai client đồng thời chèn nội dung vào cùng một vị trí:** Client 1 chèn `"Alice "` tại vị trí 0, Client 2 chèn `"Bob "` tại vị trí 0. OT Server phân định thứ tự ưu tiên, kết quả hội tụ tại mọi client thành `"Alice Bob "` hoặc `"Bob Alice "` thống nhất tuyệt đối.
4. **Kịch bản 4: Một client chèn trong khi một client khác xóa:** Kiểm chứng khả năng tính toán lại vị trí (Index shift) để không xảy ra lỗi out-of-bounds hoặc xóa nhầm ký tự.
5. **Kịch bản 5: Chứng minh tính hội tụ (Convergence):** Sau các thao tác đồng thời, nội dung văn bản và số phiên bản tại cả 3 client và server là trùng khớp 100%.
6. **Kịch bản 6: Rớt mạng 1 client, client khác tiếp tục gõ, sau đó reconnect và tái đồng bộ:** Ngắt kết nối mạng của Client 3, Client 1 và 2 tiếp tục chỉnh sửa. Client 3 sau đó kết nối lại, gửi `SYNC_REQ` và tự động cập nhật bắt kịp trạng thái mới nhất.
7. **Kịch bản 7: Server dừng đột ngột và phục hồi tài liệu:** Dừng đột ngột tiến trình Server. Khởi động lại Server, kiểm tra Server nạp lại tài liệu nguyên vẹn từ Snapshot + WAL.

---

## 7. Yêu Cầu Môi Trường & Hướng Dẫn Cài Đặt (Installation)

### 7.1. Yêu cầu môi trường
- **Hệ điều hành:** Windows 10/11, Linux, hoặc macOS.
- **Java Development Kit (JDK):** JDK 17, 21, hoặc 25.
- **Apache Maven:** 3.8+ (hoặc sử dụng Maven Wrapper `./mvnw` đi kèm sẵn).

### 7.2. Biên dịch dự án
```bash
# Sử dụng Maven Wrapper trên Windows
.\mvnw.cmd clean compile

# Hoặc trên Linux/macOS:
# ./mvnw clean compile
```

---

## 8. Hướng Dẫn Vận Hành Hệ Thống (Execution Instructions)

### 8.1. Khởi động Collaboration Server & Embedded Web UI
```bash
# Trên Windows
.\mvnw.cmd compile exec:java -Dexec.mainClass="edu.ptit.ds04.server.CollaborationServer"

# Hoặc trên Linux/macOS
bash scripts/run_server.sh
```
- WebSocket Server lắng nghe tại: `ws://0.0.0.0:8765`.
- Embedded Web UI Google Docs tự động khả dụng tại: `http://localhost:8080/?user=Phuc`.

### 8.2. Khởi động Client
Mở các cửa sổ terminal độc lập:
```bash
# Client Alice
.\mvnw.cmd compile exec:java -Dexec.mainClass="edu.ptit.ds04.client.InteractiveCLI" -Dexec.args="--doc doc-1 --user Alice"

# Client Bob
.\mvnw.cmd compile exec:java -Dexec.mainClass="edu.ptit.ds04.client.InteractiveCLI" -Dexec.args="--doc doc-1 --user Bob"

# Client Phuc (hoặc Charlie)
.\mvnw.cmd compile exec:java -Dexec.mainClass="edu.ptit.ds04.client.InteractiveCLI" -Dexec.args="--doc doc-1 --user Phuc"
```
*Lưu ý:* Bên trong CLI, có thể gõ `status` để xem trạng thái Jupiter State Machine (`SYNCHRONIZED`, `AWAITING_ACK`, `AWAITING_WITH_BUFFER`), gõ `offline` để giả lập rớt mạng gõ ngoại tuyến và `online` để kích hoạt Catch-up sync.

### 8.3. Chạy Toàn Bộ Kịch Bản Demo Tự Động (7 Scenarios Runner)
```bash
# Trên Windows:
.\mvnw.cmd compile exec:java -Dexec.mainClass="edu.ptit.ds04.demo.DemoAll"

# Trên Linux/macOS:
bash scripts/demo_all.sh
```

### 8.4. Chạy Kiểm Thử Tiêm Lỗi Mạng & Khôi Phục (Fault Injection Runner)
```bash
# Trên Windows:
scripts\fault_injection.bat

# Trên Linux/macOS:
bash scripts/fault_injection.sh
```

### 8.5. Chạy Đánh Giá Hiệu Năng (Benchmark Suite)
```bash
# Trên Windows:
scripts\benchmark.bat

# Trên Linux/macOS:
bash scripts/benchmark.sh --clients 5 --ops 20
```
Báo cáo đo kiểm sẽ tự động xuất ra: [docs/benchmark_results.md](docs/benchmark_results.md).

### 8.6. Chạy Toàn Bộ Bộ Kiểm Thử Tự Động (JUnit 5 Tests)
```bash
# Trên Windows:
.\mvnw.cmd test

# Trên Linux/macOS:
bash scripts/run_tests.sh
```

### 8.7. Tài Liệu Báo Cáo Kỹ Thuật & Slide Thuyết Trình
- Báo cáo kỹ thuật chi tiết: [docs/REPORT.md](docs/REPORT.md) & [docs/report.pdf](docs/report.pdf).
- Slide thuyết trình đồ án: [docs/SLIDES.md](docs/SLIDES.md) & [docs/slides.pdf](docs/slides.pdf).

---

## 9. Hướng Dẫn Tái Triển Khai Qua Docker (Redeployment Instructions)

```bash
# Xây dựng và khởi động container qua Docker Compose
docker compose -f deploy/docker-compose.yml up --build -d

# Xem log hoạt động
docker compose -f deploy/docker-compose.yml logs -f

# Dừng hệ thống
docker compose -f deploy/docker-compose.yml down
```

---

## 10. Đánh Giá & Minh Chứng Đóng Góp (Contribution & Git History)

- Toàn bộ commit history được ghi nhận chi tiết theo từng nhiệm vụ được giao.
- Mỗi thành viên thực hiện commit với email và định danh sinh viên rõ ràng, đáp ứng quy định kiểm tra đóng góp cá nhân tại Mục 2 và Mục 13 của tài liệu đặc tả đồ án.
