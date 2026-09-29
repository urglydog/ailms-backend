# Kế Hoạch Phát Triển Nâng Cao LMS (Premium Commercial Roadmap)

Tài liệu này định nghĩa chi tiết các Epic/User Story để nâng cấp hệ thống LMS lên tiêu chuẩn thương mại cao cấp. Các tính năng được chia theo Sprint dựa trên ma trận ưu tiên: **Hạ tầng chống chịu -> Dòng tiền -> Tính năng giữ chân/AI**.

Trước mỗi tính năng đều đi kèm với **Bối cảnh & Nỗi đau (Pain Point)** để đội ngũ kỹ thuật/vận hành luôn nắm rõ bài toán cốt lõi.

---

## SPRINT 1: NỀN MÓNG HẠ TẦNG & DÒNG TIỀN (INFRASTRUCTURE & MONETIZATION)

### ~~1. Redis Cache cho dữ liệu tĩnh/bán tĩnh~~ [ĐÃ HOÀN THÀNH]
* **Bối cảnh & Nỗi đau (Pain Point):** Khi lượng truy cập lớn (hàng nghìn học viên cùng vào xem bài giảng hoặc truy cập dashboard), việc query liên tục vào DB quan hệ sẽ làm nghẽn connection, tăng độ trễ và đẩy CPU database lên 100%. Các API đọc dữ liệu tĩnh/bán tĩnh đang tạo áp lực không cần thiết lên hệ thống.
* **Technical Story:** Là một Hệ thống Backend, chúng tôi cần áp dụng bộ nhớ đệm Redis Cache cho các API đọc dữ liệu tĩnh/bán tĩnh (thông tin khóa học, mục lục bài giảng, hồ sơ công khai), để giảm tải 70-80% truy vấn trực tiếp vào Database chính và giữ thời gian phản hồi của API dưới 100ms ngay cả khi traffic tăng đột biến.
* **Acceptance Criteria (AC):**
  - Áp dụng chiến lược Cache-Aside hoặc Write-Through phù hợp.
  - Cơ chế Cache Invalidation chính xác: Khi giảng viên cập nhật nội dung bài học, cache tương ứng phải được làm mới ngay lập tức.
* **Đã làm (29/09/2026):** Cache-Aside cho `courseDetails`/`publicCourseSearch`/`categories`/`publicProfile` (Redis, `CacheConfig.java`); evict ngay sau commit khi giảng viên sửa khóa/chương/bài/review (`CacheEvictionHelper`, không evict thủ công cho `publicProfile` — TTL 10 phút, chấp nhận trễ vì ghép từ nhiều nguồn). Đã vá 1 lỗ hổng bảo mật (cache khóa "Riêng tư mời" từng lộ nội dung cho người không được mời) + 1 bug khiến cache `courseDetails` trước đó không bao giờ thực sự đọc được (luôn âm thầm miss, chỉ ghi) — xem chi tiết trong lịch sử commit `be`.



### ~~3. Hệ thống Khuyến mãi (Coupons & Vouchers)~~ [ĐÃ HOÀN THÀNH]
* **Bối cảnh & Nỗi đau (Pain Point):** Hiện tại nền tảng thiếu công cụ tạo ưu đãi linh hoạt để kích thích chuyển đổi cho các chiến dịch ra mắt hoặc bán hàng qua mạng xã hội, làm mất đi lượng lớn khách hàng nhạy cảm về giá.
* **User Story:** Là một quản trị viên (Admin) hoặc Giảng viên, tôi muốn tạo mã giảm giá linh hoạt (theo %, số tiền cố định, giới hạn lượt dùng hoặc thời hạn sử dụng), để tôi triển khai các chiến dịch Marketing thúc đẩy doanh số bán khóa học.
* **Acceptance Criteria (AC):**
  - Áp mã thành công tại trang thanh toán và tự động tính lại số tiền trừ trực tiếp.
  - Quản lý giới hạn: Mỗi người chỉ được dùng 1 lần, hết hạn tự động vô hiệu hóa.

### ~~4. Bán chéo & Gói khóa học (Bundles & Upsells)~~ [ĐÃ HOÀN THÀNH]
* **Bối cảnh & Nỗi đau (Pain Point):** Học viên có xu hướng chỉ mua 1 khóa học lẻ, khiến chỉ số Giá trị vòng đời khách hàng (LTV) thấp. Khó khăn trong việc gợi ý học viên theo đuổi trọn vẹn một lộ trình kỹ năng.
* **User Story:** Là một học viên chuẩn bị thanh toán, tôi muốn nhìn thấy các gói combo khóa học liên quan với mức giá ưu đãi hơn mua lẻ, để tôi có thể tiết kiệm chi phí và sở hữu trọn vẹn lộ trình kỹ năng.
* **Acceptance Criteria (AC):**
  - Module combo: Mua khóa A + khóa B được giảm thêm 20%.
  - Giỏ hàng tự động gợi ý các khóa học bổ trợ kèm nút "Thêm vào đơn hàng chỉ với +XX đồng".
  - **Refined AC (Pro-rated Pricing):** Xử lý trường hợp trùng lặp: Nếu học viên đã sở hữu Khóa A, khi bấm mua Combo (Khóa A + Khóa B), hệ thống tự động trừ tiền Khóa A đã thanh toán trước đó để ra giá cuối hợp lý.
