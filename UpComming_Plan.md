# Kế Hoạch Các Hạng Mục Còn Lại (Cập nhật 25/09/2026)

Các hạng mục A1, A2, A3, A4a, A4b, A5, B1, B2 của đợt trước (Certificate PDF, gộp điểm Quiz vào %, gỡ trang `/progress`, Mermaid Live Preview + Tree-card, Export PDF cheatsheet/đề trắng, nút Tài nguyên tĩnh, Import Anki/Quizlet) **đã xong toàn bộ phần code** — chỉ còn chờ bạn test tay qua UI (chi tiết từng test case đã có ở lịch sử làm việc, không lặp lại ở đây). Không phát hiện lỗi/dở dang nào khi rà lại.

File này giữ đúng 1 nơi duy nhất để ghi việc còn phải làm — cập nhật mỗi phiên làm việc, xoá mục nào xong.

---

## ✅ Task nhỏ đã dứt điểm trong phiên 25/09/2026 (đợt 3, "râu ria")

### 1. Momo/ZaloPay ở checkout âm thầm trả sai URL — ĐÃ XONG

`PaymentService.createPayment`/`createBatchPayment` trước đây fallback mọi `paymentMethod` lạ (kể cả MOMO/ZALOPAY) sang URL sandbox VNPAY, không báo lỗi. Đã validate ngay đầu hàm, ném `BusinessRuleViolationException` 422 rõ ràng ("Phương thức thanh toán MOMO hiện chưa được hỗ trợ..."). Đã test thật qua API: MOMO → 422 đúng, VNPAY vẫn hoạt động bình thường, không để lại row `Payment` PENDING rác (rollback `@Transactional` sạch).

### 2. FE hiện sai thông báo lỗi thanh toán/ghi danh (bug liên đới phát hiện lúc sửa mục 1)

`checkout/[slug]/page.tsx`, `checkout/cart/page.tsx`, `EnrollButton.tsx` đọc `err.detail` — nhưng `ApiError` (client dùng chung) không có field này, chỉ có `.message`. Kết quả: mọi lỗi thanh toán/ghi danh luôn hiện thông báo chung chung, kể cả lỗi Momo vừa sửa ở trên (không sửa cùng lúc thì bạn sẽ vẫn thấy "Có lỗi xảy ra" thay vì lý do thật). Đã sửa cả 3 chỗ theo đúng pattern `applyCoupon` đã làm đúng từ trước.

### 3. Màn hình Workspace học liệu không có filter/search hiệu quả

`CourseMaterialsManager.tsx` (màn `.../edit/materials`) trước đây chỉ có ô tìm kiếm lọc theo tiêu đề — vô dụng với phần lớn học liệu vì tiêu đề trống ("Học liệu không tên"), và hoàn toàn không có cách nào tách Quiz/Flashcard/Mindmap hay Draft/Official — mọi loại nằm chung 1 lưới. Đã thêm 1 hàng filter mới (chip button) lọc theo loại + trạng thái, kết hợp với ô tìm kiếm sẵn có. Icon `lucide-react` đơn sắc tái dùng từ bộ đã import sẵn, màu theo token có sẵn — đúng CLAUDE.md mục 9 (quy tắc icon/label khu vực Materials Workspace). Lưu ý: "Tài nguyên tĩnh" là màn hình riêng ở sidebar (không nằm trong Workspace này) nên không có trong bộ lọc.

---

## 🎯 Thuật toán điểm nhấn cho bảo vệ luận văn — ưu tiên cao nhất, đã lên thiết kế đủ chi tiết để làm ngay

**Bối cảnh**: rà lại cho thấy AI Gia sư (Socratic Tutor) và Dubbing đều đã có chủ trong nhóm, không dùng làm điểm nhấn riêng được. 3 mục dưới đây (Anti-Cheat, Auto-ban, Discovery) hiện đều là "vỏ rỗng" — có giao diện/field nhưng KHÔNG có logic thật phía sau — và đều đang **chưa ai nhận**, có thể trở thành thuật toán thật sự của riêng bạn để trình bày trước hội đồng.

### 1. AI Anti-Cheat — Composite Behavioral Risk Engine ✅ ĐÃ XONG TOÀN BỘ (25/09/2026, kể cả video bằng chứng + màn hình giám sát)

