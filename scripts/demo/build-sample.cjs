// Ghép dữ liệu thô từ yt-sync.mjs với định nghĩa trong defs_sample.cjs → courses-sample.json (cùng định dạng courses.json).
// Chạy:  node build-sample.cjs raw-sample.json
const fs = require('fs');
const path = require('path');
const defs = require('./defs_sample.cjs');
const raw = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));

const slugify = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/&/g, ' and ').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 120);

// Bỏ phần tên series/số thứ tự khỏi tiêu đề bài ("Learn Vue 3 - Ep 4, Your First Vue Component" → "Your First Vue Component").
function topic(title) {
  const parts = title.split('|').map((s) => s.trim()).filter(Boolean);
  let t = parts.find((p) => !/series|tutorial\s*$/i.test(p) || parts.length === 1) || parts[0];
  const stripped = t.replace(/^.*?(?:#\s*\d+|\bEp\.?\s*\d+|\bPart\s*\d+|[-–]\s*\d+\s*[-–])\s*[,:\-–]?\s*/i, '').trim();
  if (stripped.length >= 3) t = stripped;
  return t.replace(/\s*[-–]\s*(?:#\s*)?\d+\s*$/, '').replace(/\s+/g, ' ').trim();
}

const BAD = /(@|https?:|www\.|subscribe|discount|patreon|donate|follow|instagram|twitter|github|discord|sponsor|affiliate|#\w|©|coupon|enroll|join|premium|netninja\.dev|support|watch thousands|check out|download|link|course on|pro:)/i;
function clean(desc) {
  const lines = (desc || '').split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  const good = lines.filter((l) => !BAD.test(l) && !/^[\W_]+$/.test(l) && !/^\d{1,2}:\d{2}/.test(l) && l.length > 30 && !l.includes('|'));
  // Chỉ lấy đoạn mở đầu (1–2 dòng đầu): các dòng sau thường là quảng cáo khóa khác hoặc từ khóa SEO.
  const head = good.length && good[0].length < 80 ? good.slice(0, 2) : good.slice(0, 1);
  let t = head.join(' ').replace(/[​-‏⁠﻿]/g, '').replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/gu, '').replace(/\s+/g, ' ').trim();
  if (t.length < 40) return '';
  if (t.length > 300) {
    const cut = t.slice(0, 300);
    const i = Math.max(cut.lastIndexOf('. '), cut.lastIndexOf('! '), cut.lastIndexOf('? '));
    t = i > 80 ? cut.slice(0, i + 1) : cut.replace(/\s+\S*$/, '') + '…';
  }
  return t;
}

async function thumb(id) {
  for (const n of ['maxresdefault', 'sddefault', 'hqdefault']) {
    const u = `https://i.ytimg.com/vi/${id}/${n}.jpg`;
    try { if ((await fetch(u, { method: 'HEAD' })).ok) return u; } catch (e) { /* thử cỡ khác */ }
  }
  return `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;
}

(async () => {
  const courses = []; const instructors = {};
  for (const [key, d] of Object.entries(defs)) {
    const r = raw[d.playlist];
    if (!r) { console.log('Thiếu dữ liệu thô cho', key); continue; }
    const L = r.lessons;
    // Mô tả bài lặp lại ở nhiều bài (mô tả chung của series) thì không dùng — dùng câu mô tả theo chủ đề bài.
    const counts = {};
    L.forEach((l) => { const c = clean(l.desc); if (c) counts[c] = (counts[c] || 0) + 1; });
    const covered = new Array(L.length).fill(false);
    const chapters = d.chapters.map(([name, from, to]) => {
      for (let i = from; i <= to; i++) covered[i - 1] = true;
      return {
        title: name,
        lessons: L.slice(from - 1, to).map((l, j) => {
          const idx = from - 1 + j;
          const own = clean(l.desc);
          const t = topic(l.title);
          return {
            title: t.length > 240 ? t.slice(0, 237) + '…' : t,
            description: own && counts[own] <= 2 ? own : `In this lesson: ${t}. Part of the "${name}" section of ${d.title}.`,
            youtubeId: l.id, durationSec: l.sec, isPreview: idx < 2,
          };
        }),
      };
    });
    const missing = covered.map((c, i) => (c ? null : i + 1)).filter(Boolean);
    if (missing.length) console.log('CẢNH BÁO', key, 'bài chưa vào chương nào:', missing.join(','));
    const hours = (L.reduce((a, l) => a + l.sec, 0) / 3600).toFixed(1);
    const description = [
      d.tag, ...d.p,
      "## What you'll learn", d.learn.map((x) => '• ' + x).join('\n'),
      '## Requirements', d.req.map((x) => '• ' + x).join('\n'),
      '## Who this course is for', d.aud.map((x) => '• ' + x).join('\n'),
      '## Course details',
      `**Instructor:** ${d.inst[0]}\n**Video source:** YouTube playlist "${r.title}" by ${d.inst[0]}\n**Video language:** English, can be dubbed into many languages\n**Content:** ${L.length} lessons · about ${hours} hours`,
    ].join('\n\n');
    const email = d.inst[1];
    instructors[email] = { fullName: d.inst[0], email, headline: d.inst[2], bio: d.inst[3], avatarUrl: r.avatar };
    courses.push({
      key, lang: 'en', slug: slugify(d.title), title: d.title, description, thumbnailUrl: await thumb(L[0].id), level: d.lvl, price: d.price,
      category: d.cat, status: d.st, ageMonths: 0, ageDays: d.age, playlist: d.playlist, instructor: { fullName: d.inst[0], email }, chapters,
    });
    console.log(key.padEnd(10), L.length, 'bài,', chapters.length, 'chương');
  }
  const out = { categories: {}, instructors: Object.values(instructors), courses };
  const file = path.resolve(__dirname, '../../src/main/resources/demo/courses-sample.json');
  fs.writeFileSync(file, JSON.stringify(out));
  console.log('Đã ghi', file);
})();
