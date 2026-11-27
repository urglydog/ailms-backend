#!/usr/bin/env bash
# Theo dõi một lần "crash thật" của backend (người vận hành chạy `sudo kill -9 <PID java>` từ máy chủ):
# - chờ tới khi backend bị dừng/đổi tiến trình, rồi ghi trạng thái + trả lời của web mỗi 3 giây;
# - nếu container ở trạng thái dừng quá GRACE giây mà Docker không tự chạy lại → tự `docker start` (đã được chủ dự án cho phép).
set -u
C=lms_backend
B=https://46.224.175.90.nip.io
LOG=/home/dev-thien/ops/logs/drill_20261127_backend_crash_that.log
GRACE=60          # giây chờ Docker tự chạy lại trước khi bật tay
WAIT_KILL=1200    # chờ tối đa 20 phút để người vận hành chạy lệnh kill
HEALTHY_MAX=240   # sau khi chạy lại, chờ tối đa 240 giây để healthy
log() { echo "$(date -u +%T) UTC  $*" | tee -a "$LOG"; }
probe() { curl -s -o /dev/null -m 6 -w '%{http_code}/%{time_total}s' "$B/actuator/health"; }
snap() { printf 'state=%s health=%s restarts=%s pid=%s web=%s' \
  "$(docker inspect -f '{{.State.Status}}' $C 2>/dev/null)" "$(docker inspect -f '{{.State.Health.Status}}' $C 2>/dev/null)" \
  "$(docker inspect -f '{{.RestartCount}}' $C 2>/dev/null)" "$(docker inspect -f '{{.State.Pid}}' $C 2>/dev/null)" "$(probe)"; }

: > "$LOG"
pid0=$(docker inspect -f '{{.State.Pid}}' $C); st0=$(docker inspect -f '{{.State.StartedAt}}' $C); rc0=$(docker inspect -f '{{.RestartCount}}' $C)
log "NỀN: $(snap)  | RAM: $(free -m | awk 'NR==2{print "dùng "$3" còn "$7" MB"}')  swap: $(free -m | awk 'NR==3{print $3" MB"}')"
log "Đang chờ bạn chạy: sudo kill -9 \$(docker inspect -f '{{.State.Pid}}' $C)   (PID hiện tại $pid0)"

t_wait=0
while :; do
  cur=$(docker inspect -f '{{.State.Pid}}|{{.State.Status}}|{{.State.StartedAt}}' $C 2>/dev/null)
  [ "$cur" != "$pid0|running|$st0" ] && break
  sleep 1; t_wait=$((t_wait+1))
  [ $t_wait -ge $WAIT_KILL ] && { log "HẾT $WAIT_KILL s chờ, không thấy lệnh kill — dừng theo dõi (không làm gì)."; exit 0; }
done
t0=$(date +%s)
log "PHÁT HIỆN backend bị dừng/đổi tiến trình (sau ${t_wait}s chờ)."

manual=0; run_at=""; healthy_at=""
while :; do
  el=$(( $(date +%s) - t0 ))
  state=$(docker inspect -f '{{.State.Status}}' $C 2>/dev/null)
  health=$(docker inspect -f '{{.State.Health.Status}}' $C 2>/dev/null)
  log "[+${el}s] $(snap)"
  [ "$state" = "running" ] && [ -z "$run_at" ] && { run_at=$el; log "  → container ĐÃ CHẠY LẠI sau ${el}s ($([ $manual = 1 ] && echo 'do tôi bật tay' || echo 'Docker tự chạy lại'))"; }
  [ "$health" = "healthy" ] && { healthy_at=$el; break; }
  if [ "$state" != "running" ] && [ $el -ge $GRACE ] && [ $manual = 0 ]; then
    manual=1; log "  → ${el}s mà Docker KHÔNG tự chạy lại → BẬT TAY: docker start $C"; docker start $C >/dev/null 2>&1
  fi
  [ $el -ge $((GRACE + HEALTHY_MAX)) ] && { log "QUÁ THỜI GIAN chờ healthy — dừng theo dõi, cần kiểm tra tay."; break; }
  sleep 3
done

rc1=$(docker inspect -f '{{.RestartCount}}' $C)
log "===== TÓM TẮT ====="
log "Docker tự chạy lại: $([ $manual = 0 ] && [ -n "$run_at" ] && echo 'CÓ' || echo 'KHÔNG (phải bật tay)')"
log "Thời gian tới khi container chạy: ${run_at:-chưa}s | tới khi healthy: ${healthy_at:-chưa}s | RestartCount $rc0 → $rc1"
log "SAU: $(snap) | RAM $(free -m | awk 'NR==2{print "dùng "$3" còn "$7" MB"}') | OOMKilled=$(docker inspect -f '{{.State.OOMKilled}}' $C) | exit=$(docker inspect -f '{{.State.ExitCode}}' $C)"
log "Container khác: $(docker ps --format '{{.Names}}:{{.Status}}' | grep -v "$C" | grep -vc ':Up') không Up"
