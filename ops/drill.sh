#!/usr/bin/env bash
# Thử sập có kiểm soát: dừng 1 dịch vụ, đo web phản ứng ra sao mỗi 5 giây, rồi bật lại và đo thời gian phục hồi.
# Dùng: ./drill.sh redis | mysql | backend     (CHẠY LÚC ÍT NGƯỜI DÙNG; web sẽ gián đoạn trong lúc thử)
# Kết quả để ghi vào docs/perf/KICH_BAN_SU_CO.md.
set -u
B=https://46.224.175.90.nip.io
what=${1:-}
probe() { printf "%s:%s " "$1" "$(curl -s -o /dev/null -m 4 -w '%{http_code}' "$B$2")"; }
sample() { printf "[%3ss] " "$1"; probe courses "/api/v1/courses?size=1"; probe categories /api/v1/categories; probe health /actuator/health; echo; }
wait_healthy() { local c=$1 t0=$(date +%s); for i in $(seq 1 120); do [ "$(docker inspect "$c" --format '{{.State.Health.Status}}' 2>/dev/null)" = healthy ] && { echo $(( $(date +%s) - t0 )); return; }; sleep 2; done; echo "quá 240"; }
[ -n "$what" ] || { echo "Dùng: $0 redis|mysql|backend"; exit 1; }
echo "=== TRƯỚC KHI THỬ"; sample 0
t0=$(date +%s)
case $what in
  redis)
    echo "=== dừng lms_redis ~25 giây"; docker stop -t 5 lms_redis >/dev/null
    for i in 1 2 3 4 5; do sleep 5; sample $(( $(date +%s) - t0 )); done
    echo "=== bật lại Redis"; docker start lms_redis >/dev/null; echo "Redis healthy sau $(wait_healthy lms_redis)s" ;;
  mysql)
    echo "=== dừng lms_mysql ~20 giây"; docker stop -t 30 lms_mysql >/dev/null
    for i in 1 2 3 4; do sleep 5; sample $(( $(date +%s) - t0 )); done
    echo "=== bật lại MySQL"; docker start lms_mysql >/dev/null; echo "MySQL healthy sau $(wait_healthy lms_mysql)s" ;;
  backend)
    echo "=== docker kill lms_backend (mô phỏng sập đột ngột)"; docker kill -s KILL lms_backend >/dev/null
    for i in $(seq 1 16); do sleep 5; sample $(( $(date +%s) - t0 )); [ "$(docker inspect lms_backend --format '{{.State.Health.Status}}' 2>/dev/null)" = healthy ] && break; done
    echo "backend: health=$(docker inspect lms_backend --format '{{.State.Health.Status}}') sau $(( $(date +%s) - t0 ))s, restarts=$(docker inspect lms_backend --format '{{.RestartCount}}')" ;;
  *) echo "không hiểu: $what"; exit 1 ;;
esac
echo "=== SAU PHỤC HỒI (chờ 20 giây cho các kết nối nối lại)"; sleep 20; sample $(( $(date +%s) - t0 ))
