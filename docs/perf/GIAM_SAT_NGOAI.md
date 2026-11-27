# Giám sát từ bên ngoài — UptimeRobot (27/11/2026)

## Vì sao cần
Grafana + Prometheus chạy **cùng máy chủ** với hệ thống, nên khi cả máy sập, mất mạng, Caddy/HTTPS hỏng hoặc tên miền không vào được thì chúng cũng chết và **không ai báo**. UptimeRobot gọi vào web từ Internet (trạm ở châu Âu, mỗi 5 phút) nên bắt được các sự cố đó.

## Cấu hình (gói Free, tài khoản của chủ dự án)
| Monitor | Địa chỉ | Loại | Chu kỳ |
| --- | --- | --- | --- |
| LinguaLearn API health | `https://46.224.175.90.nip.io/actuator/health` | HTTP(s) | 5 phút |
| LinguaLearn Web (Vercel) | `https://ailms-web-client.vercel.app` | HTTP(s) | 5 phút |
| LinguaLearn API courses | `https://46.224.175.90.nip.io/api/v1/courses?size=1` | HTTP(s) | 5 phút |

- Địa chỉ health do Caddy cho phép (chỉ `/actuator/health`; mọi `/actuator/*` khác bị chặn 404); trả 200 `{"status":"UP"}` khi backend + DB + Redis đều khoẻ, 503 khi một trong số đó lỗi.
- **Kênh nhận cảnh báo: email** (đã xác nhận bằng thư "Monitor is UP" ngày 27/11) và **thông báo đẩy trên ứng dụng UptimeRobot** (chủ dự án đã cài và cho phép thông báo).
- **Telegram không dùng được ở gói miễn phí của UptimeRobot** (Slack, Telegram, Webhook yêu cầu nâng cấp); Telegram do Grafana lo (xem `CANH_BAO_TELEGRAM.md`).
- Không có bí mật nào trong Git hay trên server cho phần này; dịch vụ chỉ gọi vào các địa chỉ công khai, không có dữ liệu người dùng gửi ra ngoài.

## Kiểm tra từ phía server (27/11, sau khi cấu hình)
Cả 3 địa chỉ trả 200 từ Internet (health 0,8 s; courses 0,8 s; web Vercel 2,5 s). Không bị chặn tần suất khi gọi liên tiếp.

## Hai hệ thống bổ sung nhau
| Sự cố | Grafana → Telegram (trên máy chủ) | UptimeRobot → email/ứng dụng (bên ngoài) |
| --- | --- | --- |
| Backend, DB, Redis lỗi | có (5xx, backend ngừng) | có (health 503) |
| RAM, swap, đĩa, sao lưu quá hạn | **có** | không |
| **Máy chủ sập / mất mạng** | **không báo được** | **có** |
| Caddy/HTTPS hỏng, tên miền không vào được | không thấy | **có** |
| Web Vercel (FE) lỗi | không | **có** |

## Thử báo động (chủ dự án thực hiện)
Sửa tạm địa chỉ monitor health thành `.../kiem-tra-bao-dong` → nhận thư **Down** (~5–10 phút) → sửa lại đúng địa chỉ → nhận thư **Up**. **Kết quả thử: chưa được chủ dự án xác nhận trong tài liệu này** (đã tạo monitor và nhận thư Up đầu tiên; cần ghi lại ngày giờ nhận Down/Up sau khi thử).

## Giới hạn
- Phát hiện chậm tối đa ~5 phút (gói Free), không báo tức thì.
- Không có Telegram từ nguồn bên ngoài; nếu muốn, có thể thêm Healthchecks.io (có Telegram miễn phí) làm heartbeat từ server.
- Một monitor kiểm tra `/api/v1/courses?size=1` có thể vẫn trả 200 khi MySQL tắt (được cache Redis); phát hiện lỗi DB dựa vào monitor health.
