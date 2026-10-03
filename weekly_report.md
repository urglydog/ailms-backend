# Báo cáo tuần — Demo tính năng mới cho Giáo viên

**Tuần:** 29/09/2026 – 03/10/2026
**Repo liên quan:** `be` (Spring Boot), `fe` (Next.js), `ai-worker` (FastAPI/Celery)

> File này tóm tắt các chức năng MỚI đã hoàn thành trong tuần, dùng để demo trực tiếp cho giáo viên mỗi Chủ nhật. Mỗi mục gồm: **Mô tả** (giải quyết vấn đề gì) và **Cách test** (bước demo cụ thể). Cập nhật lại nội dung này mỗi tuần — xoá mục tuần cũ hoặc dời sang file lưu trữ nếu cần giữ lịch sử.

---

## 1. Hệ thống Khuyến mãi — Coupon & Voucher

**Mô tả:** Admin/Giảng viên tạo mã giảm giá (theo % hoặc số tiền cố định), áp dụng tự động hoặc nhập mã tay lúc thanh toán, có giới hạn số lượt dùng tổng và số lượt dùng/người, tự hết hiệu lực theo ngày bắt đầu/kết thúc.

**Cách test:**
1. Đăng nhập Admin/Giảng viên → vào trang **Quản lý Coupon** → tạo 1 mã mới (vd `-20%`, giới hạn 1 lượt/người, còn hiệu lực).
2. Đăng nhập 1 tài khoản học viên → vào trang thanh toán 1 khóa học → nhập mã → giá tự giảm đúng %.
3. Thanh toán xong, thử áp lại mã đó lần 2 → bị báo đã dùng hết lượt.
4. Vào lại trang Admin Coupon → sửa ngày hết hạn về quá khứ → badge trạng thái đổi thành **"Hết hạn"** (khác với "Đang bật"/"Đã tắt") — tránh hiểu lầm mã vẫn dùng được.

---

## 2. Bán chéo & Gói khóa học — Bundles & Upsells

**Mô tả:** Giảng viên đóng gói nhiều khóa học thành 1 combo giá ưu đãi. Trang chi tiết khóa học gợi ý combo liên quan; giỏ hàng tự phát hiện khi học viên đã chọn đủ các khóa trong 1 gói và tự áp giá combo. Nếu học viên đã sở hữu sẵn 1 khóa trong gói, hệ thống tự trừ phần đã mua (pro-rated) để ra giá cuối hợp lý.

**Cách test:**
1. Giảng viên → tạo 1 Bundle gồm 2 khóa học, giảm 20%.
2. Học viên → vào trang chi tiết 1 trong 2 khóa → thấy widget gợi ý mua combo.
3. Thêm cả 2 khóa vào giỏ hàng → giỏ tự nhận diện đủ bộ, áp giá combo (giảm 20%) thay vì giá lẻ.
4. Thử case đã sở hữu sẵn 1 khóa trong gói → giá combo chỉ tính phần khóa còn thiếu, trừ đúng phần đã mua trước.
5. (Giá luôn do server tính lại — không tin số giá gửi từ trình duyệt, chống gian lận giá.)

---

## 3. Streak — Chuỗi ngày học liên tục

**Mô tả:** Ghi nhận và hiển thị số ngày học liên tục (streak) để tạo động lực học đều. Một ngày được tính "hoàn thành" khi học viên xem ≥10 phút video **hoặc** làm xong 1 quiz. Có cơ chế **Streak Freeze** tự động (tối đa 2 lần/tháng): nếu lỡ đúng 1 ngày không học, hệ thống tự "đóng băng" để không mất chuỗi. Có thông báo nhắc nhở vào cuối ngày nếu chưa học, tính theo múi giờ local của từng học viên (không hardcode giờ server).

**Cách test:**
1. Học viên đăng nhập → học 1 bài ≥10 phút hoặc làm 1 quiz → icon lửa 🔥 ở Header tăng lên, có hiệu ứng khi vừa đạt.
2. Hover vào icon lửa → xem dropdown: streak hiện tại, kỷ lục dài nhất, lịch 7 ngày gần nhất, và dòng chữ "còn N lần đóng băng streak trong tháng này".
3. (Demo cơ chế Freeze) — set `last_activity_date` của 1 user về 2 ngày trước trong DB rồi gọi lại `GET /api/v1/streak/me` → streak KHÔNG bị reset về 0, có toast "🧊 đã tự động đóng băng".
4. (Demo nhắc nhở) — `StreakReminderJob` chạy mỗi giờ, tự gửi thông báo qua chuông 🔔 vào khoảng 20h giờ local nếu chưa học hôm đó và vẫn còn streak cần giữ.

---

## 4. Đánh giá khóa học — AI Sentiment Filter + Report

**Mô tả:** Chỉ học viên đã học ≥20% khóa học mới được viết đánh giá (tránh review "dạo"). Sau khi đăng, hệ thống tự đẩy nội dung sang AI Worker (Gemini) để phát hiện review toxic/spam và tự ẩn nếu phát hiện vi phạm. Giảng viên có thể **Report** 1 review vi phạm trên khóa của mình — review bị ẩn ngay và vào hàng chờ cho Admin duyệt (chấp nhận ẩn hẳn hoặc khôi phục hiển thị).

