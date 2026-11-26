#!/usr/bin/env bash
# Ghi RAM máy chủ và RAM từng container mỗi 10 giây vào file CSV, cho tới khi bị dừng (Ctrl+C / kill).
# Dùng trong lúc chạy loadtest/k6-public.js — xem docs/perf/HUONG_DAN_SERVER_DOT8.md.
#   bash loadtest/ghi-ram.sh ket-qua-ram.csv
set -u
OUT="${1:-ket-qua-ram.csv}"
echo "thoi_gian,ram_available_mb,swap_used_mb,container,mem_used,mem_pct,cpu_pct" > "$OUT"
while true; do
  t=$(date +%H:%M:%S)
  avail=$(awk '/MemAvailable/ {printf "%d", $2/1024}' /proc/meminfo)
  swap=$(awk '/SwapTotal/ {t=$2} /SwapFree/ {f=$2} END {printf "%d", (t-f)/1024}' /proc/meminfo)
  docker stats --no-stream --format '{{.Name}},{{.MemUsage}},{{.MemPerc}},{{.CPUPerc}}' \
    | sed 's| / [^,]*||' \
    | while IFS= read -r line; do echo "$t,$avail,$swap,$line"; done >> "$OUT"
  sleep 10
done
