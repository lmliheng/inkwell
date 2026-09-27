#!/usr/bin/env bash
# 本地起一个 Java 服务做开发/对照（不进 Docker），连隔离库、脱离当前进程组。
#
#   scripts/dev_service.sh start auth [db_name] [port]   # 默认 fastweb_m1a / 8090
#   scripts/dev_service.sh stop  auth
#   scripts/dev_service.sh status auth
#
# 为什么用 setsid：否则这条命令一结束，进程组被回收，服务就跟着没了。
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
MOD="${2:-auth}"
DB="${3:-fastweb_m1a}"
case "$MOD" in
    auth) PORT="${4:-8090}" ;;
    content) PORT="${4:-8091}" ;;
    social) PORT="${4:-8092}" ;;
    system) PORT="${4:-8093}" ;;
    *) echo "未知模块：$MOD（auth/content/social/system）"; exit 1 ;;
esac

# pid/日志按端口区分：多个开发实例（不同库/不同端口）可以同时跑，互不干扰
PIDFILE="/tmp/jscreator-$MOD-$PORT.pid"
LOGFILE="/tmp/jscreator-$MOD-$PORT.log"

set -a
# shellcheck disable=SC1091
. ./.env
set +a

JAR="jscreator-$MOD/target/jscreator-$MOD-1.0.0.jar"

case "${1:-start}" in
    start)
        [ -f "$JAR" ] || { echo "先编译：mvn -B -q -DskipTests -pl jscreator-$MOD -am package"; exit 1; }
        if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
            echo "$MOD 已在运行（pid $(cat "$PIDFILE")）"; exit 0
        fi
        DB_HOST=127.0.0.1 DB_PORT="${MYSQL_PORT:-3308}" DB_NAME="$DB" \
            setsid nohup java -jar "$JAR" --server.port="$PORT" > "$LOGFILE" 2>&1 < /dev/null &
        echo $! > "$PIDFILE"
        for _ in $(seq 1 60); do
            sleep 1
            if grep -q "Started .*Application" "$LOGFILE" 2>/dev/null; then
                echo "$MOD 已启动：http://127.0.0.1:$PORT（库 $DB，pid $(cat "$PIDFILE")，日志 $LOGFILE）"
                exit 0
            fi
        done
        echo "启动失败，看 $LOGFILE"; tail -20 "$LOGFILE"; exit 1 ;;
    stop)
        if [ -f "$PIDFILE" ]; then kill "$(cat "$PIDFILE")" 2>/dev/null || true; rm -f "$PIDFILE"; fi
        echo "$MOD(:$PORT) 已停" ;;
    status)
        if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
            echo "$MOD running pid $(cat "$PIDFILE") port $PORT db $DB"
        else
            echo "$MOD not running"
        fi ;;
    *) echo "用法：$0 {start|stop|status} {auth|content|social|system} [db_name] [port]"; exit 1 ;;
esac
