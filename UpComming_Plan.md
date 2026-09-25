# Kế Hoạch Các Hạng Mục Còn Lại (Cập nhật 25/09/2026)

Các hạng mục A1, A2, A3, A4a, A4b, A5, B1, B2 của đợt trước (Certificate PDF, gộp điểm Quiz vào %, gỡ trang `/progress`, Mermaid Live Preview + Tree-card, Export PDF cheatsheet/đề trắng, nút Tài nguyên tĩnh, Import Anki/Quizlet) **đã xong toàn bộ phần code** — chỉ còn chờ bạn test tay qua UI (chi tiết từng test case đã có ở lịch sử làm việc, không lặp lại ở đây). Không phát hiện lỗi/dở dang nào khi rà lại.

File này giữ đúng 1 nơi duy nhất để ghi việc còn phải làm — cập nhật mỗi phiên làm việc, xoá mục nào xong.

---

## 🔴 Ưu tiên xử lý trước — lỗi đang tồn tại (không phải tính năng thiếu)

### Momo/ZaloPay ở checkout đang âm thầm trả sai URL

**Hiện trạng**: FE (`checkout/[slug]/page.tsx`, `checkout/cart/page.tsx`) có nút chọn Momo/ZaloPay, nhưng BE (`PaymentService.java`, nhánh "Fallback for Momo / Others") không có xử lý riêng cho 2 cổng này — mọi lựa chọn Momo/ZaloPay đều rơi vào nhánh fallback và trả về **URL sandbox của VNPAY**. Học viên chọn Momo nhưng bị đưa sang trang VNPAY, không có thông báo lỗi nào.

**Việc cần làm** (làm ngay được, không cần chờ quyết định lớn): chặn ở BE — nếu `paymentMethod` là MOMO/ZALOPAY mà chưa có triển khai thật, trả lỗi rõ ràng (vd 501/thông báo "Phương thức đang bảo trì") thay vì âm thầm trả URL VNPAY sai. Cân nhắc song song: ẩn tạm 2 nút này ở FE nếu chưa có kế hoạch làm thật trong ngắn hạn.

---

## 🎯 Thuật toán điểm nhấn cho bảo vệ luận văn — ưu tiên cao nhất, đã lên thiết kế đủ chi tiết để làm ngay

**Bối cảnh**: rà lại cho thấy AI Gia sư (Socratic Tutor) và Dubbing đều đã có chủ trong nhóm, không dùng làm điểm nhấn riêng được. 3 mục dưới đây (Anti-Cheat, Auto-ban, Discovery) hiện đều là "vỏ rỗng" — có giao diện/field nhưng KHÔNG có logic thật phía sau — và đều đang **chưa ai nhận**, có thể trở thành thuật toán thật sự của riêng bạn để trình bày trước hội đồng.

### 1. AI Anti-Cheat — giám sát thi cử thật, 2 tầng

**Hiện trạng thật**: `Quiz.isProctored`/`maxViolations` chỉ là cột dữ liệu được copy qua lại giữa DTO — đã grep toàn bộ BE/AI-worker/FE, **0 dòng code** phát hiện chuyển tab, mất focus, webcam. Giao diện bật/tắt "AI giám sát" đang đánh lừa cảm giác đã có logic thật phía sau.

**Tầng 1 — Rule-based (nền tảng bắt buộc, làm trước)**:
- FE trang làm bài thi: bắt `visibilitychange`/`blur` (chuyển tab/thu nhỏ cửa sổ), phát hiện thoát fullscreen (nếu bài thi yêu cầu fullscreen), chặn `copy`/`paste`/`contextmenu` trên vùng đề, phát hiện DevTools mở (chênh lệch `window.outerWidth`/`innerWidth`).
- API mới `POST /api/v1/quizzes/attempts/{attemptId}/violations` — BE tăng biến đếm, so với `maxViolations` có sẵn → vượt ngưỡng thì tự động nộp bài kèm cờ lý do.

