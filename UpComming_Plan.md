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
*   **Phân tích điểm yếu (Knowledge Gap), Adaptive Testing, Gamification, SCORM:** Độ khả thi **Thấp - Rất Khó**. Cần cấu trúc dữ liệu đồ sộ (tagging chi tiết cho từng câu hỏi), real-time engine phức tạp.
*   👉 ### 3. Học liệu - Quiz 
- ✅ **Trạng thái**: Đã hoàn thành
- **Tiến độ**:
  - Giao diện bài thi (có/không giới hạn thời gian).
  - Tích hợp Socratic Tutor giải thích đáp án sai.

### 3.3 Nhóm Mindmap
*   **Xuất ảnh (PNG/PDF):** Độ khả thi **Cao**. Dễ dàng thực hiện bằng các thư viện Frontend chụp màn hình canvas/SVG (ví dụ: html2canvas).
*   **Mở rộng bằng AI (Node Expansion):** Độ khả thi **Trung bình**. Cần xử lý UI phức tạp (Right click -> gọi API LLM -> chèn thêm node vào biểu đồ Mermaid/React Flow).
*   **Thuyết trình, Biên tập cộng tác (Real-time), Đính kèm file:** Độ khả thi **Thấp**. Đòi hỏi xây dựng hệ thống WebSocket đồng bộ trạng thái như Google Docs.
*   👉 *Kế hoạch triển khai (Ngắn hạn):* Thêm nút Export hình ảnh/PDF. Chỉnh sửa UI hiển thị Mindmap rõ ràng, đẹp mắt hơn. Đưa các tính năng khác vào Dài hạn.

---

## 4. Cân Bằng Quyền Hạn, Vai Trò (Instructor & Admin)
**🔴 Vấn đề hiện tại:**
- Đang trao quá nhiều quyền hạn tiêu tốn tài nguyên (API Key, LLM Token) cho Học viên, dễ dẫn đến quá tải hệ thống hoặc vượt hạn mức chi phí.
- Giảng viên thiếu công cụ để tự tổ chức các bộ đề chuẩn cho khóa học.

**🟢 Độ khả thi:** Rất Cao (Thiên về kiến trúc và cấu quyền truy cập).

**🛠 Cách triển khai:**
- **Phân quyền sinh học liệu:**
  - **Giảng viên (Instructor):** Là người nắm quyền **chính** trong việc sinh ra bộ Quiz, Flashcard, Mindmap chuẩn (Official Materials) cho khóa học của mình. Giảng viên cấu hình độ khó, số lượng, lưu lại làm tài nguyên dùng chung.
  - **Học viên (Student):** Học viên sử dụng các bộ học liệu chuẩn do Giảng viên tạo ra. Chỉ cung cấp cho học viên quyền sinh học liệu "Tùy chỉnh cá nhân" với **giới hạn rất khắt khe** (BR-MAT-08).
- **Hệ thống tổ chức thi (Quiz Engine):** Cập nhật bảng `quizzes` có thêm cờ `is_official`, cho phép Giảng viên tạo ngân hàng câu hỏi (100 câu), hệ thống tự động bốc ngẫu nhiên (30 câu) cho mỗi lần học viên làm kiểm tra. Lấy điểm cao nhất.
- **Admin Dashboard:** Bổ sung tính năng theo dõi chi phí API / số lượng token sinh ra theo từng người dùng (Giảng viên/Học viên). Thêm nút "Khóa quyền AI" đối với các tài khoản có dấu hiệu spam. 

---

## 🚀 ROADMAP THỰC THI NGẮN HẠN (Sắp xếp theo thứ tự)
1. ✅ **[AI-Discovery]** Fix lỗi Function Calling và RAG để AI tìm đúng khóa học. (Hoàn thành)
2. ✅ **[Học liệu - Scope & Language]** Chuẩn hóa dropdown chọn chương bài học và lọc động danh sách ngôn ngữ dưa trên transcript hiện có. (Hoàn thành)
3. ✅ **[Học liệu - Quiz]** Xây dựng hệ thống làm bài Quiz, chấm điểm, lưu lịch sử, cho phép Giảng viên tạo ngân hàng câu hỏi. (Hoàn thành)
4. ✅ **[Học liệu - Flashcard]** Triển khai thuật toán Spaced Repetition (SM-2) nhắc nhở học viên. (Hoàn thành)
5. 🟡 **[Quyền hạn]** Cập nhật giao diện Instructor để quản lý bộ học liệu, cập nhật Admin để tracking tài nguyên LLM. (Đang tiến hành)
