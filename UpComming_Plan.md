# UPCOMING PLAN - KẾ HOẠCH PHÁT TRIỂN TIẾP THEO

Tài liệu này ghi chú lại các vấn đề hiện đọng và định hướng mở rộng các tính năng cốt lõi của nền tảng AI-Powered LMS. Kế hoạch được phân rã dựa trên mức độ khả thi và thứ tự ưu tiên triển khai.

---

## 1. Cải Tiến AI Course Discovery (Agent Tìm Kiếm)
**🔴 Vấn đề hiện tại:**
- AI Discovery đang hoạt động kém hiệu quả, hành xử như một thanh công cụ tìm kiếm keyword truyền thống thay vì một trợ lý thông minh. 
- Lỗi không tìm thấy khóa học khi gõ "tiếng anh" dù khóa học có tồn tại (như quan sát trên giao diện). Việc bóc tách keyword và lọc (filter) đang gặp vấn đề.

**🟢 Độ khả thi:** Rất cao (Có thể triển khai ngay lập tức).

**🛠 Cách triển khai:**
- **Nâng cấp Function Calling:** Cấu hình lại prompt của LLM để AI thực sự phân tích ngữ nghĩa (intent) thay vì chỉ nhặt keyword. 
- **Tích hợp Vector Search (RAG) hoặc Full-text Search mạnh hơn:** Nếu người dùng hỏi "tiếng anh", AI cần gọi function tìm kiếm với đa dạng biến thể (English, Tiếng Anh giao tiếp, IELTS...) hoặc map trực tiếp vào Category ID phù hợp.
- **Fallback logic:** Đảm bảo function gọi DB trả về kết quả chính xác, AI lấy danh sách đó format lại thành ngôn ngữ tự nhiên thay vì tự bịa ra câu trả lời chung chung "Dưới đây là một số khóa học" nhưng danh sách thì rỗng.

---

## 2. Tối Ưu UX/UI và Logic Sinh Học Liệu (Scope & Ngôn ngữ)
**🔴 Vấn đề hiện tại:**
- **Phạm vi (Scope):** Người dùng không phân biệt được sự khác nhau giữa sinh "toàn khóa học" và "từng chương". Thiếu tính năng cho phép chọn sinh học liệu cho một chương cụ thể hoặc một bài học cụ thể. 
- **Ngôn ngữ (Language):** Chỉ fix cứng (hardcode) Tiếng Anh và Tiếng Việt. Đôi khi chọn Tiếng Anh nhưng Mindmap ra Tiếng Việt do video chưa từng được lồng tiếng/tạo transcript tiếng Anh.

**🟢 Độ khả thi:** Cao.

**🛠 Cách triển khai:**
- **Mở rộng dropdown Phạm vi:** Thay vì chỉ có "Theo chương", phải là "Chọn chương: [Chương 1, Chương 2...]". Nếu có thể, hiển thị cấu trúc cây (Tree Select) cho phép tick chọn đích danh các bài học muốn sinh học liệu để tránh lãng phí token LLM.
- **Động hóa danh sách Ngôn ngữ:** Dropdown ngôn ngữ khi sinh học liệu **chỉ hiển thị các ngôn ngữ mà khóa học (hoặc video đó) đã có sẵn transcript trên CloudCDN**. Backend cần truy vấn bảng `audio_tracks` hoặc `transcripts` để trả về danh sách ngôn ngữ hợp lệ.
- **Xử lý ngoại lệ (Validation):** Chặn hoàn toàn việc sinh học liệu bằng ngôn ngữ chưa được lồng tiếng. Hiện cảnh báo: "Vui lòng lồng tiếng khóa học sang ngôn ngữ [X] trước khi sinh học liệu bằng ngôn ngữ này".

---

## 3. Đánh Giá Khả Thi Mở Rộng Tiện Ích Flashcard, Quiz & Mindmap

Dưới đây là đánh giá quy mô của các tính năng đề xuất. Để phù hợp với năng lực hệ thống hiện tại, kế hoạch sẽ chia làm 2 giai đoạn (Ngắn hạn & Dài hạn).

