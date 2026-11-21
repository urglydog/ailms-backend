# Kế hoạch phát triển LMS — tinh gọn 21/11/2026

> Bản đầy đủ trước khi tinh gọn: `docs/archive/UpComming_Plan_20261121_truoc_khi_tinh_gon.md` (và bản 15/11: `docs/archive/UpComming_Plan_20261115_truoc_khi_don.md`). Chi tiết epic thanh toán/hoàn tiền/chi trả: `docs/PAYMENT_PAYOUT_EPIC.md`; hóa đơn: `docs/INVOICE_AND_REPORTING_PLAN.md`. Quy tắc làm việc: `/var/lms/CLAUDE.md`.
>
> **Đã xong, không còn ở đây:** epic hoàn tiền – chi trả – công nợ – quảng bá; chi tự động PayOS (đã có 2 lệnh chi thành công); hóa đơn/chứng từ + thống kê tài chính + báo cáo tự động (V169–V171); theo dõi chi phí AI + đối soát hóa đơn Google + bảng giá theo model (V172–V173); mức suy nghĩ thấp cho mọi tính năng AI (trừ Gia sư hỏi đáp); số lượng/độ khó học liệu có tác dụng; sửa lỗi in khóa ra log + xoay khóa + xóa khóa cũ; đề thi chính thức tạo bằng AI (V174, đã nghiệm thu); Q&A/học viên của giảng viên tìm ở máy chủ; giới hạn 200 thông báo; sửa IP giả ở giới hạn tần suất; trang admin tìm-trước/biểu đồ/cài đặt theo nhóm. Các việc cấu hình/chính sách chủ dự án đã xác nhận xong (hỏi PayOS, quyết định chính sách khiếu nại/nợ, email demo, `PAYOUT_MIN_AMOUNT`, `REFUND_HOLIDAYS`, hạ tầng nhỏ) — xem mục 2F để kiểm lại một lần lúc chốt.

---

## LỘ TRÌNH ĐẾN HẠN CUỐI

| Giai đoạn | Nội dung | Kết quả |
|---|---|---|
| **Tuần này + tuần sau** | **WEB**: test lại mọi chức năng, nâng hiệu suất, hạ tầng Hetzner (có tính cân bằng tải), kịch bản dùng thực tế, chốt bảo mật (mục 2) | Web **chốt, không đụng nữa** (chỉ sửa lỗi nghiêm trọng) |
| **2 tuần cuối** | **MOBILE** (mục 3) + **TÀI LIỆU** (mục 4) | Nộp báo cáo cuối 12/2026 |

---

## 2. WEB — TUẦN CUỐI TRƯỚC KHI CHỐT

Hiện trạng server (đo 21/11): **1 máy Hetzner, 2 vCPU, 3,8 GB RAM (đã dùng ~2,6 GB, backend Java ~1,3 GB, có dùng swap), đĩa 38 GB (~81%)**, Docker Compose một máy: Caddy (HTTPS) → backend / ai-api; MySQL, Redis, ai-worker (Celery 1 node), mailpit, adminer (chỉ localhost). Cùng máy còn container của dự án khác (`roadmap-worker`). FE chạy trên Vercel. Cổng mở ra ngoài: 22, 80, 443, **8443 (cần xác định để làm gì)**.

### A. Test lại mọi chức năng (checklist; ghi kết quả vào `docs/`)
- **Học viên:** đăng ký/đăng nhập Google, refresh token; giỏ, mã giảm giá, gói khóa, thanh toán PayOS (thành công, hết hạn, lặp webhook); hoàn tiền/khiếu nại và đổi tài khoản nhận hoàn; học bài + phụ đề + lồng tiếng đa ngôn ngữ; Gia sư AI (kho vector thiếu bài 23, 2284, 2285, 2286, 2288 → Gia sư báo "không có" — đã chấp nhận); Discovery; Quick Check và **thi chính thức có giám sát**; flashcard/mindmap/quiz tạo bằng AI (mọi mức số lượng/độ khó); chứng chỉ; hóa đơn + QR; thông báo; phòng live.
- **Giảng viên:** tạo khóa/chương/bài; lồng tiếng; học liệu AI + đổi loại bài thi; Q&A; bảng điểm; doanh thu, rút tiền PayOS, sao kê tháng; **công nợ (tạo nợ vượt số dư → trả bằng QR): chưa ai chạy trọn**; **khiếu nại do giảng viên gửi: chưa ai chạy**; gói khóa, mã giảm giá, quảng bá.
- **Admin:** người dùng (khóa/spam), duyệt giảng viên, danh mục, khiếu nại (3 ca cũ `RESOLVED_REFUNDED` còn PAID cần xử lý tay từng ca), chi trả, thống kê tài chính + báo cáo tự động, AI Analytics + đối soát, hàng đợi AI, **mọi nhóm Cài đặt**, thông báo hệ thống.
- **Đường lỗi PayOS chưa thử:** tài khoản sai, vượt hạn mức, thiếu số dư (ghi lại thông báo lỗi). Học viên bị ngân hàng từ chối tài khoản: chưa có màn tự cập nhật và chưa có thông báo khi chi thất bại.
- **Giao diện:** chữ mới của giai đoạn G (panel chính sách, công nợ, chi PayOS) chỉ có tiếng Việt, chưa vào 4 file i18n (vi/en/ja/zh); test vitest chỉ chạy ở CI (không chạy trên server).
- **Trình duyệt/thiết bị:** Chrome, Edge, Safari desktop; Android Chrome; màn nhỏ. Safari iOS nộp bài thi bị đứng: **đã quyết định không vá trên web** (sẽ hết khi làm trên app native).

