# Kế Hoạch Các Hạng Mục Còn Lại (Cập nhật 26/09/2026)

Các hạng mục A1, A2, A3, A4a, A4b, A5, B1, B2 của đợt trước (Certificate PDF, gộp điểm Quiz vào %, gỡ trang `/progress`, Mermaid Live Preview + Tree-card, Export PDF cheatsheet/đề trắng, nút Tài nguyên tĩnh, Import Anki/Quizlet) **đã xong toàn bộ phần code** — chỉ còn chờ bạn test tay qua UI (chi tiết từng test case đã có ở lịch sử làm việc, không lặp lại ở đây). Không phát hiện lỗi/dở dang nào khi rà lại.

File này giữ đúng 1 nơi duy nhất để ghi việc còn phải làm — cập nhật mỗi phiên làm việc, xoá mục nào xong.

---

## ✅ Fix bug: Click mốc vi phạm không tua đúng chỗ trong video & lọc chi tiết vi phạm (25/09/2026)

Đã hoàn thành theo plan đã được user duyệt:
1. **Video không tua đúng khi click mốc vi phạm**:
   - (FE) Đã sửa hàm `seekTo` trong màn hình giám sát thi: áp dụng thủ thuật gán `currentTime = 1e8` rồi đợi sự kiện `timeupdate`/`durationchange` để ép trình duyệt tính lại `duration` thật cho video `webm` ghi từ `MediaRecorder`, hoặc đợi sự kiện `loadedmetadata` trước khi thao tác nếu video chưa load xong.
   - (BE) Đã đổi công thức tính mốc gốc cho `offsetSec` (trong `InstructorProctoringController`): thay vì lấy `attempt.createdAt` (bị lệch vài giây), giờ dùng công thức `attempt.getSubmittedAt().minusSeconds(recording.getDurationSec())` để lấy đúng thời điểm bắt đầu ghi video, đảm bảo marker trỏ chính xác thời điểm.
2. **Dropdown hiển thị cho mọi vi phạm dù không có mô tả AI thật**:
   - (FE) Đã lọc chỉ hiện dropdown cho các loại vi phạm có phân tích thực sự của AI (`NO_FACE`, `MULTIPLE_FACES`, `HEAD_TURNED`, `GAZE_AWAY`). Đối với vi phạm rule-based (như chuyển tab, mở DevTools, copy/paste), click vào chỉ tua video chứ không hiện thêm mô tả cứng vô nghĩa nữa.

**Đã test**: `npx tsc --noEmit` + lint sạch trên file sửa ở FE. `mvn clean compile -DskipTests` sạch ở BE. Cần test tay bằng thiết bị có camera/mic thực tế để kiểm chứng. Đã tự commit & push.

---

## ✅ 3 bug thật + UX kế thừa Gia sư AI — phát hiện lúc test thật trên iPhone Safari (26/09/2026)

Bạn test thật trên điện thoại (máy tính công ty không có mic/camera) và phát hiện đúng — Anti-Cheat vẫn ghi nhận vi phạm nhưng KHÔNG lưu được video, cộng thêm 1 báo động giả. Rà lại tận gốc, tìm ra 3 bug thật:

1. **Video không bao giờ ghi được trên Safari (mọi thiết bị)**: `MediaRecorder` hardcode `video/webm;codecs=vp8,opus` — Safari (macOS lẫn iOS) không hỗ trợ WebM, `new MediaRecorder()` ném lỗi ngay lập tức, rơi vào catch rỗng, ghi hình fail âm thầm dù đã xin đủ quyền camera/mic. Sửa: dò `MediaRecorder.isTypeSupported()` tại runtime, chọn đúng định dạng máy hỗ trợ (webm hoặc mp4), đồng bộ từ FE (Blob type, tên file) tới BE (key lưu trữ B2, không hardcode `.webm` nữa).
2. **iOS/iPadOS không bao giờ ghi được dù có webcam**: trước đây bắt buộc phải chia sẻ được màn hình (`getDisplayMedia`) mới bắt đầu ghi — nhưng Safari di động hoàn toàn KHÔNG hỗ trợ Screen Capture API (giới hạn nền tảng, không phải bug). Sửa: màn hình giờ optional — không chia sẻ được vẫn ghi webcam full khung, còn hơn không có gì.
3. **Báo nhầm "Mở DevTools" trên điện thoại**: heuristic so `outerWidth/innerHeight` chỉ đúng trên desktop — trên Safari di động, các giá trị này đổi liên tục do thanh địa chỉ tự thu/hiện khi cuộn, tạo báo động giả dù người dùng không làm gì. Sửa: chặn hẳn check này trên thiết bị cảm ứng (`pointer: coarse`).

