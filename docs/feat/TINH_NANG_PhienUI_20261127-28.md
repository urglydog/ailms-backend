# Tính năng backend thêm trong phiên UI (26–28/11/2026)

Phần lớn đã nằm trong `develop`. Migration liên quan: V175–V178.

## Gợi ý câu hỏi cho gia sư AI
- Thứ tự ưu tiên: **POPULAR > AI > TEMPLATE**.
- POPULAR là câu hỏi nhiều người hỏi, chỉ hiện khi có từ **2 học viên khác nhau** trở lên, để giữ riêng tư.
- AI là câu hỏi do Gemini sinh theo nội dung bài. Kết quả được cache theo bài ở bảng `lesson_ai_suggestions`, kèm fingerprint nội dung; một job nền sinh lại khi bài đổi. Nhờ vậy không gọi Gemini mỗi lần học viên mở bài, giúp tiết kiệm chi phí.
- TEMPLATE là câu mẫu dự phòng khi chưa có hai loại trên.

## Insight AI cho giảng viên (`TutorInsightAnalyzer`)
- Gom câu hỏi của học viên theo chủ đề. Nhãn chủ đề là cụm 2–4 âm tiết tiếng Việt, không cắt giữa từ. Các chủ đề trùng nhãn được gộp lại (`fdc477b`, `fcd7981`).
- Tìm các câu hỏi gia sư trả lời kém (NOT_COVERED = bài học không đề cập, REPEAT = học viên phải hỏi lại) và các bài học có số câu hỏi bất thường.
- Phần tóm tắt và đề xuất do AI viết được cache ở `course_tutor_insights`. Sau 10 phút mới được tạo lại, để kiểm soát chi phí Gemini.

## Hỏi đáp trong bài
- Lưu trạng thái đã đọc và câu trả lời hay nhất (V178).
- Bộ lọc: chưa đọc, chưa có câu trả lời hay nhất, giảng viên chưa trả lời.
- Khi chọn câu trả lời hay nhất, hệ thống phát realtime qua `/topic/lesson/{id}/best-answer`.

## Khác
- Thêm cờ `viewerIsOwner` vào DTO chi tiết khóa và trình phát, để FE hiện nút dành cho chủ khóa thay vì nút mua.
- Trả thêm `learningOutcomes`, `requirements`, `targetAudience` (mục tiêu khóa học) ra API công khai.
- Sửa lỗi 403 ở bảng điểm (gradebook).
- Sửa nhãn Insight bị cắt giữa từ hoặc bị lặp.
