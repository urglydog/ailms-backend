// Chấm điểm cấu trúc một playlist (dùng chung cho yt-discover và yt-score).
import { yt, isoToSec, chunk } from './yt-lib.mjs';

const JUNK = /\b(top\s*\d+|best of|reaction|trailer|music|official video|lyrics|compilation|shorts?|live stream|podcast)\b/i;
const NUMBERED = /(#\s*\d+|\bpart\s*\d+|\blesson\s*\d+|\bep(isode)?\.?\s*\d+|\bchapter\s*\d+|^\s*\d+\s*[.)\-:–]|[-–]\s*\d+\s*[-–]|\b\d+\s*[/|]\s*\d+|\(\s*\d+\s*\))/i;
/** Số thứ tự của bài trong tiêu đề (Part 3, #3, Ep 3, "- 3 -", "3."), hoặc null. */
const seqNumber = (t) => {
  const m = /(?:#\s*|\bpart\s*|\blesson\s*|\bep(?:isode)?\.?\s*|\bchapter\s*|[-–]\s*)(\d{1,3})(?:\s*[-–,:]|\s*$)/i.exec(t) || /^\s*(\d{1,3})\s*[.)\-:–]/.exec(t);
  return m ? +m[1] : null;
};
const median = (a) => { const s = [...a].sort((x, y) => x - y); return s.length ? s[Math.floor(s.length / 2)] : 0; };

export async function inspect(pl) {
  const info = await yt('playlists', { part: 'snippet,contentDetails', id: pl.id });
  const meta = info.items?.[0];
  if (!meta) return null;
  const itemCount = meta.contentDetails.itemCount;
  // Chỉ đọc tối đa 100 bài đầu để chấm điểm (playlist dài hơn 60 bài bị đánh dấu quá dài).
  const items = [];
  let pageToken;
  for (let i = 0; i < 2; i++) {
    const page = await yt('playlistItems', { part: 'snippet,contentDetails,status', playlistId: pl.id, maxResults: 50, pageToken });
    items.push(...page.items);
    pageToken = page.nextPageToken;
    if (!pageToken) break;
  }
  const alive = items.filter((it) => it.status?.privacyStatus === 'public' && it.snippet.title !== 'Deleted video' && it.snippet.title !== 'Private video');
  const ids = alive.map((it) => it.contentDetails.videoId);
  const videos = [];
  for (const part of chunk(ids, 50)) {
    const r = await yt('videos', { part: 'snippet,contentDetails,status,statistics', id: part.join(',') });
    videos.push(...r.items);
  }
  const byId = new Map(videos.map((v) => [v.id, v]));
  const ordered = alive.map((it) => byId.get(it.contentDetails.videoId)).filter(Boolean);

  const flags = [];
  let score = 100;
  const penalise = (pts, why) => { score -= pts; flags.push(why); };

  const n = ordered.length;
  if (itemCount > 60) penalise(15, `quá dài (${itemCount} bài)`);
  if (n < 8) penalise(25, `quá ít bài (${n})`);
  const removed = items.length - alive.length;
  if (removed > 0) penalise(Math.min(20, removed * 4), `${removed} video riêng tư/đã xóa`);

  const channels = new Set(ordered.map((v) => v.snippet.channelId));
  if (channels.size > 1) penalise(30, `lẫn ${channels.size} kênh`);
  else if (n && meta.snippet.channelId !== [...channels][0]) penalise(10, 'playlist của kênh khác với kênh video');

  const embeddable = ordered.filter((v) => v.status.embeddable).length;
  if (n && embeddable / n < 1) penalise(Math.round(40 * (1 - embeddable / n)), `${n - embeddable} video không nhúng được`);

  const secs = ordered.map((v) => isoToSec(v.contentDetails.duration));
  const tooLong = secs.filter((s) => s > 10800).length;
  const tooShort = secs.filter((s) => s < 60).length;
  if (tooLong) penalise(15, `${tooLong} video dài hơn 180 phút`);
  if (tooShort) penalise(Math.min(15, tooShort * 3), `${tooShort} video dưới 1 phút`);
  const med = median(secs) / 60;
  if (n && (med < 3 || med > 45)) penalise(10, `thời lượng trung vị ${med.toFixed(0)} phút`);

  const titles = ordered.map((v) => v.snippet.title);
  const numbered = titles.filter((t) => NUMBERED.test(t)).length;
  if (n && numbered / n < 0.5) penalise(10, `chỉ ${Math.round((100 * numbered) / n)}% tiêu đề có số thứ tự`);
  // Thứ tự và trùng lặp trong series: bài sai thứ tự, trùng số, hoặc bài "ngoài series" lẫn ở cuối đều làm khóa lộn xộn.
  const nums = titles.map(seqNumber);
  const numbered2 = nums.filter((x) => x !== null);
  if (numbered2.length >= Math.max(5, n * 0.6)) {
    let outOfOrder = 0;
    for (let i = 1; i < nums.length; i++) if (nums[i] !== null && nums[i - 1] !== null && nums[i] < nums[i - 1]) outOfOrder++;
    if (outOfOrder) penalise(Math.min(20, outOfOrder * 8), `${outOfOrder} chỗ sai thứ tự số bài`);
    const dup = numbered2.length - new Set(numbered2).size;
    if (dup) penalise(Math.min(15, dup * 5), `${dup} số bài bị trùng`);
    const extras = n - numbered2.length;
    if (extras / n > 0.1) penalise(15, `${extras} bài không thuộc mạch đánh số (lẫn video ngoài series)`);
  }
  const junk = titles.filter((t) => JUNK.test(t)).length;
  if (junk) penalise(Math.min(20, junk * 5), `${junk} tiêu đề giống video rác/tổng hợp`);

  const ascii = titles.filter((t) => /^[\x20-\x7E]+$/.test(t)).length;
  if (n && ascii / n < 0.9) penalise(15, `tiêu đề không đồng nhất ngôn ngữ (${Math.round((100 * ascii) / n)}% tiếng Anh)`);
  const langs = ordered.map((v) => v.snippet.defaultAudioLanguage || v.snippet.defaultLanguage).filter(Boolean);
  const nonEn = langs.filter((l) => !/^en/i.test(l)).length;
  if (nonEn / Math.max(1, langs.length) > 0.2) penalise(15, `âm thanh khai báo không phải tiếng Anh (${nonEn}/${langs.length})`);

  return {
    id: pl.id, title: meta.snippet.title, channel: meta.snippet.channelTitle, channelId: meta.snippet.channelId,
    itemCount, usable: n, totalHours: +(secs.reduce((a, b) => a + b, 0) / 3600).toFixed(1), medianMin: +med.toFixed(1),
    score: Math.max(0, score), flags, firstTitles: titles.slice(0, 4),
  };
}
