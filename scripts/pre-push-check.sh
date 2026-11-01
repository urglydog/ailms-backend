#!/usr/bin/env bash
# Kiểm tra bảo mật trước khi push: secret hardcode, file nhạy cảm bị track,
# .env.example chứa giá trị thật thay vì placeholder.
#
# Chạy tự động qua git hook .git/hooks/pre-push (symlink/copy tới file này).
# Chỉ quét các COMMIT SẮP PUSH (chưa có trên remote), không quét toàn bộ lịch sử
# repo mỗi lần — giữ hook nhanh.
#
# Bỏ qua có chủ ý: SKIP_PUSH_CHECK=1 git push
#
# Input chuẩn của git pre-push hook (đọc từ stdin), mỗi dòng:
#   <local ref> <local sha1> <remote ref> <remote sha1>

set -u

if [ "${SKIP_PUSH_CHECK:-0}" = "1" ]; then
  echo "⚠️  [pre-push-check] BỎ QUA kiểm tra bảo mật (SKIP_PUSH_CHECK=1). Tự chịu trách nhiệm." >&2
  exit 0
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT" || exit 1

FAIL=0
ZERO_SHA="0000000000000000000000000000000000000000"

# ── Thu thập các commit sắp push (hợp nhất từ mọi ref trong stdin) ──────
RANGES=()
while read -r local_ref local_sha remote_ref remote_sha; do
  [ -z "${local_sha:-}" ] && continue
  # Xoá nhánh (local_sha toàn 0) -> không có gì để quét
  if [ "$local_sha" = "$ZERO_SHA" ]; then
    continue
  fi
  if [ "$remote_sha" = "$ZERO_SHA" ] || [ -z "${remote_sha:-}" ]; then
    # Nhánh mới trên remote: so với merge-base gần nhất đang có trên BẤT KỲ remote-tracking
    # branch nào hiện có (vd origin/develop) để không phải quét lại toàn bộ lịch sử dùng chung.
    # Nếu repo hoàn toàn chưa có remote-tracking branch nào -> dùng empty tree (quét hết nhánh mới).
    existing_remote_tips="$(git for-each-ref --format='%(objectname)' refs/remotes/ 2>/dev/null)"
    base=""
    if [ -n "$existing_remote_tips" ]; then
      # shellcheck disable=SC2086
      base="$(git merge-base "$local_sha" $existing_remote_tips 2>/dev/null | head -1)"
    fi
    if [ -z "$base" ]; then
      base="4b825dc642cb6eb9a060e54bf8d69288fbee4904"  # empty tree hash (hằng số của git)
    fi
    RANGES+=("${base}..${local_sha}")
  else
    RANGES+=("${remote_sha}..${local_sha}")
  fi
done

if [ "${#RANGES[@]}" -eq 0 ]; then
  echo "✅ [pre-push-check] Không có commit mới nào để kiểm tra." >&2
  exit 0
fi

COMMIT_LIST="$(git rev-list --no-merges "${RANGES[@]}" 2>/dev/null | sort -u)"

if [ -z "$COMMIT_LIST" ]; then
  echo "✅ [pre-push-check] Không có commit mới nào để kiểm tra." >&2
  exit 0
fi

echo "🔎 [pre-push-check] Đang quét $(echo "$COMMIT_LIST" | wc -l | tr -d ' ') commit sắp push..." >&2

# ── 1. gitleaks nếu có cài, ưu tiên dùng vì chính xác hơn regex tay ─────
if command -v gitleaks >/dev/null 2>&1; then
  for range in "${RANGES[@]}"; do
    if ! gitleaks detect --source "$REPO_ROOT" --log-opts="$range" --redact --no-banner 2>&1; then
      echo "❌ [pre-push-check] gitleaks phát hiện khả năng rò rỉ secret trong range: $range" >&2
      FAIL=1
    fi
  done
else
  echo "ℹ️  [pre-push-check] Không tìm thấy gitleaks trên PATH — dùng regex dự phòng (kém chính xác hơn)." >&2
fi

