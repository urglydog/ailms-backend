// Chấm điểm cấu trúc các playlist đã biết ID (≈5 đơn vị/playlist, không tốn Search).
// Chạy:  node yt-score.mjs out.json PLAYLIST_ID [PLAYLIST_ID ...]
import fs from 'node:fs';
import { quota } from './yt-lib.mjs';
import { inspect } from './yt-inspect.mjs';

const [outFile, ...ids] = process.argv.slice(2);
if (!outFile || ids.length === 0) { console.log('Cách dùng: node yt-score.mjs out.json ID ...'); process.exit(1); }
const results = [];
for (const id of ids) {
  const r = await inspect({ id, title: id });
  if (r) { results.push(r); console.log(String(r.score).padStart(3), id, '|', r.title.slice(0, 45), '|', r.channel, '|', r.usable + '/' + r.itemCount, r.totalHours + 'h |', r.flags.join('; ') || 'ok'); }
  else console.log('  --', id, 'không còn');
}
fs.writeFileSync(outFile, JSON.stringify(results, null, 1));
console.log('Đã ghi', outFile, '~' + quota() + ' đơn vị');
