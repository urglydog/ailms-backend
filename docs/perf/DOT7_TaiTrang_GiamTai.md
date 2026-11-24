# Đợt 7: Tải trang nhanh hơn (fe) và giảm tải backend (nhóm B, C)

> Nhánh `perf` ở cả `project/fe` và `project/be`. Tiếp theo [đợt 6](DOT6_OnDinh_NhieuNguoiDung.md).

## Tổng quan

| Mục | Repo | Vấn đề trước đây | Cách sửa | Trạng thái |
| --- | --- | --- | --- | --- |
| B6. Tải lười thư viện nặng | fe | Cả dự án không dùng `next/dynamic`; mermaid (~1 MB), bộ sửa mindmap (@xyflow + dagre + html-to-image), recharts trong chi tiết hóa đơn tải ngay khi mở trang | `import()` động / `next/dynamic` | Đã làm |
| B7. Render trang công khai ở server | fe | 84/97 trang là `'use client'` | — | Chưa làm, xem bên dưới |
| B8. `<img>` sang `next/image` | fe | 15 thẻ `<img>` | — | Rà xong, không đổi (lý do bên dưới) |
| C9. Cache banner thông báo | be | Mọi người dùng gọi `/system-announcements/banner` 5 phút/lần, mỗi lần 1 câu SQL | Redis cache 30 s, xóa cache khi admin sửa | Đã làm |
| C10a. Lỗi nhắc ôn flashcard gửi sai topic | be | Ghi ở mục 8 của tổng kết | — | Đã được sửa sẵn trên develop (gửi qua `notificationService.notify(userId…)`) |
| C10b. Prometheus | be | Actuator chỉ mở `health`, `info` | — | Chờ quyết định cách deploy |

## B6. Tải lười thư viện nặng

**Nguyên lý:** Next.js đã tự tách code theo **trang**, nhưng mọi thứ một trang `import` tĩnh vẫn nằm trong
bundle đầu của trang đó, kể cả khi người dùng không bao giờ mở phần dùng tới nó. `import()` động tách thư
viện thành chunk riêng, chỉ tải khi cần; chunk được trình duyệt cache nên lần sau không tải lại.

| File | Trước | Sau |
| --- | --- | --- |
| `components/materials/MermaidViewer.tsx` | `import mermaid from 'mermaid'` ở đầu file | `const mermaid = (await import('mermaid')).default` ngay trước khi vẽ. Component vốn đã có trạng thái `loading`, nên không đổi giao diện |
| `components/instructor/CourseMaterialsManager.tsx` | `import { MindmapEditor }` tĩnh | `dynamic(() => import(...).then(m => m.MindmapEditor), { ssr: false })` — trình sửa chỉ hiện khi giảng viên bấm sửa mindmap |
| `app/admin/transactions/invoices/page.tsx`, `components/instructor/earnings/StatementPanel.tsx` | `import { InvoiceDetailBody }` tĩnh (kéo theo recharts) | `dynamic(...)` — chỉ tải khi mở chi tiết hóa đơn |

Các trang thống kê dùng recharts ngay khi mở (`admin/ai-analytics`, `instructor/revenue/*`…) giữ nguyên, vì
biểu đồ là nội dung chính của trang, tải lười chỉ làm biểu đồ hiện chậm hơn.

`face-api` (trang thi) và `livekit` (trang live) cũng giữ nguyên: chỉ nằm trong đúng trang cần nó và được
dùng ngay khi vào trang.

## B7. Render ở server — chưa làm

Chuyển trang chủ, kho khóa học và chi tiết khóa học sang Server Component giúp trang hiện nhanh hơn và tốt
cho SEO, nhưng phải viết lại cách lấy dữ liệu của các trang này. Nhánh `feat/UI` đang thiết kế lại chính các
trang học viên, nên làm bây giờ chắc chắn xung đột. Nên làm **sau khi `feat/UI` vào develop**.

## B8. `<img>` sang `next/image` — rà xong, không đổi

15 thẻ còn lại đều thuộc các loại không lợi khi đổi:
- Avatar 16–64 px (header, menu tài khoản, topbar, hồ sơ): ảnh nhỏ. Avatar Google nằm ở host không có
  trong `images.remotePatterns`, đổi sang `next/image` sẽ làm ảnh lỗi.
