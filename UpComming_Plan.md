# Kế Hoạch Phát Triển Nâng Cao LMS (Premium Commercial Roadmap)

Tài liệu này định nghĩa chi tiết các Epic/User Story để nâng cấp hệ thống LMS lên tiêu chuẩn thương mại cao cấp. Các tính năng được chia theo Sprint dựa trên ma trận ưu tiên: **Hạ tầng chống chịu -> Dòng tiền -> Tính năng giữ chân/AI**.

Trước mỗi tính năng đều đi kèm với **Bối cảnh & Nỗi đau (Pain Point)** để đội ngũ kỹ thuật/vận hành luôn nắm rõ bài toán cốt lõi.

> **Lưu ý:** Các task đã hoàn thành (Redis Cache, Coupons, Bundles, Streak, AI Sentiment Filter, AI Study Plan, Knowledge Gaps, Instructor Dashboard) đã được dọn khỏi file này để giữ chỗ cho task mới — xem lại chi tiết triển khai/ngày hoàn thành trong lịch sử commit của từng repo (`be`/`fe`/`ai-worker`) hoặc trong `weekly_report.md` (tóm tắt theo tuần để demo).

---

## BACKLOG / TASK MỚI

### Epic: Roadmap Mobile/Responsive + Offline Learning (định hướng — chưa code)

**Bối cảnh & Nỗi đau:** Plan ban đầu của dự án là hoàn thiện web trước rồi mới chuyển sang di động, để hiện thực tính năng xem học liệu offline (vd: học viên ôn Flashcard trên máy bay không có mạng). Giờ web đã đủ trưởng thành để bắt đầu nghĩ tới bước chuyển này, nhưng CHƯA quyết định hướng kỹ thuật (PWA hay app native riêng) — quyết định này ảnh hưởng toàn bộ cách thiết kế offline-sync sau này nên cần chốt sớm.

**User Story:** Là học viên, tôi muốn ôn lại Flashcard đã lưu ngay cả khi không có mạng (vd đang trên máy bay), và trải nghiệm web trên điện thoại phải mượt như app thật.

**Đề xuất hướng kỹ thuật (cần confirm trước khi bắt đầu Sprint nào thuộc mục này — thay đổi kiến trúc lớn theo mục 1 CLAUDE.md):**
1. **Giai đoạn 1 — PWA hoá web hiện có:** thêm Service Worker + Web App Manifest, cho phép "Add to Home Screen" trên iOS/Android; đây là bước rẻ nhất vì tái dùng 100% code Next.js hiện tại, không cần stack mới.
2. **Giai đoạn 2 — Offline cache cho Flashcard:** dùng IndexedDB lưu bộ Flashcard học viên đã mở gần đây (không cache toàn bộ học liệu — chỉ cache thứ học viên chủ động "Lưu để học offline"), đồng bộ lại kết quả ôn tập (spaced repetition) khi có mạng trở lại. Phân loại rõ API nào BẮT BUỘC online (quiz có chấm điểm, AI chat/Gia sư AI, bất kỳ hành động ảnh hưởng điểm/XP) vs API được phép cache cho offline (nội dung Flashcard, tài nguyên tĩnh).
3. **Giai đoạn 3 — Quyết định PWA tiếp tục hay tách app native:** nếu nhu cầu người dùng đẩy cao hơn PWA đáp ứng được (vd cần push notification mạnh hơn, hiệu năng camera/mic cho tính năng ghi âm luyện nói...), mới cân nhắc React Native/Expo riêng — tái dùng toàn bộ BE API hiện có, không viết lại backend.

**Lưu ý kỹ thuật xuyên suốt cả 3 giai đoạn (đã có tiền lệ lỗi thật — xem mục 3 CLAUDE.md):** mọi Web API mới (storage quota, camera/mic...) và mọi heuristic dựa vào kích thước/hành vi cửa sổ trình duyệt đều phải tự hỏi "chạy đúng trên Safari iOS/iPadOS và Android Chrome không" TRƯỚC khi code, có feature-detect + fallback graceful, không giả định API luôn sẵn có.

**Acceptance Criteria (của riêng việc LÊN KẾ HOẠCH — chưa phải code):**
- Đã chốt được hướng Giai đoạn 1 (PWA) có triển khai ngay hay chưa, và mốc thời gian dự kiến.
- Đã liệt kê được danh sách API nào bắt buộc online / được phép cache offline (ít nhất cho tính năng Flashcard) trước khi bắt tay viết Service Worker.

---

## TASK CÒN DANG DỞ / CẦN THEO DÕI TIẾP

*(hiện không có — các vá lỗi độ hoàn thiện ngày 03/10/2026 cho Streak/Review AI/Coupon/Bundle/Study Plan đã đóng toàn bộ AC còn thiếu)*

---

Tip bảo mật data: check request nào thường xuyên hoặc truy xuất lượng dữ liệu lớn => AI cảnh báo admin
