# Kịch bản sự cố và phương án ứng phó (runbook)

> Cập nhật 26/11/2026. Người quản lý máy chủ: chủ dự án. Mọi lệnh chạy trên server qua SSH, thư mục `/var/lms/be` trừ khi ghi khác.
> **Nguyên tắc:** Phát hiện → Cô lập → Khôi phục → Rà lại và ghi bài học. Không sửa `.env` khi chưa hiểu rõ; không `docker compose down -v`, không xoá volume `be_mysql_data`.

## 1. Kiểm tra nhanh khi có người báo "web lỗi"
```bash
docker ps --format 'table {{.Names}}\t{{.Status}}'          # container nào không chạy/không healthy
curl -s -o /dev/null -w "%{http_code}\n" https://46.224.175.90.nip.io/actuator/health   # 200 = backend sống
free -h; df -h /                                             # RAM/đĩa
docker logs --tail 50 lms_backend 2>&1 | grep -E ' ERROR | WARN '   # lỗi gần nhất
```
Bật lại đúng danh sách dịch vụ (KHÔNG chạy `docker compose up -d` kèm frontend):
`docker compose up -d --no-build mysql redis backend caddy ai-api ai-worker ai-beat adminer mailpit`; dự án khác: `docker start roadmap-worker-1`.

## 2. Bảng kịch bản
| Sự cố | Dấu hiệu | Hệ thống tự làm gì | Việc bạn làm | Đã thử? |
| --- | --- | --- | --- | --- |
| Backend sập / hết RAM | web trả 502/lỗi; `lms_backend` không healthy | Docker tự chạy lại (`unless-stopped`), ~70 s | xem `docker logs lms_backend`; nếu lặp lại: tăng `mem_limit` hoặc xem rò rỉ bộ nhớ | chạy `ops/drill.sh backend` |
| MySQL sập | trang công khai có cache vẫn lên (cache 30 s–1 ngày), **mọi API không cache lỗi**; `/actuator/health` 503 | tự chạy lại; dữ liệu nằm volume `be_mysql_data`; backend tự nối lại | nếu không lên: `docker logs lms_mysql`; hỏng dữ liệu → khôi phục từ sao lưu (mục 4) | **đã thử 27/11**: dừng ~40 s, MySQL healthy sau 7 s, web ổn ≤ ~21 s sau đó, dữ liệu nguyên vẹn: `drills/DRILL_20261127.md` |
| Redis sập | web **vẫn chạy nhưng chậm ~3 s/yêu cầu** (timeout Redis 1 s; trước 27/11 là treo ≥ 15 s); `/actuator/health` trả 503 | tự chạy lại; backend tự nối lại; dữ liệu AOF giữ | bật lại Redis (`docker start lms_redis`); web tự hồi phục ≤ ~21 s sau khi Redis `healthy` | **đã thử 27/11 (3 lần, trước/sau sửa)**: `drills/DRILL_20261127.md` |
| Cạn RAM (OOM) | `OOMKilled=true`, swap cao | hệ điều hành dừng **ai-worker trước** (`oom_score_adj`), MySQL/backend được bảo vệ | giảm `--concurrency`, tăng RAM bằng Rescale nếu thường xuyên | chưa |
| Đĩa đầy | ghi file lỗi, MySQL ngừng | — | `docker system df`, xoá image/cache cũ (`docker builder prune`, `docker image prune`), dọn `~/backups` cũ; log container đã giới hạn | log đã giới hạn |
| Máy chủ treo / mất | không SSH được | không | Hetzner Console: Power → Reset; nếu hỏng đĩa: khôi phục từ Snapshot/Backups, hoặc dựng máy mới + khôi phục DB (mục 4) | chưa |
| Khởi động lại máy (vd bản vá kernel) | web gián đoạn vài phút | Docker tự bật, các container `unless-stopped` tự lên | sau khi lên: kiểm mục 1; **bật tay `roadmap-worker-1` nếu đã dừng tay trước đó** | — |
| Hết quota AI (Gemini/Groq/Azure) | tính năng AI báo lỗi 429 | hạn ngạch theo ngày, thông báo lỗi | đổi/thêm khoá ở nhà cung cấp rồi cập nhật `.env` + tạo lại `ai-api`, `ai-worker`; hoặc tạm khoá tính năng | — |
| PayOS lỗi/ngừng | thanh toán/chi tiền thất bại | job đối soát thử lại | tắt chi tiền (`PAYOS_PAYOUT_ENABLED=false` trong cài đặt admin), đối soát sổ cái, liên hệ PayOS | — |
| Webhook PayOS trùng/mất | trùng giao dịch hoặc thiếu ghi danh | idempotent theo mã giao dịch; job quét thanh toán | kiểm trong trang admin tài chính; đối soát thủ công | kiểm thử đơn vị |
| Chứng chỉ HTTPS hết hạn | trình duyệt cảnh báo | Caddy tự gia hạn (hạn hiện tại 14/02/2027) | `docker logs lms_caddy`; kiểm cổng 80/443 không bị chặn (Hetzner Firewall) | — |

