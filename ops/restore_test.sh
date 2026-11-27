#!/usr/bin/env bash
# Khôi phục THỬ bản sao lưu mới nhất vào một MySQL tạm (không đụng DB thật), rồi đối chiếu số bảng / số dòng.
# Dùng: restore_test.sh [đường-dẫn-file.sql.gz.enc]   (mặc định: bản mới nhất trong backups/auto)
set -euo pipefail
umask 077

OPS=/home/dev-thien/ops
OUT=/home/dev-thien/backups/auto
KEY=$OPS/.backup_key
LOG=$OPS/logs/restore_test.log
PROM=$OPS/metrics/restore.prom
CT=lms_restore_test
file="${1:-$(ls -1t "$OUT"/lms_db_*.sql.gz.enc 2>/dev/null | head -1)}"

log() { echo "$(date -u +%FT%TZ) $*" | tee -a "$LOG"; }
prom() { # $1 = 1 đạt / 0 không đạt
  cat > "$PROM.tmp" <<EOF
# HELP lms_restore_test_last_ok Lần khôi phục thử gần nhất có đạt không (1/0)
# TYPE lms_restore_test_last_ok gauge
lms_restore_test_last_ok $1
# HELP lms_restore_test_last_run_timestamp_seconds Lần khôi phục thử gần nhất
# TYPE lms_restore_test_last_run_timestamp_seconds gauge
lms_restore_test_last_run_timestamp_seconds $(date +%s)
EOF
  mv "$PROM.tmp" "$PROM"
  chmod 644 "$PROM"   # node-exporter (user nobody) cần đọc được; chỉ chứa dấu thời gian/số liệu, không nhạy cảm
}
cleanup() { docker rm -fv "$CT" >/dev/null 2>&1 || true; }
trap cleanup EXIT
bad() { log "KHÔNG ĐẠT: $*"; prom 0; exit 1; }

[ -n "$file" ] && [ -s "$file" ] || bad "không tìm thấy bản sao lưu"
log "khôi phục thử từ: $(basename "$file")"

cleanup
RPW=$(openssl rand -hex 16)
docker run -d --name "$CT" -m 512m --memory-swap 1g --oom-score-adj 900 --network none \
  -e MYSQL_ROOT_PASSWORD="$RPW" -e MYSQL_DATABASE=lms_db mysql:8.0.46 \
  --innodb-buffer-pool-size=64M --performance-schema=OFF --skip-log-bin >/dev/null

# Image mysql khởi tạo bằng một máy chủ tạm rồi khởi động lại: phải chờ lần "ready for connections" thứ 2 (máy chủ thật).
for i in $(seq 1 90); do
  [ "$(docker logs "$CT" 2>&1 | grep -c 'mysqld: ready for connections')" -ge 2 ] && break
  sleep 2
done
docker exec "$CT" mysql -uroot -p"$RPW" -N -e 'SELECT 1' >/dev/null 2>&1 || bad "MySQL tạm không khởi động được"

openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass file:"$KEY" -in "$file" | gunzip \
  | docker exec -i "$CT" mysql -uroot -p"$RPW" lms_db 2> >(grep -v 'Using a password' >> "$LOG") || bad "nạp dữ liệu vào MySQL tạm thất bại (xem $LOG)"

q_tmp()  { docker exec "$CT" mysql -uroot -p"$RPW" -N -e "$1" lms_db 2>/dev/null; }
q_live() { docker exec lms_mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -e "$0" lms_db 2>/dev/null' "$1"; }

t_tmp=$(q_tmp "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='lms_db' AND table_type='BASE TABLE'")
t_live=$(q_live "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='lms_db' AND table_type='BASE TABLE'")
v_tmp=$(q_tmp "SELECT MAX(installed_rank) FROM flyway_schema_history")
v_live=$(q_live "SELECT MAX(installed_rank) FROM flyway_schema_history")
log "số bảng: khôi phục=$t_tmp / DB thật=$t_live; flyway rank: $v_tmp / $v_live"

ok=1
[ "$t_tmp" = "$t_live" ] || ok=0
for t in users courses lessons enrollments payments lesson_progress instructor_ledger_entries flyway_schema_history; do
  exists=$(q_live "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='lms_db' AND table_name='$t'")
  [ "$exists" = "1" ] || continue
  a=$(q_tmp "SELECT COUNT(*) FROM \`$t\`"); b=$(q_live "SELECT COUNT(*) FROM \`$t\`")
  diff=$(( b > a ? b - a : a - b )); tol=$(( b / 50 )); [ "$tol" -ge 10 ] || tol=10
  mark="ok"; [ "$diff" -le "$tol" ] || { mark="LỆCH"; ok=0; }
  [ "$b" -eq 0 ] || [ "$a" -gt 0 ] || { mark="TRỐNG"; ok=0; }
  log "  $t: khôi phục=$a / DB thật=$b [$mark]"
done

if [ "$ok" = "1" ]; then log "ĐẠT: khôi phục thử khớp DB thật"; prom 1; else bad "số liệu không khớp"; fi
