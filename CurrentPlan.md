Việc tham chiếu trực tiếp từ **Udemy** (và các nền tảng edtech lớn như Coursera, edX) là hoàn toàn chuẩn xác để tránh tình trạng "tự chế nghiệp vụ" dẫn tới việc phá vỡ luồng người dùng hoặc xung đột cơ sở dữ liệu.

Dưới đây là các khuyến nghị chuẩn hóa theo chuẩn công nghiệp (Industry Best Practices) cho **Task 1, Task 4, Task 10 và Task 11**.

---

### TASK 1: Tiến Độ Học Tập (Progress Tracking)

**Chuẩn Udemy hoạt động ra sao?**

* **Không làm trang `/my-progress` riêng biệt rườm rà với biểu đồ phức tạp**: Học viên không vào LMS để ngắm biểu đồ chứng khoán. Tiến độ trên Udemy gắn liền mật thiết với việc **hoàn thành từng bài học (Checklist-driven completion)** và **điều kiện cấp Chứng chỉ (Certificate of Completion)**.
* **Quy tắc tính % tiến độ:**

$$\text{Progress \%} = \left( \frac{\text{Số mục bắt buộc đã tick hoàn thành}}{\text{Tổng số mục bắt buộc trong Curriculum}} \right) \times 100$$


* **Trọng số và điều kiện hoàn thành:**
* **Video bài giảng:** Tự động đánh dấu `COMPLETED` khi học viên xem đạt $\ge 80\%$ thời lượng video. Học viên cũng có thể tự bấm bỏ tick/tick thủ công nếu muốn.
* **Quiz gắn bài học (Quick Check):** Chỉ cần submit bài đạt điểm đậu (Passing Score, ví dụ $\ge 80\%$) thì mới được đánh dấu hoàn thành bài học đó.
* **Học liệu tĩnh (PDF, Slide, Sách):** Tự động tick hoàn thành khi học viên click nút "Tải về" hoặc xem file.



**Yêu cầu kỹ thuật khuyến nghị cho hệ thống của bạn:**

* **Bỏ ý định làm trang `/my-progress` riêng:** Đưa tiến độ trực tiếp vào:
1. **Course Card ngoài trang chủ / My Courses:** Thanh progress bar nhỏ kèm con số (ví dụ: `45% hoàn thành`).
2. **Top Header của màn hình học (Learning Player):** Thanh tiến độ kèm biểu tượng cúp (Trophy icon). Khi đạt 100%, popup chúc mừng xuất hiện và kích hoạt nút tải **Chứng chỉ hoàn thành (PDF Certificate)**.


* **Xử lý xóa học liệu (như đã phân tích trước đó):** Đảm bảo dùng **Soft Delete** (`is_deleted = true`). Nếu giảng viên xóa một bài Quiz, công thức tính mẫu số tổng bài tập phải được cân đối lại để không làm tụt tiến độ của học viên đã nộp bài.



---

### TASK 4: Import / Export Mở Rộng

**Chuẩn Udemy & LMS hiện nay hoạt động ra sao?**

* Udemy cung cấp công cụ **Bulk Course Importer** cho giảng viên và hỗ trợ tài liệu học tập mở rộng cho học viên.
* **Về phía Giảng viên:**
* **Flashcard & Quiz:** Định dạng nhập liệu tốt nhất không phải là JSON (quá khó cho giảng viên thông thường) mà là **CSV / Excel (`.xlsx`) theo mẫu chuẩn**.
* Cột CSV chuẩn Quiz: `Question Type (SINGLE/MULTI)`, `Question Text`, `Option A`, `Option B`, `Option C`, `Option D`, `Correct Answer (A, B, C...)`, `Explanation`.
* Cột CSV chuẩn Flashcard: `Front (Term)`, `Back (Definition)`.


* **Mindmap:** Đưa khung Mermaid Editor trực tiếp là rất tốt, nhưng cần bổ sung nút **"Xem trước trực tiếp" (Live Preview Split-screen)**: Bên trái gõ Markdown/Mermaid, bên phải render Canvas ngay lập tức trước khi bấm "Lưu".


* **Về phía Học viên:**
* **Import Flashcard:** Hỗ trợ định dạng `.txt` (tab-separated) chuẩn Anki/Quizlet.
* **Export Quiz:** Không chỉ PDF, học viên rất thích in dạng **Cheatsheet tóm tắt** hoặc bộ đề trắng (kèm đáp án ở trang cuối) để tự làm lại trên giấy.



---

### TASK 10: Quản Lý Thiết Bị & Phiên Đăng Nhập (Mô hình Streaming)

