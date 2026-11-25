# Kết quả rà soát bảo mật (26/11/2026)

Phạm vi: máy chủ Hetzner, Caddy, backend Spring Boot, AI worker, FE (Next.js), mobile. Phương pháp: đo cấu hình thật trên server, đọc mã nguồn, thử gọi API không đăng nhập, quét thư viện (Trivy, `npm audit`). **Không phải kiểm thử xâm nhập chuyên nghiệp**; không có nghĩa là hệ thống không còn lỗ hổng.

## 1. Đã sửa trong đợt này
| Vấn đề | Mức | Cách xử lý |
| --- | --- | --- |
| Chat bài học qua WebSocket: ai cũng gửi được, tự khai `senderId`/tên → **giả danh người khác kể cả giảng viên** | Cao | Gửi tin phải có JWT; danh tính lấy từ phiên đã xác thực, tên lấy từ DB; có test |
| `GET /api/v1/lessons/{id}/chats` công khai → lộ tên, id, nội dung chat mọi bài học | Trung bình | Yêu cầu đăng nhập |
| `GET /api/v1/lessons/{id}/player` khách nhận lỗi 500 | Thấp | Yêu cầu đăng nhập (403) |
| Vùng GET công khai dùng ký tự đại diện (`/lessons/**`, `/courses/**`) nên endpoint mới quên `@PreAuthorize` sẽ tự công khai | Hệ thống | Test chốt chặn `PublicGetEndpointsGuardTest`: endpoint công khai phải nằm trong danh sách có chủ ý |
| `/api/internal/*` và `/actuator/*` có thể đi qua Caddy ra Internet (đã có token/401 nhưng thiếu lớp thứ hai) | Trung bình | Caddy trả 404; AI worker vẫn gọi nội bộ qua `backend:8080` |
| Thiếu header bảo mật (HSTS, CSP, Referrer-Policy, Permissions-Policy) | Trung bình | Thêm ở Caddy |
| Không có sao lưu tự động; sao lưu ngoài máy định đẩy lên bucket công khai | Cao | Sao lưu mã hoá hằng đêm + khôi phục thử; **không** dùng bucket media công khai (đã xoá file thử) |
| `ai-api` chạy `--reload`, Redis không giới hạn bộ nhớ, container không giới hạn RAM/log | Trung bình | Đã giới hạn; bỏ `--reload` |
| Dịch vụ `frontend` trong compose có thể bị bật nhầm bởi `docker compose up -d` | Vận hành | Đặt profile riêng |

## 2. Hiện trạng đã tốt
HTTPS Let's Encrypt, HTTP→HTTPS; SSH chỉ khoá, tắt root, fail2ban (3 lần sai → khoá 7 ngày); `.env` quyền 600, khoá dài ngẫu nhiên; mật khẩu băm BCrypt (cost 12); khoá tài khoản khi đăng nhập sai; giới hạn tần suất; kiểm tra loại file tải lên (Tika, magic bytes); `anyRequest().authenticated()` mặc định từ chối; 114/152 endpoint cần đăng nhập có `@PreAuthorize`; webhook PayOS kiểm chữ ký; token nội bộ cho AI worker; backend chạy user thường; 207 endpoint đã liệt kê, chỉ 17 GET công khai có chủ ý.

## 3. Còn mở — cần bạn quyết định hoặc làm
| Việc | Ai | Ghi chú |
| --- | --- | --- |
| **CORS cho phép `https://*.vercel.app` + `localhost` kèm credentials** trong `ALLOWED_ORIGINS` | Chủ dự án (sửa `.env`) | Chỉ giữ `https://ailms-web-client.vercel.app`; sau đó tạo lại backend |
| **Cổng 8443** còn mở và phục vụ cùng backend | Chủ dự án | Kiểm `NEXT_PUBLIC_API_URL` (Vercel) và mobile không dùng `:8443`, rồi đóng bằng Hetzner Firewall |
| Hetzner Cloud Firewall (22/80/443) và **Hetzner Backups** | Chủ dự án | Sao lưu ngoài máy hiện chưa có đến khi tạo bucket B2 **riêng tư** (`SAO_LUU_VA_KHOI_PHUC.md`) |
| Xác thực 2 lớp: Hetzner, GitHub, Vercel, Google, B2, PayOS | Chủ dự án | |
| **FE: Next.js 15.5.22 có lỗ hổng mức CRITICAL**; bản vá nhỏ 15.5.29 | Chủ dự án | Không `npm install` trên server; chạy `npm install next@15.5.29` ở máy cá nhân, kiểm tra rồi push để Vercel build |
| Lịch sử chat/chat trực tiếp bài học cho khách *nghe* (topic công khai) | Quyết định sản phẩm | Hiện cho phép (test cũ ghi chủ ý); nếu chỉ học viên đã ghi danh mới được xem cần thêm kiểm ghi danh |
| Backend: thư viện cũ — **66 lỗ hổng HIGH/CRITICAL đã có bản vá** (Tomcat 10.1.34, Spring 6.2.1 / Security 6.4.2, Netty 4.1.116, Jackson 2.18.2, Tika 2.9.2 có 1 CRITICAL) | Tuỳ thời gian | Cách vá là nâng Spring Boot 3.4.1 → 3.5.x (lên bản cấp dưới) và Tika; phải chạy lại toàn bộ test và phát hành lại |
| AI worker/ai-api: 67 HIGH (không CRITICAL), phần lớn gói hệ điều hành (krb5, zlib, zstd); thư viện Python `starlette` (3), `python-multipart` (3) | Tuỳ thời gian | Cập nhật `requirements` và dựng lại image; AI API chỉ nghe localhost, không ra Internet trực tiếp |
| FE `npm audit`: 11 HIGH (mermaid/langium/chevrotain, sharp, postcss, lodash-es) ngoài Next; Mobile: 2 HIGH (`linkify-it`, `markdown-it`, chưa có bản vá) | Tuỳ thời gian | Nằm ở thư viện biên dịch/hiển thị, không phải API |
| Rà phân quyền từng endpoint **bằng tay** (quyền sở hữu/IDOR) | Chưa làm | Công cụ chỉ phát hiện thiếu xác thực, không phát hiện "người A đọc dữ liệu người B" khi đã đăng nhập |
| Quét ứng dụng động (OWASP ZAP) | Chưa làm | |
| Giới hạn độ dài/tốc độ tin nhắn chat WebSocket | Chưa làm | Hiện không giới hạn độ dài nội dung tin |