- Ảnh xem trước dạng `blob:` (ảnh bìa vừa chọn, tệp đính kèm khiếu nại/hỗ trợ): `next/image` không tối ưu được.
- `CertificatePreview`: được chụp lại thành ảnh bằng html-to-image, cần thẻ `<img>` thường.

Ảnh nặng thật sự (bìa khóa học trên thẻ khóa, trang chi tiết) đã dùng `next/image` từ trước.

## C9. Cache banner thông báo hệ thống

**Trước đây:** `GET /api/v1/system-announcements/banner` được mọi trang gọi khi mở và gọi lại mỗi 5 phút
(`useSystemAnnouncements`). 1.000 người đang mở web ≈ 200 câu SQL/phút chỉ để biết "có banner không".

**Sau khi sửa** (`SystemAnnouncementService`):
- `getActiveBanner()` có `@Cacheable("systemBanner")`, TTL **30 s** (`CacheConfig`). Kết quả "không có banner"
  (null) cũng được cache, vì đây là trường hợp phổ biến nhất.
- `create`, `update`, `revoke`, `restore`, `deleteRevoked` có `@CacheEvict(allEntries = true)`, nên banner
  mới hiện ngay khi admin tạo.
- TTL ngắn thay vì dài để banner tự hiện hoặc ẩn đúng giờ bắt đầu/kết thúc mà không cần job xóa cache.

Kết quả: mỗi backend tối đa ~2 câu SQL/phút cho banner, không phụ thuộc số người dùng.

## C10b. Prometheus — chờ quyết định

Mở `/actuator/prometheus` cần chọn cách bảo vệ phù hợp với cách deploy (cổng quản trị riêng không public,
hay giới hạn IP nội bộ). Nếu đặt sau reverse proxy, giới hạn IP có thể bị vượt qua, nên chưa bật vội.

## Kiểm chứng

- fe: `tsc --noEmit` không lỗi; `vitest run` **634/634** test qua; eslint các file sửa không lỗi.
- be: `mvn test` toàn bộ test qua.
- Kiểm tay (sau khi chạy thử): mở trang tài liệu có sơ đồ, tab Network thấy chunk mermaid chỉ tải khi sơ đồ
  hiện; gọi API banner 2 lần liên tiếp, lần 2 không có câu SQL (log Hibernate ở profile dev).

## Kết quả chạy thử (24/11/2026, máy local)

**fe — `next build` production, cột First Load JS (JavaScript phải tải khi mở trang lần đầu):**

| Trang | Trước | Sau | Giảm |
| --- | --- | --- | --- |
| `/instructor/courses/[id]/edit/materials` (quản lý tài liệu) | 462 kB | **233 kB** | −50% |
| `/materials/[id]` (học viên xem tài liệu) | 387 kB | **239 kB** | −38% |
| `/admin/transactions/invoices` | 292 kB | **179 kB** | −39% |
| `/instructor/earnings` | 259 kB | **146 kB** | −44% |

Phần bị tách ra chỉ tải khi người dùng thật sự mở sơ đồ, mindmap hoặc chi tiết hóa đơn.

**be — cache banner:** xóa cache rồi gọi API banner 5 lần liên tiếp → chỉ **1** câu SQL; key
`systemBanner::active` có TTL 30 s trên Redis; thời gian phản hồi 9–26 ms.

## Thử trên trình duyệt (24/11/2026, tài khoản giảng viên, dữ liệu thử đã xóa sau khi đo)

| Chỗ | Cách thử | Kết quả |
| --- | --- | --- |
| Sơ đồ mermaid (`/materials/[id]`) | Tạo 1 mindmap thử, mở trang | Trang tải xong mới tải chunk mermaid (~1,1 MB) lúc 2,2 s; sơ đồ vẽ đúng tiếng Việt |
| Trình sửa mindmap (quản lý tài liệu) | Mở mindmap, bấm "Chỉnh sửa" | Chunk mermaid tải khi mở; chunk trình sửa 751 KB chỉ tải khi bấm "Chỉnh sửa"; React Flow vẽ đủ 5 nút |
| Chi tiết bảng kê (`/instructor/earnings`) | Tạo 1 bảng kê tháng thử | Chunk `InvoiceDetailBody` (1,3 MB, có recharts) chỉ tải khi có bảng kê để hiện; giảng viên chưa có bảng kê thì không tải; biểu đồ vẽ đủ 2 cột |

Trang hóa đơn admin dùng cùng component và cùng cách tải, chưa thử bằng tài khoản admin.
