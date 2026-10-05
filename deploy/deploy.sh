#!/usr/bin/env bash
# 一条命令重新部署：本机编译 → 重建镜像 → 重启容器
#
# 用法：deploy/deploy.sh          （在项目根目录或任意位置执行都行）
set -euo pipefail

cd "$(dirname "$0")/.."

echo "== 1/3 本机编译（Java 17 + Maven）=="
mvn -B -q -DskipTests package

echo "== 2/3 构建后端运行镜像 =="
docker build -t inkwell-app:local -f deploy/Dockerfile.runtime .

echo "== 3/3 构建前端镜像并启动/更新容器（--build 让 apps/ 的改动也一起生效）=="
docker compose up -d --build

docker compose ps
echo
echo "网关：   http://127.0.0.1:${APP_PORT:-7000}"
echo "后台：   http://127.0.0.1:${WEB_PORT:-80}      （admin / 123456）"
echo "博客：   http://127.0.0.1:${BLOG_PORT:-8080}"
echo "冒烟：   python3 scripts/smoke_test.py"