## 3. Sự cố bảo mật
- **Lộ khoá/bí mật:** (1) thu hồi khoá ở nhà cung cấp trước; (2) tạo khoá mới, cập nhật `.env`; (3) tạo lại container liên quan; (4) nếu nghi lộ `JWT_SECRET` → đổi, mọi người phải đăng nhập lại; (5) rà `git log`/log xem khoá đã đi đâu. Không dán log có chuỗi giống khoá cho người khác.
- **Nghi bị xâm nhập máy:** (1) Hetzner Firewall: chỉ cho IP của bạn vào cổng 22, chặn còn lại; (2) chụp Snapshot để giữ bằng chứng; (3) kiểm `last`, `ps`, `crontab -l`, `ss -tlnp`; (4) nếu xác nhận: **Rebuild** máy sạch + khôi phục DB từ sao lưu + đổi **toàn bộ** khoá/mật khẩu/khoá SSH; (5) thông báo người dùng nếu dữ liệu cá nhân có thể bị lộ.
- **Bị dồn request (DoS):** chặn IP ở Hetzner Firewall; nâng tạm gói (Rescale, tính theo giờ); xem `docker logs lms_caddy`. Hetzner có lọc tấn công mức mạng cơ bản; tấn công mức ứng dụng cần giới hạn tần suất.
- **Có người giả danh/spam chat:** đã chặn (gửi chat bắt buộc đăng nhập, danh tính lấy từ phiên). Nếu vẫn xảy ra: khoá tài khoản ở trang admin, xoá tin nhắn trong bảng `lesson_chats`.

## 4. Khôi phục dữ liệu từ sao lưu
Xem `docs/perf/SAO_LUU_VA_KHOI_PHUC.md` (sao lưu hằng đêm 02:30 giờ VN, giữ 7 bản; khôi phục thử mỗi chủ nhật; khoá mã hoá `/home/dev-thien/ops/.backup_key` phải được cất ở nơi khác). Sau khi khôi phục luôn **đối chiếu sổ cái giảng viên và thanh toán** trước khi mở lại.

## 5. Quay lại bản backend trước
```bash
docker tag lms-backend:prev lms-backend:prod && docker compose up -d --no-build --no-deps backend
```
Chỉ an toàn nếu bản mới chưa áp dụng migration làm đổi schema (xem `QUY_TRINH_PHAT_HANH_BACKEND.md`).

## 6. Thử sập có kiểm soát (làm một lần, lúc ít người dùng)
Script `/home/dev-thien/ops/drill.sh redis|mysql|backend` dừng một dịch vụ, ghi trạng thái web mỗi 5 giây, bật lại và đo thời gian phục hồi. Web **gián đoạn** trong lúc thử (backend ~70 s, MySQL/Redis ~20–30 s) nên chọn giờ vắng. Dán kết quả vào bảng ở mục 2 cột "Đã thử?".

## 7. Liên hệ
Hỗ trợ Hetzner (Console → Support), PayOS (kênh hỗ trợ đối tác), Backblaze B2, Google Cloud/Gemini, Azure. Ghi tên người nắm từng tài khoản và nơi cất khoá sao lưu ở đây: `____`.