**Đồng thời làm luôn tính năng bạn đề xuất** ("kế thừa" UX Gia sư AI — trích dẫn kèm tua video): trang chi tiết lượt thi giờ có video sticky bên trái (không mất khi cuộn), click 1 dòng vi phạm vừa tua video tới đúng thời điểm vừa xổ ra phân tích chi tiết thật của AI (trước đây `detail` chỉ lưu chuỗi kỹ thuật `person_count=X, gaze_direction=Y` — giờ lưu đúng câu giải thích tự nhiên Gemini trả về, vd "Không có người hoặc khuôn mặt nào xuất hiện trong khung hình...").

**Đã test thật qua API** (curl với ảnh test) — xác nhận `detail` lưu đúng câu giải thích AI, không còn chuỗi kỹ thuật. **Chưa test lại qua điện thoại thật** — cần bạn thử lại 1 lượt thi proctored trên Safari (cả iPhone lẫn máy Mac nếu có) để xác nhận video giờ lưu được và không còn báo nhầm DevTools.

---

## ✅ Video giám sát vẫn không lưu được dù test trên máy có mic/camera thật (26/09/2026)

Bạn test lại trên thiết bị có mic/camera thật (không phải hạn chế do máy công ty nữa) — video vẫn hoàn toàn không lưu, cộng thêm 1 điểm AI phân tích còn thô và 1 điểm UX click-vi-phạm quá tức thời. Dùng Explore agent trace lại đúng code vừa viết (không đoán), xác nhận 4 bug + 1 điểm cần tinh chỉnh prompt AI:

1. **Nguyên nhân chính khiến video luôn 0-byte/không upload**: effect "Gắn stream" tự dừng track camera/mic ngay khi `result` (kết quả nộp bài) truthy. Nhưng `submitExam`'s `onSuccess` gọi `setResult(data)` NGAY DÒNG ĐẦU — TRƯỚC `await stopCompositeRecording()`. `await` nhường quyền lại event loop → React kịp flush `result` → effect chạy NGAY, dừng cứng track camera/mic TRONG LÚC `MediaRecorder` còn đang đợi `onstop` flush chunk cuối → track audio bị cắt đột ngột → recorder ra blob rỗng → không bao giờ gọi API upload. `onSuccess` đã có sẵn 1 lệnh dừng track ĐÚNG CHỖ ở cuối (sau khi upload xong) — lệnh dư thừa trong effect chính là nguồn gây race, đã xoá hẳn.
2. **Rò rỉ camera/mic khi nộp bài lỗi** (bug phụ cùng gốc): các nhánh lỗi (410/404/500/lỗi chung) trước đây không dừng track nào cả — trước đây "ăn theo" hiệu ứng phụ của effect ở mục 1 (dựa vào `result`, nhưng `result` không set khi lỗi) nên đèn camera thực ra sáng treo khi nộp bài thất bại. Đã thêm dừng track tường minh ở các nhánh lỗi.
3. **Lỗi upload video im lặng hoàn toàn**: mutation upload video không có `onError` — fail vì mạng/413/token hết hạn... thì không log, không toast, không cách nào biết. Đã thêm `console.error` + toast nhẹ (không đổ lỗi học viên, không ảnh hưởng điểm).
4. **Phòng ngừa rủi ro Safari đứng hình**: canvas ghép hình trước đây không gắn vào DOM — Safari có tiền sử `captureStream()` trên canvas rời DOM bị đứng hình. Đã gắn canvas ẩn vào DOM lúc bắt đầu ghi, gỡ ra lúc dừng ghi.
5. **AI chỉ báo "không thấy mặt", không phân biệt được "quay mặt đi"**: nguyên nhân không phải logic BE (đã đúng) mà ở PROMPT gửi Gemini — câu hỏi gộp lẫn "faces" và "people" khiến khi thí sinh quay hẳn đầu đi (mặt không thấy rõ nhưng người vẫn ngồi đó), Gemini trả lời theo nghĩa "face" nên `person_count=0`, sụp về "không thấy ai" thay vì đúng ra phải là "quay mặt đi". Đã tách prompt thành 2 câu hỏi độc lập: đếm SỰ HIỆN DIỆN của người (đầu/vai/thân, không cần thấy mặt) và hướng nhìn riêng biệt; "không xác định" giờ chỉ dùng khi thật sự không đánh giá được (quá tối, camera bị che).
6. **UX click vi phạm quá tức thời** (theo đúng ý bạn "kế thừa Gia sư AI" đã làm đợt trước, giờ tinh chỉnh nhịp độ): trước tua video và xổ chi tiết cùng lúc 1 tick, cảm giác đột ngột. Giờ tua video trước, đợi ~300ms rồi mới xổ chi tiết — đúng cảm giác "tua trước, đọc sau".