* **Đã làm (29/09/2026):** Giảng viên tạo/sửa gói combo (`CourseBundleController/Service`); trang chi tiết khóa hiện widget gợi ý gói (`BundleUpsellWidget`); giỏ hàng TỰ PHÁT HIỆN khi đã đủ khóa của 1 gói (`matchCartBundles.ts`, greedy chọn tập gói rời nhau tránh chồng lấn, tính pro-rated đúng công thức BE, xử lý cả case đã sở hữu sẵn 1 khóa), gợi ý mua thêm khi thiếu đúng 1 khóa; thanh toán gửi đúng `bundleIds`, giá chốt khớp giữa `/cart`, `/checkout/cart` và BE. User đã test thật trên production, xác nhận hoạt động đúng.

---

## SPRINT 2: TRẢI NGHIỆM NGƯỜI HỌC, GIỮ CHÂN & TƯƠNG TÁC (ENGAGEMENT)

### ~~5. Hệ thống Streak (Chuỗi ngày học)~~ [ĐÃ HOÀN THÀNH]
* **Bối cảnh & Nỗi đau (Pain Point):** Tỷ lệ bỏ dở khóa học trực tuyến (drop-off rate) thường rất cao do học viên thiếu động lực tự giác duy trì thói quen hàng ngày.
* **User Story:** Là một học viên tự học, tôi muốn hệ thống ghi nhận và hiển thị chuỗi ngày học liên tục (Streak) kèm thông báo nhắc nhở giữ chuỗi, để tôi có thêm động lực duy trì kỷ luật học tập đều đặn mỗi ngày mà không bị ngắt quãng.
* **Acceptance Criteria (AC):**
  - Định nghĩa hoàn thành rõ ràng: xem tối thiểu 10 phút video hoặc hoàn thành ít nhất 1 bài quiz.
  - Hiển thị biểu tượng ngọn lửa/số ngày streak rõ ràng trên UI.
  - Cơ chế "đóng băng streak" (Streak Freeze) để tránh mất chuỗi khi có việc đột xuất.
  - **Refined AC (Timezone):** Hệ thống phải tính ngày Streak dựa trên Múi giờ Local của trình duyệt thiết bị người học, không fix cứng theo giờ UTC của Server để tránh mất streak oan uổng.

### ~~6. Đánh giá & Review nâng cao (AI Sentiment Filter)~~ [ĐÃ HOÀN THÀNH]
* **Bối cảnh & Nỗi đau (Pain Point):** Khóa học bị spam đánh giá rác, bot cạnh tranh không lành mạnh hoặc những bình luận mang tính xúc phạm làm sai lệch chất lượng thực tế.
* **User Story:** Là một người mua hàng, tôi muốn đọc đánh giá chân thực; Là một Admin, tôi muốn AI tự động phát hiện, gắn cờ các đánh giá toxic/spam.
* **Acceptance Criteria (AC):**
  - Chỉ cho phép tài khoản đã học tối thiểu 20-30% khóa học mới được viết review.
  - AI Sentiment phân tích nội dung review và tự động giữ lại (ẩn đi) nếu có dấu hiệu thù địch/spam.
  - **Refined AC:** Giảng viên có quyền "Report" một review, review đó bị ẩn tạm thời và chờ Admin duyệt tay.

---

## SPRINT 3: TỐI ƯU HÓA BẰNG AI & BÁO CÁO (ADVANCED AI & ANALYTICS)


### 8. AI Personalized Study Plan (Lộ trình học cá nhân hóa)
* **Bối cảnh & Nỗi đau (Pain Point):** Khóa học có dung lượng lớn, học viên dễ bị choáng ngợp, không biết phân bổ thời gian học sao cho kịp thi.
* **User Story:** Là học viên bận rộn, tôi muốn nhập ngày mục tiêu để AI lên lịch học chi tiết từng ngày.
* **Acceptance Criteria (AC):**
  - Form đầu vào: Mục tiêu kết thúc + Khung giờ học.
  - AI sinh lịch biểu đồng bộ Calendar. Nếu trễ hạn, AI tự động bù trừ cho các ngày sau.

### 9. Phân tích điểm yếu (Knowledge Gaps Analysis)
* **Bối cảnh & Nỗi đau (Pain Point):** Học viên làm sai quiz không biết hổng kiến thức ở đâu, tốn thời gian mò lại video.
* **User Story:** Là học viên vừa thi xong, tôi muốn hệ thống chỉ ra điểm yếu và link thẳng tới đoạn video lý thuyết đó để ôn tập ngay.
* **Acceptance Criteria (AC):**
  - Câu hỏi gắn Topic Tag. Nút "Ôn tập ngay" dẫn tới mốc thời gian (seconds) trong video.
  - **Refined AC (Rolling Window):** Phân tích điểm yếu phải dựa trên cửa sổ trượt thời gian (VD: kết quả 5 lần test gần nhất, hoặc trong 30 ngày qua), không cộng dồn các lỗi sai từ quá khứ xa xôi khi học viên đã tiến bộ.

### 10. Advanced Instructor Dashboard & Revenue Analytics
* **Bối cảnh & Nỗi đau (Pain Point):** Giảng viên như "ném đá vào hư không", không biết đoạn video nào nhàm chán, câu hỏi nào bị lỗi. Khó theo dõi dòng tiền thực nhận.
* **User Story:** Là giảng viên, tôi muốn xem biểu đồ rớt nhịp (Drop-off rate), độ khó câu hỏi và báo cáo dòng tiền chia sẻ lợi nhuận.
* **Acceptance Criteria (AC):**
  - Biểu đồ giữ chân (Retention Heatmap).
  - Bảng xếp hạng Top câu hỏi có tỷ lệ sai > 60%.
  - Báo cáo phân tích doanh thu gộp (Gross), phí nền tảng, thực nhận (Net).

---

*Tài liệu này được định hướng cho giai đoạn chuyển đổi LMS sang phiên bản thương mại. Sprint 1 (Redis, CDN, Coupons, Bundles) sẽ được ưu tiên triển khai trước.*