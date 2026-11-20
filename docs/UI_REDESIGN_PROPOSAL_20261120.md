# Rà soát giao diện "render hàng loạt" & hướng sửa đổi (20/11/2026)

Nguồn: phản hồi nghiệm thu hóa đơn của chủ dự án. **Trạng thái: ĐÃ TRIỂN KHAI 20/11/2026 (chờ nghiệm thu).** Chủ dự án chốt thêm: màn có việc đang chờ admin xử lý
(khiếu nại, rút tiền, hỗ trợ) vẫn hiện sẵn hàng chờ; dữ liệu lớn (người dùng, giao dịch, chứng từ) chuyển sang tìm kiếm trước.

## Đã làm
- Chữ: bỏ mọi chữ "demo" khỏi nội dung người dùng đọc (PDF/email/trang báo cáo/cài đặt); ghi chú cắt bảng chỉ hiện khi bảng thật sự bị cắt.
- Phí PayOS 2.950đ là hằng số trong code, không còn là cấu hình; các khóa `INVOICE_EXCLUDED_EMAIL_SUFFIX`, `PAYOS_PAYOUT_FEE` không hiện ở giao diện.
- Người nhận báo cáo `.local` bị bỏ qua (thư tới `admin@lms.local` bị Gmail trả lại vì miền không tồn tại — xác nhận thư đã ra khỏi server).
- Tra cứu-trước (BE phân trang + tìm kiếm, bỏ `GET /payments/admin/all` và `GET /users`): Giao dịch, Chứng từ, Người dùng (tất cả/học viên). Hàng chờ + tìm kiếm + "Xem thêm": Khiếu nại (mặc định "Cần xử lý"), Chi trả (mặc định chờ duyệt), Hỗ trợ (mặc định chưa xử lý). Huy hiệu khiếu nại dùng endpoint đếm thay vì tải toàn bộ danh sách mỗi 30 giây.
- Chứng từ gắn vào nghiệp vụ: học viên có biên lai/phiếu hoàn ngay trên từng giao dịch (trang `/invoices` chuyển hướng); giảng viên có phiếu chi ở Lịch sử rút tiền + ô chọn tháng cho bảng kê (biểu đồ cột); admin có tra cứu → chi tiết → Chia sẻ (PDF, gửi lại email).
- Thống kê tài chính dạng biểu đồ, mỗi biểu đồ gắn một câu hỏi; so sánh kỳ trước; cảnh báo khóa hoàn tiền cao; đối soát PayOS liệt kê đúng khoản lệch.
- Nút "Chia sẻ" thống nhất (`ExportMenu`).
- Cấu hình hệ thống: menu nhóm, công tắc, ô số có đơn vị, email dạng danh sách, thanh Lưu cố định.
- Giảng viên › Học viên: mặc định chỉ nhóm "cần chú ý", tìm kiếm, "Xem thêm".

## Chưa làm / ghi nhận
- Danh mục nhỏ do admin tự tạo (danh mục, giọng đọc, mã giảm giá, thông báo hệ thống, quảng bá) vẫn liệt kê vì số lượng ít và là danh mục quản trị.
- Chưa rà từng trang giảng viên còn lại (Q&A, bài tập, gradebook) và học viên (ví, chứng chỉ, khóa học của tôi — dữ liệu của chính họ, nhỏ).
- Giảng viên › Học viên: số liệu thống kê vẫn tính ở trình duyệt từ toàn bộ danh sách; nên chuyển sang tổng hợp ở BE nếu số học viên rất lớn.


## 1. Nguyên tắc (chủ dự án chốt)
1. **Không render cả tập dữ liệu để ngắm.** Màn quản lý thực thể (người dùng, giao dịch, hóa đơn, khiếu nại, chi trả...) mặc định chỉ có ô tìm kiếm (mã, ID, email, khóa học...). Có kết quả mới hiện danh sách ngắn; bấm vào một dòng mới hiện chi tiết và các nút thao tác. Mô hình tham chiếu: GRDM (Gakunin RDM).
2. **Thống kê phải trả lời một câu hỏi** và dùng đúng loại biểu đồ: cột để so cao–thấp, tròn để thể hiện phân bổ %, đường cho xu hướng theo thời gian, có điểm nhấn (màu, mũi tên tăng/giảm, cảnh báo) cho bất thường.
3. **Các thứ liên quan nằm chung một màn hình** (lịch sử giao dịch và hóa đơn không tách thành hai trang độc lập). Tính năng không có lối vào trên giao diện là lỗi.
4. **Một nút "Chia sẻ/Xuất" duy nhất** mở menu: PDF, CSV, XLSX, gửi email cho tôi... thay cho nhiều nút rời rạc.
5. **Cấu hình hệ thống:** menu nhóm bên trái, giá trị bên phải, công tắc cho giá trị 0/1, thanh Lưu cố định, không hiện tên biến, không cho sửa giá trị do bên thứ ba quy định.
6. **Không có chữ "demo" trong bất kỳ chữ nào người dùng đọc.**

## 2. Kết quả rà soát