### B. Nâng hiệu suất
- Đo nền: thời gian phản hồi API chính, truy vấn chậm MySQL (bật slow log), N+1, chỉ mục thiếu, kích thước trang danh sách lớn, cache Redis, bộ nhớ Java (heap) trên máy 3,8 GB.
- Tải thử nhẹ (k6/ab) vào: đăng nhập, danh sách/tìm khóa học, thanh toán + webhook, nộp bài thi, chat/WebSocket, trang admin nặng (thống kê tài chính). Đặt mục tiêu số (vd p95 < 500 ms ở 50 người dùng đồng thời) rồi sửa chỗ nghẽn.
- Lồng tiếng: bước ghép âm thanh và đo độ dài MP3 do **bạn của chủ dự án** đảm nhiệm (nghẽn ~6,9 file/s trên 2 vCPU); `TTS_CONCURRENCY` mặc định đã 16.

### C. Hạ tầng Hetzner và cân bằng tải
- **Quyết định cần chốt:** với khóa luận nên **nâng cấu hình máy + tách MySQL** (đơn giản, rủi ro thấp) rồi **trình bày phương án cân bằng tải trong báo cáo**, thay vì dựng LB thật; nếu vẫn muốn làm thật thì dùng Hetzner Load Balancer/Caddy và xử lý các điểm sau.
- **Điều kiện để chạy nhiều bản backend (phải kiểm trong code):** WebSocket/STOMP chat + live có thể đang dùng bộ phát tin trong bộ nhớ (cần broker chung hoặc sticky session); các job `@Scheduled` (đối soát sổ cái, chứng từ, báo cáo tự động, nhắc flashcard…) chạy trùng khi có nhiều bản → cần khóa phân tán (vd ShedLock hoặc khóa Redis); cache cục bộ; JWT stateless; tệp lưu ở Backblaze B2 (ổn); giới hạn tần suất dùng Redis (ổn, đã sửa IP giả).
- **AI Worker:** Celery nhiều node đọc chung hàng đợi Redis; file tạm `/tmp/lms-processing` hiện là volume cục bộ → nhiều node cần kho chung; theo dõi số job song song so với RAM.
- **Dữ liệu:** sao lưu MySQL tự động (cron + mã hóa + chép ra ngoài máy) **và thử khôi phục một lần**; Redis có mật khẩu (đã có), đặt `maxmemory` + chính sách; đĩa còn ~7 GB cần dọn thêm (bản VS Code Server cũ ~5 GB).
- **Quan sát:** giám sát RAM/CPU/đĩa/dịch vụ và cảnh báo (Uptime Kuma hoặc Netdata), health check, giới hạn log container (`lms_backend`, `lms_caddy`, `lms_mysql`, `lms_redis` lúc kiểm chưa giới hạn), giới hạn bộ nhớ container, bỏ `--reload` ở `ai-api`, cảnh báo khi RAM thấp (máy đang dùng swap).

### D. Kịch bản dùng thực tế (test hoặc có phương án sẵn sàng)
- **Tiền:** webhook PayOS trùng/mất/chậm; thanh toán hai lần; PayOS ngừng; số dư tài khoản chi hết; lệnh chi treo lâu; hoàn tiền/khiếu nại đồng thời.
- **AI:** Gemini/Groq/Azure hết quota, lỗi 429, ngừng dịch vụ (thông báo người dùng rõ ràng); **hết credit Azure for Students (100 USD)**; trần ngân sách và cảnh báo chi tiêu AI (trang AI Analytics đã có đối soát, cần thêm ngưỡng cảnh báo); lạm dụng AI (khóa AI, hạn ngạch); nhiều thí sinh thi cùng lúc (tải Gemini Vision); Supabase vector ngừng.
- **Hệ thống:** đĩa đầy; hết RAM/OOM của backend; MySQL hoặc Redis chết (rate limit đang từ chối-khi-lỗi → có chặn hết người dùng không?); khởi động lại giữa job lồng tiếng (job có tự chạy lại không); email Gmail SMTP chạm giới hạn gửi; tệp tải lên quá lớn/độc hại; tăng đột biến truy cập; mất kết nối tới B2.
- **Dữ liệu:** thử khôi phục sao lưu; migration trên bản sao dữ liệu thật trước mỗi lần deploy (đã có quy trình).

