#!/usr/bin/env bash
# 合并 JScreator 前端源码并构建成静态产物 web/dist/
#
# 为什么要“合并”：仓库是在 Windows 上编辑的，git 里同时存在 admin/ 与 Admin/ 两个路径，
# 但 Windows 不区分大小写，实际是同一个目录。在 Linux 上 clone 后它们被拆成两半：
#   admin/  —— 脚手架：index.html、vite.config.ts、tsconfig、main.ts、router、store、i18n、composables、全局样式
#   Admin/  —— 业务：package.json、.env、views、components、字体
# 两半互补且无同名冲突，合起来才能构建。
#
# 用法：bash web/build.sh          # 产物在 web/src-app/dist
#      API_BASE=/api bash web/build.sh
set -euo pipefail

SRC="${SRC:-/root/JScreator}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/src-app"
API_BASE="${API_BASE:-/api}"

echo "==> 合并源码 $SRC/{admin,Admin} -> $OUT"
rm -rf "$OUT"
mkdir -p "$OUT/src"

# 1) 脚手架半
cp "$SRC/admin/index.html" "$SRC/admin/vite.config.ts" \
   "$SRC/admin/tsconfig.json" "$SRC/admin/tsconfig.app.json" \
   "$SRC/admin/tsconfig.node.json" "$SRC/admin/tsconfig.vitest.json" \
   "$SRC/admin/env.d.ts" "$OUT/"
cp -a "$SRC/admin/src/." "$OUT/src/"

# 2) 业务半（后拷贝，同名文件以业务半为准）
cp "$SRC/Admin/package.json" "$SRC/Admin/package-lock.json" "$OUT/"
cp -a "$SRC/Admin/src/." "$OUT/src/"

# 3) API 基址改成同源 /api：由 80 端口的 nginx 反代到网关，前端不再写死 127.0.0.1:7000
printf "VITE_API_BASE=%s\nVITE_PUBLIC_Path='/'\n" "$API_BASE" > "$OUT/.env.production"
printf "VITE_API_BASE=%s\nVITE_PUBLIC_Path='/'\n" "$API_BASE" > "$OUT/.env.development"

# 源码里 useAxiosConfig.ts 用的是非 VITE_ 前缀的 import.meta.env.API_BASE，只有 define 能替换它
python3 - "$OUT/vite.config.ts" "$API_BASE" <<'PY'
import pathlib, sys
path, api = sys.argv[1], sys.argv[2]
p = pathlib.Path(path)
s = p.read_text(encoding='utf-8')
if 'import.meta.env.API_BASE' not in s:
    s = s.replace(
        'export default defineConfig({',
        "export default defineConfig({\n"
        "  // 由 web/build.sh 注入：源码用的是没有 VITE_ 前缀的 import.meta.env.API_BASE\n"
        "  define: { 'import.meta.env.API_BASE': JSON.stringify(%r) }," % api,
        1)
    p.write_text(s, encoding='utf-8')
PY

# 4) 依赖。@element-plus/icons-vue 被页面 import 但没写进 package.json，ci 之后单独补上
cd "$OUT"
echo "==> npm ci"
npm ci --ignore-scripts --no-audit --no-fund
npm install --no-save --no-package-lock --ignore-scripts --no-audit --no-fund @element-plus/icons-vue

# 5) 构建。跳过 vue-tsc 类型检查（vue-tsc --build 对这份历史代码并不干净）
echo "==> vite build"
npx vite build

# 6) 产物拷到 web/dist（compose 里挂载的就是这个目录）
# 注意：这里**不能** `rm -rf dist` 再 `cp`——compose 把 web/dist 以 bind mount 挂进
# nginx 容器，删掉目录会让容器里那份挂载指向已删除的 inode，表现为首页 403、容器内 html 目录为空。
# 正确做法是清空目录内容、保留目录本身。
echo "==> 同步产物到 $HERE/dist（保留目录 inode，避免 bind mount 失效）"
mkdir -p "$HERE/dist"
find "$HERE/dist" -mindepth 1 -delete
cp -a "$OUT/dist/." "$HERE/dist/"

echo "==> 产物：$HERE/dist"
ls -1 "$HERE/dist"