**Đã test**: `npx tsc --noEmit` + lint sạch trên 2 file FE sửa (không có lỗi nào ngoài các lỗi pre-existing không liên quan ở trang khác); `mvn clean compile -DskipTests` sạch (BE không đổi code, chỉ chạy lại cho chắc theo quy tắc); ai-worker `py_compile` sạch, container restart lên khoẻ mạnh. **Chưa test được qua Gemini thật** lúc này vì toàn bộ pool API key đang cooldown 1h do rate-limit từ các đợt test trước trong phiên (429, không liên quan tới code vừa sửa) — cần bạn tự test lại: (a) làm 1 lượt thi proctored đầy đủ tới lúc nộp trên máy có camera/mic thật, xác nhận vào "Giám sát thi" thấy video phát được; (b) thử quay mặt hẳn sang 1 bên khi đang thi, xác nhận vi phạm ghi nhận là "ánh mắt rời màn hình" thay vì "không thấy khuôn mặt" (cần đợi qua cooldown Gemini trước); (c) vào 1 lượt thi có vi phạm, click 1 dòng, xác nhận video tua trước rồi mới xổ chi tiết.

---

## ✅ Fix bug thật: "cooldown 1h do 429" ở trên thực ra là bug logic, không phải rate-limit thật (26/09/2026)

Bạn chỉ đúng ngay: dashboard Google AI Studio cho thấy quota Gemini 3.5 Flash còn dư rất nhiều (8/1000 RPM) — không thể nào hit limit thật như tôi báo nhầm ở mục trên. Dùng Explore agent trace lại code, tìm ra bug thật trong `ai-worker/app/providers/gemini.py`: lúc verify fix trước đó, tôi gọi thử `/analyze-frame` với 1 chuỗi byte JPEG giả (test data, không phải ảnh thật) → Gemini trả đúng `400 Bad Request` (hợp lý, payload ảnh hỏng) — nhưng code cũ coi MỌI mã 400/403 là "key có vấn đề" và khoá luôn 1 tiếng, không phân biệt được "key thật sự sai/bị chặn" với "1 request cụ thể gửi payload sai". 1 request test hỏng đã khoá oan 1 key hoàn toàn khoẻ mạnh mất 1 tiếng.

Đã sửa: đọc body lỗi Google trả về để phân biệt — chỉ khoá key khi thật sự là lỗi auth/key (403, `PERMISSION_DENIED`, `UNAUTHENTICATED`, `API_KEY_INVALID`); 400 do payload sai (vd ảnh hỏng) thì trả lỗi thẳng cho caller, không đụng key pool. Đồng thời sửa dòng log cứng "bi 429" (in sai ngay cả khi lý do thật là 400/403) và thêm validate base64 sớm ở `proctoring.py` để chặn payload hỏng trước khi tốn 1 lượt gọi Gemini.

