# Kế hoạch phát triển LMS — cập nhật 28/11/2026 (đã xoá các việc xong)

> Bản đầy đủ trước khi dọn: `docs/archive/UpComming_Plan_20261128_truoc_khi_don_viec_xong.md`; các bản cũ hơn: `docs/archive/UpComming_Plan_20261121_truoc_khi_tinh_gon.md`, `docs/archive/UpComming_Plan_20261115_truoc_khi_don.md`. Chi tiết epic thanh toán/hoàn tiền/chi trả: `docs/PAYMENT_PAYOUT_EPIC.md`; hóa đơn: `docs/INVOICE_AND_REPORTING_PLAN.md`. Quy tắc làm việc: `/var/lms/CLAUDE.md`.
>
> **Đã xong, không còn ở đây:** toàn bộ epic hoàn tiền – chi trả – công nợ – quảng bá – hóa đơn – theo dõi chi phí AI (V169–V178); hạ tầng + bảo mật + giám sát (image `lms-backend:prod`, giới hạn RAM/log, sao lưu MySQL mã hoá + khôi phục thử, Caddy chặn nội bộ, Hetzner Firewall + Backups, Prometheus/Grafana/Telegram, UptimeRobot, diễn tập Redis/MySQL/backend crash); **đo tải k6 lần B** (`docs/perf/DO_TAI_K6_LAN_B_20261128.md`); rà bảo mật tự động 207 endpoint; vá IDOR flashcard/học liệu Official/kế hoạch học (INSTRUCTOR); quảng bá hết hạn; Google không ghi đè tên/ảnh; mục cấu hình F đã kiểm. **Mobile:** xem mục 3 — mọi thứ đã xong và test đạt trừ video YouTube chưa lên hình.
> Quyết định hạ tầng đã chốt: giữ Hetzner CX23 (2 vCPU, ~3,8 GB), **không dựng cân bằng tải thật**, trình bày phương án + điều kiện chưa thoả trong báo cáo (`docs/perf/KIEN_TRUC_VA_CAN_BANG_TAI.md`).

---

## LỘ TRÌNH ĐẾN HẠN CUỐI

| Giai đoạn | Nội dung | Trạng thái |
|---|---|---|
| Web + hạ tầng | Test chức năng, hiệu suất, hạ tầng, bảo mật | **Xong phần lớn, tạm đóng băng** — việc nhỏ còn lại ở mục 2G |
| **Cuối tuần 28–29/11** | **TÀI LIỆU** (mục 4) | Chủ dự án tự viết |
| Trước ngày báo cáo hội đồng | Mục 2G (web) + video YouTube mobile + test Android | Làm lần cuối, sau khi có tài liệu |

---

## 2. WEB — VIỆC CÒN LẠI

Hiện trạng server: **1 máy Hetzner CX23, 2 vCPU, ~3,8 GB RAM, đĩa 40 GB**, Docker Compose một máy: Caddy (HTTPS) → backend (image prod) / ai-api; MySQL, Redis, ai-worker (Celery 1 node), mailpit, adminer (chỉ localhost). FE chạy trên Vercel.

### A. Test chuyên sâu còn lại (ghi kết quả vào `docs/`)
- **Giảng viên/admin chưa ai chạy trọn:** công nợ (tạo nợ vượt số dư → trả bằng QR); khiếu nại do giảng viên gửi; 3 ca cũ `RESOLVED_REFUNDED` còn PAID cần xử lý tay từng ca.
- **Đường lỗi PayOS chưa thử:** tài khoản sai, vượt hạn mức, thiếu số dư (ghi lại thông báo lỗi); chưa có thông báo khi chi thất bại và chưa có màn học viên tự cập nhật tài khoản bị ngân hàng từ chối.
- **Trình duyệt/thiết bị:** Chrome, Edge, Safari desktop; Android Chrome; màn nhỏ. Safari iOS nộp bài thi bị đứng: **đã quyết định không vá trên web** (hết khi làm trên app native).
- Gia sư AI: kho vector thiếu bài 23, 2284, 2285, 2286, 2288 → Gia sư báo "không có" — đã chấp nhận.

### B. Hiệu suất
- Đã đo k6 (báo cáo `docs/perf/DO_TAI_K6_LAN_B_20261128.md`). Còn: sửa chỗ nghẽn nếu muốn cải thiện thêm số đo trước khi báo cáo; lồng tiếng: bước ghép âm thanh/đo độ dài MP3 do **bạn của chủ dự án** đảm nhiệm (nghẽn ~6,9 file/s trên 2 vCPU).

### C. Hạ tầng và quan sát — còn lại
- **Bucket B2 riêng tư** để chép sao lưu ra ngoài máy (bucket media hiện công khai nên không dùng).
- Chưa có exporter MySQL/Redis/AI cho Prometheus; ghi kết quả thử báo động Down/Up của UptimeRobot; xoá dòng `8443:443` trong compose sau vài ngày ổn.
- Nhiều node chạy backend cần: broker STOMP chung (hiện `enableSimpleBroker` trong bộ nhớ), kho chung cho `/tmp/lms-processing` của AI worker, MySQL/Redis tách máy — chỉ ghi vào báo cáo.