**Tầng 2 — AI thật (điểm nhấn)**: chụp khung hình webcam định kỳ (vd 20-30s/lần), gửi AI-worker (tái dùng client Gemini đã có, không cần SDK mới) hỏi: đúng 1 người trong khung hình không, có đang nhìn màn hình không. Bất thường (0 người/>1 người/không nhìn nhiều lần liên tiếp) cộng vào bộ đếm violation chung. Đây là phần dùng AI đa phương thức ra quyết định thật, không phải if-else đơn thuần.

**Việc cụ thể cần làm**:
- [ ] BE: bảng/cột lưu lịch sử violation theo attempt (loại vi phạm, thời điểm), endpoint ghi nhận, ngưỡng tự động nộp bài dùng `maxViolations` có sẵn.
- [ ] FE: listener phát hiện hành vi (tab/focus/fullscreen/devtools/copy-paste) ở trang làm bài thi.
- [ ] FE: chụp webcam định kỳ, xin quyền camera, thông báo rõ cho học viên đang bị giám sát (vấn đề đạo đức/pháp lý — nên nêu rõ trong luận văn).
- [ ] AI-worker: task mới nhận ảnh, gọi Gemini Vision phân tích, trả nhãn bất thường.
- [ ] **Cần bạn quyết định**: bật webcam bắt buộc cho MỌI bài thi, hay chỉ bài có `isProctored=true` (đúng ý nghĩa field đã có sẵn)?

### 2. Auto-ban bằng AI — thiết kế mới hoàn toàn (hiện chưa có gì)

**Hiện trạng thật**: `User.isAiLocked` chỉ được set thủ công qua 1 endpoint admin bấm tay (`AdminController`) — không có job/quy tắc tự động nào, dù comment trong code có nhắc ý định này (`AiUsageLog` javadoc).

**Thiết kế đề xuất — human-in-the-loop** (an toàn hơn khi trình bày, tránh câu hỏi "AI khoá nhầm thì sao"):
- Tín hiệu bất thường (khai thác `AiUsageLog` có sẵn, không cần bảng mới cho tín hiệu):
  1. Dùng hết quota AI liên tục N ngày (vd 3 ngày).
  2. Tần suất request bất thường (nhiều request trong thời gian rất ngắn — dấu hiệu script/bot).
  3. (Tầng AI thật) Gemini phân loại các prompt/yêu cầu gần nhất: có dấu hiệu prompt injection hay nội dung vi phạm chính sách không.
- Đạt đủ 2/3 tín hiệu → **không tự khoá ngay**, tạo "đề xuất khoá" kèm lý do cụ thể, hiện ở màn Admin có sẵn → admin xác nhận mới thật sự khoá (dùng lại đúng `isAiLocked`).

**Việc cụ thể cần làm**:
- [ ] Job định kỳ (Celery beat bên AI-worker đã có cơ chế sẵn, đúng khuôn `cleanup_old_notifications`/`remind_flashcard_reviews`) quét `AiUsageLog` theo 2 tín hiệu rule-based đầu.
- [ ] Tích hợp bước phân loại nội dung bằng Gemini cho tín hiệu thứ 3.
- [ ] Bảng/field lưu "đề xuất khoá" + lý do, hiển thị ở màn Admin.
- [ ] Endpoint admin xác nhận/bỏ qua đề xuất.
- [ ] **Cần bạn quyết định**: ngưỡng cụ thể (bao nhiêu ngày liên tục, bao nhiêu request/phút là bất thường) — đề xuất trên chỉ là điểm khởi đầu.

### 3. Nâng cấp AI Discovery — từ "bộ lọc đội lốt AI" thành tìm kiếm ngữ nghĩa thật ✅ ĐÃ XONG (25/09/2026)

