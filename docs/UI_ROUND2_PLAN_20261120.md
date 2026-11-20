# Đợt chỉnh 2 sau nghiệm thu (20/11/2026) — rà soát & kế hoạch

Nguồn: phản hồi nghiệm thu đợt 1. **Đã duyệt (kể cả đường công khai ở D4) và ĐÃ TRIỂN KHAI 20/11/2026, chờ nghiệm thu.**

## Ghi chú thực hiện
- Khác kế hoạch: Q&A giảng viên phân trang phía máy chủ nhưng ô tìm kiếm chỉ lọc trong trang đang xem (tìm toàn bộ cần sửa truy vấn BE, để lượt sau). Danh mục, mã giảm giá, gói khóa học, khiếu nại/thông báo/bài tập của giảng viên, khuyến mãi tìm + lọc + phân trang ở trình duyệt (danh sách nhỏ do chính chủ sở hữu). Giọng đọc lồng tiếng vốn đã có tìm kiếm + "Hiển thị thêm".
- Mã truy cập chứng từ là chuỗi ngẫu nhiên 256-bit lưu trong DB (không cần khóa bí mật mới trong `.env`); gốc API công khai ở `lms.public-api-base-url` (mặc định domain server hiện tại).
- Chứng từ phát hành trước đó được cấp mã bằng job nền; PDF của chúng có QR từ lần tải sau job chạy (trong vài phút sau khi khởi động).
- Báo cáo tài chính PDF chỉ có mã kiểm (checksum) trong đợt này nếu cần; QR chỉ cho chứng từ vì báo cáo không lưu cố định.

## Quyết định đã chốt với chủ dự án
- QR trên chứng từ mở **thẳng file PDF** trong trình duyệt; **một QR duy nhất, ai quét cũng thấy y như bản gửi cho người dùng** (không ẩn tên, không phân quyền theo vai trò).
- Thông báo hệ thống: **Sửa nội dung + Thu hồi** (thu hồi = ẩn khỏi người dùng, giữ bản ghi ở admin).
- Trang Người dùng: chỉ **Quản trị, Bị khóa, Spam** liệt kê sẵn; Tất cả/Học viên/Giảng viên chỉ hiện khi tìm; **bỏ số đếm** trên các nút lọc.
- Gói khóa học: **xóa nếu chưa ai mua, đã có người mua thì Lưu trữ**.

## A. Học viên
- **A1 Lịch sử giao dịch:** lọc theo trạng thái (Tất cả / Thành công / Đang chờ / Thất bại–Hết hạn / Hoàn tiền) bằng tab có số lượng, khoảng ngày, tìm theo mã đơn hoặc tên khóa, phân trang 10 dòng. BE: `GET /api/v1/payments/mine/search` (lọc + phân trang phía máy chủ).

## B. Giảng viên
- **B1 Thu nhập:** Sao kê (sổ cái) có lọc theo loại khoản + khoảng ngày + phân trang; Lịch sử rút tiền có lọc trạng thái + phân trang.
- **B2 Giao tiếp (Q&A, Bài tập, Hoàn tiền, Tin nhắn, Thông báo):** thêm tìm kiếm / lọc / phân trang cho từng tab có dữ liệu.
- **B3 Gói khóa học:** thêm Xóa (chưa ai mua) / Lưu trữ (đã có đơn) — BE `DELETE /instructor/bundles/{id}` + trạng thái lưu trữ.
- **B4 Quét phần còn lại của khu giảng viên** (khóa học, mã giảm giá, quảng bá, đánh giá…) theo cùng quy tắc.