### D. Diễn tập sự cố còn thiếu
- Thử hết RAM/OOM, khởi động lại giữa job lồng tiếng, email Gmail chạm giới hạn, mất kết nối B2, đăng nhập khi Redis tắt (chức năng cần đăng nhập vẫn lỗi lúc Redis tắt); webhook PayOS trùng/mất/chậm; hết quota Gemini/Groq/Azure (thông báo rõ ràng), **hết credit Azure for Students (100 USD)**; ngưỡng cảnh báo chi tiêu AI; nhiều thí sinh thi cùng lúc (tải Gemini Vision).

### E. Bảo mật còn lại
- Thêm quét tự động (gitleaks) vào CI; rà IDOR bằng tay các nhóm còn lại (endpoint chỉ `isAuthenticated()` mà không kiểm chủ sở hữu/ghi danh — đã rà xong flashcard/học liệu Official/kế hoạch học); quét ZAP; xác nhận xác thực 2 lớp mọi tài khoản; nâng Next.js 15.5.29 (CRITICAL, chủ dự án nâng ở máy cá nhân); nâng Spring Boot/Tika (66 lỗ HIGH/CRITICAL thư viện, bản nâng cất trong stash của `be-work`) **để sau khi nộp**. Chi tiết `docs/perf/RA_SOAT_BAO_MAT_20261126.md`.

### G. Việc nhỏ để dành — làm lần cuối trước ngày báo cáo hội đồng (không ảnh hưởng hoạt động web)
1. B2 riêng tư (mục C) · 2. test chuyên sâu (mục A) · 3. **điều tra điểm 0.00** ở các lượt thi thử 96–100 (liên quan cả bài thi mobile) · 4. **i18n:** chữ panel chính sách, công nợ, chi PayOS, **quy định giám sát thi (9 khoá mới đã có đủ 4 ngôn ngữ)** → rà phần panel còn lại vào 4 file vi/en/ja/zh · 5. diễn tập sự cố (D) · 6. bảo mật còn lại (E) · 7. exporter + UptimeRobot + dòng 8443 (C).
8. **Web — giọng đọc flashcard không dừng khi bấm mũi tên quay lại** (lỗi giống đã sửa ở mobile): dừng `speechSynthesis` khi rời trang/chấm điểm thẻ.
9. **Đưa các bản vá BE từ `feat/additional-features` vào `develop`** (cần chủ dự án xác nhận merge): quảng bá hết hạn (`existsOpen`), quyền flashcard/học liệu/kế hoạch học, Google không ghi đè, scheme `exps`.
10. Commit file kế hoạch này và các tệp `docs/` mới nếu chưa commit.

---

## 3. MOBILE (React Native/Expo, repo `urglydog/ailms-mobile`, `/var/lms/mobile`, push thẳng `main`)

**Hiện trạng 28/11:** mọi chức năng học viên trên mobile **đã xong và test đạt trên iPhone, trừ một việc: video YouTube phát có tiếng nhưng chưa lên hình.** Chỉ vai trò Học viên; test bằng Expo Go + tunnel; không làm i18n (demo chính ở web; điểm nổi bật mobile = ôn flashcard offline).

| # | Việc còn lại | Ghi chú |
|---|---|---|
| 1 | **Video YouTube chưa lên hình** | Âm thanh/tiến độ chạy, hình không dựng. Đã thử: UA Safari/Chrome di động, đợi khung có kích thước thật, `baseUrl` = domain web. Phương án tiếp: **B** trang tĩnh `youtube-player.html` trong `fe/public` rồi WebView mở từ domain web; **C** `react-native-youtube-iframe`. Chờ chủ dự án chọn/gửi chữ chẩn đoán `YT WxH sN` (chỉ hiện khi chạy dev) |
| 2 | **Test trên Android** | Chưa test lần nào (toàn bộ test trên iPhone) |
| 3 | **Test 6 cải tiến thi giám sát vừa thêm (28/11, chưa chạy trên máy)** | Cần chủ dự án chạy `npx expo install expo-keep-awake expo-screen-capture`, `npm run lint`, `npx tsc --noEmit` rồi thử: quy định + công tắc đồng ý + kiểm tra camera ở trang giới thiệu; màn hình không tự tắt khi làm bài; thanh % khi tải video sau khi nộp; đóng app giữa chừng rồi mở lại → tự tải tiếp; chụp màn hình khi thi → ghi vi phạm `SCREENSHOT` (iOS) / bị chặn (Android). Hệ điều hành/thiết bị nào không hỗ trợ thì bỏ qua êm, không lỗi |
| 4 | Còn nhỏ (để sau): reset mật khẩu qua link email, hồ sơ công khai đủ 3 tab, xuất PDF đề quiz, kéo-thả vị trí phụ đề, biểu tượng "có tài liệu đính kèm", kệ khóa theo danh mục ở trang chủ | Ưu tiên thấp |
| 5 | **Hoãn:** live session native (LiveKit, cần EAS dev client — hiện mở phòng live bằng trình duyệt); push notification (Expo Go không nhận remote push, BE chưa có bảng lưu token — tạm làm mới thông báo mỗi 30 giây); chia sẻ mobile | Ngoài phạm vi |