### E. Chốt bảo mật
- **Bí mật:** rà mọi khóa (Gemini mới đã sạch; Groq, Azure, PayOS, B2, JWT secret, `X-Internal-Token`, Google OAuth) không xuất hiện trong log/URL/git; `.env` quyền 600; quy trình xoay khóa; repo **đã công khai** (quét lịch sử sạch với mẫu `AIza…`, `AQ.…`, `sk-`, `gsk_`, GitHub, AWS, private key) → thêm quét tự động (gitleaks) vào CI.
- **Ứng dụng:** rà phân quyền từng endpoint (`@PreAuthorize`, kiểm quyền sở hữu để tránh IDOR); JWT hết hạn/refresh/thu hồi; CORS; header bảo mật (HSTS, CSP, X-Frame-Options, X-Content-Type-Options); kiểm tra tải lên; chống SQL injection/XSS; API công khai đủ 9 lớp (mục 5); lớp chống prompt injection cho AI đã có; quét phụ thuộc (OWASP dependency-check, `npm audit`).
- **Máy chủ:** `ufw` và `fail2ban` đã bật (kiểm rule thực tế); SSH chỉ khóa, tắt đăng nhập root bằng mật khẩu; **xác định và đóng cổng 8443 nếu không cần**; adminer/mailpit/ai-api/backend chỉ lắng nghe localhost (đúng); MySQL/Redis không mở ra ngoài (đúng); cập nhật hệ điều hành và Docker; sao lưu có mã hóa; phân quyền tài khoản quản trị (cân nhắc 2FA cho admin); nhật ký kiểm toán hành động tài chính (đã có `finance_audit_logs`).

### F. Cấu hình phải kiểm lại ngay trước khi chốt (chủ dự án báo đã làm; lúc kiểm DB ngày 21/11 chưa thấy)
`PAYOUT_MIN_AMOUNT = 100000` (lúc đó vẫn 20000), `PAYOS_PAYOUT_ENABLED` đúng ý, `PAYOS_PAYOUT_MAX_AMOUNT`, `REFUND_HOLIDAYS` (lúc đó rỗng), Redis `maxmemory`, `ai-api` không còn `--reload`, giới hạn log/bộ nhớ container.

### Tiêu chí chốt web
Checklist A đạt (ghi kết quả), không còn lỗi nghiêm trọng; B có số đo trước/sau; C có quyết định LB đã chốt + sao lưu đã thử khôi phục; D các kịch bản quan trọng đã xử lý hoặc ghi nhận rủi ro; E không còn mục bảo mật mở; F đã kiểm. Sau đó **đóng băng**: chỉ sửa lỗi nghiêm trọng, mọi thay đổi qua `UpComming_Plan.md`.

---

## 3. MOBILE — 2 tuần cuối (React Native/Expo, BẮT BUỘC cho khóa luận)

**Đã chốt:** Expo (repo `urglydog/ailms-mobile`, `/var/lms/mobile`, push thẳng `main`); chỉ vai trò Học viên; test bằng Expo Go + tunnel (không cần Apple Developer); Metro chạy trên máy cá nhân, không chạy trên server; offline flashcard dùng `expo-sqlite`. **Nếu trễ, cắt offline flashcard trước, không cắt phần AI.** Quy ước: hỏi thứ tự ưu tiên trước khi bắt đầu gap mới; nghiệm thu = chạy qua Expo Go trên Android và iPhone thật, một luồng trọn vẹn.

| # | Việc | Trạng thái |
|---|---|---|
| 1 | Test thiết bị thật (Android + iOS) cho toàn bộ luồng mới — code mới chỉ kiểm bằng `tsc` + lint | **Việc đầu tiên** |
| 2 | Giám sát thi (chụp frame định kỳ gửi Gemini Vision) | Cần test thiết bị thật; rủi ro `takePictureAsync` xen giữa `recordAsync` làm dừng video → nếu cắt thì bỏ quay liên tục, chỉ dùng chuỗi frame |
| 3 | Offline flashcard | Đã vá 09/11; test thật (bật chế độ máy bay giữa chừng) |
| 4 | Bắt kịp tính năng web mới: nhiều tài khoản nhận tiền, form khiếu nại chuẩn, nhãn "chờ chuyển", thông báo `PROMOTION_*`/`PAYOUT_*`, nhắc flashcard theo khóa, liên kết `/materials?courseId=`, loại bài thi (Quick Check/Thi chính thức) | Chưa |
| 5 | Đa ngôn ngữ giao diện (vi/en/ja/zh) | Chưa — việc lớn, cân nhắc chỉ vi/en |
| 6 | Live session native (LiveKit) | **Hoãn** — cần EAS dev client; mở phòng live bằng trình duyệt |
| 7 | Push notification | **Hoãn** — Expo Go không nhận remote push, BE chưa có bảng lưu token; tạm làm mới thông báo mỗi 30 giây |
| 8 | Ưu tiên thấp: kéo-thả vị trí phụ đề, chất lượng video, biểu tượng "có tài liệu đính kèm", kệ khóa học theo danh mục ở trang chủ | Để sau |

