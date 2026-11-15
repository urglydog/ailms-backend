# Kế hoạch: Hóa đơn / chứng từ & Thống kê – Báo cáo tự động (đề xuất 15/11/2026)

> Trạng thái: **ĐÃ CHỐT PHẠM VI (15/11/2026), CHƯA LÀM** — chủ dự án đã trả lời các câu hỏi (mục 8). Bắt đầu code **sau khi hai lệnh chi PayOS LLR5-1 và LLP1-1 hoàn tất** và sau khi chủ dự án duyệt hướng triển khai ở mục 7 (theo CLAUDE.md: việc mới / đổi schema phải confirm trước).

## 1. Vấn đề
- Mọi khoản tiền (học viên trả, hoàn tiền, chi trả giảng viên, trả nợ) hiện chỉ có **bản ghi trong DB và email chữ thuần**, không có chứng từ chính thức; email thanh toán không đính kèm hóa đơn/biên lai.
- Khoản chi phí chưa minh bạch với từng bên: học viên không thấy giá/chiết khấu/phí, giảng viên không có bảng kê doanh thu–khấu trừ rõ ràng, admin không có bản đối soát tổng.
- Admin **thiếu thống kê toàn nền tảng** (hiện có `/dashboard/admin` và `/dashboard/admin/system` cho tình trạng hệ thống, `/dashboard/instructor/*` cho giảng viên) và **không xuất báo cáo tự động**.

## 2. Hiện trạng có thể tận dụng
- `Payment` có `billing_name`, `billing_phone`, `txn_ref`, `gateway_txn_no`, `paid_at`, `amount`, `platform_fee`, `instructor_earning`, `platform_rate` (chốt lúc PAID).
- Sổ cái giảng viên (`instructor_ledger_entries`: EARNING, REFUND_REVERSAL, PAYOUT_RESERVE/RELEASE, ADJUSTMENT, DEBT_REPAYMENT), `payment_refunds`, `instructor_payouts`, `payos_payout_orders`, `instructor_debt_payments`, nhật ký kiểm toán `finance_audit_log`.
- Thư viện PDF **OpenPDF** đã có (`CertificatePdfRenderer` làm chứng chỉ) → dùng lại cho hóa đơn; FE có `apiBlob` để tải PDF.
- Email: `EmailService` (Gmail SMTP) — thêm đính kèm PDF.

## 3. Phạm vi chứng từ
| Loại | Khi nào phát hành | Người nhận | Nội dung chính |
|---|---|---|---|
| **Hóa đơn/biên lai thanh toán** | đơn chuyển PAID | học viên | số chứng từ, người mua (billing), khóa/gói, giá gốc, khuyến mãi, tổng, phương thức, mã GD cổng |
| **Phiếu hoàn tiền** (liên kết hóa đơn gốc) | duyệt hoàn tiền; cập nhật khi đã chuyển | học viên | số tiền hoàn, mã lý do, mã chuyển khoản, ngày chuyển |
| **Phiếu chi** | yêu cầu rút ở trạng thái PAID | giảng viên | số tiền, tài khoản (che), mã PayOS/ngân hàng, kỳ rút |
| **Bảng kê thu nhập theo kỳ** (tháng) | đầu tháng sau (job) | giảng viên | từng đơn: giá, tỷ lệ chia, phần giảng viên; trừ hoàn tiền; bù trừ công nợ; đã rút; số dư |
| **Biên lai công nợ** | trả nợ qua PayOS thành công / xóa nợ | giảng viên | số tiền, nợ còn lại |
| **Bảng kê nền tảng** (nội bộ) | theo kỳ | admin | phí nền tảng thu được, phí cổng, hoàn tiền, chi trả, công nợ |

Nguyên tắc: **bất biến** (ảnh chụp dữ liệu lúc phát hành, đổi giá sau không đổi chứng từ), **không sửa mà hủy bằng phiếu điều chỉnh**, **idempotent** (mỗi nguồn giao dịch chỉ một chứng từ).