**Cách test:**
1. Học viên mới enroll (chưa học gì) → thử viết review → bị chặn "cần học ít nhất 20%".
2. Học viên đã học ≥20% → viết review bình thường → review hiện công khai ngay, đồng thời được gửi ngầm cho AI kiểm duyệt (nếu nội dung toxic/spam sẽ tự ẩn sau vài giây).
3. Giảng viên → vào "Hiệu suất > Đánh giá" → bấm icon 🚩 Report trên 1 review → review biến mất khỏi trang công khai ngay.
4. Admin → vào trang **Quản lý Đánh giá** → chọn filter "Chờ duyệt (Giảng viên report)" → thấy review vừa report, chọn **Hiện lại** (từ chối report) hoặc **Giữ ẩn** (xác nhận report).

---

## 5. Lộ trình học cá nhân hóa bằng AI (AI Study Plan) + Xuất Calendar (.ics)

**Mô tả:** Học viên nhập ngày mục tiêu hoàn thành khóa + số giờ học/tuần, AI (qua ai-worker/Gemini) tự sinh lịch học chi tiết từng ngày (không trộn bài của 2 chương khác nhau trong 1 ngày). Nếu học trễ lịch, có nút **"Cập nhật tiến độ"** để AI tự tính lại lịch còn lại (thuật toán tại chỗ, không tốn thêm chi phí gọi AI). Có thể **xuất file .ics** để nhập lịch học vào Google Calendar/Outlook/Apple Calendar — mỗi lần xuất lại sau khi cập nhật tiến độ sẽ **ghi đè đúng sự kiện cũ** (không tạo trùng lặp) nhờ ID ổn định theo chuẩn iCalendar.

**Cách test:**
1. Học viên → vào 1 khóa đang học → tab "Lộ trình học" → nhập ngày mục tiêu (cách hôm nay ≥2 ngày) + số giờ/tuần → bấm Tạo lộ trình.
2. Xem lịch học theo ngày, mỗi ngày chỉ có bài của 1 chương.
3. Bấm **"Xuất Calendar (.ics)"** → tải file → mở bằng Google Calendar (Import) → thấy đúng số ngày/sự kiện với mô tả bài học.
4. Giả lập bị trễ lịch (bỏ qua vài ngày) → bấm "Cập nhật tiến độ" → lịch các ngày còn lại được dồn lại hợp lý, không mất bài đã học.
5. Xuất lại .ics lần 2 → import lại vào Calendar → các sự kiện cũ được **cập nhật tại chỗ**, không nhân đôi.

---

## 6. Phân tích điểm yếu sau Quiz (Knowledge Gaps Analysis)

**Mô tả:** Sau khi làm quiz, hệ thống nhóm các câu sai theo chủ đề (Topic Tag) và chỉ ra đúng mốc thời gian trong video lý thuyết liên quan để học viên ôn lại ngay. Chỉ tính dựa trên **5 lần thi gần nhất hoặc 30 ngày gần nhất** (không cộng dồn lỗi sai cũ khi học viên đã tiến bộ).

**Cách test:**
1. Học viên làm 1 quiz, trả lời sai có chủ đích ở 1 chủ đề cụ thể.
2. Vào tab **Bảng điểm** → widget "Điểm yếu cần ôn tập" hiện chủ đề vừa sai, kèm tỷ lệ sai (%).
3. Bấm nút **"Ôn tập lý thuyết"** → nhảy thẳng tới đúng mốc giây trong video bài giảng liên quan.
4. Làm lại quiz đó và trả lời ĐÚNG ở chủ đề vừa sai → vào lại Bảng điểm → chủ đề đó biến mất khỏi danh sách điểm yếu (không còn bị tính lỗi cũ).

---

## 7. Dashboard Giảng viên nâng cao — Doanh thu & Retention

**Mô tả:** Giảng viên xem được: báo cáo doanh thu gộp/phí nền tảng/thực nhận theo khoảng ngày tự chọn, top câu hỏi quiz có tỷ lệ sai cao (>60%) cần xem lại, và biểu đồ giữ chân người xem (Retention Heatmap) theo từng mốc 10% thời lượng video để biết đoạn nào học viên bỏ xem.

**Cách test:**
1. Giảng viên → "Hiệu suất" → **Doanh thu** → chọn khoảng ngày → xem đúng 3 số: Gross / Phí nền tảng / Thực nhận, khớp với các giao dịch PAID trong khoảng đó.
2. Vào **"Câu hỏi khó"** → xem danh sách câu quiz tỷ lệ sai > 60% trên các khóa của mình.
3. Vào **"Retention"** → chọn 1 bài giảng → xem biểu đồ % học viên còn xem tại mỗi 10% thời lượng video, phát hiện đoạn rớt nhiều.

---

## Các vá lỗi/hoàn thiện đáng chú ý trong tuần (không phải feature mới nhưng ảnh hưởng chất lượng demo)

- Chặn được lỗi "lách luật" coupon khi mở 2 tab thanh toán cùng lúc với cùng 1 mã giới hạn 1 lượt/người.
- Giảng viên không thể chỉnh % giảm giá Bundle ra giá trị âm hoặc ≥100% làm vỡ công thức tính tiền.
- Thống nhất lại cách tính "hôm nay" giữa Backend và AI Worker cho tính năng Study Plan (trước đây lệch múi giờ giữa 2 nơi).
- AI Worker tự kiểm tra lại cấu trúc JSON mà Gemini trả về trước khi gửi cho Backend, tránh lỗi mơ hồ khi AI trả sai định dạng.
