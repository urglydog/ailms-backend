# Kiến trúc triển khai và phương án cân bằng tải

> Cập nhật 26/11/2026. Dùng cho báo cáo khóa luận (mục kiến trúc triển khai) và làm cơ sở quyết định mở rộng.

## 1. Kiến trúc hiện tại (một máy)
```
Người dùng ──HTTPS──> Vercel (Frontend Next.js)
     │                         │ gọi API
     └── Mobile (Expo) ────────┤
                               ▼
              Hetzner CX23 (2 vCPU · 4 GB RAM · 40 GB đĩa), Docker Compose
   ┌──────────────────────────────────────────────────────────────────────┐
   │ Caddy :443  ──►  backend (Spring Boot, jar)  ──►  MySQL 8  ·  Redis   │
   │ (HTTPS, header bảo mật)      │  ▲                                     │
   │                              │  │ callback nội bộ (X-Internal-Token)  │
   │                              ▼  │                                     │
   │                 ai-api (FastAPI)  ·  ai-worker (Celery)  ·  ai-beat   │
   └──────────────────────────────────────────────────────────────────────┘
        │ lưu video/tài liệu                       │ gọi dịch vụ ngoài
        ▼                                          ▼
   Backblaze B2 (kho tệp)           Gemini · Groq · Azure TTS · PayOS · LiveKit · Supabase (vector)
```
Cổng mở ra Internet: 22 (SSH, chỉ khoá), 80/443 (Caddy); 8443 cần đóng nếu không ai dùng. Backend, AI, Adminer, Mailpit chỉ nghe localhost / mạng nội bộ Docker.

## 2. Giới hạn của một máy (số đo 26/11/2026)
| Tài nguyên | Hiện có | Mức dùng thường | Điểm nghẽn khi đông người |
| --- | --- | --- | --- |
| RAM | 3,7 GB (+ swap 4 GB) | ~2,2 GB, swap ~1,7 GB | backend ~0,8 GB, MySQL ~0,4 GB, AI worker 0,3–1,3 GB |
| CPU | 2 vCPU | load < 1 | lồng tiếng/ffmpeg và AI tranh CPU với web |
| Đĩa | 40 GB | ~28 GB | video lưu ở B2 nên chủ yếu là image Docker và log |
| MySQL | 21/151 kết nối đã dùng tối đa | rất nhỏ (< 30 MB dữ liệu) | chưa phải điểm nghẽn |

Hệ quả: một máy đủ cho quy mô khóa luận và buổi bảo vệ; nghẽn đầu tiên sẽ là **RAM và CPU** khi nhiều tác vụ AI chạy cùng lúc với lượng người dùng web lớn.

## 3. Hai hướng mở rộng
**A. Mở rộng dọc (nâng cấu hình máy) — đã chọn cho khóa luận.** Đổi gói Hetzner (Rescale) lên nhiều RAM/CPU hơn; đơn giản, giữ IP, không đổi mã. Đã dùng giải pháp bù: giới hạn RAM từng container, Redis giới hạn bộ nhớ, backend chạy bản jar thay vì Maven.

**B. Mở rộng ngang (nhiều máy sau cân bằng tải).**
```
Người dùng ─► Hetzner Load Balancer (HTTPS, health check /actuator/health)
                    ├─► App server 1: backend (Load Balancer lo HTTPS, Caddy không cần nữa)
                    ├─► App server 2: backend
                    └─► (AI node riêng: ai-api + ai-worker, nhiều worker đọc chung hàng đợi Redis)
              Mạng riêng ─► MySQL (máy riêng hoặc dịch vụ quản lý) · Redis (máy riêng)
```
Cân bằng tải chia request cho nhiều bản backend và tự bỏ bản hỏng; nó **chỉ có ích khi có ≥ 2 bản backend**.

### Những gì đã sẵn sàng cho chạy nhiều bản
- Đăng nhập dùng JWT, không lưu phiên trên máy → bản nào cũng xử lý được.
- Job định kỳ (`@Scheduled`) đã có khoá phân tán ShedLock qua Redis → không chạy trùng.
- Giới hạn tần suất lưu ở Redis → dùng chung giữa các bản.
- Tệp lưu ở Backblaze B2, không lưu trên đĩa máy ứng dụng.
- Backend không giữ trạng thái trong ổ đĩa; cấu hình qua biến môi trường.

### Những gì CHƯA sẵn sàng (cần làm trước khi chạy nhiều bản)
1. **Chat/live WebSocket dùng bộ phát tin trong bộ nhớ** (`enableSimpleBroker` trong `WebSocketConfig`): tin gửi tới bản backend A không tới được người đang nối vào bản B. Cần broker dùng chung (RabbitMQ/Redis relay) hoặc ép mỗi người dùng cố định một bản (sticky session).
2. **AI worker:** thư mục tạm `/tmp/lms-processing` là volume cục bộ; nhiều node cần kho dùng chung (hoặc truyền qua B2).
3. **MySQL và Redis đang nằm cùng máy với ứng dụng**; muốn nhiều bản cần tách ra máy riêng trong mạng riêng.
4. **Chưa có chiến lược phát hành không gián đoạn** (hiện tạo lại container backend mất ~1 phút); với 2 bản có thể cuốn chiếu từng bản.

## 4. Quyết định cho khóa luận
Không dựng cân bằng tải thật vì chỉ có một máy, tải mục tiêu nhỏ và các điểm 1–3 ở trên cần thay đổi kiến trúc lớn, rủi ro cao so với thời gian còn lại. Thay vào đó:
- Chạy một máy, có giới hạn tài nguyên, tự khởi động lại khi sập, sao lưu tự động, giám sát và cảnh báo.
- **Trình bày sơ đồ B** trong báo cáo cùng danh sách các điều kiện chưa thoả như trên, như hướng phát triển.
- Có phương án dự phòng cho ngày bảo vệ: tăng tạm RAM/CPU bằng chức năng Rescale (tính theo giờ) nếu đo thấy quá tải.

## 5. Mốc dung lượng để quyết định nâng cấp
| Quan sát | Hành động |
| --- | --- |
| Swap dùng liên tục > 3 GB hoặc có container bị OOM (`docker inspect … OOMKilled=true`) | tăng RAM (Rescale) hoặc giảm tải đồng thời của AI worker |
| p95 phản hồi API > 1 giây khi tải thử | xem truy vấn chậm (`/var/lib/mysql/slow.log`), cache, rồi mới tăng CPU |
| CPU > 80% kéo dài lúc lồng tiếng | giảm `--concurrency` của AI worker hoặc tách AI sang máy riêng |
