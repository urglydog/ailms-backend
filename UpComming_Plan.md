# Kế Hoạch Các Hạng Mục Còn Lại (Cập nhật 26/09/2026)

Các hạng mục cốt lõi (Anti-Cheat, Discovery, Auto-Ban) và các bug tồn đọng đều đã được giải quyết hoàn toàn ở các phiên trước.

File này giữ đúng 1 nơi duy nhất để ghi việc còn phải làm — cập nhật mỗi phiên làm việc, xoá mục nào xong.

---

## 🚀 Nâng cấp Kiến trúc Bảo mật & Tối ưu AI (Phân tích 25/09/2026 - Cần hiện thực)

*Ghi chú: Đây là kết quả từ cuộc thảo luận chuyên sâu về kiến trúc hệ thống và các véc-tơ tấn công (Attack Vectors). Cần hiện thực các ý tưởng này để biến hệ thống thành một sản phẩm có tính bảo mật/tối ưu thực tế cao (Production-ready), rất có giá trị cho báo cáo luận văn.*

### 1. Nâng cấp Anti-Cheat: Hybrid Event-Driven Architecture (AI Biên - Đám mây)
**Vấn đề / Phản biện từ user**: 
> "Nếu server-side chỉ dùng đúng 1 tấm hình sau mỗi 25s thì không tránh khỏi chụp đúng lúc người dùng chớp mắt hoặc lắc nhẹ đầu... mà nếu capture khung hình liên tục thì tốn tài nguyên. Khoảng 30 tấm hình cho 15 phút thi để ra kết luận là quá rủi ro. Liệu ta có thể tận dụng cái AI quét mỗi 2s ở client-side cho phía server-side không?"

**Thiết kế hiện thực (Cần làm)**:
- **Chuyển từ "Định kỳ" (Periodic 25s) sang "Hướng sự kiện" (Event-driven)**: 
  Sử dụng mô hình AI nhẹ ở FE (`face-api.js` quét mỗi 2s) làm "Cò súng" (Trigger).
- **Logic FE**: Thay vì gửi ảnh lên AI Worker mù quáng mỗi 25s, FE sẽ theo dõi kết quả của mô hình 2s. Nếu phát hiện `NO_FACE` hoặc `MULTIPLE_FACES` **liên tục trong 3 chu kỳ (6 giây)**, FE lập tức cắt 1 frame ảnh và gửi khẩn cấp lên Server (Gemini) để xác minh (Bypass chu kỳ chờ).
- **Throttling (Giới hạn tỷ lệ)**: Vẫn phải giữ một khoảng thời gian chờ tối thiểu (ví dụ: tối đa 1 request mỗi 15-20s) kể cả khi FE báo động liên tục, để chống spam API nếu mô hình FE bị nhiễu.
- **Bắt lỗi hướng nhìn (Gaze/Head turn)**: Vì model FE không bắt được hướng nhìn, vẫn giữ một chu kỳ gửi ảnh ngẫu nhiên (Randomized Polling: ví dụ random 30s-60s gửi 1 lần thay vì cố định 25s) để học viên không thể căn giờ gian lận.

### 2. Nâng cấp Auto-Ban: Chống tấn công Denial of Wallet (DoW) qua Prompt Injection
**Vấn đề / Phản biện từ user**:
> "Hết token/Spam request ở học liệu đã có Daily Quota và Redis Lock lo. Kẽ hở duy nhất là AI Discovery/Tutor. Kẻ gian có thể lách luật: Gửi một văn bản CỰC KỲ DÀI vào prompt và bọc bằng câu 'Tôi thấy nội dung này có trong khóa học XXX phải không?'. Nhờ có chữ 'khóa học XXX', nó qua mặt bộ lọc relevance, ép Server đọc 1 đống text rác dài ngoằng, làm cạn kiệt API key (Tốn tiền)."

**Thiết kế hiện thực (Defense in Depth - Cần làm)**:
- **Lớp 1: Giới hạn độ dài đầu vào (Hard Length Limit)**:
  - FE: Thêm thuộc tính `maxLength={1000}` (hoặc 500) vào tất cả các ô input chat AI (Tutor / Discovery).
  - BE/AI-Worker: Dừng ngay và trả về `400 Bad Request` nếu payload `len(prompt) > 1000` trước khi chạm vào bất kỳ logic AI hay Token router nào.