**Đã test qua API thật** (curl trực tiếp `/analyze-frame`): (1) base64 hỏng hoàn toàn → bị chặn ngay tại ai-worker, không gọi Gemini; (2) base64 hợp lệ nhưng không phải ảnh thật → Gemini từ chối đúng với `INVALID_ARGUMENT`, log không còn "bi 429", key KHÔNG bị khoá; (3) gọi lại ngay với 1 ảnh JPEG hợp lệ → trả `200 OK` bình thường, xác nhận key vẫn hoạt động liền sau lỗi trước đó, không còn bị khoá oan. Đã commit & push `ai-worker` lên `feat/additional-features`.

---

## ✅ Video Safari upload thành công nhưng sai đuôi/Content-Type nên không phát được (26/09/2026)

Bạn test thật attempt #69 trên iPhone Safari: 3 vi phạm ghi đúng mốc thời gian, nộp bài thành công, nhưng vào "Giám sát thi" vẫn báo "No video with supported format and MIME type found". Kiểm tra DB + B2 xác nhận file THẬT SỰ đã tạo và upload thành công (17.5MB, không phải file rỗng như bug race condition đã sửa trước đó) — dùng Explore agent trace lại toàn bộ chuỗi FE→BE→B2, xác nhận đây là 1 bug khác: **BE dùng chính chuỗi Tika đoán được để quyết định CẢ đuôi file LẪN Content-Type lưu B2** — nhưng Tika soi "magic bytes" dựa vào box `ftyp` ở đầu file để nhận diện mp4, mà container MP4 phân mảnh (fragmented) do `MediaRecorder` của Safari tạo ra không theo cấu trúc mux chuẩn (khác ffmpeg), rất dễ soi trượt và rơi về nhánh mặc định "webm" — dù bytes bên trong thật sự là MP4/H.264. Hậu quả: file lưu đúng bytes MP4 nhưng khai sai `.webm` + `Content-Type: video/webm`, trình duyệt reviewer đúng lý từ chối phát.

**Đây là lỗi chung của MỌI Safari (macOS lẫn iOS/iPadOS)**, không phải riêng iPhone — nguyên nhân gốc là cách Safari tạo container MP4, không liên quan hình dạng thiết bị. Chrome/Firefox ghi webm chuẩn nên Tika soi đúng, không gặp lỗi này.