### 3.1 Nhóm Flashcard
*   **Xuất/Nhập (Anki/Quizlet), Text-to-Speech (TTS), lật thẻ 2 chiều, Rich Media:** Độ khả thi **Trung bình - Cao**. Dễ làm ở Frontend. TTS có thể dùng Web Speech API miễn phí của trình duyệt.
*   **Hệ thống ôn tập ngắt quãng (SRS - SM-2):** Độ khả thi **Rất Cao**. (Thực tế Business Rule BR-CARD-01 đã có thiết kế này, chỉ cần code logic Backend/Redis để tính toán ngày ôn tập).
*   👉 *Kế hoạch triển khai (Ngắn hạn):* Tập trung làm thuật toán SRS (SM-2), lật thẻ 2 chiều và TTS bằng Web API. Các tính năng Import/Export đẩy xuống Dài hạn.

### 3.2 Nhóm Quiz (Trắc nghiệm)
*   **Lưu lịch sử, chấm điểm, xem đáp án:** Độ khả thi **Rất Cao**. Đây là tính năng bắt buộc phải có cho một LMS tiêu chuẩn.
*   **Giải thích chuyên sâu (AI Socratic Tutor):** Độ khả thi **Cao**. Tận dụng lại module Socratic Tutor hiện có, gọi prompt phụ khi học viên làm sai.
*   **Phân tích điểm yếu (Knowledge Gap), Adaptive Testing, Gamification, SCORM:** Độ khả thi **Thấp - Rất Khó**. Cần cấu trúc## 4. Quy Trình Xuất Bản Khóa Học & Tích Hợp Giám Sát Thi Cử AI (Anti-Cheat Proctoring)

**🔴 Kết Quả Kiểm Tra Mã Nguồn & Tích Hợp Tính Năng Giám Sát Thi Cử AI:**

### 1. Kiểm Tra Mã Nguồn Frontend (`AntiCheatExamPage` - `app/(student)/exam/[quizId]/page.tsx`):
- FE **ĐÃ CÓ SẴN** module giám sát thi cử thông minh bằng AI tích hợp thư viện `@vladmandic/face-api`:
  1. **Nhận diện Khuôn mặt AI Continuous Loop:** Quét webcam mỗi 2 giây, cảnh báo khi `NO_FACE` (không thấy mặt) hoặc phát hiện nhiều hơn 1 người trong khung hình.
  2. **Theo dõi Chuyển động & Hành vi Vi phạm:** Phát hiện chuyển tab (`visibilitychange`), mất tiêu điểm cửa sổ (`blur`), rời chuột khỏi viền màn hình (`mouseleave`).
  3. **Tự Động Đóng Bài Thi (Auto-Close/Submit):** Vi phạm quá 3 lần (`violationCount >= 3`) -> Hệ thống tự động thu bài và chấm điểm lập tức.

---

### 2. Tích Hợp Vào Màn Hình Cấu Hình Bài Thi Của GIẢNG VIÊN:
- Trong **Modal Cấu Hình Quiz** phía Giảng viên (`/instructor/courses/[id]/materials`):
  - Bổ sung công tắc (Toggle): **[ 🟢 Bật Giám Sát Thi Cử AI (Camera & Tab Tracking) ]**.
  - Bổ sung ô cấu hình: **[ Số lần vi phạm tối đa trước khi tự đóng bài ]** (Mặc định: `3` lần).
  - Khi Giảng viên bật tính năng này, đề thi Official sẽ tự động bắt buộc Học viên phải bật Camera và chạy qua bộ lọc AI Anti-Cheat khi làm bài.

---

### 3. Bảng Tổng Hợp Công Việc Sắp Thực Hiện (Task 5 Comprehensive Plan):

- **Bước 1: Triển khai Backend Shared Language Pool & Dubbing Cache**
  - Tận dụng `DubbingLockService` & `DubbingRequestService` hiện có. Cung cấp API ngôn ngữ sẵn có cho Giảng viên.

- **Bước 2: Xây Dựng Màn Hình Quản Lý Học Liệu Official Cho Giảng Viên (`/instructor/courses/[id]/materials`)**
  - Khung bấm sinh AI Official (Quiz, Flashcard, Mindmap).
  - Full Edit CRUD câu hỏi & đáp án đề thi Official.
  - Modal Cấu hình Bài thi tích hợp **Công tắc Bật/Tắt Giám sát Thi cử AI (Camera Proctoring)**, bộ chọn ngày giờ, chặn số âm.

- **Bước 3: Xây Dựng Màn Hình Gradebook & Tracking Chi Tiết (`/instructor/courses/[id]/gradebook`)**
  - Thống kê tiến độ lớp học.
  - Bảng điểm và Modal **[Xem Chi Tiết Bài Làm]** soi lại từng câu nộp của học viên.

