#!/usr/bin/env bash
# 一条命令重新部署：本机编译 → 重建镜像 → 重启容器
#
# 用法：deploy/deploy.sh          （在项目根目录或任意位置执行都行）
set -euo pipefail

cd "$(dirname "$0")/.."

echo "== 1/3 本机编译（Java 17 + Maven）=="
mvn -B -q -DskipTests package

echo "== 2/3 构建运行镜像 =="
docker build -t inkwell-app:local -f deploy/Dockerfile.runtime .

echo "== 3/3 启动/更新容器 =="
docker compose up -d

docker compose ps
echo
echo "网关： http://127.0.0.1:${APP_PORT:-7000}"
echo "冒烟： python3 scripts/smoke_test.py"
