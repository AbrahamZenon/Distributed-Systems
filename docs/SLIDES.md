# DS04: HỆ THỐNG SOẠN THẢO VĂN BẢN CỘNG TÁC THỜI GIAN THỰC (MÔ PHỎNG GOOGLE DOCS)
## BÁO CÁO THUYẾT TRÌNH BÀI TẬP LỚN MÔN HỆ PHÂN TÁN (DISTRIBUTED SYSTEMS)

---

### SLIDE 1: TRANG TIÊU ĐỀ
- **Tên đề tài:** DS04 - Real-Time Collaborative Document Editing System Simulating Google Docs
- **Ngôn ngữ:** Java (JDK 17+)
- **Sinh viên thực hiện:**
  - **Nguyễn Tất Khôi Nguyên** - MSV: **B23DCVT315** (Nhóm trưởng)
  - **Nguyễn Duy Phúc** - MSV: **B23DCVT341** (Thành viên)
- **Giảng viên hướng dẫn:** Bộ môn Hệ thống Phân tán - Khoa CNTT - PTIT

---

### SLIDE 2: BÀI TOÁN & THÁCH THỨC HỆ PHÂN TÁN
- **Thách thức 1:** Hàng chục cộng tác viên gõ đồng thời vào cùng một văn bản.
- **Thách thức 2:** Không sử dụng khóa bi quan (No locking) để tránh giảm trải nghiệm người dùng.
- **Thách thức 3:** Không sử dụng giải pháp Last-Write-Wins (LWW) vì gây mất dữ liệu.
- **Thách thức 4:** Đảm bảo tính hội tụ mạnh (Strong Eventual Consistency) và bảo toàn ý định người dùng (Intention Preservation).
- **Thách thức 5:** Hoạt động trơn tru ngay cả khi mạng chập chờn, rớt mạng hoặc máy chủ sập đột ngột.

---

### SLIDE 3: KIẾN TRÚC HỆ THỐNG TỔNG QUAN
- **Mô hình hình sao (Client-Server Architecture):**
  - **Server:** Centralized Sequencer, Document Lock per Doc, Concurrency Control, WAL Persistence.
  - **Clients:** Jupiter Client State Machine, Optimistic Rendering Buffer, Offline Queue.
  - **Giao thức:** WebSocket đa luồng (`org.java_websocket`), trao đổi thông điệp JSON.
  - **Tích hợp:** HTTP Static Server mini phục vụ Web UI trực tiếp tại cổng 8080.

---

### SLIDE 4: THUẬT TOÁN OPERATIONAL TRANSFORMATION (OT)
- **Thuộc tính hội tụ TP1:** $S \circ Op_1 \circ Op_2' \equiv S \circ Op_2 \circ Op_1'$
- **Các trường hợp biến đổi cốt lõi:**
  1. **Insert vs Insert:** Điều chỉnh index theo chiều dài xâu chèn; deterministic priority tie-breaking.
  2. **Insert vs Delete:** Tịnh tiến index hoặc tách vùng xóa để bảo toàn xâu chèn bên trong.
  3. **Delete vs Delete:** Tịnh tiến index hoặc triệt tiêu phần giao nhau (idempotency).

---

### SLIDE 5: MÁY TRẠNG THÁI JUPITER CLIENT (CLIENT ENGINE)
- **3 trạng thái chính:**
  - `SYNCHRONIZED`: Không có op chờ xác nhận. Edit gửi ngay lên server.
  - `AWAITING_ACK`: 1 op đang bay (in-flight). Edit mới đưa vào hàng đợi `bufferOps`.
  - `AWAITING_WITH_BUFFER`: Chờ xác nhận kèm buffer tích lũy. Khi nhận remote op, biến đổi tuần tự qua toàn bộ buffer.
- **Optimistic Local Update:** Ký tự xuất hiện tức thời trên màn hình (0ms delay nhận thức).

---