**Đã triển khai đầy đủ** — hybrid search filter-first + rerank bằng cosine similarity (pgvector, Supabase, tái dùng đúng hạ tầng của AI Gia sư):
- BE: `CourseEmbeddingService` (LPUSH Redis job) hook vào `CourseService.create/update`, `ChapterService.create/update`, `LessonService.create/update` — tự động re-embed khi tiêu đề/mô tả/tên chương/bài đổi. Backfill 1 lần cho course cũ qua `POST /api/internal/courses/embeddings/backfill`.
- AI-worker: `course_indexing.py` (sinh embedding), `tasks/course_embedding.py` (Celery task), consumer mới trong `main.py`, `discovery.py` thêm bước rerank similarity sau bước lọc cứng category/level/priceType.
- Bảng `course_embeddings` + RPC `match_courses_by_ids`/`match_courses` tạo thủ công trong Supabase (giống tiền lệ `transcript_embeddings`).

**Test case xác nhận đã nâng cấp thật — ĐÃ PASS**: hỏi "tôi muốn học cách giao tiếp lưu loát hơn với đồng nghiệp nước ngoài trong công việc lập trình" (không chứa "tiếng Anh") vẫn ra đúng 2 khoá "Tiếng Anh giao tiếp" / "Tiếng Anh giao tiếp cho IT". Câu hỏi gốc bị báo lỗi ("có khóa học ngôn ngữ nào không?") cũng đã ra đúng kết quả thay vì "không có khoá nào phù hợp" như trước.

**3 bug thật phát sinh lúc làm, đã sửa cùng đợt** (không nằm trong scope ban đầu nhưng chặn đứng tính năng nếu không sửa):
1. Celery `include=[...]` thiếu module task mới → job bị `Received unregistered task` và discard âm thầm.
2. `gemini`/`supabase_vector` client không đóng đúng giữa các task Celery (prefork worker tái sử dụng process, mỗi task 1 event loop riêng) → `RuntimeError: Event loop is closed`. Sửa cả ở `transcript_extraction.py` (bug có từ trước, cùng pattern, bị nuốt lỗi âm thầm nên chưa ai phát hiện — Gia sư AI có thể đã mất index 1 số lesson mà không ai biết).
3. System prompt Discovery đưa ví dụ `categorySlug` sai ("it, language, business", không khớp slug thật trong DB) khiến Gemini tự bịa slug không tồn tại → BE lọc cứng ra 0 kết quả, che khuất cả bước rerank mới thêm. Sửa bằng cách lấy danh mục thật từ BE làm `enum` cho tool schema.
4. Index `ivfflat` (`lists=100`) dư thừa ở quy mô catalog nhỏ (~20 dòng) khiến pgvector trả kết quả rỗng ngẫu nhiên (approximate search, không đủ dữ liệu để phân cụm đúng) — đã gỡ index, dùng quét tuần tự chính xác (đủ nhanh ở quy mô này).
5. Ngưỡng `discovery_min_similarity` mặc định 0.5 quá lỏng (khoá không liên quan vẫn ~0.55-0.63) — tinh chỉnh lên 0.65 dựa trên test thực tế, tách rõ khoá liên quan (~0.70+) khỏi nhiễu.

