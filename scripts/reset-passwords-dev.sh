#!/usr/bin/env bash
# 开发用：把库里所有用户的密码统一成同一个口令（默认 123456）的 SHA-256 哈希。
#
#   scripts/reset-passwords-dev.sh            # 统一成 123456
#   scripts/reset-passwords-dev.sh mypass     # 统一成 mypass
#
# ⚠️ 只用于本地联调：执行后任何账号都能用这个口令登录（含 role_id=1 的管理员），
#    千万别在对外暴露的环境里跑，也不要放进 deploy/mysql/init/（那样每次建库都会自动执行）。
set -euo pipefail

cd "$(dirname "$0")/.."
PW="${1:-123456}"

set -a
# shellcheck disable=SC1091
. ./.env
set +a

HASH=$(printf '%s' "$PW" | sha256sum | cut -d' ' -f1)
echo "口令 '$PW' 的 SHA-256：$HASH"

docker exec inkwell-mysql mysql -uroot -p"$DB_PASSWORD" "$DB_NAME" -N -e \
  "UPDATE user SET password='$HASH';
   SELECT CONCAT('已统一 ', COUNT(*), ' 个账号') FROM user WHERE password='$HASH';" 2>/dev/null

echo "现在可以用任意用户名 + 口令 '$PW' 登录（例：admin / $PW）"
