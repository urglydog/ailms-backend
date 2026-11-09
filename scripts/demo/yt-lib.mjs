// Tiện ích dùng chung cho các script đồng bộ YouTube (chạy trên máy dev, Node 18+).
// Key đọc từ biến môi trường YOUTUBE_API_KEY hoặc từ be/.env — KHÔNG in key ra và không commit.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

export function loadKey() {
  if (process.env.YOUTUBE_API_KEY) return process.env.YOUTUBE_API_KEY;
  const envFile = path.resolve(here, '../../.env');
  if (fs.existsSync(envFile)) {
    for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
      const m = line.match(/^YOUTUBE_API_KEY=(.*)$/);
      if (m && m[1].trim()) return m[1].trim().replace(/^["']|["']$/g, '');
    }
  }
  throw new Error('Thiếu YOUTUBE_API_KEY (đặt trong be/.env hoặc biến môi trường)');
}

let quotaUsed = 0;
export const quota = () => quotaUsed;

/** Gọi YouTube Data API v3; dừng ngay với thông báo rõ khi key lỗi/hết hạn mức (không retry mù). */
export async function yt(endpoint, params, cost = 1) {
  const key = loadKey();
  const url = new URL('https://www.googleapis.com/youtube/v3/' + endpoint);
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null) url.searchParams.set(k, v);
  url.searchParams.set('key', key);
  const res = await fetch(url);
  const body = await res.json().catch(() => ({}));
  quotaUsed += cost;
  if (!res.ok) {
    const reason = body?.error?.errors?.[0]?.reason || res.status;
    const msg = body?.error?.message || '';
    const err = new Error(`YouTube API lỗi ${res.status} (${reason}): ${msg.replace(key, '***')}`);
    err.status = res.status; err.reason = reason;
    throw err;
  }
  return body;
}

/** Chuyển ISO 8601 (PT1H2M3S) sang giây. */
export function isoToSec(iso) {
  const m = /^P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/.exec(iso || '');
  if (!m) return 0;
  return (+m[1] || 0) * 86400 + (+m[2] || 0) * 3600 + (+m[3] || 0) * 60 + (+m[4] || 0);
}

export const chunk = (arr, n) => Array.from({ length: Math.ceil(arr.length / n) }, (_, i) => arr.slice(i * n, i * n + n));