**Còn lại 2 mục trong nhóm 🎯**: Anti-Cheat (#1) và Auto-ban (#2) bên dưới vẫn CHƯA làm — Discovery là mục duy nhất trong 3 thuật toán điểm nhấn đã xong.

---

## ✅ Việc phát sinh đã sửa xong trong phiên 25/09/2026 (ngoài scope Discovery ban đầu)

Phát hiện lúc GV hỏi thử "AI Trợ lý Giảng dạy" về tình trạng khoá Unity — không liên quan Discovery nhưng cùng phiên nên gộp báo cáo ở đây, đã sửa + push hết:

1. **401 giả ở AI Trợ lý Giảng dạy** — `instructor_ai.py` gọi callback về BE bằng header `Authorization: Bearer` thay vì `X-Internal-Token` đúng chuẩn dự án → BE từ chối 401 vô điều kiện, Gemini tự bịa lời giải thích "đăng xuất đăng nhập lại" nghe rất thật nhưng KHÔNG liên quan JWT/phiên đăng nhập. Sửa dùng lại `backend_client` dùng chung. Sửa luôn `discovery.py` vì cùng 1 pattern lỗi (hiện vô hại vì endpoint public).
2. **`InternalInstructorAiService` trả số liệu mock cứng** — `totalStudents/averageRating/revenue/recentCourses` trước đây HARDCODE (kể cả 3 khoá học bịa "React Masterclass" không tồn tại trong DB) — GV hỏi khoá thật (Unity) luôn nhận câu trả lời sai. Sửa bằng cách tái dùng `DashboardService` (trang "Hiệu suất" GV, dữ liệu thật). Sửa luôn field `revenue`→`totalRevenue` khớp đúng tên FE đang đọc (trước đó card "Doanh thu" luôn hiện trống), và `period` (this_month/this_year...) giờ mới thực sự được gửi cho BE.
3. **7 màn hình FE dùng `fetch()` thô bỏ qua auto-refresh access token** — certificate PDF, export quiz PDF (2 chỗ), nộp bài tập multipart, danh sách user admin, trang thông báo, lịch sử chat lesson — tất cả im lặng fail khi access token hết hạn thay vì tự refresh như các API khác. Thêm 2 helper `apiBlob`/`apiFormData` dùng chung logic refresh với `api.*`, migrate hết 7 chỗ.
4. **`InstructorChat.tsx` lệch kích thước/animation so với `DiscoveryChat.tsx`** — thiếu `overflow-hidden` khiến phần trên bị che ở cửa sổ trình duyệt thấp, animation mở/đóng khác hẳn khung học viên. Đồng bộ lại đúng theo `DiscoveryChat.tsx` làm chuẩn.

Cả 4 đều đã cập nhật vào `CLAUDE.md` (mục 3/4/8) để tránh lặp lại.

---

## 🟡 Cần bạn quyết định hướng trước khi làm

### 1. Gửi OTP qua email thật (đang dùng Mailpit — chỉ chạy được lúc dev)

**Hiện trạng**: `EmailService.java` gửi qua `JavaMailSender`, cấu hình trong `application.yml` trỏ thẳng vào Mailpit (`host=mailpit`, `port=1025`, không có `username`/`password`, `smtp.auth=false`/`smtp.starttls.enable=false` hardcode). Chưa có profile `application-prod.yml` nào khác. OTP dùng cho đăng ký và quên mật khẩu (2 luồng OTP hơi trùng lặp code, gộp về sau được, không gấp).

**Checklist "đầy đủ" để dùng được ở production**:
- [ ] Bạn chọn 1 dịch vụ gửi email thật + có domain đã xác thực (SPF/DKIM) — vd Gmail SMTP (dễ, giới hạn số lượng thấp) hoặc SendGrid/Mailgun/AWS SES (chuyên dụng hơn, cần đăng ký + xác thực domain).
- [ ] Thêm field `spring.mail.username`/`spring.mail.password` vào `application.yml` (hiện chưa tồn tại).
- [ ] Đổi `smtp.auth`/`smtp.starttls.enable` sang đọc từ env thay vì hardcode `false`.
- [ ] Đổi `from` (đang hardcode `noreply@lms.local` trong `EmailService.java`) sang địa chỉ thuộc domain đã xác thực.
- [ ] Tách `application-prod.yml` riêng, giữ nguyên Mailpit cho dev/test.

**Cần bạn trả lời**: chọn dịch vụ nào, đã có domain email riêng chưa?

### 2. Thanh toán — VNPAY lên thật hay giữ sandbox? Momo/ZaloPay xây thật hay bỏ?

**Hiện trạng**: PayOS đã chạy thật (SDK riêng, webhook có xác thực chữ ký, tự động paid + ghi danh). VNPAY có logic ký/tạo URL thật nhưng hardcode sandbox + bank code test, chưa có IPN thật (chỉ có `/ipn-mock` — code tự ghi "giả lập để test"). Kiến trúc `Payment`/`PaymentService` đã gateway-agnostic, thêm cổng mới không cần đổi kiến trúc, chỉ cần thêm đúng khuôn PayOS.

**Cần bạn trả lời**:
- VNPAY có cần lên production thật không, hay giữ sandbox vì PayOS đã là cổng chính?
- Momo: xây thật (cần đăng ký merchant Momo Business) hay bỏ nút?
- ZaloPay: tương tự — xây thật hay bỏ nút?
- Có muốn thêm hình thức nào khác không (vd VietQR chuyển khoản trực tiếp, phí thường rẻ hơn gateway)?

### 3. Thêm đăng nhập Facebook (hoặc khác)?

**Hiện trạng**: Google OAuth (Google Identity Services, verify ID token, đọc `GOOGLE_OAUTH_CLIENT_ID` từ env) và Email/mật khẩu đều chạy thật. Facebook/Apple/GitHub/Zalo: hoàn toàn chưa có — không nút, không dependency, không TODO. Kiến trúc auth hiện tại KHÔNG dùng cơ chế OAuth2-client chuẩn của Spring cho Google (tự viết verify riêng) — thêm nhà cung cấp mới cần viết 1 provider riêng theo đúng khuôn `GoogleOAuthProvider`, không phải chỉ thêm config.

**Cần bạn trả lời**: có thật sự cần thêm không (Google + Email/mật khẩu đã đủ cho phần lớn người dùng VN)? Nếu cần, ưu tiên Facebook hay Zalo (Zalo phổ biến hơn với nhiều nhóm người dùng Việt)?

---

## Mục còn treo từ trước (giữ nguyên quyết định cũ)

| Hạng mục | Trạng thái |
|---|---|
| A4c — Template Fishbone/Matrix cho Mindmap | Không khả thi với Mermaid (thư viện không hỗ trợ cú pháp này) — cần đánh giá lại từ đầu bằng lib khác (D3/Markmap) nếu vẫn muốn làm. Quy mô lớn, tách riêng hoàn toàn. |
| A6 — Redis token-bucket thay DB-COUNT cho rate-limit AI | Không cần làm trừ khi có bằng chứng cụ thể về race-condition/hiệu năng xảy ra thật. |
| A7 — Onboarding Profile Wizard (Task 11B) | **Bỏ qua theo chỉ đạo trước đó** — thuộc luồng người khác đang phụ trách, không đụng vào cho tới khi có chỉ đạo khác. |

---

## Task tiếp theo — thứ tự đề xuất

1. **Momo/ZaloPay trả sai URL** (🔴, nhanh, làm ngay được) — vẫn còn treo từ đầu, chưa đụng tới.
2. **AI Anti-Cheat** (🎯 #1) — còn lại đúng 2/3 thuật toán điểm nhấn cho luận văn, đã có thiết kế chi tiết (Tầng 1 rule-based + Tầng 2 AI Vision webcam), chỉ cần 1 quyết định nhỏ trước khi code (bật webcam bắt buộc mọi bài thi hay chỉ bài `isProctored=true`).
3. **Auto-ban bằng AI** (🎯 #2) — thiết kế human-in-the-loop đã có, cần bạn chốt ngưỡng cụ thể (số ngày liên tục hết quota, số request/phút bất thường) trước khi code.
4. Sau khi xong cả 3 thuật toán điểm nhấn: quay lại 3 mục 🟡 cần bạn quyết định hướng (Email OTP thật, VNPAY/Momo/ZaloPay production, thêm Facebook/Zalo login) — không gấp cho buổi bảo vệ, xử lý sau.

Bạn muốn bắt đầu từ mục nào?
