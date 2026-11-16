# Kiểm chứng chi tự động PayOS — lần 1 (16/11/2026)

> Lệnh thật đầu tiên qua kênh `ailms-refund`. **Kết quả: cả hai lệnh THẤT BẠI phía ngân hàng đích** (không phải lỗi của hệ thống). Giữ nguyên báo cáo để đối chiếu các lần sau.

## Lệnh
| Mã | Loại | Số tiền | Kết quả |
|---|---|---|---|
| LLR5-1 | Hoàn tiền khiếu nại #5 | 10.000đ | FAILED — `PAYOUT_BANK_SYSTEM_ERROR` ("Hệ thống ngân hàng gián đoạn, vui lòng thử lại sau") |
| LLP1-1 | Chi giảng viên #1 | 20.000đ | FAILED — cùng mã lỗi |

Cả hai bị ngân hàng đích (Techcombank) từ chối cùng thời điểm (16/11 ~14:02 giờ DB), sau khoảng 15 giờ ở trạng thái PROCESSING.

## Đã kiểm (đạt)
- **BE bắt trạng thái:** `payos_payout_orders` cả hai dòng chuyển `FAILED`, lưu `error_code`, `error_message`, `completed_at` (job đối soát 2 phút/lần hoạt động).
- **Thông báo admin:** có 2 thông báo `PAYOS_PAYOUT_FAILED` (mỗi lệnh một thông báo, không trùng).
- **Dữ liệu nghiệp vụ không bị đổi sai:** yêu cầu rút `instructor_payouts#1` vẫn `APPROVED`, `paid_at` rỗng; khoản hoàn `payment_refunds#2` vẫn `PENDING_TRANSFER`, `transferred_at` rỗng.
- **Sổ cái:** chỉ có 1 bút toán `PAYOUT_RESERVE -20.000` cho lệnh rút, không có `PAYOUT_RELEASE`/chi hai lần → tiền vẫn đang giữ chỗ đúng.
- **Chống chi trùng (mức DB/PayOS):** `UNIQUE KEY uk_ppo_reference (reference_id)`; `referenceId` = `LLR{id}-{lần}`/`LLP{id}-{lần}` làm khóa idempotency gửi PayOS; gửi lại sau FAILED tạo lệnh mới `attempt+1` (LLR5-2/LLP1-2); đang `SUBMITTED/PENDING` hoặc `SUCCEEDED` bị chặn; `UNKNOWN` gửi lại cùng mã và tra `findByReferenceId` trước; "Idempotency key đã tồn tại" được xử lý bằng tra cứu, không đánh FAILED; `applyResult` bỏ qua lệnh đã `SUCCEEDED`. Có `PayosPayoutServiceTest`.

## Chưa kiểm được / điểm yếu cần xử lý
1. **Đường THÀNH CÔNG chưa từng chạy thật** (`onSucceeded` → `markPaid`/`markTransferred`, thông báo giảng viên/học viên, bút toán chốt): cần một lệnh thật ra `SUCCEEDED`. Việc gửi lại LLR5-2/LLP1-2 phải do admin bấm trên giao diện khi ngân hàng hoạt động lại.
2. **Chưa có khóa Redis** cho thao tác chi. Hai admin bấm "chi" cùng lúc khi lệnh gần nhất là FAILED: cả hai cùng tính `attempt+1`, lần chèn thứ hai vỡ `uk_ppo_reference` → **không chi hai lần** (an toàn), nhưng admin thứ hai nhận lỗi 500 chung chung thay vì thông báo rõ. Đề xuất: khóa Redis ngắn (`SET NX EX 60`, khóa theo `kind:entityId`) bọc `transfer()` + bắt `DataIntegrityViolationException` trả "đang có lệnh chi khác đang tạo". Thay đổi nhỏ, khoanh vùng trong `PayosPayoutService`.
3. Giao diện admin hiển thị lệnh FAILED và nút gửi lại (đọc code FE + chụp màn hình thật) — cần xác nhận khi bấm gửi lại.
4. Thông báo cho giảng viên/học viên khi thất bại: hiện chỉ báo admin; người nhận vẫn thấy "đang chờ chuyển" (đúng, vì khoản vẫn nợ họ). Chưa cần đổi.

## Kết luận
Đường thất bại ĐẠT. Chưa đủ điều kiện coi cả đợt kiểm chứng là đạt vì đường thành công chưa chạy và còn điểm yếu #2. Theo quy ước: xử lý #2 và chạy lại lệnh thật trước khi chuyển sang hóa đơn.