**Đã triển khai đầy đủ và test end-to-end thật** (không phải mock — đã curl trực tiếp qua tài khoản dev, xem `quiz_attempt_violations` trong DB):
- Server-side violation tracking: bảng `quiz_attempt_violations` (audit trail vĩnh viễn, thay hẳn `localStorage` cũ mà học viên có thể sửa JS để vô hiệu hoá), endpoint `POST /api/v1/quizzes/attempts/{attemptId}/violations`, tự động báo `shouldAutoSubmit=true` khi vượt `maxViolations` — đã test thật: 3 vi phạm liên tiếp (maxViolations=3) → đúng `shouldAutoSubmit=true` ở lần thứ 3.
- Tier 1 rule-based đầy đủ (FE `exam/[quizId]/page.tsx`): tab-switch, window-blur (bắt được cả trường hợp visibilitychange bỏ lỡ), bắt buộc fullscreen, copy/paste/contextmenu, DevTools mở, idle bất thường, **âm thanh giọng nói kéo dài bất thường** (100% client-side qua Web Audio API `AnalyserNode`, KHÔNG tốn API Gemini nào — đáp ứng đúng lo ngại chi phí, và đáp ứng góp ý của giảng viên bạn về kịch bản "agent đọc câu hỏi hộ bằng giọng nói").
- Tier 2 AI thật: `POST /api/v1/proctoring/analyze-frame` (AI-worker) — Gemini Vision phân tích khung hình webcam thật (đếm người + đánh giá **định tính hướng nhìn/ánh mắt** như giám thị con người nhìn ảnh — đã test: ảnh xám không có mặt → đúng `person_count=0`), gọi định kỳ 25s, kết quả bất thường ghi thẳng vào cùng đường `quiz_attempt_violations`.
- **Composite Risk Scoring** (điểm nhấn thật sự, trả lời đúng yêu cầu "suy đoán mức độ gian lận" chứ không đếm đơn thuần): lúc nộp bài, Gemini nhận toàn bộ tín hiệu hành vi đã ghi nhận trong phiên thi, suy luận ra `risk_level` (LOW/MEDIUM/HIGH) + giải thích tiếng Việt cụ thể — đã test thật, phân biệt rõ session "sạch" (LOW) vs session nhiều tín hiệu bất thường cùng lúc (MEDIUM, giải thích đúng lý do); Gemini còn đủ thông minh để nhận ra 1 attempt test giả (thời lượng 1 giây) không phải gian lận thật.
- Giới hạn kỹ thuật đã xác nhận và cần nói rõ khi bảo vệ: **không thể phát hiện trực tiếp remote-desktop/AnyDesk** (ngoài khả năng JS trong trình duyệt, giới hạn sandbox OS — không riêng dự án này, mọi tool proctoring thương mại cũng vậy) — Composite Risk Engine là lớp phòng vệ hành vi gián tiếp đúng cách các tool đó tiếp cận vấn đề.

**Video bằng chứng + màn hình "Giám sát thi" — ĐÃ XONG (đợt 2 cùng ngày)**: FE ghép màn hình (`getDisplayMedia`) + webcam vào 1 canvas mỗi frame, `MediaRecorder` ghi thành 1 file `.webm`, upload lúc nộp bài (`POST .../recording`, tái dùng `StorageService`/B2, key `proctoring/{attemptId}/{uuid}.webm` — đúng quy ước đặt tên hiện có của dự án, KHÔNG nhúng tiêu đề/ngày giờ vào tên file). Màn hình mới **"Giám sát thi"** ở sidebar giảng viên (`/instructor/proctoring`) — chọn khoá học → danh sách quiz có giám sát (badge số lượt rủi ro cao) → danh sách lượt thi (badge màu theo risk level) → chi tiết 1 lượt thi: video + nhận định AI + danh sách marker vi phạm, click nhảy đúng tới thời điểm trên video (offset-giây tính sẵn ở BE). Đã test qua API thật: `GET .../courses/9/quizzes`, `.../quizzes/9/attempts`, `.../attempts/64` đều trả đúng dữ liệu thật kèm marker offset chính xác. Panel khoanh vùng + border nhẹ (`.card`/`border-line`), màu theo token có sẵn, không emoji/phối màu tuỳ tiện — đúng quy tắc thiết kế chung.

**Lưu ý còn treo (nhỏ, không chặn demo)**: job Celery tự xoá video quá hạn (30-90 ngày) trong `maintenance.py` — CHƯA làm (video hiện lưu vô thời hạn trên B2), không gấp cho buổi bảo vệ nhưng nên làm trước khi đưa vào production thật lâu dài.