Giới hạn đã biết (ghi vào tài liệu): BE không chống trùng lượt ôn flashcard nên nếu mất mạng đúng lúc server vừa xử lý thì một lượt có thể bị tính hai lần (hiếm); giờ ôn tiếp theo tính theo giờ server lúc đồng bộ; một số kiểm tra giám sát (chặn/phát hiện chụp màn hình) phụ thuộc hệ điều hành. Nút bánh răng xanh là công cụ dev của Expo Go, biến mất ở bản build thật.

---

## 4. TÀI LIỆU — 2 tuần cuối (chủ dự án chưa nêu loại cụ thể; đề xuất)

- **Báo cáo khóa luận / NCKH:** kiến trúc, quy trình lồng tiếng, kết quả đo hiệu năng và chi phí AI (số đo thật đã có: lồng tiếng ≈ 54.000đ/bài → ≈ 1.300–1.700đ/10 phút; mindmap 633đ → ~170–200đ; hạ mức suy nghĩ), thiết kế thanh toán/hoàn tiền/chi trả/hóa đơn, bảo mật 9 lớp.
- **Tài liệu kiến trúc và triển khai:** sơ đồ hệ thống, sơ đồ triển khai Hetzner, phương án cân bằng tải (kết quả mục 2C), cấu hình môi trường (không kèm bí mật).
- **Hướng dẫn sử dụng** theo vai trò (học viên, giảng viên, admin) và **runbook vận hành**: sao lưu/khôi phục, xoay khóa, xử lý sự cố (hết quota AI, PayOS ngừng, đĩa đầy), danh sách cảnh báo.
- **Bảng kết quả kiểm thử:** từ mục 2A/2D.
- Slide/demo bảo vệ (nếu cần); tài liệu NCKH nằm ở `ai-worker/NCKH/`.

---

## 5. QUY TRÌNH & GHI CHÚ

- **Làm việc trên nhánh `feat/additional-features`** (BE/FE/ai-worker); **BE chạy thật bằng image `lms-backend:prod`** (jar build sẵn): sửa code/`git pull` trong `/var/lms/be` KHÔNG làm đổi bản đang chạy — phát hành theo `docs/perf/QUY_TRINH_PHAT_HANH_BACKEND.md` (build trong **một** worktree dùng lại `/home/dev-thien/work/be-work`, xóa `target/` sau khi xong, kiểm `df -h /`). Trước khi push/phát hành BE: `mvn clean verify` đầy đủ; có migration → sao lưu DB trước, thử trên bản sao dữ liệu thật với `ddl-auto=validate`. **Bật lại dịch vụ chỉ bằng danh sách cụ thể** (`docker compose up -d --no-build mysql redis backend caddy ai-api ai-worker ai-beat adminer mailpit`), `frontend` đã đặt profile riêng; container AI nạp lại `.env` bằng `docker compose up -d --force-recreate --no-deps ai-api ai-worker ai-beat` (không `down`/`-v`). Code AI vẫn mount từ `../ai-worker` nên sau khi sửa phải tạo lại container.
- **FE chỉ chạy trên Vercel**; trên server chỉ chạy `npm run lint` + `npx tsc --noEmit`; không `npm install`/build; kiểm git identity trước khi commit.
- **API công khai** phải đủ 9 lớp bảo vệ (danh sách trắng đầu vào, không ghép chuỗi vào SQL/Redis, phản hồi cố định, giới hạn bộ nhớ, rate limit từ chối-khi-lỗi, công tắc tắt khẩn cấp, handler nhỏ, test âm tính, rà Jackson/Tomcat).
- **Lỗi/khoảng trống đã biết, chấp nhận hoặc để sau khóa luận:** quyết định không vá Safari iOS trên web; Gia sư AI chỉ tìm theo bài và nói "không có" nếu thiếu dữ liệu; giải thích đáp án quiz giữ ngắn và theo ngôn ngữ bộ quiz; 3 `catch JsonProcessingException` im lặng ở `MaterialGenerationService`; `discovery.py` tự mở `httpx.AsyncClient`; điểm các lượt thi thử 96–100 đều 0.00 (chưa điều tra); tác vụ xóa thông báo đã đọc >90 ngày của ai-worker chưa lập trình; Gia sư hỏi đáp, giám sát thi (khung hình) và kế hoạch học chưa đo chất lượng ở mức suy nghĩ mới; chưa đo chi phí Gemini Live và Google Search grounding; HOÃN: chia sẻ D6/D7 (đếm lượt xem, link ngắn — API công khai cần 9 lớp), quảng bá E (boost tìm kiếm, quy kết click, chiến dịch email), dark theme thật, chia sẻ mobile.
- Ý tưởng bảo mật dữ liệu (sau khóa luận): nhận diện truy xuất lượng dữ liệu lớn → AI cảnh báo admin.
