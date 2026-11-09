// Tìm playlist ứng viên theo từ khóa (Search API) rồi chấm điểm theo CẤU TRÚC khóa học, không theo độ nổi tiếng.
// Chạy:  node yt-discover.mjs out.json "vue.js tutorial for beginners" "angular full course" ...
// Hạn mức: mỗi từ khóa 100 đơn vị (search.list) + ~5 đơn vị cho mỗi playlist ứng viên.
import fs from 'node:fs';
import { yt, quota } from './yt-lib.mjs';
import { inspect } from './yt-inspect.mjs';

const [outFile, ...queries] = process.argv.slice(2);
if (!outFile || queries.length === 0) {
  console.log('Cách dùng: node yt-discover.mjs out.json "từ khóa 1" "từ khóa 2" ...');
  process.exit(1);
}


const results = [];
const seen = new Set();
for (const q of queries) {
  const s = await yt('search', { part: 'snippet', type: 'playlist', q, maxResults: 8, relevanceLanguage: 'en' }, 100);
  const found = s.items.map((i) => ({ id: i.id.playlistId, title: i.snippet.title })).filter((p) => p.id && !seen.has(p.id));
  console.log(`## ${q} → ${found.length} playlist mới`);
  for (const pl of found) {
    seen.add(pl.id);
    try {
      const r = await inspect(pl);
      if (r) results.push({ query: q, ...r });
    } catch (e) {
      console.log('  bỏ qua', pl.id, e.message.slice(0, 120));
      if (e.status === 403 || e.status === 400) throw e; // key/hạn mức lỗi → dừng, báo người dùng
    }
  }
}
results.sort((a, b) => b.score - a.score);
fs.writeFileSync(outFile, JSON.stringify(results, null, 1));
console.log(`\nĐã ghi ${results.length} ứng viên vào ${outFile}. Hạn mức đã dùng ~${quota()} đơn vị.\n`);
for (const r of results) {
  console.log(`${String(r.score).padStart(3)} | ${r.id} | ${r.title.slice(0, 50)} | ${r.channel} | ${r.usable}/${r.itemCount} bài | ${r.totalHours}h | ${r.flags.join('; ') || 'không cảnh báo'}`);
}