### 2. Auto-ban bằng AI ✅ ĐÃ XONG (25/09/2026, human-in-the-loop, không tự khoá)

**Đã triển khai và test end-to-end thật**: `AiLockScanService` quét 2 tín hiệu (rút gọn từ 3 — tín hiệu "Gemini phân loại nội dung prompt" không khả thi vì `AiUsageLog` không lưu nội dung prompt, chỉ lưu số token): (1) quota gần cạn liên tục 3 ngày, (2) tần suất >10 request/phút. Đạt 1 trong 2 → chỉ tạo **đề xuất** (`User.aiLockProposedAt/Reason`), KHÔNG tự khoá — Admin xem lý do cụ thể ở `GET /api/v1/admin/ai-lock-proposals` (đã có UI ở `app/admin/users/page.tsx`, banner đề xuất + nút Xác nhận/Bỏ qua) rồi mới quyết định qua `POST .../confirm-lock-proposal`. Celery beat gọi quét mỗi 6 giờ, đã confirm task đăng ký đúng trong worker.

<details><summary>Hiện trạng cũ trước khi làm (giữ lại để tham khảo lịch sử)</summary>

**Hiện trạng thật (trước 25/09/2026)**: `User.isAiLocked` chỉ được set thủ công qua 1 endpoint admin bấm tay (`AdminController`) — không có job/quy tắc tự động nào, dù comment trong code có nhắc ý định này (`AiUsageLog` javadoc).

**Thiết kế đề xuất — human-in-the-loop** (an toàn hơn khi trình bày, tránh câu hỏi "AI khoá nhầm thì sao"):
- Tín hiệu bất thường (khai thác `AiUsageLog` có sẵn, không cần bảng mới cho tín hiệu):
  1. Dùng hết quota AI liên tục N ngày (vd 3 ngày).
  2. Tần suất request bất thường (nhiều request trong thời gian rất ngắn — dấu hiệu script/bot).
  3. (Tầng AI thật) Gemini phân loại các prompt/yêu cầu gần nhất: có dấu hiệu prompt injection hay nội dung vi phạm chính sách không.
- Đạt đủ 2/3 tín hiệu → **không tự khoá ngay**, tạo "đề xuất khoá" kèm lý do cụ thể, hiện ở màn Admin có sẵn → admin xác nhận mới thật sự khoá (dùng lại đúng `isAiLocked`).

**Việc cụ thể cần làm**:
Ngưỡng đã chốt và triển khai: 3 ngày liên tục + >10 request/phút (xem mục ✅ ĐÃ XONG phía trên).
</details>

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

Cả 3/3 thuật toán điểm nhấn (Discovery, Anti-Cheat + video bằng chứng + màn hình giám sát, Auto-ban) đã xong phần code và test qua API thật. Momo/ZaloPay + 3 bug/thiếu-sót nhỏ liên đới cũng đã dứt điểm (xem mục ✅ phía trên). Còn lại:

1. **Test tay qua UI thật cho Anti-Cheat** (ưu tiên trước — code mới test qua API/curl, chưa test qua trình duyệt thật): mở 1 quiz `isProctored=true`, làm thử 1 lượt (xin quyền Camera+Micro+chia sẻ màn hình — 3 quyền riêng, trình duyệt hỏi lần lượt), thử chuyển tab/thoát fullscreen/mở DevTools/nói to liên tục, xác nhận toast cảnh báo + tự nộp bài đúng lúc. Sau đó vào `/instructor/proctoring` với tài khoản giảng viên, xác nhận thấy đúng lượt thi vừa làm, video phát được, click marker nhảy đúng thời điểm.
2. 3 mục 🟡 cần bạn quyết định hướng (Email OTP thật, VNPAY/Momo/ZaloPay production, thêm Facebook/Zalo login) — không gấp cho buổi bảo vệ, xử lý sau.
3. (Nhỏ, không gấp) Job Celery tự xoá video proctoring quá hạn 30-90 ngày — video hiện lưu vô thời hạn.

Bạn muốn bắt đầu từ mục nào? Gợi ý: nếu muốn "nâng cấp Anti-Cheat" tiếp (bạn nhắc ở đầu phiên) — cần nói rõ hơn bạn muốn nâng cấp phần nào cụ thể (thêm tín hiệu hành vi mới? tinh chỉnh ngưỡng risk score? mở rộng màn hình giám sát?) để lên kế hoạch cụ thể, hay ý bạn là test/tinh chỉnh phần vừa xong ở mục 1 trước.