## 4. Thiết kế dữ liệu (đề xuất)
- `invoices`: `id, number (UNIQUE, vd INV-202611-000123), type, status (ISSUED/VOIDED), party_user_id, payment_id? refund_id? payout_id? debt_payment_id? (UNIQUE theo nguồn+loại), currency, subtotal, discount, tax, total, issued_at, snapshot_json, template_version`.
- `invoice_lines`: dòng chi tiết (khóa, mô tả, đơn giá, số lượng, thành tiền).
- `invoice_sequences`: bộ đếm theo loại+tháng (khóa dòng để không trùng số).
- Không lưu file PDF (tiết kiệm đĩa — server 38 GB đang chật): **sinh PDF theo yêu cầu** từ `snapshot_json`.
- Migration Flyway + **backfill** idempotent cho các đơn PAID/hoàn/chi đã có (job chạy một lần).

## 5. API & giao diện
- Học viên: trang **Hóa đơn** (danh sách + tải PDF), nút tải trong chi tiết giao dịch; email thanh toán/hoàn tiền **đính kèm PDF**.
- Giảng viên: tab **Chứng từ** ở Thu nhập (phiếu chi, bảng kê tháng, biên lai nợ).
- Admin: **Quản lý chứng từ** (lọc theo loại/kỳ/người), tải PDF, xuất CSV.
- API: `GET /api/v1/invoices` (của tôi), `GET /api/v1/invoices/{id}/pdf`, `GET /api/v1/admin/invoices`, `.../export.csv`.

## 6. Thống kê & báo cáo admin
**Chỉ số (ưu tiên tài chính):** doanh thu gộp/ròng, phí nền tảng, phí cổng thanh toán, tổng hoàn tiền và tỷ lệ hoàn, chi trả giảng viên (chờ duyệt/đã duyệt/đã chi), **nghĩa vụ chưa trả** (tổng số dư giảng viên), công nợ phải thu, đối soát PayOS (vào/ra) với sổ cái; theo kỳ/khóa/giảng viên/danh mục. Mở rộng: người dùng mới, hoạt động học, sử dụng AI (đã có một phần ở `ai-analytics`).
**Xuất:** CSV (và XLSX/PDF nếu duyệt), theo bộ lọc đang xem.
**Tự động:** cấu hình lịch (ngày/tuần/tháng) + người nhận trong Cài đặt; job tạo báo cáo, lưu bản chụp (`report_snapshots`), gửi email kèm file; có nút "chạy ngay".

## 7. Giai đoạn triển khai (theo quyết định ở mục 8 — chờ duyệt trước khi code)
1. **H1 – Lõi chứng từ (gộp H1+H2 cũ, theo D1):**
   - Migration: `invoices`, `invoice_lines`, `invoice_sequences`; lưu **phí cổng thanh toán** vào đơn (cần để in phí đầy đủ trên biên lai học viên — B3).
   - Đánh số `LOẠI-YYYYMM-STT` (RCP biên lai, RFD phiếu hoàn, PAY phiếu chi, DBT biên lai nợ, STM bảng kê tháng giảng viên, PLT bảng kê nền tảng), khóa dòng bộ đếm.
   - Phát hành tự động khi: đơn PAID, hoàn tiền được duyệt/đã chuyển, yêu cầu rút ở PAID, trả nợ thành công; job bảng kê tháng.
   - PDF **tiếng Anh mặc định** (OpenPDF, nhúng font để in đúng tên tiếng Việt), ghi "Internal electronic receipt, not a VAT invoice"; đính kèm email thanh toán/hoàn tiền (B2).
   - FE: trang Hóa đơn của học viên, tab Chứng từ của giảng viên, nút tải trong chi tiết giao dịch.
   - **Backfill** một lần cho giao dịch cũ, bỏ qua tài khoản demo (B4).
2. **H2 – Quản trị chứng từ:** màn admin lọc theo loại/kỳ/người, tải PDF, xuất CSV; bảng kê nền tảng.
3. **H3 – Thống kê tài chính admin:** API + trang theo kỳ/khóa/giảng viên; xuất **CSV + XLSX + PDF**; màn đối soát PayOS với sổ cái.
4. **H4 – Báo cáo tự động:** lịch ngày/tuần/tháng cấu hình được, người nhận trong Cài đặt, bản chụp `report_snapshots`, gửi email kèm file, nút "chạy ngay".
5. **(Không làm)** Hóa đơn điện tử pháp lý — chỉ chừa điểm nối (A1 chọn biên lai nội bộ).

