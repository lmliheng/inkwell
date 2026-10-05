#!/bin/sh
# 生成后台的来源白名单（nginx allow 列表），内容取自环境变量 ADMIN_ALLOW_HOSTS。
#
# 为什么放进环境变量：这份白名单是**部署者自己的出口 IP**，属于本地配置而不是项目代码 ——
# 公开仓库里不该出现某个人的固定 IP，所以仓库只带默认值（本机 + docker 私网段），
# 自己的 IP 写进 .env 的 ADMIN_ALLOW_HOSTS（分号分隔），容器重建后自动生效：
#
#   ADMIN_ALLOW_HOSTS=127.0.0.1;::1;172.16.0.0/12;203.0.113.7
#   也可以写成逗号分隔；.env 里记得给值加引号（这个文件会被部署脚本 source 进 shell）
#
# 生成的文件放在 /etc/nginx/ 下而不是 conf.d/ 里：conf.d 下的 *.conf 会被主配置在 http 段
# 整体 include，把 allow/deny 提到 http 段会作用到所有 server，容易踩坑，所以这里显式 include。
set -e

hosts="${ADMIN_ALLOW_HOSTS:-127.0.0.1;::1;172.16.0.0/12}"
out=/etc/nginx/inkwell-allow.conf

{
  echo "# 由 /docker-entrypoint.d/10-allow-hosts.sh 依据 ADMIN_ALLOW_HOSTS 生成，不要手改这个文件"
  for h in $(printf '%s' "$hosts" | tr ';,' '  '); do
    echo "allow $h;"
  done
  echo "deny all;"
} > "$out"

echo "[allow-hosts] 白名单 $(grep -c '^allow' "$out") 条 -> $out"