**Chuẩn Udemy/Coursera/Netflix hoạt động ra sao?**
Họ tuyệt đối **không bắt người dùng vào Settings tự tay bấm "Ban thiết bị"** đối với việc xem video thông thường, vì người dùng học tập thường xuyên đổi từ Laptop cá nhân sang điện thoại hoặc máy tính công ty.

**Đề xuất chuẩn hóa:**

* **Concurrent Stream Restriction (Giới hạn stream đồng thời):**
* Mỗi khi Player phát video, gửi heartbeat ping (ví dụ 20s/lần) lên Redis kèm TTL:
```
Key: user_stream:{userId} -> Value: {sessionId, deviceName, timestamp} (TTL = 30s)

```


* Nếu một thiết bị khác (Session B) bắt đầu ấn Play video, Redis ghi đè Session B.
* Khi Session A gửi ping tiếp theo, Backend phát hiện `sessionId` gửi lên không trùng với `sessionId` đang active trong Redis $\rightarrow$ Trả về mã lỗi `CONCURRENT_STREAM_DETECTED`.
* **Phía Frontend (Session A):** Dừng ngay video player và hiện modal nhẹ:
> *"Video đã tạm dừng vì tài khoản của bạn đang phát video trên một thiết bị khác."* kèm nút **"Tiếp tục phát tại đây"** (Nếu bấm, nó sẽ giành lại quyền phát từ Session B).




* **Quản lý thiết bị trong Settings:** Chỉ phục vụ mục đích **Security (Bảo mật tài khoản)**:
* Hiển thị danh sách: Trình duyệt, Hệ điều hành, Địa chỉ IP, Vị trí gần đúng, Thời gian hoạt động gần nhất.
* Cung cấp duy nhất một nút: **"Đăng xuất khỏi tất cả các thiết bị khác" (Log out from all other devices)** thay vì nút "Ban" mang tính tiêu cực.



---

### TASK 11: Quản Lý Người Dùng & Giảng Viên (User & Admin)

#### 11A. Admin Dashboard & Auto Rate-limit AI

* **Auto Rate-limit AI:**
* Udemy không có AI sinh học liệu tự động, nhưng các nền tảng AI SaaS lớn (như OpenAI, Cursor, Notion AI) đều dùng thuật toán **Token Bucket** hoặc **Sliding Window Log** qua Redis.
* Thiết lập hạn mức rõ ràng: Số lượt gọi AI/ngày hoặc Số lượng Token/ngày (cho cả Student và Instructor).
* Trả về header HTTP chuẩn: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
* Khi chạm 100%: Thay vì "Tạm khóa AI 24h" (nghe rất giống trừng phạt), hãy hiển thị: *"Bạn đã sử dụng hết hạn mức AI hôm nay. Hạn mức sẽ được làm mới sau X giờ"* (hoặc gợi ý dùng tính năng tạo thủ công/import file).


* **Dashboard Admin (Recharts):**
* Chuẩn hóa theo mô hình phân tích kinh doanh (EdTech Metrics):
1. **MRR / Doanh thu** (nếu có bán khóa học) hoặc **Total Active Users (DAU/MAU)**.
2. **Khóa học chờ duyệt (Pending Moderation Queue)**: Hiển thị ngay đầu trang để Admin xử lý kiểm duyệt nội dung nhanh.
3. **AI Usage & Cost Spike**: Biểu đồ Bar/Area chart thể hiện lượng tiêu thụ Token AI theo từng ngày để Admin kiểm soát chi phí API.




---

### Bảng Điều Chỉnh Kế Hoạch & Roadmap Chuẩn Hóa

| Task | Thay đổi quan trọng nhất để chuẩn Udemy | Mức độ ưu tiên |
| --- | --- | --- |
| **Task 1 (Progress)** | Bỏ trang `/my-progress` rời rạc; tích hợp % checklist vào Player Header + cấp Chứng chỉ khi $100\%$ | Thấp (Làm sau khi xong Curriculum) |
| **Task 4 (Import/Export)** | Cung cấp mẫu file Excel/CSV chuẩn; Live Preview cho Mermaid | Trung bình |
| **Task 10 (Multi-device)** | Thay nút "Ban thiết bị" thủ công bằng **Chặn phát video đồng thời tự động (Heartbeat Redis)** | Cao (Bảo vệ nội dung khóa học) |
| **Task 11B (Onboarding)** | Triển khai Onboarding Profile Wizard 3 bước trước khi tạo khóa học đầu tiên | Cao (Chặn spam tài khoản) |
| **Task 11A (Admin)** | Rate-limit AI dạng quota hàng ngày; Dashboard tập trung vào Moderation Queue & Token usage | Trung bình |

