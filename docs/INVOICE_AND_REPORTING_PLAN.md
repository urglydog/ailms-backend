# Kế hoạch: Hóa đơn / chứng từ & Thống kê – Báo cáo tự động (đề xuất 15/11/2026)

> Trạng thái: **ĐỀ XUẤT, CHƯA LÀM** — chờ chủ dự án duyệt phạm vi và trả lời các câu hỏi ở mục 8 (theo CLAUDE.md: việc mới / đổi schema phải confirm trước).

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

## 7. Giai đoạn đề xuất
1. **H1 – Lõi hóa đơn:** bảng + đánh số + biên lai thanh toán + PDF + đính kèm email + trang học viên + backfill.
2. **H2 – Chứng từ còn lại:** phiếu hoàn, phiếu chi, biên lai nợ, bảng kê thu nhập tháng (giảng viên).
3. **H3 – Minh bạch chi phí & quản trị chứng từ:** thêm phí cổng vào đơn, màn quản lý/xuất CSV cho admin.
4. **H4 – Thống kê tài chính admin:** API + trang + xuất; màn đối soát.
5. **H5 – Báo cáo tự động:** lịch, email, bản chụp, cài đặt.
6. **H6 (tùy chọn) – Hóa đơn điện tử pháp lý:** nối nhà cung cấp (xem câu hỏi 1).

Mỗi giai đoạn: migration + `mvn clean verify` + test; tách PR; sao lưu DB trước khi deploy (quy trình ở `PAYMENT_PAYOUT_EPIC.md`).

## 8. Câu hỏi cần chủ dự án quyết định
1. **Pháp lý:** cần **hóa đơn điện tử đúng quy định thuế** (cần nhà cung cấp được cấp phép, mã số thuế, mẫu số/ký hiệu) hay chỉ **biên lai/phiếu thu nội bộ** cho đề tài (khuyến nghị làm biên lai nội bộ trước, chừa điểm nối nhà cung cấp)? Cần kiểm tra với người có chuyên môn thuế trước khi dùng thật.
2. **Bên phát hành & thông tin pháp nhân** của nền tảng (tên, địa chỉ, mã số thuế, logo), giá đã gồm/chưa gồm thuế, thuế suất.
3. Định dạng **số chứng từ** (đề xuất `LOẠI-YYYYMM-STT`).
4. Ngôn ngữ PDF: chỉ tiếng Việt hay 4 ngôn ngữ như giao diện?
5. Danh sách **chỉ số** cần có ở thống kê (ưu tiên), định dạng xuất (CSV/XLSX/PDF), **lịch** và **người nhận** báo cáo tự động.
6. Có **backfill** chứng từ cho giao dịch cũ (kể cả dữ liệu demo) không?
7. Thời gian lưu trữ chứng từ.

## 9. Rủi ro / lưu ý
- Font tiếng Việt trong PDF (nhúng font); số tiền dùng `BigDecimal`, múi giờ `Asia/Ho_Chi_Minh`.
- Đánh số liên tục không trùng khi chạy song song (khóa dòng bộ đếm).
- Dữ liệu cá nhân (billing phone, tài khoản ngân hàng): chỉ che 4 số cuối, kiểm quyền (học viên chỉ thấy chứng từ của mình).
- Dung lượng đĩa: không lưu PDF; báo cáo định kỳ chỉ lưu bản chụp số liệu.
- Hóa đơn pháp lý ngoài phạm vi nếu chọn biên lai nội bộ — ghi rõ trên chứng từ "Biên lai điện tử nội bộ, không phải hóa đơn GTGT".
