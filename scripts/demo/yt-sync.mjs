// Đồng bộ dữ liệu thô của các playlist đã chọn (API chính thức): video hợp lệ theo thứ tự, mô tả, kênh, avatar.
// Chạy:  node yt-sync.mjs raw-out.json PLAYLIST_ID [PLAYLIST_ID ...]
import fs from 'node:fs';
import { yt, isoToSec, chunk, quota } from './yt-lib.mjs';

const [outFile, ...ids] = process.argv.slice(2);
if (!outFile || ids.length === 0) {
  console.log('Cách dùng: node yt-sync.mjs raw-out.json PLAYLIST_ID ...');
  process.exit(1);
}
const MAX_LESSONS = 60;
const out = {};

for (const id of ids) {
  const pl = (await yt('playlists', { part: 'snippet,contentDetails', id })).items?.[0];
  if (!pl) { console.log('Không thấy playlist', id); continue; }
  const items = [];
  let pageToken;
  do {
    const page = await yt('playlistItems', { part: 'snippet,contentDetails,status', playlistId: id, maxResults: 50, pageToken });
    items.push(...page.items);
    pageToken = items.length >= 120 ? undefined : page.nextPageToken;
  } while (pageToken);

  const alive = items.filter((it) => it.status?.privacyStatus === 'public' && !['Deleted video', 'Private video'].includes(it.snippet.title));
  const videos = [];
  for (const part of chunk(alive.map((it) => it.contentDetails.videoId), 50)) {
    videos.push(...(await yt('videos', { part: 'snippet,contentDetails,status', id: part.join(',') })).items);
  }
  const byId = new Map(videos.map((v) => [v.id, v]));
  const seen = new Set();
  const lessons = [];
  for (const it of alive) {
    const v = byId.get(it.contentDetails.videoId);
    if (!v || seen.has(v.id) || !v.status.embeddable) continue;
    const sec = isoToSec(v.contentDetails.duration);
    if (sec < 60 || sec > 10800) continue;
    seen.add(v.id);
    lessons.push({ id: v.id, title: v.snippet.title, sec, desc: v.snippet.description || '', lang: v.snippet.defaultAudioLanguage || v.snippet.defaultLanguage || null });
  }
  const ch = (await yt('channels', { part: 'snippet', id: pl.snippet.channelId })).items?.[0];
  out[id] = {
    playlist: id, title: pl.snippet.title, description: pl.snippet.description || '',
    channelId: pl.snippet.channelId, channel: pl.snippet.channelTitle,
    channelDesc: ch?.snippet?.description || '',
    avatar: ch?.snippet?.thumbnails?.high?.url || ch?.snippet?.thumbnails?.medium?.url || ch?.snippet?.thumbnails?.default?.url || null,
    total: pl.contentDetails.itemCount, lessons: lessons.slice(0, MAX_LESSONS),
  };
  console.log(`${id} | ${pl.snippet.title.slice(0, 50)} | ${pl.snippet.channelTitle} | ${out[id].lessons.length}/${pl.contentDetails.itemCount} bài`);
}
fs.writeFileSync(outFile, JSON.stringify(out, null, 1));
console.log(`Đã ghi ${outFile}. Hạn mức dùng ~${quota()} đơn vị.`);