Mỗi giai đoạn: migration + `mvn clean verify` + test; tách PR; sao lưu DB trước khi deploy (quy trình ở `PAYMENT_PAYOUT_EPIC.md`). XLSX cần thêm thư viện (đề xuất Apache POI) — là thay đổi dependency, confirm trước khi thêm.

## 8. Quyết định của chủ dự án (15/11/2026)
| Mã | Quyết định |
|---|---|
| A1 | **Biên lai điện tử nội bộ** (không phải hóa đơn GTGT), chừa điểm nối nhà cung cấp hóa đơn điện tử |
| A2 | Bên phát hành = **LinguaLearn**, ghi chú đề tài, chưa có mã số thuế; thông tin sửa được trong Cài đặt |
| A3 | **Không ghi thuế** |
| A4 | Số chứng từ `LOẠI-YYYYMM-STT` |
| A5 | PDF **tiếng Anh mặc định** (cho chuyên nghiệp) |
| B1 | Làm đủ: biên lai thanh toán, phiếu hoàn, phiếu chi, biên lai công nợ, bảng kê thu nhập tháng (GV), bảng kê nền tảng (admin) |
| B2 | **Đính kèm PDF** vào email thanh toán/hoàn tiền |
| B3 | Biên lai học viên **hiện đầy đủ cả phí** (phí nền tảng, phí cổng) — khác đề xuất ban đầu |
| B4 | **Backfill** giao dịch cũ, **bỏ qua tài khoản demo** |
| B5 | Lưu trữ **có thời hạn** (ví dụ 10 năm) |
| C1 | Doanh thu gộp/ròng + phí nền tảng + phí cổng; hoàn tiền (số tiền, tỷ lệ); chi trả GV + nghĩa vụ chưa trả + công nợ phải thu; đối soát PayOS với sổ cái |
| C2 | Chiều xem: kỳ (ngày/tuần/tháng), khóa học, giảng viên (không làm danh mục) |
| C3 | Xuất **CSV + XLSX + PDF** |
| C4 | Lịch báo cáo tự động **cấu hình được** ngày/tuần/tháng |
| D1 | **Gộp H1 và H2** làm một lượt |
| D2 | Bắt đầu **sau khi hai lệnh PayOS LLR5-1 và LLP1-1 hoàn tất** |

### Còn cần chốt (phát sinh từ các quyết định trên)
1. **Tài khoản demo** nhận diện thế nào để backfill bỏ qua (theo đuôi email `@lms.local`, theo cờ, hay danh sách cụ thể)?
2. **Phí cổng thanh toán:** lấy giá trị từ đâu (PayOS có trả phí theo giao dịch không, hay theo tỷ lệ cấu hình)? Đơn cũ chưa có phí cổng thì in "N/A" hay ước tính?
3. **Thời hạn lưu trữ:** chốt con số (đề xuất 10 năm, cấu hình được); khi hết hạn xóa hay chỉ ẩn?
4. **Phí hiển thị trên biên lai học viên:** xác nhận hiển thị cả tỷ lệ chia giảng viên hay chỉ số tiền phí.
5. **Người nhận báo cáo tự động:** chỉ admin hay có thể thêm email ngoài hệ thống.
6. Chấp thuận thêm **Apache POI** cho XLSX.

## 9. Rủi ro / lưu ý
- Font tiếng Việt trong PDF (nhúng font); số tiền dùng `BigDecimal`, múi giờ `Asia/Ho_Chi_Minh`.
- Đánh số liên tục không trùng khi chạy song song (khóa dòng bộ đếm).
- Dữ liệu cá nhân (billing phone, tài khoản ngân hàng): chỉ che 4 số cuối, kiểm quyền (học viên chỉ thấy chứng từ của mình).
- Dung lượng đĩa: không lưu PDF; báo cáo định kỳ chỉ lưu bản chụp số liệu.
- Hóa đơn pháp lý ngoài phạm vi nếu chọn biên lai nội bộ — ghi rõ trên chứng từ "Biên lai điện tử nội bộ, không phải hóa đơn GTGT".
