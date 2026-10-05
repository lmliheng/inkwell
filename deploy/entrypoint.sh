#!/bin/sh
# 用法：entrypoint.sh <gateway|auth|content|social|system>
# 内存参数由 compose 的 JAVA_TOOL_OPTIONS 传入（小机器必须限额，否则 JVM 会按物理内存的 1/4 要堆）。
set -e

MODULE="${1:-gateway}"

case "$MODULE" in
  gateway) JAR=/app/gateway.jar ;;
  auth)    JAR=/app/auth.jar ;;
  content) JAR=/app/content.jar ;;
  social)  JAR=/app/social.jar ;;
  system)  JAR=/app/system.jar ;;
  *) echo "未知模块: $MODULE（可选 gateway|auth|content|social|system）" >&2; exit 2 ;;
esac

echo "[entrypoint] 启动 $MODULE -> $JAR"
exec java $JAVA_TOOL_OPTIONS -jar "$JAR"
