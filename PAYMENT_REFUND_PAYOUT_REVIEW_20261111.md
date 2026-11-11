# Rà soát luồng thanh toán / hoàn tiền / chi trả giảng viên (11/11/2026)

Phạm vi: đọc code `be/payment`, `DisputeService`, migration V122/V143, trang legal ở `fe`. Chưa sửa gì.

## A. Tỷ lệ chia doanh thu

- **Không phải lỗi.** 37/63 (ORGANIC) và 97/3 (INSTRUCTOR_REFERRAL) là quyết định có chủ đích ngày 20/09/2026, nhất quán ở V122, `PaymentService.applyOutcome`, test, trang admin, trang `/legal/revenue-share`. Đây là mô hình của Udemy.
- Tỷ lệ 63% hard-code ở `PaymentService.applyOutcome` (`new BigDecimal("0.63")`/`"0.03"`).
- `application.yml` còn `platform-fee-percent: 30` / `instructor-earning-percent: 70` — **không được Java nào đọc**, chỉ gây hiểu nhầm.
- Tỷ lệ chốt cứng lúc PAID → đổi tỷ lệ chỉ ảnh hưởng đơn mới.
- Vấn đề thực chất: Udemy giữ 63% vì họ tự mang lượng học viên khổng lồ + chi marketing. Nền tảng mới chưa có traffic mà lấy 63% thì thiếu sức hút với giảng viên.

## B. Lỗ hổng luồng khiếu nại / hoàn tiền (đã xác minh trong code)

| # | Phát hiện | Hậu quả |
|---|---|---|
| B1 | `/legal/refund` ghi "không hoàn tiền, truy cập trọn đời", nhưng `DisputeService` vẫn cho khiếu nại và admin đánh dấu `RESOLVED_REFUNDED`; lý do khiếu nại là text tự do | Chính sách công bố mâu thuẫn với hệ thống, không có tiêu chí duyệt/từ chối |
| B2 | `resolve()` chỉ đổi trạng thái dispute + gửi mail; **không đụng `Payment`** (vẫn `PAID`, `instructor_earning`/`platform_fee` giữ nguyên) | Doanh thu giảng viên (`sumInstructorEarningSince`, dashboard) và doanh thu gộp admin (`sumTotalRevenue`) bị thổi phồng sau mỗi lần hoàn |
| B3 | `resolve()` không thu hồi quyền học (không có dependency Enrollment) | Học viên được hoàn tiền vẫn giữ khóa học |
| B4 | `fileDispute` chỉ chặn khi đã có dispute `OPEN`; sau `RESOLVED_REFUNDED`/`RESOLVED_REJECTED` vẫn tạo được dispute mới cho cùng payment | Nguy cơ hoàn tiền 2 lần cho 1 giao dịch |
| B5 | Dispute chỉ thông báo cho ADMIN; giảng viên không được báo, không có quyền phản hồi | Thiếu công bằng, giảng viên không biết vì sao bị trừ tiền |
| B6 | Hoàn tiền là chuyển khoản tay từ tài khoản nền tảng, không lưu số tiền hoàn / mã giao dịch hoàn | Không đối soát được |
| B7 | Không có cơ chế giữ tiền: nếu sau này cho rút ngay thì tiền đã chi cho giảng viên không đòi lại được | Nền tảng gánh toàn bộ khoản hoàn (đặc biệt referral: giảng viên đã nhận 97%) |

## C. Đề xuất quy tắc trách nhiệm hoàn tiền

Nguyên tắc (giống Udemy/Coursera): **mỗi bên trả lại đúng phần mình đã nhận**, học viên được hoàn 100% số tiền đã trả.