---

## 4. TÀI LIỆU — 2 tuần cuối (chủ dự án chưa nêu loại cụ thể; đề xuất)

- **Báo cáo khóa luận / NCKH:** kiến trúc, quy trình lồng tiếng, kết quả đo hiệu năng và chi phí AI (số đo thật đã có: lồng tiếng ≈ 54.000đ/bài → ≈ 1.300–1.700đ/10 phút; mindmap 633đ → ~170–200đ; hạ mức suy nghĩ), thiết kế thanh toán/hoàn tiền/chi trả/hóa đơn, bảo mật 9 lớp.
- **Tài liệu kiến trúc và triển khai:** sơ đồ hệ thống, sơ đồ triển khai Hetzner, phương án cân bằng tải (kết quả mục 2C), cấu hình môi trường (không kèm bí mật).
- **Hướng dẫn sử dụng** theo vai trò (học viên, giảng viên, admin) và **runbook vận hành**: sao lưu/khôi phục, xoay khóa, xử lý sự cố (hết quota AI, PayOS ngừng, đĩa đầy), danh sách cảnh báo.
- **Bảng kết quả kiểm thử:** từ mục 2A/2D.
- Slide/demo bảo vệ (nếu cần); tài liệu NCKH nằm ở `ai-worker/NCKH/`.

---

## 5. QUY TRÌNH & GHI CHÚ

- **Làm việc trên nhánh `feat/additional-features`** (BE/FE/ai-worker); BE chạy thật từ `/var/lms/be` (bind-mount + devtools) nên code dở KHÔNG để ở đó — dùng **một** worktree dùng lại (`/home/dev-thien/work/be-work`), xóa `target/` sau mỗi lần verify (bằng docker nếu file thuộc root), kiểm `df -h /` trước khi build lớn. Trước khi push BE: `mvn clean verify` đầy đủ; có migration → sao lưu DB trước, thử trên bản sao dữ liệu thật với `ddl-auto=validate`, rồi mới restart backend. Container AI nạp lại `.env` bằng `docker compose up -d --force-recreate --no-deps ai-api ai-worker ai-beat` tại `/var/lms/be` (không dùng `restart`, không `down`/`-v`).
- **FE chỉ chạy trên Vercel**; trên server chỉ chạy `npm run lint` + `npx tsc --noEmit`; không `npm install`/build; kiểm git identity trước khi commit.
- **API công khai** phải đủ 9 lớp bảo vệ (danh sách trắng đầu vào, không ghép chuỗi vào SQL/Redis, phản hồi cố định, giới hạn bộ nhớ, rate limit từ chối-khi-lỗi, công tắc tắt khẩn cấp, handler nhỏ, test âm tính, rà Jackson/Tomcat).
- **Lỗi/khoảng trống đã biết, chấp nhận hoặc để sau khóa luận:** quyết định không vá Safari iOS trên web; Gia sư AI chỉ tìm theo bài và nói "không có" nếu thiếu dữ liệu; giải thích đáp án quiz giữ ngắn và theo ngôn ngữ bộ quiz; 3 `catch JsonProcessingException` im lặng ở `MaterialGenerationService`; `discovery.py` tự mở `httpx.AsyncClient`; điểm các lượt thi thử 96–100 đều 0.00 (chưa điều tra); tác vụ xóa thông báo đã đọc >90 ngày của ai-worker chưa lập trình; Gia sư hỏi đáp, giám sát thi (khung hình) và kế hoạch học chưa đo chất lượng ở mức suy nghĩ mới; chưa đo chi phí Gemini Live và Google Search grounding; HOÃN: chia sẻ D6/D7 (đếm lượt xem, link ngắn — API công khai cần 9 lớp), quảng bá E (boost tìm kiếm, quy kết click, chiến dịch email), dark theme thật, chia sẻ mobile.
- Ý tưởng bảo mật dữ liệu (sau khóa luận): nhận diện truy xuất lượng dữ liệu lớn → AI cảnh báo admin.
