# 博客前端（Vue 3 + Vite + Tailwind CSS 4）
#   构建阶段在容器里跑 vite build，产物直接拷进 nginx 镜像。
#
#   docker build -t inkwell-blog:local apps/blog
#   docker build -t inkwell-blog:local --build-arg VITE_ADMIN_ADDRESS=http://your-host/ apps/blog
#
# 接口地址 VITE_API_BASE=/api，由本目录的 nginx.conf 反代到网关（浏览器看来是同源，不涉跨域）。
FROM node:24-bookworm-slim AS build
WORKDIR /app

# 后台地址：博客首页那个「进入后台」的链接指向它，换域名时用 --build-arg 覆盖
ARG VITE_ADMIN_ADDRESS=http://127.0.0.1/
ARG VITE_FOOT_TEXT=

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

# 构建时生成 .env.production（覆盖仓库里那份占位），避免依赖 .env 文件的合并顺序
RUN printf "VITE_API_BASE=/api\nVITE_USE_MOCK=false\nVITE_ADMIN_ADDRESS=%s\nVITE_FOOT_TEXT=%s\n" \
        "$VITE_ADMIN_ADDRESS" "${VITE_FOOT_TEXT:-由腾讯云CloudBase提供托管服务}" > .env.production \
    && npx vite build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