# ── 2. Quét pattern secret bằng regex trên diff từng commit (fallback, luôn chạy thêm) ──
# Các pattern theo đúng yêu cầu: Google OAuth, Google API key, OpenAI-style, GitHub token,
# AWS access key, private key PEM, JWT dài, chuỗi base64 dài gán cho biến KEY/SECRET/TOKEN/PASSWORD.
SECRET_PATTERN='GOCSPX-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{20,}|sk-[A-Za-z0-9]{20,}|gh[ps]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|-----BEGIN[ A-Z]*PRIVATE KEY-----|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
VAR_SECRET_PATTERN='(KEY|SECRET|TOKEN|PASSWORD)[A-Za-z0-9_]*[[:space:]]*[=:][[:space:]]*["'"'"']?[A-Za-z0-9+/_=-]{40,}'

for range in "${RANGES[@]}"; do
  DIFF_HITS="$(git log -p --no-merges "$range" -- . 2>/dev/null \
    | grep -E "^\+" \
    | grep -vE '^\+\+\+' \
    | grep -E "$SECRET_PATTERN|$VAR_SECRET_PATTERN" \
    || true)"
  if [ -n "$DIFF_HITS" ]; then
    echo "❌ [pre-push-check] Nghi ngờ secret hardcode trong diff sắp push (range: $range):" >&2
    echo "$DIFF_HITS" | sed -E 's/(GOCSPX-|AIza|sk-|ghp_|gho_|github_pat_|AKIA|eyJ)[A-Za-z0-9_+/=.-]{4}[A-Za-z0-9_+/=.-]*/\1****(ẩn)/g' >&2
    FAIL=1
  fi
done

# ── 3. File nhạy cảm bị track trong các commit sắp push ─────────────────
SENSITIVE_FILE_PATTERN='(^|/)\.env$|\.pem$|\.key$|id_rsa|id_ed25519|credentials|\.gpg$'
for range in "${RANGES[@]}"; do
  ADDED_FILES="$(git diff --name-only --diff-filter=A "$range" 2>/dev/null | grep -E "$SENSITIVE_FILE_PATTERN" || true)"
  if [ -n "$ADDED_FILES" ]; then
    echo "❌ [pre-push-check] File nhạy cảm bị thêm vào git trong commit sắp push:" >&2
    echo "$ADDED_FILES" | sed 's/^/    - /' >&2
    FAIL=1
  fi
done

# ── 4. .env.example: nếu nằm trong diff, kiểm tra không có giá trị thật ──
for range in "${RANGES[@]}"; do
  ENV_EXAMPLE_CHANGED="$(git diff --name-only "$range" 2>/dev/null | grep -E '(^|/)\.env\.example$' || true)"
  if [ -n "$ENV_EXAMPLE_CHANGED" ]; then
    while IFS= read -r f; do
      [ -z "$f" ] && continue
      # Chỉ xét dòng mà TÊN BIẾN gợi ý là bí mật (KEY/SECRET/TOKEN/PASSWORD/CREDENTIAL...)
      # và giá trị là 1 chuỗi liền (không khoảng trắng/# trước đó) >=20 ký tự —
      # tránh báo nhầm các biến cấu hình bình thường (tên model, số quota, timeout...).
      BAD_LINES="$(git show "${range#*..}:$f" 2>/dev/null \
        | grep -E '^[A-Za-z0-9_]*(KEY|SECRET|TOKEN|PASSWORD|PASSWD|CREDENTIAL)[A-Za-z0-9_]*[[:space:]]*=[[:space:]]*[^[:space:]#]{20,}' \
        | grep -viE 'changeme|your-|-here|example\.com|placeholder|xxxx|TODO|localhost|^#' \
        || true)"
      if [ -n "$BAD_LINES" ]; then
        echo "❌ [pre-push-check] $f có dòng trông như giá trị thật (không phải placeholder):" >&2
        echo "$BAD_LINES" | sed -E 's/=(.{4}).*/=\1...(ẩn)/' | sed 's/^/    /' >&2
        FAIL=1
      fi
    done <<< "$ENV_EXAMPLE_CHANGED"
  fi
done

if [ "$FAIL" -ne 0 ]; then
  echo "" >&2
  echo "🚫 [pre-push-check] PUSH BỊ CHẶN — phát hiện vấn đề bảo mật ở trên." >&2
  echo "    Nếu chắc chắn đây là false positive, có thể bỏ qua MỘT LẦN bằng:" >&2
  echo "    SKIP_PUSH_CHECK=1 git push" >&2
  echo "    (không khuyến khích — ưu tiên sửa/rotate secret trước khi push)" >&2
  exit 1
fi

echo "✅ [pre-push-check] Không phát hiện vấn đề bảo mật." >&2
exit 0
