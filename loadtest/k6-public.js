// Đo tải backend LinguaLearn bằng k6 — chỉ gọi các API ĐỌC công khai (không ghi DB, không cần đăng nhập).
//
// Chạy TRONG mạng Docker, thẳng vào backend:8080 (không qua Caddy/Internet), xem
// docs/perf/HUONG_DAN_SERVER_DOT8.md. Mỗi người dùng ảo (VU) gửi X-Forwarded-For là một IP riêng:
// RateLimitFilter lấy phần tử CUỐI của header này làm IP client, nên mỗi VU được tính như một người
// dùng thật (giới hạn 120 lượt tìm kiếm + 600 request/phút/IP) thay vì cả bài đo chung 1 IP và bị 429.
//
// Tham số (biến môi trường):
//   BASE_URL   mặc định http://backend:8080
//   PEAK_VUS   số người dùng ảo lúc đỉnh, mặc định 50
//   HOLD       thời gian giữ ở đỉnh, mặc định 3m
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://backend:8080';
const PEAK = Number(__ENV.PEAK_VUS || 50);
const HOLD = __ENV.HOLD || '3m';

export const options = {
  stages: [
    { duration: '1m', target: PEAK },   // tăng dần
    { duration: HOLD, target: PEAK },   // giữ ở đỉnh
    { duration: '30s', target: 0 },     // giảm
  ],
  thresholds: {
    // Mục tiêu chấp nhận được cho server 2 vCPU / 3,7 GB.
    http_req_failed: ['rate<0.01'],          // < 1% lỗi (không tính 404 có chủ đích)
    'http_req_duration{kind:list}': ['p(95)<800'],
    'http_req_duration{kind:detail}': ['p(95)<800'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const res = http.get(`${BASE}/api/v1/courses?size=24&page=0`, { headers: { 'X-Forwarded-For': '10.250.0.1' } });
  let ids = [];
  try {
    const body = res.json();
    const content = (body.data && body.data.content) || body.content || [];
    ids = content.map((c) => c.slug).filter(Boolean); // API chi tiết công khai: GET /api/v1/courses/{slug}
  } catch (e) { /* để trống: chỉ đo danh sách */ }
  return { ids };
}

export default function (data) {
  const ip = `10.${200 + (__VU >> 16) % 50}.${(__VU >> 8) % 256}.${__VU % 256}`;
  const params = (kind) => ({ headers: { 'X-Forwarded-For': ip }, tags: { kind } });

  const page = Math.floor(Math.random() * 3);
  const list = http.get(`${BASE}/api/v1/courses?size=24&page=${page}`, params('list'));
  check(list, { 'danh sách 200': (r) => r.status === 200 });

  http.get(`${BASE}/api/v1/categories`, params('list'));
  http.get(`${BASE}/api/v1/system-announcements/banner`, params('list'));

  if (data.ids.length) {
    const slug = data.ids[Math.floor(Math.random() * data.ids.length)];
    const detail = http.get(`${BASE}/api/v1/courses/${encodeURIComponent(slug)}`, params('detail'));
    check(detail, { 'chi tiết 200': (r) => r.status === 200 });
  }

  // Tìm kiếm có từ khóa: không dùng được cache, là đường nặng nhất (đợt 2).
  const kw = ['english', 'unity', 'giao tiếp', 'ngữ pháp', 'java'][Math.floor(Math.random() * 5)];
  const search = http.get(`${BASE}/api/v1/courses?keyword=${encodeURIComponent(kw)}&size=24`, params('list'));
  check(search, { 'tìm kiếm 200': (r) => r.status === 200 });

  sleep(1 + Math.random() * 2); // người thật đọc trang 1–3 giây rồi mới bấm tiếp
}
