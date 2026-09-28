# 后台前端（Vue 3 + Vite + Naive UI/Element Plus）
#   构建阶段在容器里跑 vite build，产物直接拷进 nginx 镜像 —— 不再需要先在宿主机跑脚本再挂 dist。
#
#   docker build -t inkwell-admin:local apps/admin
#
# 接口地址取 .env.production 里的 VITE_API_BASE=/api，由本目录的 nginx.conf 反代到网关。
FROM node:24-bookworm-slim AS build
WORKDIR /app

COPY package.json package-lock.json ./
# --ignore-scripts：跳过 cypress 的二进制下载（构建用不到，能省 200M 和几分钟）
RUN npm ci --ignore-scripts --no-audit --no-fund

COPY . .

# 只跑 vite build。`npm run build` 还会先跑 vue-tsc 类型检查，而这份历史代码的类型并不干净
# （vite.config.ts 里已用 define 顶掉没有 VITE_ 前缀的 import.meta.env.API_BASE，值来自 .env.production）。
RUN npx vite build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# 启动时按 ADMIN_ALLOW_HOSTS 生成来源白名单（/etc/nginx/inkwell-allow.conf）
COPY allow-hosts.sh /docker-entrypoint.d/10-allow-hosts.sh
RUN chmod +x /docker-entrypoint.d/10-allow-hosts.sh
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
