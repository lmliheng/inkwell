import { createApp } from 'vue'
import App from './App.vue'
import { pinia } from './stores'
import router from './router'
import { setupMock } from './api/mock'

// Tailwind CSS 4 入口（v4 新写法：@import "tailwindcss"）
import './style.css'
// 代码高亮主题（配合正文里的深色代码块）
import 'highlight.js/styles/github-dark.css'

// 按需启用 mock：VITE_USE_MOCK=true 时拦截 axios；后端就绪后改为 false 即可直连后端
setupMock()

const app = createApp(App)
app.use(pinia)
app.use(router)
app.mount('#app')
