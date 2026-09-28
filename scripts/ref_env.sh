#!/usr/bin/env bash
# 「原版 Node 后端」对照运行环境 —— 用来逐接口比对 Java 移植版的响应体（M1 的验收方式）。
#
# 原版跑在独立端口 + 独立库（库名从 dump 导入并统一口令成 123456），
# 这样它和 Java 版两边起点一致、写入互不干扰，对照才有意义。
#
#   scripts/ref_env.sh db     [db_name]        # 建/重置一个对照库（默认 fastweb_m1ref）
#   scripts/ref_env.sh start  [db_name] [port] # 编译并启动原版（默认 fastweb_m1ref / 7001）
#   scripts/ref_env.sh stop   [port]           # 停掉原版（默认 7001）
#   scripts/ref_env.sh status [port]
#   scripts/ref_env.sh clean  [db_name]        # 清掉对照/冒烟测试写进某库的残留（默认 fastweb_test）
#
# 依赖：/root/JScreator 的仓库根目录（package.json + Backend/src），npm 已装依赖。
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
REPO="${REPO:-/root/JScreator}"

set -a
# shellcheck disable=SC1091
. ./.env
set +a

mysql_exec() { docker exec -i inkwell-mysql mysql -uroot -p"$DB_PASSWORD" "$@" 2>/dev/null; }

make_db() {
    local db="${1:-fastweb_m1ref}"
    echo "==> 建库 $db（从 deploy/mysql/init/01-schema.sql 导入 + 口令统一成 123456）"
    mysql_exec -e "CREATE DATABASE IF NOT EXISTS \`$db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    # 库是新建的，直接导入即可；重复执行时先清表再由 dump 重建
    mysql_exec -e "DROP DATABASE \`$db\`; CREATE DATABASE \`$db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    mysql_exec "$db" < deploy/mysql/init/01-schema.sql
    local hash
    hash=$(printf '%s' 123456 | sha256sum | cut -d' ' -f1)
    mysql_exec "$db" -e "UPDATE user SET password='$hash';"
    mysql_exec -N -e "SELECT CONCAT('  库 $db：', COUNT(*), ' 张表 / ', (SELECT COUNT(*) FROM \`$db\`.user), ' 个账号') FROM information_schema.tables WHERE table_schema='$db';"
}

start_ref() {
    local db="${1:-fastweb_m1ref}" port="${2:-7001}"
    # pid/日志按端口区分：可以同时跑多份对照（例如对着线上库的那一份）
    local PIDFILE="/tmp/jscreator-ref-$port.pid"
    local LOGFILE="/tmp/jscreator-ref-$port.log"
    if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
        echo "原版已在运行（pid $(cat "$PIDFILE")）"; exit 0
    fi
    [ -d "$REPO/node_modules" ] || (cd "$REPO" && npm install --no-audit --no-fund)
    (cd "$REPO" && npm run build:ts)
    cd "$REPO"
    # setsid：脱离当前会话的进程组，免得启动它的那条命令结束时被一起收走
    # 命令行环境变量优先于仓库根 .env（dotenv 不覆盖已有变量）
    PORT="$port" DB_HOST=127.0.0.1 DB_PORT="${MYSQL_PORT:-3308}" DB_NAME="$db" \
        DB_USER="$DB_USER" DB_PASSWORD="$DB_PASSWORD" JWT_SECRET="$JWT_SECRET" \
        setsid nohup node Backend/dist/server.js > "$LOGFILE" 2>&1 < /dev/null &
    echo $! > "$PIDFILE"
    cd "$ROOT"
    for _ in $(seq 1 20); do
        sleep 0.5
        if curl -sf -o /dev/null "http://127.0.0.1:$port/role/list"; then
            echo "==> 原版已启动：http://127.0.0.1:$port（库 $db，pid $(cat "$PIDFILE")，日志 $LOGFILE）"
            return 0
        fi
    done
    echo "启动失败，看 $LOGFILE"; tail -20 "$LOGFILE"; exit 1
}

case "${1:-status}" in
    db)     make_db "${2:-fastweb_m1ref}" ;;
    start)  start_ref "${2:-fastweb_m1ref}" "${3:-7001}" ;;
    clean)
        # 对照 harness（ref_diff）与 smoke_test 会往目标库写数据且不带善后：
        # 角色/用户/API Key/OAuth 客户端/文章都会留下带标记的行。跑完对照后清一次，
        # 库就回到 dump 的状态（这几张表在 dump 里是空的；role/user 只删标记前缀的行）。
        db="${2:-fastweb_test}"
        echo "==> 清理 $db 里的测试残留"
        mysql_exec "$db" -e "
            DELETE FROM role WHERE role_name LIKE 'm1-%';
            DELETE FROM user WHERE username LIKE 'm1b-%' OR username LIKE 'java_m0_%';
            DELETE FROM api_key;
            DELETE FROM oauth_client;
            DELETE FROM oauth_code;
            DELETE FROM oauth_consent;
            DELETE FROM article WHERE title LIKE 'm1c-%';
            DELETE FROM roleandpermission_middle WHERE role_id NOT IN (SELECT role_id FROM role);
        "
        mysql_exec -N "$db" -e "
            SELECT CONCAT('  清完后：', (SELECT COUNT(*) FROM role), ' 个角色 / ',
                          (SELECT COUNT(*) FROM user), ' 个账号 / ',
                          (SELECT COUNT(*) FROM article), ' 篇文章');
        "
        echo "提示：对照用例改过 role 3 的权限，用例自带还原；若中途中断，可重建对照库（ref_env.sh db）" ;;
    stop)
        port="${2:-7001}"
        PIDFILE="/tmp/jscreator-ref-$port.pid"
        if [ -f "$PIDFILE" ]; then kill "$(cat "$PIDFILE")" 2>/dev/null || true; rm -f "$PIDFILE"; fi
        echo "已停原版对照进程（端口 $port）" ;;
    status)
        port="${2:-7001}"
        PIDFILE="/tmp/jscreator-ref-$port.pid"
        if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
            echo "port $port running pid $(cat "$PIDFILE")"
        else
            echo "port $port not running"
        fi ;;
    *) echo "用法：$0 {db|start|stop|status} [db_name] [port]"; exit 1 ;;
esac
