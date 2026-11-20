# Kế hoạch: Hóa đơn / chứng từ & Thống kê – Báo cáo tự động (đề xuất 15/11/2026)

> Trạng thái: **ĐÃ CHỐT PHẠM VI (15/11/2026); H1 phần BE ĐÃ CODE (20/11/2026), chờ sao lưu DB + deploy; email/FE/H2–H4 CHƯA LÀM** — chủ dự án đã trả lời các câu hỏi (mục 8). Bắt đầu code **sau khi hai lệnh chi PayOS LLR5-1 và LLP1-1 hoàn tất** và sau khi chủ dự án duyệt hướng triển khai ở mục 7 (theo CLAUDE.md: việc mới / đổi schema phải confirm trước).

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
| **Bảng kê nền tảng** (nội bộ) | theo kỳ | admin | phí nền tảng thu được, hoàn tiền, chi trả, công nợ |

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
**Chỉ số (ưu tiên tài chính):** doanh thu gộp/ròng, phí nền tảng (cổng không thu phí nên không có phí cổng), tổng hoàn tiền và tỷ lệ hoàn, chi trả giảng viên (chờ duyệt/đã duyệt/đã chi), **nghĩa vụ chưa trả** (tổng số dư giảng viên), công nợ phải thu, đối soát PayOS (vào/ra) với sổ cái; theo kỳ/khóa/giảng viên. Mở rộng: người dùng mới, hoạt động học, sử dụng AI (đã có một phần ở `ai-analytics`).
**Xuất:** CSV (và XLSX/PDF nếu duyệt), theo bộ lọc đang xem.
**Tự động:** cấu hình lịch (ngày/tuần/tháng) + người nhận trong Cài đặt; job tạo báo cáo, lưu bản chụp (`report_snapshots`), gửi email kèm file; có nút "chạy ngay".

## 7. Giai đoạn triển khai (theo quyết định ở mục 8 — chờ duyệt trước khi code)
1. **H1 – Lõi chứng từ (gộp H1+H2 cũ, theo D1):**
   - Migration: `invoices`, `invoice_lines`, `invoice_sequences` (không cần cột phí cổng — cổng không thu phí).
   - Đánh số `LOẠI-YYYYMM-STT` (RCP biên lai, RFD phiếu hoàn, PAY phiếu chi, DBT biên lai nợ, STM bảng kê tháng giảng viên, PLT bảng kê nền tảng), khóa dòng bộ đếm.
   - Phát hành tự động khi: đơn PAID, hoàn tiền được duyệt/đã chuyển, yêu cầu rút ở PAID, trả nợ thành công; job bảng kê tháng.
   - Biên lai học viên chỉ hiện giá/khuyến mãi/tổng đã trả; bảng kê giảng viên mới hiện tỷ lệ chia và phí nền tảng.
   - PDF **tiếng Anh mặc định** (OpenPDF, nhúng font để in đúng tên tiếng Việt), ghi "Internal electronic receipt, not a VAT invoice"; đính kèm email thanh toán/hoàn tiền (B2).
   - FE: trang Hóa đơn của học viên, tab Chứng từ của giảng viên, nút tải trong chi tiết giao dịch.
   - **Backfill** một lần cho giao dịch cũ, bỏ qua tài khoản đuôi `@example.com` (B4).
2. **H2 – Quản trị chứng từ:** màn admin lọc theo loại/kỳ/người, tải PDF, xuất CSV; bảng kê nền tảng.
3. **H3 – Thống kê tài chính admin:** API + trang theo kỳ/khóa/giảng viên; xuất **CSV + XLSX + PDF**; màn đối soát PayOS với sổ cái.
4. **H4 – Báo cáo tự động:** lịch ngày/tuần/tháng cấu hình được, người nhận trong Cài đặt, bản chụp `report_snapshots`, gửi email kèm file, nút "chạy ngay".
5. **(Không làm)** Hóa đơn điện tử pháp lý — chỉ chừa điểm nối (A1 chọn biên lai nội bộ).

