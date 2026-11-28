# Cảnh báo Grafana → Telegram (27/11/2026)

## Đã làm và đã kiểm chứng
- Kênh Telegram `telegram-lms` (bot `@LinguaLearnServer_bot`) + chính sách: mọi cảnh báo gửi vào Telegram của chủ dự án, gom 30 giây, **nhắc lại mỗi 4 giờ** nếu chưa hết, có tin "đã hết" khi phục hồi.
- 7 luật (file `monitoring/grafana/provisioning/alerting/rules.yml`, Prometheus làm nguồn, kiểm tra mỗi 1 phút):

| Luật | Điều kiện | Mức | Khi không có số liệu |
| --- | --- | --- | --- |
| Backend ngừng phản hồi | `up{job="backend"}` < 1 trong 1 phút | critical | **báo** |
| Tỷ lệ lỗi 5xx cao | > 5% yêu cầu lỗi 5xx trong 5 phút | critical | bỏ qua |
| API chậm | p95 độ trễ > 2 giây trong 10 phút (không tính AI, chat, xuất file, WebSocket) | warning | bỏ qua |
| RAM máy chủ thấp | RAM còn dùng được < 300 MB trong 5 phút | warning | bỏ qua |
| Swap dùng nhiều | swap > 3 GB trong 10 phút | warning | bỏ qua |
| Ổ đĩa sắp đầy | đĩa gốc trống < 2 GB trong 5 phút | critical | bỏ qua |
| Sao lưu MySQL quá hạn | không có sao lưu thành công trong 26 giờ | critical | **báo** (kể cả khi node-exporter ngừng) |

- Số liệu sao lưu (`lms_backup_*`, `lms_restore_test_*`) do script sao lưu ghi vào `/home/dev-thien/ops/metrics/*.prom`, node-exporter đọc qua textfile collector.
- **Kiểm chứng đầu-cuối 27/11:** tạo một luật tạm luôn kích hoạt → bản tin "Firing" đến Telegram của chủ dự án trong ~30 giây → luật tạm bị xoá, không còn sót (log: `drills/grafana_alert_e2e_20261127.log`).

## Vì sao kênh Telegram tạo bằng API, không bằng file
Thử khai báo trong file provisioning thì Grafana tự đổi Chat ID dạng số thành **kiểu số** khi thay biến môi trường, mà Telegram cần chuỗi
(`json: cannot unmarshal number into ... chatid of type string`), làm Grafana dừng khởi động. Vì vậy chỉ giữ **luật** trong file, còn kênh + chính sách tạo bằng `ops/setup_grafana_alerts.py`
(đọc `.env`, chạy lại bao nhiêu lần cũng được, không in token).

## Vận hành
```bash
# Nếu Grafana bị tạo lại từ đầu (mất volume be_grafana_data) hoặc đổi token/chat id: tạo lại kênh + chính sách
cd /var/lms/be && python3 -I ops/setup_grafana_alerts.py

# Đổi token bot: sửa TELEGRAM_BOT_TOKEN trong .env, rồi
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d --no-deps grafana
python3 -I ops/setup_grafana_alerts.py

# Tắt tiếng một cảnh báo tạm thời: Grafana → Alerting → Silences (qua đường hầm SSH, xem BAT_GIAM_SAT_20261127.md)
```
Không bao giờ ghi token/chat id vào file trong Git; chỉ ở `be/.env` (quyền 600).

## Giới hạn
- Chưa có số liệu và cảnh báo riêng cho **MySQL, Redis, AI worker** (chỉ backend + máy chủ). MySQL/Redis sập vẫn được phát hiện gián tiếp qua "Tỷ lệ lỗi 5xx cao" hoặc "Backend ngừng phản hồi".
- Cảnh báo đi qua chính máy chủ này: nếu cả máy mất mạng hoặc sập hẳn thì **không có ai báo** (Prometheus/Grafana cùng chết). Muốn khắc phục cần giám sát từ bên ngoài (vd dịch vụ ping công khai) — chưa làm.
- Ngưỡng là ước lượng ban đầu; chỉnh trong `rules.yml` sau khi có số đo tải thật (k6).
- Grafana đang dùng ~90–135 MB trên trần 192 MB.
