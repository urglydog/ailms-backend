# Báo cáo công việc sau Prometheus + Grafana (26–28/11/2026)

> Tổng hợp những việc đã làm từ lúc chuẩn bị giám sát (đợt 8) tới khi kết thúc phần hiệu năng: đo đạc, đợt 9,
> và hai lần điều tra lỗi. Chi tiết kỹ thuật từng đợt nằm ở các file được dẫn link.
> Bảng tổng 9 đợt: [TONG_KET_ToiUu_HeThong.md](TONG_KET_ToiUu_HeThong.md).

## Tóm tắt

| # | Việc | Kết quả chính | Trạng thái |
| --- | --- | --- | --- |
| 1 | Đợt 8: sẵn sàng giám sát | Endpoint `/actuator/prometheus` + histogram độ trễ; xoay vòng log còn thiếu; `stop_grace_period` | Đã merge (PR #241) |
| 2 | Đo bản production ở local | RAM backend **1,62 → 0,90 GB** (−45%) so với bản dev; log 28.508 → 146 dòng/2 phút | Ghi trong DOT8 |
| 3 | Nhánh `feat/monitoring` | Prometheus + Grafana + node-exporter, dashboard 13 panel; thử local ~130 MB RAM | Đã merge (PR #243), Thiện bật trên server |
| 4 | Hướng dẫn server gộp một file | Phát hành → đo tải k6 → quyết định RAM → bật giám sát; kịch bản k6 + script ghi RAM | Đã merge |
| 5 | Rà lại develop sau khi Thiện bật giám sát | Xác nhận đủ commit; ghi nhận cảnh báo Telegram, diễn tập sự cố | Không cần sửa |
| 6 | Đợt 9: ghi tiến độ học | **~15 → ~11** câu lệnh MySQL mỗi lần gửi (đo thật) | Đã merge (PR #248) |
| 7 | Đợt 9: trang học bài | First Load JS **328 → 247 kB** (−25%) | Đã merge |
| 8 | Kiểm tra lỗi "lặp đoạn lồng tiếng" | **Không có lỗi**: DB sạch, job thật + ASR nghe lại không lặp câu | Không cần sửa |
| 9 | Lỗi Gemini 401 ở Tutor / AI insight | Container `ai-api`, `ai-beat` còn **key cũ** từ 05/09; tạo lại → chạy được | Đã sửa ở local |

---

## 1. Đợt 8: sẵn sàng giám sát

Chi tiết: [DOT8_SanSangGiamSat.md](DOT8_SanSangGiamSat.md).

Kết quả kiểm tra server ngày 25/11: RAM 3,7 GB, còn 0,6–1,2 GB và đang dùng swap; backend chạy chế độ dev. Khi bắt
tay vào thì PR #240 của nhóm đã làm sẵn 4/5 việc dự định (image production, `mem_limit`, xoay vòng log một phần,
slow query log). Đợt 8 chỉ bổ sung phần còn thiếu:

- `micrometer-registry-prometheus`, mở `/actuator/prometheus`. Không cần cổng riêng vì `Caddyfile` đã chặn
  `/actuator/*` (trừ health) từ Internet.
- `percentiles-histogram.http.server.requests` để Grafana tính được p95/p99; nhãn `application` cho mọi số liệu.
- Xoay vòng log (3 × 10 MB) cho MySQL, Redis, Adminer, Mailpit.
- `stop_grace_period: 35s` cho backend, khớp với graceful shutdown 30 s của đợt 6.

Kiểm chứng: 1212/1212 test qua; `docker compose config` hợp lệ.

## 2. Đo bản production so với bản dev (local)

Hai image chạy cạnh nhau, cùng MySQL/Redis, cùng 300 request:

| | Bản dev | Bản production |
| --- | --- | --- |
| RAM backend | 1,62 GB | **0,90 GB** |
| Log trong 2 phút | 28.508 dòng | 146 dòng |
| Câu SQL in ra log | 264 | 0 |
| Khởi động | ~3 phút | 23 giây |

Ước lượng: chuyển sang production giải phóng ~0,7 GB RAM trên server, đủ để bật giám sát mà không phải nâng VPS.

## 3. Nhánh `feat/monitoring`

- `docker-compose.monitoring.yml`: `lms_prometheus` (trần 256 MB, giữ 15 ngày hoặc 2 GB), `lms_grafana` (192 MB,
  chỉ nghe `127.0.0.1:3001`), `lms_node_exporter` (48 MB). Không mở cổng nào ra Internet.
- `monitoring/`: cấu hình Prometheus, nguồn dữ liệu và dashboard Grafana tự nạp.
- Dashboard **LinguaLearn — Tổng quan** (13 panel): request/giây theo mã trạng thái, độ trễ p50/p95/p99, 5xx và 429,
  10 endpoint chậm nhất, heap JVM, kết nối HikariCP, CPU, RAM/swap, ổ đĩa.

Thử ở local: 3 target đều `up`; 16/18 truy vấn có dữ liệu ngay (2 truy vấn rỗng là đúng: chưa có lỗi 5xx; Docker
Desktop không có phân vùng `/` dạng ext4). Tổng RAM thực tế ~130 MB.

Trên server (Thiện ghi ở [BAT_GIAM_SAT_20261127.md](BAT_GIAM_SAT_20261127.md)): Prometheus 73 MB, Grafana 135 MB,
node-exporter 17 MB; RAM còn ~1.190 MB. Thiện làm thêm cảnh báo Grafana qua Telegram (7 luật), giám sát từ ngoài
bằng UptimeRobot và diễn tập sự cố backend/MySQL/Redis.

## 4. Hướng dẫn server gộp một file

[HUONG_DAN_SERVER_DOT8.md](HUONG_DAN_SERVER_DOT8.md): một file cho AI trên server làm từ đầu tới cuối: ghi trạng thái
→ đo lần A → test, build, phát hành → kiểm tra → đo lần B → bảng quyết định RAM → merge và bật giám sát → đo lần D
→ mẫu báo cáo.

Vì dự án chưa có người dùng thật nên thêm **đo tải chủ động**:
- `loadtest/k6-public.js`: 50 người dùng ảo (đổi được), chỉ gọi API đọc công khai (danh sách, chi tiết, danh mục,
  banner, tìm kiếm có từ khóa), nghỉ 1–3 s giữa các lượt như người thật. Chạy trong mạng Docker, không qua Internet.
- Mỗi người dùng ảo gửi một IP riêng trong `X-Forwarded-For`. `RateLimitFilter` lấy phần tử cuối của header này,
  nên bài đo không bị chặn 429 như khi dùng chung 1 IP.
- `loadtest/ghi-ram.sh`: ghi RAM máy và từng container mỗi 10 s ra CSV.

Thử ở local (10 người dùng ảo): 0% lỗi, p95 ~20 ms. Lần chạy đầu phát hiện đường dẫn chi tiết khóa học là
`/courses/{slug}` chứ không phải `/{id}`, đã sửa.

## 5. Rà lại develop sau khi Thiện bật giám sát

Cả `perf` (PR #241) và `feat/monitoring` (PR #243) đều đã vào develop, không thiếu commit. Hai việc còn mở bên server:
**chưa có kết quả đo tải k6** (phần 2 và 5 của hướng dẫn), và **ổ đĩa còn ~5 GB** (hôm 25/11 còn 12 GB).

## 6–7. Đợt 9: ghi tiến độ học và trang học bài

Chi tiết: [DOT9_TienDo_TrangHocBai.md](DOT9_TienDo_TrangHocBai.md).

**Ghi tiến độ học (be).** Trình phát gửi tiến độ 15 s/lần cho mỗi người đang xem, và mỗi lần đều tính lại tiến độ
cả khóa (đếm bài, đếm bài xong, tìm quiz, tìm lượt làm, đọc và ghi enrollment). Giờ chỉ tính lại khi bài vừa hoàn
thành hoặc học viên mở bài lần đầu. Làm theo TDD: 3 test mới, 2 test fail trên code cũ, qua sau khi sửa; toàn bộ
**1267/1267** test qua.

Đo thật bằng 2 image chạy cạnh nhau, 20 lần gửi × 2 lượt: **~15 → ~11** câu lệnh MySQL mỗi lần (−27%), khoảng 50
câu/giây ít hơn khi có 200 người học cùng lúc. Thấp hơn ước lượng ban đầu (~50%) vì phần còn lại là kiểm quyền truy
cập bài học và ghi điểm mốc xem cho biểu đồ giữ chân, đều cần thiết.

**Trang học bài (fe).** Tải lười 9 tab/panel chỉ hiện khi bấm (bảng điểm, kế hoạch học, bài tập, tài liệu, đánh giá,
học liệu, Tutor, ghi chú, chat trực tiếp). Trình phát và tab mặc định giữ nguyên. First Load JS **328 → 247 kB**;
679/679 test qua; build thành công.

Trước khi merge, đã merge thử cùng nhánh `fix/post-merge-polish` của session khác ở cả be và fe: không file nào trùng,
không xung đột.

**Chưa kiểm:** chưa bấm thử từng tab trang học bài trên trình duyệt.

## 8. Kiểm tra lỗi "lặp đoạn lồng tiếng"

Session UI ghi một dòng "Backend có lỗi lặp đoạn lồng tiếng (duplicate segment)" nhưng không kèm bằng chứng. Đã
kiểm 4 cách:

1. **DB:** 26 transcript / 2.091 segment và mọi audio chunk. Không trùng; có UNIQUE `(audio_track_id, chunk_index)`
   và `(transcript_id, seq)`. Không câu nào giống hệt câu ngay trước; không segment nào chồng thời gian quá 0,5 s.
2. **Job thật:** lồng tiếng tiếng Việt bài 26 (81 s), job 47 `COMPLETED` sau 2 phút 26 giây. ASR nghe lại file kết
   quả: dài 81,03 s, 17 câu, mỗi câu đúng một lần, đúng thứ tự.
3. **Code:** mỗi câu thuộc đúng một chunk theo `chunk.start <= câu.start < chunk.end`.
4. **Chỗ nối chunk** của bài dài 3464 (0–600 và 600–718 s): ASR đoạn 585–615 s liền mạch, không đọc lại câu nào.

Kết luận: không có lỗi. Nhiều khả năng đây là mục cũ (lỗi "trùng transcript gây 500" đã sửa từ trước).

## 9. Lỗi Gemini 401 ở Tutor và AI insight

**Hiện tượng:** lồng tiếng vẫn chạy, nhưng Tutor AI và AI insight (session UI mới làm) báo API key không hợp lệ.

**Nguyên nhân:** Docker chỉ đọc `env_file` lúc **tạo** container, lệnh `restart` không đọc lại.

| Container | Tạo lúc | Gọi Gemini |
| --- | --- | --- |
| `lms_ai_worker` (lồng tiếng) | 22/11, sau khi thay key | 200 |
| `lms_ai_api` (Tutor, gợi ý, insight) | 05/09 | **401**, key cũ đã thu hồi |
| `lms_ai_beat` | 05/09 | key cũ |

**Sửa:** `docker compose up -d --no-deps --force-recreate ai-api ai-beat`. So bằng mã băm (không in key): cả 3
container cùng một key. Kiểm đầu–cuối qua backend:
- Tutor (`student1`, "Unity AI có miễn phí không?"): 200 sau 7,8 s, trả lời gợi mở, dẫn đúng mốc 00:51.
- AI insight (`instructor`, khóa 6): danh sách 200; tóm tắt `PENDING` rồi `READY`, nội dung tiếng Việt do Gemini viết.

**Bài học vận hành:** đổi giá trị trong `.env` thì phải `docker compose up -d --force-recreate <service>`, không dùng
`docker restart`. Trên server cũng có thể gặp y hệt.

---

## Việc còn lại (vận hành, không cần code)

1. Phát hành bản `develop` mới nhất lên server (có đợt 9 và các commit hiệu năng của nhánh polish).
2. Chạy bài đo tải k6 theo phần 2 và 5 của [HUONG_DAN_SERVER_DOT8.md](HUONG_DAN_SERVER_DOT8.md), chụp Grafana trong
   lúc đo cho báo cáo.
3. Dọn ổ đĩa server (còn ~5 GB).
4. Bấm thử các tab trang học bài trên bản develop.

Dữ liệu thử còn lại ở local: một track tiếng Việt cho bài 26 (job 47).