Mỗi giai đoạn: migration + `mvn clean verify` + test; tách PR; sao lưu DB trước khi deploy (quy trình ở `PAYMENT_PAYOUT_EPIC.md`). XLSX: ghi dạng luồng, nhẹ (xem "Đã chốt thêm" #6); thêm dependency phải confirm.

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
| B3 | ~~Hiện đầy đủ cả phí~~ → **đã đổi:** biên lai học viên chỉ hiện giá/khuyến mãi/tổng đã trả (xem "Đã chốt thêm" #4) |
| B4 | **Backfill** giao dịch cũ, **bỏ qua tài khoản demo** |
| B5 | ~~Có thời hạn 10 năm~~ → **đã đổi: lưu vĩnh viễn**, thiết kế bất biến theo chuẩn hệ thống lớn (xem "Đã chốt thêm" #3) |
| C1 | Doanh thu gộp/ròng + phí nền tảng (không có phí cổng — cổng không thu phí); hoàn tiền (số tiền, tỷ lệ); chi trả GV + nghĩa vụ chưa trả + công nợ phải thu; đối soát PayOS với sổ cái |
| C2 | Chiều xem: kỳ (ngày/tuần/tháng), khóa học, giảng viên (không làm danh mục) |
| C3 | Xuất **CSV + XLSX + PDF** |
| C4 | Lịch báo cáo tự động **cấu hình được** ngày/tuần/tháng |
| D1 | **Gộp H1 và H2** làm một lượt |
| D2 | Bắt đầu **sau khi hai lệnh PayOS LLR5-1 và LLP1-1 hoàn tất** |

### Đã chốt thêm (15/11/2026, sau câu hỏi bổ sung)
1. **Tài khoản ảo/demo:** nhận diện theo **đuôi email `@example.com`** — backfill và thống kê bỏ qua các tài khoản này (cấu hình đuôi trong Cài đặt để đổi được).
2. **Phí cổng thanh toán = 0:** cổng không thu phí nên **không có cột/dòng phí cổng** trên chứng từ và thống kê; hoàn 10k thì đúng 10k rời khỏi nền tảng. Bỏ "phí cổng" khỏi chỉ số C1 và khỏi migration (không cần thêm cột phí cổng vào đơn). Nếu sau này đổi cổng có phí thì thêm lúc đó.
   > ✅ **Đã chốt (17/11/2026) — phí kênh CHI PayOS:** kênh chi PayOS tính **2.950đ cố định cho mỗi lệnh chi THÀNH CÔNG** (lệnh thất bại không tính; xác nhận qua lịch sử PayOS của LLR5-2 10.000đ và LLP1-2 20.000đ). **Nền tảng chịu toàn bộ**, đã nằm trong phần nền tảng giữ lại (63% organic / 3% referral), KHÔNG trừ vào giảng viên và KHÔNG tính cho học viên (hoàn 100% giá trị đã thanh toán). Hệ quả cho chứng từ/thống kê: (1) phiếu chi và bảng kê thu nhập của giảng viên ghi dòng "Phí chuyển tiền: do nền tảng chịu (0đ trừ vào thu nhập)", không ghi số tiền cụ thể trong văn bản chính sách vì PayOS có thể đổi; (2) bảng kê nền tảng (admin) có một dòng chi phí "Phí chi hộ PayOS" = số lệnh thành công × phí (cấu hình được, mặc định 2.950đ); (3) biên lai học viên không hiện khoản này. Chưa rõ phí trừ vào ví chi hay một quỹ phí riêng và hậu quả khi quỹ phí thấp — đang chờ PayOS trả lời nên chưa đổi cách kiểm số dư ở BE.
3. **Lưu trữ vĩnh viễn** (thay cho "có thời hạn 10 năm" ở B5): thiết kế theo chuẩn hệ thống lớn — chứng từ **bất biến**, không xóa/sửa, sai thì **hủy bằng phiếu điều chỉnh/ghi có**, đánh số liên tục không đứt quãng, mọi thao tác có nhật ký (`finance_audit_log`). Không cần job dọn dữ liệu cũ. Không lưu PDF, sinh từ ảnh chụp dữ liệu (`snapshot_json`) nên DB không phình.
4. **Biên lai học viên chỉ hiện số liên quan đến họ:** giá khóa học, khuyến mãi/giảm giá (nếu có), **tổng đã trả**, phương thức, mã giao dịch, ngày. **Không** hiện phí nền tảng, tỷ lệ chia giảng viên hay phí cổng (đảo ngược quyết định B3 trước đó). Phí nền tảng/phần giảng viên chỉ xuất hiện ở bảng kê giảng viên và bảng kê nền tảng (admin).
5. **Báo cáo tự động:** người nhận mặc định **chỉ admin**, xem trên giao diện hoặc nhận file qua email. **Bảng kê thu nhập tháng gửi cho từng giảng viên** qua email của họ, có **công tắc bật/tắt trong Cài đặt hệ thống** (tắt thì giảng viên vẫn tải được trên giao diện).
6. **XLSX nhẹ, không làm sập server:** không dùng Apache POI bản đầy đủ (dựng cả bảng tính trong RAM). Dùng ghi **dạng luồng** (POI SXSSF hoặc bộ ghi XLSX tối giản tự viết bằng zip+XML), giới hạn số dòng mỗi lần xuất, chạy tuần tự (không song song), PDF báo cáo chỉ tóm tắt + bảng giới hạn dòng. Quyết định thư viện cuối cùng khi code, đo bộ nhớ trước (server 3.8GB).

## 9. Rủi ro / lưu ý
- Font tiếng Việt trong PDF (nhúng font); số tiền dùng `BigDecimal`, múi giờ `Asia/Ho_Chi_Minh`.
- Đánh số liên tục không trùng khi chạy song song (khóa dòng bộ đếm).
- Dữ liệu cá nhân (billing phone, tài khoản ngân hàng): chỉ che 4 số cuối, kiểm quyền (học viên chỉ thấy chứng từ của mình).
- Dung lượng đĩa: không lưu PDF; báo cáo định kỳ chỉ lưu bản chụp số liệu.
- Hóa đơn pháp lý ngoài phạm vi nếu chọn biên lai nội bộ — ghi rõ trên chứng từ "Biên lai điện tử nội bộ, không phải hóa đơn GTGT".
