# Sao lưu và khôi phục MySQL

> Áp dụng từ 26/11/2026. Script nằm ở `/home/dev-thien/ops/` (bản sao để tham chiếu trong `be/ops/`), chạy bằng `cron` của user `dev-thien`.

## Lịch tự động
| Việc | Khi nào (giờ VN) | Script |
| --- | --- | --- |
| Sao lưu `lms_db`: dump → nén → mã hoá AES-256 → giữ 7 bản trên máy → chép ra B2 (giữ 14 ngày) | 02:30 mỗi đêm | `backup_mysql.sh` |
| **Khôi phục thử** vào MySQL tạm rồi đối chiếu số bảng/số dòng với DB thật | 03:30 chủ nhật | `restore_test.sh` |

- Bản sao lưu nằm ở `/home/dev-thien/backups/auto/lms_db_<ngày_giờ>.sql.gz.enc`; nhật ký ở `/home/dev-thien/ops/logs/backup.log`.
- Dump dùng `--single-transaction`: **không khoá bảng**, web vẫn chạy bình thường (mất vài giây, ~1,3 MB).
- Số liệu cho giám sát: `/home/dev-thien/ops/metrics/backup.prom` và `restore.prom` (Prometheus đọc qua node-exporter textfile).

## Khoá mã hoá — QUAN TRỌNG
Khoá nằm ở `/home/dev-thien/ops/.backup_key`. **Mất khoá là không giải mã được bản sao lưu.** Hãy chép khoá vào trình quản lý mật khẩu của bạn
(chạy `cat /home/dev-thien/ops/.backup_key` rồi lưu lại), không gửi qua chat hay commit lên Git.

## Kho ngoài máy (B2) — cần bucket RIÊNG TƯ
Bucket media `lms-lingualearn-media` là **công khai** nên **không** được dùng cho sao lưu (đã kiểm, đã xoá file thử). Cách làm:
1. Trên Backblaze B2 tạo bucket mới, đặt **Private** (ví dụ `lms-backups-<tên bạn>`).
2. Tạo **Application Key** giới hạn riêng cho bucket đó (quyền đọc/ghi/xoá file).
3. Tạo file `/home/dev-thien/ops/offsite.env` (quyền 600) với nội dung:
   ```
   B2_BACKUP_BUCKET=<tên bucket riêng tư>
   B2_BACKUP_ENDPOINT=s3.us-east-005.backblazeb2.com
   B2_BACKUP_KEY_ID=<keyID>
   B2_BACKUP_APP_KEY=<applicationKey>
   ```
4. Chạy thử: `/home/dev-thien/ops/backup_mysql.sh` rồi xem `tail /home/dev-thien/ops/logs/backup.log` có dòng "OK ngoài máy".
Trước khi có bước này, chỉ có bản trên máy; hãy bật thêm **Hetzner Backups** (sao lưu cả máy ở phía nhà cung cấp) để có bản ngoài máy.

## Khôi phục thật khi có sự cố
1. Dừng ghi: `cd /var/lms/be && docker compose stop backend ai-worker ai-beat ai-api`.
2. Giải mã + nạp vào MySQL thật (thay tên file):
   ```bash
   openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass file:/home/dev-thien/ops/.backup_key \
     -in /home/dev-thien/backups/auto/lms_db_XXXX.sql.gz.enc | gunzip \
     | docker exec -i lms_mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot lms_db'
   ```
   (Muốn an toàn hơn, đổi tên DB hiện tại hoặc dump DB hiện tại ra file riêng trước khi nạp.)
3. Bật lại: `docker compose up -d --no-build mysql redis backend caddy ai-api ai-worker ai-beat`.
4. Đối chiếu số liệu tiền (sổ cái giảng viên, thanh toán) trước khi mở lại cho người dùng.

Kiểm tra bản sao lưu bất kỳ mà không đụng DB thật: `/home/dev-thien/ops/restore_test.sh <đường-dẫn-file>`.
