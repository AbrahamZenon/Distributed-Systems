# DS04 Test Suite (JUnit 5 - Java)

Thư mục kiểm thử tự động của hệ thống DS04 được xây dựng hoàn toàn bằng ngôn ngữ **Java** sử dụng framework **JUnit 5 (Jupiter Engine)**, thay thế toàn bộ cho framework kiểm thử cũ.

## 1. Cấu trúc mã nguồn kiểm thử
Toàn bộ mã nguồn kiểm thử đơn vị và tích hợp được bố trí theo chuẩn Maven tại thư mục:  
`src/test/java/edu/ptit/ds04/`

Bao gồm các file kiểm thử:
1. **`OperationalTransformationTest.java`**: Kiểm thử giải thuật Operational Transformation (OT), chứng minh thuộc tính hội tụ TP1:
   - `testInsertInsertDisjoint`: Hai thao tác chèn ở các vị trí khác nhau.
   - `testInsertInsertSamePositionPriority`: Hai thao tác chèn đồng thời tại cùng vị trí với cơ chế tie-breaking theo priority.
   - `testInsertDeleteDisjoint`: Thao tác chèn và xóa ở hai vị trí tách biệt.
   - `testInsertDeleteInsideRangeIntentionPreservation`: Thao tác chèn rơi vào giữa khoảng bị xóa (Bảo toàn ý định người dùng).
   - `testDeleteDeleteDisjoint`: Hai thao tác xóa ở các khoảng không giao nhau.
   - `testDeleteDeleteOverlapping`: Hai thao tác xóa giao nhau (Idempotent Deletion).
   - `testDeleteDeleteIdentical`: Hai người dùng xóa cùng một đoạn văn bản.
2. **`StorageEngineTest.java`**: Kiểm thử tầng lưu trữ và khả năng dung lỗi phục hồi:
   - `testWalAppendAndRecovery`: Ghi Write-Ahead Log và tái dựng tài liệu chính xác sau khi giả lập sập server.
   - `testSnapshotCombinedWithWal`: Tái dựng dữ liệu kết hợp từ Snapshot checkpoint và các log WAL kế tiếp.
3. **`ServerIntegrationTest.java`**: Kiểm thử tích hợp WebSocket Server:
   - `testServerMultiClientAndOTConcurrency`: Kết nối đa client, xác thực token, đồng bộ thời gian thực và xử lý xung đột đồng thời.
4. **`JupiterClientTest.java`** *(Tác giả: Nguyễn Duy Phúc - B23DCVT341)*: Kiểm thử máy trạng thái Jupiter Client:
   - `testJupiterStateTransitions`: Chuỗi chuyển đổi trạng thái `SYNCHRONIZED` -> `AWAITING_ACK` -> `AWAITING_WITH_BUFFER` và quay lại.
   - `testRemoteOpTransformationWhileAwaitingAck`: Biến đổi remote op khi đang chờ server ACK.
   - `testBufferTransformationWithMultipleRemoteOps`: Biến đổi nhiều remote ops qua buffer tích lũy.
5. **`OfflineManagerTest.java`** *(Tác giả: Nguyễn Duy Phúc - B23DCVT341)*: Kiểm thử soạn thảo ngoại tuyến và tái đồng bộ:
   - `testOfflineQueueAndCatchUpSync`: Đệm thao tác khi mất kết nối mạng, nhận `SYNC_RESP`, reconcile biến đổi chéo và xả hàng đợi đã re-base lên server.

---

## 2. Hướng dẫn chạy kiểm thử

### Cách 1: Sử dụng script trong thư mục này
- Trên Windows:
  ```cmd
  tests\run_tests.bat
  ```
- Trên Linux / macOS:
  ```bash
  bash tests/run_tests.sh
  ```

### Cách 2: Sử dụng Maven Wrapper
- Trên Windows:
  ```cmd
  .\mvnw.cmd test
  ```
- Trên Linux / macOS:
  ```bash
  ./mvnw test
  ```