- **Bước 4: Chuẩn Hóa Trải Nghiệm Học Viên & Anti-Cheat Exam**
  - Học viên thi Quiz Official với giao diện AI Proctoring (nếu Giảng viên bật).
  - Học liệu tự luyện cá nhân nằm trong tủ cá nhân, bị giới hạn 6 lượt/ngày (`BR-MAT-08`), không bị đẩy sang trang Giảng viên.

- **Bước 5: Admin LLM Resource Analytics & Dynamic Quantity Scaling**
  - Áp trần số câu hỏi theo Word Count bài giảng để chống AI Hallucination.
  - Log token (`ai_usage_logs`) và công tắc khóa AI (`is_ai_locked`).

---

## 🚀 ROADMAP THỰC THI NGẮN HẠN (Sắp xếp theo thứ tự)
1. ✅ **[AI-Discovery]** Fix lỗi Function Calling và RAG để AI tìm đúng khóa học. (Hoàn thành)
2. ✅ **[Học liệu - Scope & Language]** Chuẩn hóa dropdown chọn chương bài học và lọc động danh sách ngôn ngữ dựa trên transcript hiện có. (Hoàn thành)
3. ✅ **[Học liệu - Quiz]** Xây dựng hệ thống làm bài Quiz, chấm điểm, lưu lịch sử cho Học viên. (Hoàn thành)
4. ✅ **[Học liệu - Flashcard]** Triển khai thuật toán Spaced Repetition (SM-2) nhắc nhở học viên. (Hoàn thành)
5. 🟡 **[Task 5 Full Execution]** Triển khai Kho Ngôn Ngữ Dùng Chung, Trang Materials & Gradebook Giảng viên (tích hợp Cấu hình Anti-Cheat Proctoring), và Admin LLM Tracking. (Đang chờ DUYỆT TỪ BẠN để bắt đầu code!)








ích hợp bộ chọn Thời gian trực quan (Time Wheel/Spinner hoặc Hour:Minute picker) giúp Giảng viên chọn giờ chính xác mà không cần gõ phím thô.
- **Tự động tính Thời gian đóng bài:**
  - Ngay khi Giảng viên chọn "Thời gian mở bài" + "Thời gian làm bài", hệ thống **tự động tính sẵn và điền** `Thời gian đóng bài = Thời gian mở bài + Thời gian làm bài`. Vẫn cho phép Giảng viên điều chỉnh nới rộng khung giờ làm bài nếu cần.
- **Khắc phục lỗi HTTP Response Save Settings:**
  - Sửa Backend `QuizController` để trả về JSON response hợp lệ (ví dụ: `{"message": "Quiz settings updated successfully"}`), khắc phục triệt để lỗi `JSON.parse`.

### 5.3. Admin Dashboard & Tracking Tài Nguyên LLM
- **Bảng `ai_usage_logs`:** Ghi nhận token, cost, model, userId mỗi lượt gọi LLM.
- **Màn hình Analytics Admin:** Báo cáo chi tiết tài nguyên AI tiêu thụ theo thời gian và theo user.
- **Khóa quyền AI:** Nút công tắc "Khóa quyền AI" (`is_ai_locked`) cho Admin khóa các tài khoản lạm dụng.

---

## 🚀 ROADMAP THỰC THI NGẮN HẠN (Sắp xếp theo thứ tự)
1. ✅ **[AI-Discovery]** Fix lỗi Function Calling và RAG để AI tìm đúng khóa học. (Hoàn thành)
2. ✅ **[Học liệu - Scope & Language]** Chuẩn hóa dropdown chọn chương bài học và lọc động danh sách ngôn ngữ dựa trên transcript hiện có. (Hoàn thành)
3. ✅ **[Học liệu - Quiz]** Xây dựng hệ thống làm bài Quiz, chấm điểm, lưu lịch sử, cho phép Giảng viên tạo ngân hàng câu hỏi. (Hoàn thành)
4. ✅ **[Học liệu - Flashcard]** Triển khai thuật toán Spaced Repetition (SM-2) nhắc nhở học viên. (Hoàn thành)
5. 🟡 **[Quyền hạn & UX Instructor & Admin Tracking]** Tái cấu trúc phân quyền học liệu Instructor (Official vs Personal), bổ sung Preview học liệu, tối ưu toàn diện UI/UX Modal Cấu hình Quiz, và xây dựng Admin LLM Tracking. (Đang chờ duyệt Plan mới)

