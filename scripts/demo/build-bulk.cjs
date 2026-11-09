// Ghép dữ liệu thô (yt-sync.mjs) + định nghĩa defs_bulk.cjs + 5 khóa mẫu → src/main/resources/demo/courses.json
// Chạy:  node build-bulk.cjs raw1.json [raw2.json ...]
const fs = require('fs');
const path = require('path');
const defs = require('./defs_bulk.cjs');

const RES = path.resolve(__dirname, '../../src/main/resources/demo');
const raw = Object.assign({}, ...process.argv.slice(2).map((f) => JSON.parse(fs.readFileSync(f, 'utf8'))));
const oldCatalog = JSON.parse(fs.readFileSync(path.join(__dirname, 'data/courses-old.json'), 'utf8'));
const sample = JSON.parse(fs.readFileSync(path.join(__dirname, 'data/courses-sample.json'), 'utf8'));

const NEW_CATEGORIES = {
  'giao-tiep-thuyet-trinh': 'Giao tiếp & Thuyết trình',
  'lanh-dao-quan-ly': 'Lãnh đạo & Quản lý',
  'phat-trien-ban-than': 'Phát triển bản thân',
};

const slugify = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/&/g, ' and ').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 120);

function topic(title) {
  const parts = title.split('|').map((s) => s.trim()).filter(Boolean);
  let t = parts.find((p) => !/series|tutorial\s*$/i.test(p) || parts.length === 1) || parts[0];
  const stripped = t.replace(/^.*?(?:#\s*\d+|\bEp\.?\s*\d+|\bPart\s*\d+|[-–]\s*\d+\s*[-–])\s*[,:\-–]?\s*/i, '').trim();
  if (stripped.length >= 3) t = stripped;
  return t.replace(/^[)\]:\s\-–]+/, '').replace(/\s*[-–]\s*(?:#\s*)?\d+\s*$/, '').replace(/\s+/g, ' ').trim();
}

const BAD = /(@|https?:|www\.|subscribe|discount|patreon|donate|follow|instagram|twitter|github|discord|sponsor|affiliate|#\w|©|coupon|enroll|join|premium|netninja\.dev|support|watch thousands|check out|download|link|course on|pro:)/i;
function clean(desc) {
  const lines = (desc || '').split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  const good = lines.filter((l) => !BAD.test(l) && !/^[\W_]+$/.test(l) && !/^\d{1,2}:\d{2}/.test(l) && l.length > 30 && !l.includes('|'));
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

const oldByPl = new Map(oldCatalog.courses.map((c) => [c.playlist, c]));
const oldInst = new Map(oldCatalog.instructors.map((i) => [i.email, i]));

(async () => {
  const courses = []; const instructors = {};
  for (const d of defs) {
    const r = raw[d.pl];
    if (!r) { console.log('THIẾU dữ liệu thô', d.key); continue; }
    const old = d.reuse ? oldByPl.get(d.pl) : null;
    if (d.reuse && !old) { console.log('THIẾU khóa cũ cho', d.key); continue; }
    const drop = new Set(d.drop || []);
    const L = r.lessons.filter((_, i) => !drop.has(i + 1));
    const strips = (d.strip || []).map((s) => new RegExp(s, 'i'));
    const titleOf = (l, idx) => {
      if (d.fix && d.fix[idx + 1]) return d.fix[idx + 1];
      let t = topic(l.title);
      for (const re of strips) t = t.replace(re, '').trim();
      t = t.replace(/^[)\]:\s\-–]+/, '').trim();
      if (t.length < 3) t = topic(l.title);
      return t.length > 240 ? t.slice(0, 237) + '…' : t;
    };
    const titles = L.map(titleOf);
    const title = old ? old.title : d.title;
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
          const t = titles[idx];
          return {
            title: t,
            description: own && counts[own] <= 2 ? own : `In this lesson: ${t}. Part of the "${name}" section of ${title}.`,
            youtubeId: l.id, durationSec: l.sec, isPreview: idx < 2,
          };
        }),
      };
    });
    const missing = covered.map((c, i) => (c ? null : i + 1)).filter(Boolean);
    if (missing.length) console.log('CẢNH BÁO', d.key, 'bài chưa vào chương nào:', missing.join(','));
    if (d.chapters[d.chapters.length - 1][2] > L.length) console.log('CẢNH BÁO', d.key, 'chương vượt số bài', L.length);
    const hours = (L.reduce((a, l) => a + l.sec, 0) / 3600).toFixed(1);

    const inst = old ? oldInst.get(old.instructor.email) : null;
    const instName = old ? inst.fullName : d.inst[0];
    const instEmail = old ? inst.email : d.inst[1];
    const details = `## Course details\n\n**Instructor:** ${instName}\n**Video source:** YouTube playlist "${r.title}" by ${r.channel}\n**Video language:** English, can be dubbed into many languages\n**Content:** ${L.length} lessons · about ${hours} hours`;
    let description;
    if (old) {
      description = old.description.split(/\n*## Course details/)[0].trim() + '\n\n' + details;
    } else {
      description = [
        d.tag, ...d.p,
        "## What you'll learn", d.learn.map((x) => '• ' + x).join('\n'),
        '## Requirements', d.req.map((x) => '• ' + x).join('\n'),
        '## Who this course is for', d.aud.map((x) => '• ' + x).join('\n'),
        details,
      ].join('\n\n');
    }
    instructors[instEmail] = old
      ? { fullName: inst.fullName, email: inst.email, headline: inst.headline, bio: inst.bio, avatarUrl: r.avatar || inst.avatarUrl }
      : { fullName: d.inst[0], email: d.inst[1], headline: d.inst[2], bio: d.inst[3], avatarUrl: r.avatar };
    courses.push({
      key: d.key, lang: 'en', slug: old ? old.slug : slugify(title), title, description,
      thumbnailUrl: await thumb(L[0].id), level: old ? old.level : d.lvl, price: old ? old.price : d.price,
      category: d.cat || old.category, status: old ? old.status : 'PUBLISHED', ageMonths: 0, ageDays: d.age, playlist: d.pl,
      instructor: { fullName: instName, email: instEmail }, chapters,
    });
    console.log(d.key.padEnd(12), String(L.length).padStart(3), 'bài,', chapters.length, 'chương', old ? '(giữ khóa cũ)' : '');
  }
  // 5 khóa mẫu đã duyệt
  for (const i of sample.instructors) instructors[i.email] = i;
  const all = [...sample.courses, ...courses];
  const slugs = new Set(); for (const c of all) { if (slugs.has(c.slug)) console.log('TRÙNG slug', c.slug); slugs.add(c.slug); }
  const out = { categories: { ...oldCatalog.categories, ...NEW_CATEGORIES }, instructors: Object.values(instructors), courses: all };
  fs.writeFileSync(path.join(RES, 'courses.json'), JSON.stringify(out));
  console.log('Đã ghi', all.length, 'khóa,', out.instructors.length, 'giảng viên');
})();