- **Lớp 2: Rút gọn ngữ cảnh (Context Truncation)**:
  - Ở AI-Worker, khi query Vector DB (Supabase), dù có tìm ra nhiều bài học liên quan, chỉ được phép nhồi tối đa **3 đoạn văn bản (chunks)** có độ tương đồng cao nhất vào System Prompt gửi cho Gemini. Không bao giờ gửi toàn bộ file nội dung.
- **Lớp 3: Nâng cấp Auto-Ban dựa trên Token (Token-based Tracking)**:
  - Hiện tại `AiLockScan` đang đếm số Request/phút. Cần nâng cấp: Đọc cột `total_tokens` trong bảng `ai_usage_logs`.
  - Nếu 1 user tiêu thụ quá mức bất thường (VD: > 50,000 tokens trong 5 phút), lập tức cắm cờ cảnh báo Auto-Ban, bất kể số lượng request là 1 hay 10.

---

## 🛠️ Chốt Cấu Hình Production & Cleanup (Đã thống nhất, chờ làm)

*Các hạng mục dưới đây đã được thảo luận và chốt phương án để tối ưu hóa cho mục tiêu bảo vệ luận văn (chạy thật, miễn phí, dễ setup, UI gọn gàng). Các agent ca sau cần bám sát cấu hình này để code.*

### 1. Dọn dẹp UI Cổng thanh toán
- **Yêu cầu**: Xóa hoàn toàn các phương thức thanh toán ảo/chưa kích hoạt (VNPAY, Momo, ZaloPay) khỏi giao diện Frontend.
- **Lý do**: Không có Giấy phép ĐKKD nên không thể đưa lên môi trường thật (Production) cho các cổng này. Giữ lại nút bấm ảo sẽ làm hội đồng bắt bẻ.
- **Hành động**: Chỉ giữ lại duy nhất phương thức **PayOS (Chuyển khoản VietQR)** làm phương thức thanh toán chính thức vì nó đã chạy thật thành công. Xóa/ẩn UI liên quan đến VNPAY/Momo/ZaloPay trên FE.

### 2. Thay thế Mailpit bằng Gmail SMTP
- **Yêu cầu**: Backend cần gửi OTP thật đến email người dùng thay vì dùng server local Mailpit.
- **Lý do**: Cần một hệ thống gửi mail thật, hoàn toàn miễn phí, và không yêu cầu xác thực Tên miền (Domain).
- **Hành động**: 
  - Cập nhật cấu hình `spring.mail.*` trong `application.yml` (hoặc tạo `application-prod.yml`).
  - Sử dụng Host: `smtp.gmail.com`, Port: `587`, bật `smtp.auth=true` và `smtp.starttls.enable=true`.
  - Hướng dẫn User lấy Mật khẩu ứng dụng (App Password) của Gmail và đưa vào Environment variables.

### 3. Bổ sung Chính sách cho Google OAuth Consent Screen
- **Yêu cầu**: Màn hình đăng nhập Google phải chuyên nghiệp (hiện logo, có link chính sách).
- **Quyết định**: **KHÔNG CẦN CODE**. 
- **Hành động**: Đây là việc setup trên Portal. Cần nhắc User đăng nhập Google Cloud Console -> OAuth Consent Screen -> Upload Logo dự án, điền link giả định vào ô *Privacy Policy* và *Terms of Service*. Code FE/BE hiện tại đã chuẩn.

### 4. Đăng nhập Mạng xã hội (Facebook/Zalo)
- **Quyết định**: **HỦY BỎ (Không làm)**.
- **Lý do**: Google và Email/Password đã giải quyết 99% nhu cầu. Quá trình setup Facebook/Zalo tốn nhiều thời gian (cần verify app rườm rà) mà không mang lại thêm nhiều "điểm cộng" cho luận văn. Sẽ dành thời gian tập trung trau chuốt các tính năng AI cốt lõi.

---

## 🧹 Các task dọn dẹp nhỏ (Background)
- [ ] Job Celery tự xoá video proctoring quá hạn 30-90 ngày — video hiện lưu vô thời hạn trên B2, cần xoá tự động để tránh đầy ổ cứng.
