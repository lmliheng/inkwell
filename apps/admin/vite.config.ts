import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  // 历史源码里用的是**没有 VITE_ 前缀**的 import.meta.env.API_BASE
  // （useAxiosConfig.ts、ApiKeyManage.vue、OAuthClientManage.vue），
  // Vite 只替换 VITE_ 前缀的变量，所以这里用 define 把它顶掉，值仍取自 .env[.mode]。
  //   .env.development → http://127.0.0.1:7000   本机直连网关
  //   .env.production  → /api                     由容器里的 nginx 反代到 gateway:8088
  const apiBase = env.VITE_API_BASE || 'http://127.0.0.1:7000'

  return {
    define: {
      'import.meta.env.API_BASE': JSON.stringify(apiBase),
    },
    plugins: [
      vue(),
      vueDevTools(),
    ],
    server: {
      port: 5173,        // 开发服务器端口
      strictPort: true,  // 端口被占用时直接报错，而不是自动用 5174/5175...
      host: true,        // 允许局域网/0.0.0.0 访问（可选）
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  }
})
