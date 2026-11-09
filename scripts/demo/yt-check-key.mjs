// Gọi thử 1 đơn vị hạn mức để biết key còn dùng được không.
import { yt } from './yt-lib.mjs';

try {
  const r = await yt('videos', { part: 'id,snippet', id: 'dQw4w9WgXcQ' });
  console.log('KEY OK — đọc được video:', r.items?.[0]?.snippet?.title ?? '(không có kết quả)');
} catch (e) {
  console.log('KEY LỖI:', e.message);
  process.exit(1);
}