### SLIDE 6: SOẠN THẢO NGOẠI TUYẾN & TÁI ĐỒNG BỘ BẮT KỊP (OFFLINE MANAGER)
- **Khi rớt mạng:** Thao tác gõ phím được đưa vào hàng đợi ngoại tuyến `offlineQueue`.
- **Khi kết nối lại:**
  1. Gửi thông điệp `SYNC_REQ` với `from_version`.
  2. Server phản hồi `SYNC_RESP` chứa các thao tác bị bỏ lỡ (`missedOps`).
  3. Reconcile: Biến đổi chéo giữa `missedOps` và `offlineQueue`.
  4. Cập nhật trạng thái văn bản và xả hàng đợi offline lên server.

---

### SLIDE 7: LƯU TRỮ BỀN VỮNG & PHỤC HỒI SỰ CỐ (STORAGE & CRASH RECOVERY)
- **Write-Ahead Log (WAL):** Ghi append-only từng thao tác xuống `wal.log` kèm `fsync()`.
- **Periodic Checkpoint Snapshot:** Lưu toàn vẹn trạng thái văn bản sau mỗi 50 thao tác (`snapshot.json`).
- **Crash Recovery:** Khi server restart sau crash, nạp snapshot gần nhất và replay các bản ghi WAL kế tiếp để tái lập 100% dữ liệu.

---

### SLIDE 8: GIAO DIỆN NGƯỜI DÙNG ĐA NỀN TẢNG
- **1. Interactive CLI Client (`InteractiveCLI.java`):**
  - Trực quan hóa trạng thái máy trạng thái Jupiter, số lượng buffer, toạ độ con trỏ, lệnh `offline/online`.
- **2. Google Docs Web UI (`src/main/resources/web/`):**
  - Thiết kế chuẩn Google Docs: Thanh thực đơn, thanh công cụ font/căn lề, trang A4, bóng đổ.
  - Danh sách avatar màu sắc của người dùng đang trực tuyến.
  - Nút gạt Offline/Online và ngăn kéo OT Live Event Log trực quan.

---

### SLIDE 9: KIỂM THỬ TRÌNH DIỄN 7 KỊCH BẢN BẮT BUỘC
- **Scenario 1:** 3 client độc lập cùng mở văn bản.
- **Scenario 2:** 1 client gõ, 2 client khác nhận ngay lập tức.
- **Scenario 3:** Chèn đồng thời tại cùng vị trí 0 -> OT giải quyết xung đột, hội tụ tuyệt đối.
- **Scenario 4:** 1 client chèn trong khi client khác xóa -> Bảo toàn ý định.
- **Scenario 5:** Chứng minh tính hội tụ toàn diện (Strong Eventual Consistency).
- **Scenario 6:** Client rớt mạng, các client khác gõ tiếp, reconnect và catch-up sync thành công.
- **Scenario 7:** Server sập đột ngột và phục hồi hoàn hảo từ WAL/Snapshot.

---

### SLIDE 10: ĐÁNH GIÁ HIỆU NĂNG THỰC NGHIỆM (BENCHMARK)
- **Thông lượng (Throughput):** Đạt trên **3,000 operations/giây**.
- **Độ trễ phản hồi cục bộ:** Dưới **0.1 ms** (Optimistic).
- **Thời gian hội tụ toàn mạng:** Dưới **15 ms**.
- **Tính nhất quán:** 100% không mất dữ liệu, không lệch thứ tự nhân quả.

---

### SLIDE 11: PHÂN CÔNG CÔNG VIỆC VÀ ĐÓNG GÓP
- **Nguyễn Tất Khôi Nguyên (B23DCVT315):** Core OT Engine, WebSocket Server, Storage WAL, Protocol & Security -> **100%**
- **Nguyễn Duy Phúc (B23DCVT341):** Jupiter Client Machine, Offline Manager & Catch-up Sync, Interactive CLI & Web UI, Benchmark & Fault Injection, Báo cáo & Slide -> **100%**

---

### SLIDE 12: KẾT LUẬN & HỎI ĐÁP (Q&A)
- Dự án DS04 đáp ứng trọn vẹn và vượt mức các chỉ tiêu đề ra cho đồ án Hệ phân tán.
- Trân trọng cảm ơn Thầy Cô và các bạn đã lắng nghe!