### 2.1 Backend trả danh sách không giới hạn (nguồn gốc của việc render hàng loạt)
| Màn hình | Endpoint / hàm | Ghi chú |
|---|---|---|
| Admin › Giao dịch | `GET /api/v1/payments/admin/all` → `PaymentService` (`findAllByOrderByCreatedAtDesc`) | Tải TOÀN BỘ giao dịch (DB hiện 1.540 dòng) rồi lọc phía trình duyệt |
| Admin › Khiếu nại | `DisputeService` (`findAllByOrderByCreatedAtDesc`) | Không phân trang |
| Admin › Chi trả giảng viên | `PayoutService.listForAdmin` | Không phân trang |
| Admin › Công nợ | `GET /api/v1/admin/finance/debts` | Toàn bộ giảng viên đang nợ |
| Admin › Hỗ trợ | `SupportTicketService` (`findAllByOrderByCreatedAtDesc`) | Không phân trang |
| Admin › Thông báo hệ thống | `SystemAnnouncementService` | Không phân trang |
| Admin › Quảng bá / Mã giảm giá | `PromotionService`, `CouponService` (`findAll`) | Không phân trang |
| Admin › Giọng đọc, Danh mục | `VoiceMappingService`, `CategoryService` (`findAll`) | Danh sách nhỏ, cố định — chấp nhận được |
| Admin › Người dùng | `/api/v1/users/admin` có phân trang + tìm kiếm | Đã đúng hướng nhưng vẫn liệt kê mặc định; còn `UserService.getAllUsers()` không giới hạn (`GET /api/v1/users`) |
| Admin › Chứng từ (mới làm) | `GET /api/v1/admin/invoices` | Có phân trang nhưng mặc định liệt kê; chưa có chi tiết/thao tác ngoài PDF |
| Học viên › Lịch sử giao dịch | `GET /payments/mine` | Dữ liệu của chính mình (nhỏ) nhưng tách rời trang Hóa đơn |
| Giảng viên › Thu nhập | Chứng từ, lịch sử rút tiền, sổ cái | Chứng từ là một vùng riêng chỉ có nút PDF |
Chưa rà từng trang giảng viên khác (doanh thu, học viên, Q&A, hoàn tiền...); sẽ rà ở bước 1 bên dưới trước khi sửa.

### 2.2 Lỗi do tôi gây ra ở hóa đơn/thống kê (nhận lỗi, sửa trong đợt này)
- Trang chứng từ admin chỉ liệt kê + nút PDF; mọi loại chứng từ lẫn lộn, chỉ phân biệt qua cột "Loại".
- Học viên không có lối vào `/invoices` trên giao diện (chỉ có liên kết nhỏ trong trang giao dịch, không có trong menu).
- Trang Thống kê tài chính chỉ có số, không biểu đồ, không điểm nhấn, không trả lời câu hỏi nào.
- Nút xuất rời rạc (CSV, XLSX, PDF, Chạy ngay) mỗi nơi một kiểu.
- Chữ "Số liệu loại trừ tài khoản demo..." xuất hiện ở PDF, email, tiêu đề trang, nhãn cài đặt.
- Cài đặt: các khóa mới (`PAYOS_PAYOUT_FEE`, `INVOICE_*`, `REPORT_*`) xếp vào danh sách phẳng, hiện tên biến, công tắc 0/1 là ô số; nút Lưu ở trên cùng. **Phí PayOS không phải cấu hình** — PayOS quy định, chỉ nên là hằng số trong code (có chú thích nguồn) và đổi khi PayOS đổi.
- Email báo cáo: log Gmail (ảnh) cho thấy thư RỜI server bình thường và bị trả lại chỉ vì người nhận `admin@lms.local` không tồn tại. Cần bỏ qua người nhận không gửi được (đuôi `.local`) và cho đặt email thật.

## 3. Hướng sửa đổi

### Bước 0 — sửa chữ & lỗi nhỏ (làm cùng đợt)
- Xóa mọi câu có "demo" khỏi chữ hiển thị (PDF báo cáo, email báo cáo, tiêu đề trang, i18n cài đặt); ghi chú PDF chỉ hiện khi bảng bị cắt: "Hiển thị 60 dòng đầu, xem đầy đủ trong file Excel hoặc CSV". `grep` mọi chỗ cùng kiểu.
- Gỡ `PAYOS_PAYOUT_FEE` và `INVOICE_*` khỏi giao diện cài đặt; phí PayOS thành hằng số trong code.
- Người nhận báo cáo: bỏ qua email không gửi được (`.local`), ghi log rõ.

### Bước 1 — mẫu giao diện dùng chung "Tra cứu → kết quả → chi tiết"
- Thành phần FE `LookupPanel`: ô tìm kiếm + bộ lọc loại; trạng thái ban đầu chỉ có ô tìm kiếm và thẻ đếm (vd "Chờ xử lý: 3"); kết quả tối đa 10 dòng/trang; bấm một dòng mở ngăn chi tiết có các nút thao tác.
- BE: mỗi danh sách ở 2.1 đổi sang `Pageable` + tham số tìm (`q`, mã, ID, email, trạng thái), giới hạn `size` tối đa 50, từ chối truy vấn rỗng ở các màn lớn; bỏ `getAllUsers`/`admin/all`. Chỉ mục DB cho cột tìm kiếm nếu thiếu.
- Áp dụng lần lượt: Giao dịch → Chứng từ → Khiếu nại → Chi trả → Người dùng → Hỗ trợ → Công nợ → Quảng bá/Mã giảm giá/Thông báo.