## C. Admin
- **C1 Người dùng:** theo quyết định trên.
- **C2 Mỗi tab có tìm kiếm + lọc + phân trang (khi có dữ liệu):** Danh mục, Quảng bá khóa học, Mã giảm giá, Thông báo hệ thống, Giọng đọc lồng tiếng, Đánh giá, Bảo mật AI Tutor (kiểm tra cái đã có, bổ sung cái thiếu).
- **C3 Thông báo hệ thống:** danh sách phân trang + tìm kiếm; **Sửa** nội dung; **Thu hồi**. BE thêm `PUT` và `POST /{id}/revoke` (cột/trạng thái thu hồi — migration chỉ thêm cột, không xóa dữ liệu).
- **C4 AI Analytics:** bảng tiêu thụ theo người dùng có tìm theo email, lọc (khoảng ngày, đang bị khóa AI), sắp xếp theo chi phí, phân trang; biểu đồ theo khoảng ngày chọn.
- **C5 Giám sát AI Queue:** bỏ phần chi tiết queue và bảng Celery Beat tĩnh; giữ sức khỏe AI Worker + danh sách job có tìm kiếm / lọc trạng thái / phân trang.
- **C6 Cấu hình hệ thống:**
  - Bỏ toàn bộ thông số chỉ để xem (Gemini model, ASR, độ dài đoạn; nhóm Máy chủ & môi trường) — chỉ giữ thông số **sửa được**.
  - Điều khiển theo kiểu dữ liệu: công tắc (2 giá trị); ô số nguyên có bước tăng giảm và giới hạn; nhóm ngày trong tháng dùng **bảng chọn ô 1–28**; ngày lễ dùng **bộ chọn ngày** thêm/xóa từng ngày; email dạng thẻ; văn bản có giới hạn độ dài.
  - Validate đầy đủ ở cả FE và BE (số âm, số thập phân, ký tự, để trống, vượt giới hạn, sai định dạng); BE bổ sung kiểm tra cho các khóa `QUOTA_*` (hiện chưa có) và **test cho TỪNG khóa** (hợp lệ + các kiểu sai).

## D. Chứng từ / PDF
- **D1 Bố cục PDF chuyên nghiệp (áp cho mọi loại chứng từ):** dải đầu có tên bên phát hành và tên chứng từ; khối thông tin hai cột (bên phát hành | người nhận, kèm số chứng từ/ngày/mã đơn); bảng dòng căn thẳng hàng (mô tả | SL | đơn giá | thành tiền, số căn phải); khung tổng tiền; khối thông tin chuyển khoản; chân trang có mã kiểm, số trang, ghi chú pháp lý.
- **D2 Checksum:** SHA-256 của nội dung bất biến của chứng từ (số, loại, ngày, số tiền, dữ liệu đã chụp), in ở chân trang (rút gọn 16 ký tự). Báo cáo tài chính PDF cũng in checksum (không có QR vì báo cáo không lưu cố định).
- **D3 QR → mở thẳng PDF:** mỗi chứng từ có **mã truy cập ngẫu nhiên 256-bit** (cột mới `access_token`, tạo bằng `SecureRandom`; chứng từ cũ được cấp mã bằng job một lần). QR chứa `{gốc API công khai}/api/v1/public/invoices/{mã}.pdf`. Gốc API công khai là thuộc tính mới có giá trị mặc định trong `application.yml` (không đụng `.env`).
- **D4 Endpoint công khai (CẦN BẠN DUYỆT — đụng cấu hình bảo mật):** thêm một đường GET vào danh sách công khai của `SecurityConfig`. Áp đủ 9 lớp bảo vệ theo quy tắc dự án: kiểm mã bằng danh sách trắng (đúng 43 ký tự base64url), không ghép chuỗi vào SQL, mọi lỗi trả cùng một 404, giới hạn tần suất theo IP (từ chối khi lỗi hệ thống), công tắc tắt khẩn cấp trong Cài đặt, handler nhỏ, test âm tính, rà Jackson/Tomcat. Mã dài 256-bit nên không thể đoán.
- **D5** Gửi lại email chứng từ giữ nguyên (đã có).

## E. Cách làm
Một lượt: BE + FE; `mvn clean verify`, kiểm truy vấn/migration trên bản sao dữ liệu thật, lint + tsc; commit/push; chỉ **một migration mới** (V171: `invoices.access_token`, cột thu hồi của thông báo, trạng thái lưu trữ của gói) — chỉ THÊM cột, không xóa/sửa dữ liệu; sao lưu DB trước khi áp. Giữ nguyên các quy ước đã chốt: không có chữ "demo", nhãn không hiện tên biến, mọi danh sách tìm-kiếm-trước.
