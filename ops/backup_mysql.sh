#!/usr/bin/env bash
# Sao lưu MySQL (lms_db) hằng đêm: dump -> nén -> mã hoá AES-256 -> giữ 7 bản trên máy -> chép sang B2 (giữ 14 ngày).
# Chạy bằng cron (xem docs/perf/SAO_LUU_VA_KHOI_PHUC.md). KHÔNG in khoá/mật khẩu ra log.
set -euo pipefail
umask 077

OPS=/home/dev-thien/ops
OUT=/home/dev-thien/backups/auto
KEY=$OPS/.backup_key
ENVF=/var/lms/be/.env
LOG=$OPS/logs/backup.log
PROM=$OPS/metrics/backup.prom
LAST_OK=$OPS/metrics/.backup_last_success
KEEP_LOCAL=7
KEEP_REMOTE_DAYS=14
RCLONE_IMAGE=rclone/rclone:1.68

ts=$(date +%Y%m%d_%H%M)
name="lms_db_${ts}.sql.gz.enc"
file="$OUT/$name"
start=$(date +%s)
offsite=0
size=0

log() { echo "$(date -u +%FT%TZ) $*" >> "$LOG"; }

write_prom() {  # $1 = đã sao lưu cục bộ thành công (1/0)
  local last_ok=0
  [ -f "$LAST_OK" ] && last_ok=$(cat "$LAST_OK")
  cat > "$PROM.tmp" <<EOF
# HELP lms_backup_last_run_timestamp_seconds Lần chạy sao lưu gần nhất
# TYPE lms_backup_last_run_timestamp_seconds gauge
lms_backup_last_run_timestamp_seconds $(date +%s)
# HELP lms_backup_last_success_timestamp_seconds Lần sao lưu cục bộ thành công gần nhất
# TYPE lms_backup_last_success_timestamp_seconds gauge
lms_backup_last_success_timestamp_seconds $last_ok
# HELP lms_backup_last_size_bytes Dung lượng bản sao lưu gần nhất
# TYPE lms_backup_last_size_bytes gauge
lms_backup_last_size_bytes $size
# HELP lms_backup_offsite_success Đã chép ra kho ngoài máy (B2) trong lần chạy gần nhất (1/0)
# TYPE lms_backup_offsite_success gauge
lms_backup_offsite_success $offsite
# HELP lms_backup_last_ok Lần chạy gần nhất có thành công (1/0)
# TYPE lms_backup_last_ok gauge
lms_backup_last_ok $1
EOF
  mv "$PROM.tmp" "$PROM"
}

fail() { log "LỖI: $*"; rm -f "$file.tmp"; write_prom 0; exit 1; }

[ -s "$KEY" ] || fail "thiếu khoá mã hoá $KEY"
docker inspect -f '{{.State.Running}}' lms_mysql 2>/dev/null | grep -q true || fail "container lms_mysql không chạy"

# 1) dump (single-transaction: không khoá bảng) -> nén -> mã hoá
if ! docker exec lms_mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --no-tablespaces lms_db' 2>> "$OPS/logs/backup.err" \
   | gzip -6 \
   | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass file:"$KEY" -out "$file.tmp"; then
  fail "mysqldump/nén/mã hoá thất bại"
fi

# 2) kiểm tra: giải mã + giải nén được, có dòng 'Dump completed', dung lượng hợp lý
size=$(stat -c %s "$file.tmp")
[ "$size" -gt 100000 ] || fail "bản sao lưu quá nhỏ ($size byte)"
if ! openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass file:"$KEY" -in "$file.tmp" | gunzip | tail -c 600 | grep -aq 'Dump completed'; then
  fail "bản sao lưu không hoàn chỉnh (thiếu 'Dump completed')"
fi
mv "$file.tmp" "$file"
date +%s > "$LAST_OK"
log "OK cục bộ: $name ($size byte)"

# 3) giữ KEEP_LOCAL bản mới nhất trên máy
ls -1t "$OUT"/lms_db_*.sql.gz.enc 2>/dev/null | tail -n +$((KEEP_LOCAL + 1)) | xargs -r rm -f

# 4) chép ra kho B2 RIÊNG TƯ dành cho sao lưu (khoá riêng trong $OPS/offsite.env, không dùng khoá của ứng dụng).
#    Tuyệt đối không dùng bucket media (công khai). Tạo offsite.env theo docs/perf/SAO_LUU_VA_KHOI_PHUC.md.
OFFSITE_ENV=$OPS/offsite.env
if [ -s "$OFFSITE_ENV" ]; then
  of() { grep -E "^$1=" "$OFFSITE_ENV" | head -1 | cut -d= -f2-; }
  BUCKET=$(of B2_BACKUP_BUCKET)
  export RCLONE_CONFIG_B2S3_TYPE=s3
  export RCLONE_CONFIG_B2S3_PROVIDER=Other
  export RCLONE_CONFIG_B2S3_ENDPOINT="https://$(of B2_BACKUP_ENDPOINT | sed -E 's#^https?://##')"
  export RCLONE_CONFIG_B2S3_ACCESS_KEY_ID="$(of B2_BACKUP_KEY_ID)"
  export RCLONE_CONFIG_B2S3_SECRET_ACCESS_KEY="$(of B2_BACKUP_APP_KEY)"
  RC=(docker run --rm -m 256m -e RCLONE_CONFIG_B2S3_TYPE -e RCLONE_CONFIG_B2S3_PROVIDER -e RCLONE_CONFIG_B2S3_ENDPOINT
      -e RCLONE_CONFIG_B2S3_ACCESS_KEY_ID -e RCLONE_CONFIG_B2S3_SECRET_ACCESS_KEY -v "$OUT":/data:ro "$RCLONE_IMAGE")
  if [ -n "$BUCKET" ] && "${RC[@]}" copyto "/data/$name" "B2S3:$BUCKET/db/$name" --s3-no-check-bucket >> "$LOG" 2>&1; then
    offsite=1
    log "OK ngoài máy: B2 $BUCKET/db/$name"
    "${RC[@]}" delete "B2S3:$BUCKET/db" --min-age "${KEEP_REMOTE_DAYS}d" --s3-no-check-bucket >> "$LOG" 2>&1 \
      || log "CẢNH BÁO: không xoá được bản cũ trên B2 (không ảnh hưởng bản mới)"
  else
    log "CẢNH BÁO: chép ra B2 thất bại (bản cục bộ vẫn an toàn)"
  fi
  unset RCLONE_CONFIG_B2S3_ACCESS_KEY_ID RCLONE_CONFIG_B2S3_SECRET_ACCESS_KEY
else
  log "CẢNH BÁO: chưa có $OFFSITE_ENV (bucket sao lưu riêng tư) — chưa chép ngoài máy"
fi

write_prom 1
log "xong sau $(( $(date +%s) - start )) giây"