### Bước 2 — Chứng từ gắn vào nghiệp vụ, không đứng riêng
- **Admin:** màn Chứng từ = ô tìm theo số chứng từ / email hoặc ID người dùng / mã đơn / khóa học → kết quả → ngăn chi tiết (loại có nhãn màu riêng, các dòng, chứng từ liên quan) với nút: Chia sẻ (PDF, CSV…), Gửi lại email cho người nhận.
- **Học viên:** gộp vào một màn "Giao dịch của tôi": mỗi đơn có thao tác ngay tại dòng (tải biên lai, yêu cầu hoàn tiền, phiếu hoàn tiền); bỏ trang `/invoices` (chuyển hướng về trang giao dịch); lối vào duy nhất là mục "Lịch sử giao dịch" đã có trong menu.
- **Giảng viên:** bỏ vùng "Chứng từ" riêng. Phiếu chi gắn ở từng yêu cầu rút trong "Lịch sử rút tiền"; bảng kê tháng là một ô chọn tháng ngay trên trang Thu nhập, hiển thị tóm tắt + biểu đồ thu nhập theo khóa và nút Chia sẻ.

### Bước 3 — Thống kê tài chính dạng biểu đồ (dùng `recharts` đã có, không thêm thư viện)
Mỗi biểu đồ gắn một câu hỏi:
| Câu hỏi của admin | Biểu đồ / điểm nhấn |
|---|---|
| Doanh thu đang tăng hay giảm? | Đường/vùng: gộp, hoàn, ròng theo kỳ; thẻ KPI có ▲▼% so với kỳ trước |
| Tiền thực về nền tảng là bao nhiêu? | Tròn: phần nền tảng / phần giảng viên / hoàn tiền |
| Khóa học và giảng viên nào dẫn đầu? | Cột ngang (top 10) theo doanh thu và số lượt mua |
| Khóa nào có vấn đề chất lượng? | Cột tỷ lệ hoàn theo khóa, tô đỏ khi vượt ngưỡng; tròn: hoàn tiền theo mã lý do |
| Doanh thu đến từ đâu? | Tròn: organic / giới thiệu của giảng viên |
| Nghĩa vụ trả giảng viên? | Tròn: chờ duyệt / đã duyệt / đã chi; cảnh báo yêu cầu chờ quá hạn |
| Tiền chi qua PayOS có khớp sổ sách không? | Thẻ đối soát xanh/đỏ; khi lệch, bấm vào liệt kê đúng các lệnh lệch — tương đương "17 ly" trong ví dụ của chủ dự án: con số lệch gắn với một khoản mất cụ thể |
BE bổ sung truy vấn: hoàn tiền theo mã lý do, tỷ lệ hoàn theo khóa, doanh thu theo nguồn, kỳ trước để so sánh, danh sách lệnh lệch đối soát.

### Bước 4 — nút Chia sẻ/Xuất thống nhất
Một thành phần `ExportMenu` ("Chia sẻ" → PDF / CSV / XLSX / Gửi email cho tôi) dùng ở: chứng từ, thống kê, chi trả (CSV đã duyệt), bảng kê tháng. "Chạy ngay" trở thành mục trong menu này.

### Bước 5 — Cấu hình hệ thống
- Cột trái: menu nhóm — AI Worker, Hạn mức sử dụng, Doanh thu & thanh toán, Hoàn tiền & khiếu nại, Chi trả giảng viên, Công nợ, Báo cáo & chứng từ, … Cột phải: các mục của nhóm đang chọn.
- Điều khiển theo kiểu dữ liệu: công tắc (0/1), ô số có đơn vị (%, ngày, đ), danh sách email dạng thẻ, danh sách ngày dạng chọn ngày. Chỉ hiện mô tả tự nhiên, **không hiện tên biến**.
- Thanh "Có N thay đổi — Lưu / Hoàn tác" dán cố định ở đáy màn hình; xác nhận khi rời trang chưa lưu.
- Metadata nhóm/nhãn/kiểu điều khiển nằm ở FE (mở rộng `FINANCE_FIELDS`); khóa chưa có trong metadata không hiện.

## 4. Thứ tự đề xuất & phạm vi
1. Bước 0 (nhỏ, làm ngay) → 2. Bước 5 cấu hình → 3. Bước 1 + 2 cho Giao dịch/Chứng từ/Học viên/Giảng viên → 4. Bước 3 + 4 thống kê & xuất → 5. Áp mẫu Tra cứu cho các màn admin còn lại.
Mỗi bước: BE `mvn clean verify` + FE lint/tsc, báo cáo từng bước; không đổi schema (chỉ thêm chỉ mục nếu cần — sẽ hỏi trước).