| Tình huống | Học viên | Giảng viên | Nền tảng |
|---|---|---|---|
| Hoàn trong cửa sổ hoàn tiền (học viên đổi ý, tiến độ dưới ngưỡng) | 100% | Trả lại đúng phần đã nhận (37% hoặc 97%) | Trả lại phần còn lại (63% hoặc 3%); phí cổng thanh toán nền tảng chịu |
| Lỗi hệ thống (trừ tiền hai lần, trừ tiền không ghi danh) | 100% | 0% | 100% |
| Nội dung sai mô tả nghiêm trọng / vi phạm chính sách giảng viên | 100% | 100% (kèm xử lý vi phạm) | 0% |
| Ngoài cửa sổ, không lỗi | Từ chối | — | — |

Cách khấu trừ:
1. Giao dịch còn trong thời gian giữ (chưa vào số dư khả dụng): đánh dấu `refunded_at`, khoản earning không bao giờ vào số dư. Không cần thu hồi.
2. Đã chi trả rồi: tạo khoản điều chỉnh âm (`payout_adjustments`), số dư giảng viên có thể âm, trừ vào lần chi trả kế tiếp. Điều khoản giảng viên ghi rõ quyền này.
3. Có dispute `OPEN`: earning của giao dịch đó bị đóng băng đến khi xử lý xong.

## D. Chi trả cho giảng viên

- Nền tảng thu hết (PayOS) → chi trả sau. Giảng viên có tài khoản nhận tiền; học viên không cần liên kết ngân hàng.
- **Thời gian giữ tiền (hold):** sau khi bán, `instructor_earning` chưa cho rút ngay; chờ N ngày (≥ cửa sổ hoàn tiền) mới chuyển thành "khả dụng". Lý do: tránh chi tiền rồi phải đòi lại.
- Rút tiền: giảng viên gửi yêu cầu → email xác nhận đã nhận (kèm ngày xử lý dự kiến) → admin chuyển khoản theo đợt cố định → email "đã chi" kèm mã giao dịch / email "từ chối" kèm lý do.
- Bảo mật: đổi tài khoản nhận tiền và tạo yêu cầu rút phải xác nhận qua link/OTP gửi email giảng viên; đổi tài khoản xong khóa rút vài ngày.
- Ngưỡng tối thiểu: thấp (đề xuất 100.000đ), mỗi lần chỉ 1 yêu cầu đang chờ.

## E. Kế hoạch theo giai đoạn (chờ duyệt)

- **Giai đoạn 0 — vá luồng hiện tại (không schema lớn):** chặn dispute trùng (B4); thêm `payments.refunded_at`, loại giao dịch hoàn khỏi mọi query doanh thu (B2); thu hồi quyền học khi hoàn (B3, cần quyết định); thông báo + cho giảng viên phản hồi (B5); lưu số tiền/mã giao dịch hoàn (B6); xóa 2 dòng yml chết.
- **Giai đoạn 1 — chi trả:** `instructor_payout_accounts`, `payouts`, `payments.payout_id`, `payout_adjustments`; API + trang giảng viên "Thu nhập & Rút tiền"; trang admin "Chi trả"; email các mốc.
- **Giai đoạn 2 — chính sách:** viết lại `/legal/refund` (cửa sổ + điều kiện + ai chịu), cập nhật `/legal/revenue-share` mục 3 (thời gian giữ, hoàn tiền), điều khoản giảng viên.

## F. Cần chốt trước khi code

1. Tỷ lệ ORGANIC: giữ 37/63 hay đổi (cấu hình hóa ra `application.yml` dù chọn gì).
2. Chính sách hoàn tiền: cửa sổ bao nhiêu ngày + điều kiện tiến độ học (đề xuất 7 ngày và học ≤ 20%, tái dùng ngưỡng 20% đang có).
3. Bảng trách nhiệm ở mục C có đồng ý không.
4. Thu hồi quyền học khi hoàn tiền: có (đề xuất).
5. Thời gian giữ tiền (đề xuất 10 ngày = 7 ngày cửa sổ + 3 ngày xử lý), ngưỡng rút (100.000đ), ngày chi trả theo đợt (ví dụ ngày 5 và 20 hàng tháng).