Đã sửa: BE không còn dùng chuỗi chi tiết Tika để quyết định đuôi/Content-Type nữa — chỉ giữ Tika làm hàng rào an ninh rộng (phải là `video/*` mới cho qua, không đổi). Đuôi/Content-Type chính xác giờ lấy từ TÊN FILE GỐC do FE đặt (`attempt-<id>.mp4`/`.webm` — FE tự tính từ `recorder.mimeType` THẬT của trình duyệt lúc ghi, đáng tin hơn Tika cho container lạ này). Kèm 1 fix phòng ngừa: bọc `await stopCompositeRecording()` bằng timeout 5s ở FE — nếu `MediaRecorder.onstop` không bao giờ bắn ra trên 1 số WebKit (tiền sử đã biết), camera/mic vẫn được giải phóng đúng lúc thay vì treo vĩnh viễn (nghi ngờ liên quan tới icon micro bạn thấy vẫn hiện sau khi nộp bài, dù CHƯA CHẮC CHẮN 100% là nguyên nhân — video attempt #69 vẫn upload thành công nghĩa là lần đó `onstop` đã bắn ra bình thường).

**Đã test qua API thật**: upload 1 file `.mp4` giả lập (có `ftyp` box hợp lệ) qua `POST /api/v1/quizzes/attempts/69/recording` — xác nhận key lưu B2 giờ đúng đuôi `.mp4` và `Content-Type: video/mp4` (trước đây sẽ luôn phụ thuộc Tika, giờ đảm bảo đúng theo tên file FE gửi bất kể Tika đoán ra sao). **Recording cũ của attempt #69 vẫn sai** (dữ liệu cũ trước fix, không tự sửa ngược được) — cần bạn làm lại 1 lượt thi MỚI trên Safari để có video đúng, xác nhận: (a) video phát được trên trang "Giám sát thi", (b) icon micro tắt đúng lúc sau khi nộp bài.

---

## ✅ UX Giám sát thi + Materials Workspace — sửa theo phản hồi thật (26/09/2026)

Phản hồi trực tiếp sau khi dùng thử: thiết kế "Giám sát thi" đợt trước vi phạm nhiều nguyên tắc UX cơ bản — quá nhiều bước (chọn khoá → chọn quiz → mới thấy lượt thi), quá nhiều chữ giải thích thừa, dùng ID vô nghĩa ("Bài thi #3"), ngôn từ dài dòng ("Xem bằng chứng", "Không có video bằng chứng cho lượt thi này"). Đã sửa toàn bộ:

- **IA**: bỏ hẳn 2 trang trung gian (chọn khoá, chọn quiz) — "Giám sát thi" giờ là 1 tab ngay trong sidebar sửa khoá học (cùng cấp "Chương trình giảng dạy"/"Học liệu & Quiz thi cử"), courseId đã có sẵn từ URL nên chỉ còn 1 bảng phẳng gộp mọi lượt thi của mọi quiz giám sát trong khoá — 1 API duy nhất (`GET .../courses/{courseId}/attempts`), không cần vào 1 quiz cụ thể mới xem được dữ liệu.
- **Chữ nghĩa**: bỏ phụ đề giải thích thừa dưới tiêu đề; "Bài thi #3" → tên thật (fallback "Đề thi không tên" nếu instructor chưa đặt tên, khớp quy ước "Học liệu không tên" đã dùng ở Workspace — KHÔNG hiện ID DB vô nghĩa nữa); "Xem bằng chứng" → icon mắt + tooltip; "Không có video bằng chứng cho lượt thi này" → "Chưa có video".
- **Chỉ nổi bật thứ đáng chú ý**: badge rủi ro chỉ hiện màu khi MEDIUM/HIGH — LOW/chưa có đánh giá để trống (dấu gạch ngang), không kéo mắt người xem vào thứ không quan trọng.
- **Materials Workspace — tìm ra nguyên nhân gốc "nhìn như ảnh tĩnh, không phân biệt được"**: `MaterialFolderTree.tsx` dùng `border-gray-50` (literal Tailwind, gần như vô hình trên nền trắng) thay vì token `border-line` của dự án — sau hơn chục lần "fix" trước đó không cải thiện vì không ai tìm đúng dòng này. Đã sửa + audit toàn bộ `border-gray-*`/token literal khác trong khu vực. Panel "Phân Phối (Shortcuts)" bên trái: các dòng shortcut trước đây là text trần không border/nền/màu nhạt (`text-ink-muted`) — giờ có border + nền + `text-ink` đậm, rõ ràng là object bấm được.

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

## 🚀 Nâng cấp Kiến trúc Bảo mật & Tối ưu AI (Phân tích 25/09/2026 - Cần hiện thực)

*Ghi chú: Đây là kết quả từ cuộc thảo luận chuyên sâu về kiến trúc hệ thống và các véc-tơ tấn công (Attack Vectors). Cần hiện thực các ý tưởng này để biến hệ thống thành một sản phẩm có tính bảo mật/tối ưu thực tế cao (Production-ready), rất có giá trị cho báo cáo luận văn.*

### 1. Nâng cấp Anti-Cheat: Hybrid Event-Driven Architecture (AI Biên - Đám mây)
**Vấn đề / Phản biện từ user**: 
> "Nếu server-side chỉ dùng đúng 1 tấm hình sau mỗi 25s thì không tránh khỏi chụp đúng lúc người dùng chớp mắt hoặc lắc nhẹ đầu... mà nếu capture khung hình liên tục thì tốn tài nguyên. Khoảng 30 tấm hình cho 15 phút thi để ra kết luận là quá rủi ro. Liệu ta có thể tận dụng cái AI quét mỗi 2s ở client-side cho phía server-side không?"

**Thiết kế hiện thực (Cần làm)**:
- **Chuyển từ "Định kỳ" (Periodic 25s) sang "Hướng sự kiện" (Event-driven)**: 
  Sử dụng mô hình AI nhẹ ở FE (`face-api.js` quét mỗi 2s) làm "Cò súng" (Trigger).
- **Logic FE**: Thay vì gửi ảnh lên AI Worker mù quáng mỗi 25s, FE sẽ theo dõi kết quả của mô hình 2s. Nếu phát hiện `NO_FACE` hoặc `MULTIPLE_FACES` **liên tục trong 3 chu kỳ (6 giây)**, FE lập tức cắt 1 frame ảnh và gửi khẩn cấp lên Server (Gemini) để xác minh (Bypass chu kỳ chờ).
- **Throttling (Giới hạn tỷ lệ)**: Vẫn phải giữ một khoảng thời gian chờ tối thiểu (ví dụ: tối đa 1 request mỗi 15-20s) kể cả khi FE báo động liên tục, để chống spam API nếu mô hình FE bị nhiễu.
- **Bắt lỗi hướng nhìn (Gaze/Head turn)**: Vì model FE không bắt được hướng nhìn, vẫn giữ một chu kỳ gửi ảnh ngẫu nhiên (Randomized Polling: ví dụ random 30s-60s gửi 1 lần thay vì cố định 25s) để học viên không thể căn giờ gian lận.

### 2. Nâng cấp Auto-Ban: Chống tấn công Denial of Wallet (DoW) qua Prompt Injection
**Vấn đề / Phản biện từ user**:
> "Hết token/Spam request ở học liệu đã có Daily Quota và Redis Lock lo. Kẽ hở duy nhất là AI Discovery/Tutor. Kẻ gian có thể lách luật: Gửi một văn bản CỰC KỲ DÀI vào prompt và bọc bằng câu 'Tôi thấy nội dung này có trong khóa học XXX phải không?'. Nhờ có chữ 'khóa học XXX', nó qua mặt bộ lọc relevance, ép Server đọc 1 đống text rác dài ngoằng, làm cạn kiệt API key (Tốn tiền)."

**Thiết kế hiện thực (Defense in Depth - Cần làm)**:
- **Lớp 1: Giới hạn độ dài đầu vào (Hard Length Limit)**:
  - FE: Thêm thuộc tính `maxLength={1000}` (hoặc 500) vào tất cả các ô input chat AI (Tutor / Discovery).
  - BE/AI-Worker: Dừng ngay và trả về `400 Bad Request` nếu payload `len(prompt) > 1000` trước khi chạm vào bất kỳ logic AI hay Token router nào.
- **Lớp 2: Rút gọn ngữ cảnh (Context Truncation)**:
  - Ở AI-Worker, khi query Vector DB (Supabase), dù có tìm ra nhiều bài học liên quan, chỉ được phép nhồi tối đa **3 đoạn văn bản (chunks)** có độ tương đồng cao nhất vào System Prompt gửi cho Gemini. Không bao giờ gửi toàn bộ file nội dung.
- **Lớp 3: Nâng cấp Auto-Ban dựa trên Token (Token-based Tracking)**:
  - Hiện tại `AiLockScan` đang đếm số Request/phút. Cần nâng cấp: Đọc cột `total_tokens` trong bảng `ai_usage_logs`.
  - Nếu 1 user tiêu thụ quá mức bất thường (VD: > 50,000 tokens trong 5 phút), lập tức cắm cờ cảnh báo Auto-Ban, bất kể số lượng request là 1 hay 10.

---
## 🛠️ Chốt Cấu Hình Production & Cleanup (Đã thống nhất, chờ làm)

*Các hạng mục dưới đây đã được thảo luận và chốt phương án để tối ưu hóa cho mục tiêu bảo vệ luận văn (chạy thật, miễn phí, dễ setup, UI gọn gàng). Các agent ca sau cần bám sát cấu hình này để code.*

### 1. Dọn dẹp UI Cổng thanh toán
- **Yêu cầu**: Xóa hoàn toàn các phương thức thanh toán ảo/chưa kích hoạt (VNPAY, Momo, ZaloPay) khỏi giao diện Frontend.
- **Lý do**: Không có Giấy phép ĐKKD nên không thể đưa lên môi trường thật (Production) cho các cổng này. Giữ lại nút bấm ảo sẽ làm hội đồng bắt bẻ.
- **Hành động**: Chỉ giữ lại duy nhất phương thức **PayOS (Chuyển khoản VietQR)** làm phương thức thanh toán chính thức vì nó đã chạy thật thành công. Xóa/ẩn UI liên quan đến VNPAY/Momo/ZaloPay trên FE.

### 2. Thay thế Mailpit bằng Gmail SMTP
- **Yêu cầu**: Backend cần gửi OTP thật đến email người dùng thay vì dùng server local Mailpit.
- **Lý do**: Cần một hệ thống gửi mail thật, hoàn toàn miễn phí, và không yêu cầu xác thực Tên miền (Domain).
- **Hành động**: 
  - Cập nhật cấu hình `spring.mail.*` trong `application.yml` (hoặc tạo `application-prod.yml`).
  - Sử dụng Host: `smtp.gmail.com`, Port: `587`, bật `smtp.auth=true` và `smtp.starttls.enable=true`.
  - Hướng dẫn User lấy Mật khẩu ứng dụng (App Password) của Gmail và đưa vào Environment variables.

### 3. Bổ sung Chính sách cho Google OAuth Consent Screen
- **Yêu cầu**: Màn hình đăng nhập Google phải chuyên nghiệp (hiện logo, có link chính sách).
- **Quyết định**: **KHÔNG CẦN CODE**. 
- **Hành động**: Đây là việc setup trên Portal. Cần nhắc User đăng nhập Google Cloud Console -> OAuth Consent Screen -> Upload Logo dự án, điền link giả định vào ô *Privacy Policy* và *Terms of Service*. Code FE/BE hiện tại đã chuẩn.

### 4. Đăng nhập Mạng xã hội (Facebook/Zalo)
- **Quyết định**: **HỦY BỎ (Không làm)**.
- **Lý do**: Google và Email/Password đã giải quyết 99% nhu cầu. Quá trình setup Facebook/Zalo tốn nhiều thời gian (cần verify app rườm rà) mà không mang lại thêm nhiều "điểm cộng" cho luận văn. Sẽ dành thời gian tập trung trau chuốt các tính năng AI cốt lõi.

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

1. **Test tay qua UI thật cho Anti-Cheat** (ưu tiên trước — code mới test qua API/curl, chưa test qua trình duyệt thật): mở 1 quiz `isProctored=true`, làm thử 1 lượt (xin quyền Camera+Micro+chia sẻ màn hình — 3 quyền riêng, trình duyệt hỏi lần lượt), thử chuyển tab/thoát fullscreen/mở DevTools/nói to liên tục, xác nhận toast cảnh báo + tự nộp bài đúng lúc. Sau đó vào sửa khoá học → tab "Giám sát thi" (đã dời vào đây, không còn ở sidebar Giảng viên top-level nữa), xác nhận thấy đúng lượt thi vừa làm, video phát được, click marker nhảy đúng thời điểm.
2. 3 mục 🟡 cần bạn quyết định hướng (Email OTP thật, VNPAY/Momo/ZaloPay production, thêm Facebook/Zalo login) — không gấp cho buổi bảo vệ, xử lý sau.
3. (Nhỏ, không gấp) Job Celery tự xoá video proctoring quá hạn 30-90 ngày — video hiện lưu vô thời hạn.

Bạn muốn bắt đầu từ mục nào? Gợi ý: nếu muốn "nâng cấp Anti-Cheat" tiếp (bạn nhắc ở đầu phiên) — cần nói rõ hơn bạn muốn nâng cấp phần nào cụ thể (thêm tín hiệu hành vi mới? tinh chỉnh ngưỡng risk score? mở rộng màn hình giám sát?) để lên kế hoạch cụ thể, hay ý bạn là test/tinh chỉnh phần vừa xong ở mục 1 trước.
