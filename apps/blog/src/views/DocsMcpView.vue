<script setup>
import { computed } from 'vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

// 环境区分：文档中展示的后端 API 地址
const apiBase = computed(() => {
  const base = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:7000'
  // 去掉协议，便于展示 curl
  return base.replace(/^https?:\/\//, '')
})

// 代码示例里的完整 API host（含协议）
const apiHost = computed(() => {
  return import.meta.env.VITE_API_BASE || 'http://127.0.0.1:7000'
})

function logout() {
  auth.logout()
  router.push('/')
}
</script>

<template>
  <div class="min-h-screen bg-page text-body antialiased">
    <!-- 顶部导航 -->
    <header class="docs-topbar">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-4">
        <RouterLink to="/" class="flex items-center gap-2 text-white">
          <span class="docs-logo">J</span>
          <span class="font-extrabold tracking-wide">JScreator 博客</span>
        </RouterLink>
        <nav class="hidden items-center gap-1 sm:flex">
          <RouterLink to="/" class="docs-nav-link">首页</RouterLink>
          <RouterLink to="/archive" class="docs-nav-link">归档</RouterLink>
          <RouterLink to="/search" class="docs-nav-link">搜索</RouterLink>
          <RouterLink to="/docs/mcp" class="docs-nav-link docs-nav-active">API 文档</RouterLink>
        </nav>
        <div class="flex items-center gap-2">
          <template v-if="auth.isLoggedIn">
            <RouterLink
              :to="`/${auth.username}`"
              class="rounded-full border border-white/30 px-3.5 py-1.5 text-sm font-medium text-white transition hover:bg-white/10"
            >{{ auth.displayName }}</RouterLink>
            <button class="rounded-full border border-white/30 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-white/10" @click="logout">
              退出
            </button>
          </template>
          <template v-else>
            <RouterLink to="/login" class="rounded-full border border-white/30 px-3.5 py-1.5 text-sm font-medium text-white transition hover:bg-white/10">登录</RouterLink>
            <RouterLink to="/register" class="rounded-full bg-white px-3.5 py-1.5 text-sm font-bold text-[#2c3e50] transition hover:bg-white/90">注册</RouterLink>
          </template>
        </div>
      </div>
    </header>

    <!-- 小 Hero -->
    <section class="docs-hero">
      <div class="mx-auto max-w-6xl px-4 pb-10 pt-12">
        <span class="docs-badge">开放平台</span>
        <h1 class="mt-3 text-3xl font-extrabold text-white sm:text-4xl">MCP 与开放 API 调用文档</h1>
        <p class="mt-3 max-w-2xl text-sm text-white/70 sm:text-base">
          把博客的文章、博主、AI 总结能力开放出来：既可通过标准 REST API 直接调用，
          也可接入 MCP（Model Context Protocol）让 Claude 等 AI 助手直接读写博客。
        </p>
      </div>
    </section>

    <main class="mx-auto max-w-6xl px-4 pb-16">
      <div class="docs-layout">
        <!-- 左侧目录 -->
        <aside class="docs-toc">
          <nav class="sticky top-6 flex flex-col gap-1">
            <a href="#key" class="docs-toc-link">1. 获取 API Key</a>
            <a href="#rest" class="docs-toc-link">2. REST API 调用</a>
            <a href="#mcp" class="docs-toc-link">3. MCP 接入</a>
            <a href="#tools" class="docs-toc-link">4. MCP 工具清单</a>
            <a href="#limits" class="docs-toc-link">5. 权限与限制</a>
          </nav>
        </aside>

        <!-- 内容 -->
        <div class="docs-content">
          <!-- 1. 获取 key -->
          <section id="key" class="docs-section">
            <h2>1. 获取 API Key</h2>
            <p>登录后台，进入 <strong>「API Keys」</strong> 菜单，点「新建 API Key」创建。密钥只显示一次，请妥善保存。</p>
            <div class="doc-tip">💡 只读工具（查询文章/用户/AI总结）用 read 权限即可；发布文章需要 read,write 权限（仅管理员/编辑可创建）。</div>
          </section>

          <!-- 2. REST API -->
          <section id="rest" class="docs-section">
            <h2>2. REST API 调用</h2>
            <p>所有请求需带请求头 <code>Authorization: Bearer &lt;你的 API Key&gt;</code>。</p>

            <h3>获取文章列表</h3>
            <pre class="doc-code"><code>curl -H "Authorization: Bearer sk_你的key" \
  "{{ apiHost }}/api/v1/articles?page=1&pageSize=10"</code></pre>

            <h3>获取文章详情（含 AI 总结）</h3>
            <pre class="doc-code"><code>curl -H "Authorization: Bearer sk_你的key" \
  "{{ apiHost }}/api/v1/articles/42"</code></pre>

            <h3>查询博主主页</h3>
            <pre class="doc-code"><code>curl -H "Authorization: Bearer sk_你的key" \
  "{{ apiHost }}/api/v1/users/admin"</code></pre>

            <h3>发布文章（需 write 权限）</h3>
            <pre class="doc-code"><code>curl -X POST -H "Authorization: Bearer sk_你的key" \
  -H "Content-Type: application/json" \
  -d '{"title":"我的新文章","content":"# 正文内容","status":1}' \
  "{{ apiHost }}/api/v1/articles"</code></pre>
          </section>

          <!-- 3. MCP 接入 -->
          <section id="mcp" class="docs-section">
            <h2>3. MCP 接入</h2>
            <p>MCP（Model Context Protocol）让 AI 助手直接使用这些能力。以 Claude Desktop 为例：</p>
            <ol class="docs-ol">
              <li>克隆 mcp-server 目录并安装：<code>cd mcp-server && npm install && npm run build</code></li>
              <li>编辑 Claude Desktop 配置 <code>claude_desktop_config.json</code>：</li>
            </ol>
            <pre class="doc-code"><code>{
  "mcpServers": {
    "jscreator": {
      "command": "node",
      "args": ["绝对路径/mcp-server/dist/index.js"],
      "env": { "JS_API_KEY": "sk_你的key" }
    }
  }
}</code></pre>
            <p>重启 Claude Desktop 后即可让 AI 查询/发布博客文章。</p>
          </section>

          <!-- 4. 工具清单 -->
          <section id="tools" class="docs-section">
            <h2>4. MCP 工具清单</h2>
            <div class="doc-table-wrap">
              <table class="doc-table">
                <thead>
                  <tr><th>工具</th><th>说明</th><th>权限</th></tr>
                </thead>
                <tbody>
                  <tr><td><code>list_articles</code></td><td>文章列表（分页/关键词/分类）</td><td>read</td></tr>
                  <tr><td><code>get_article</code></td><td>文章详情（含正文）</td><td>read</td></tr>
                  <tr><td><code>get_user_profile</code></td><td>博主公开主页信息</td><td>read</td></tr>
                  <tr><td><code>get_article_ai_summary</code></td><td>文章 AI 导读</td><td>read</td></tr>
                  <tr><td><code>create_article</code></td><td>发布文章</td><td>read,write</td></tr>
                </tbody>
              </table>
            </div>
          </section>

          <!-- 5. 权限 -->
          <section id="limits" class="docs-section">
            <h2>5. 权限与限制</h2>
            <ul class="docs-ul">
              <li>API Key 在后台可随时<strong>禁用/删除</strong>，删除后立即失效。</li>
              <li>数据库只存 Key 的哈希，明文无法找回——遗失需重新创建。</li>
              <li>通过 API 发布的文章归属 Key 持有者账号。</li>
              <li>调用频次由后端统一限制，请勿高频请求。</li>
            </ul>
          </section>
        </div>
      </div>
    </main>

    <SiteFooter />
  </div>
</template>

<style scoped>
@reference "../style.css";

/* ---- 顶部导航（深色，与首页 Hero 导航一致） ---- */
.docs-topbar {
  background: linear-gradient(135deg, #2c3e50, #1e3a5f);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.docs-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.18);
  font-size: 16px;
  font-weight: 900;
  color: #fff;
}
.docs-nav-link {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.8);
  transition: background-color 0.2s ease, color 0.2s ease;
}
.docs-nav-link:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}
.docs-nav-active {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

/* ---- Hero ---- */
.docs-hero {
  background: linear-gradient(135deg, #2c3e50, #405f7d, #1e3a5f, #4a6b8a, #2c3e50);
  background-size: 300% 300%;
  animation: heroGradient 10s ease infinite;
}
@keyframes heroGradient {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}
.docs-badge {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.25);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
}

/* ---- 布局 ---- */
.docs-layout {
  display: flex;
  gap: 32px;
  align-items: flex-start;
  margin-top: 24px;
}
.docs-toc {
  width: 180px;
  flex-shrink: 0;
  display: none;
}
@media (min-width: 1024px) {
  .docs-toc {
    display: block;
  }
}
.docs-toc-link {
  display: block;
  padding: 7px 10px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-muted);
  text-decoration: none;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.docs-toc-link:hover {
  background: color-mix(in oklab, var(--color-accent) 8%, transparent);
  color: var(--color-ink);
}
.docs-content {
  flex: 1;
  min-width: 0;
}
.docs-section {
  scroll-margin-top: 24px;
  padding: 20px 0;
  border-bottom: 1px solid var(--color-line);
}
.docs-section:last-child {
  border-bottom: none;
}
.docs-section h2 {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-ink);
  margin-bottom: 12px;
}
.docs-section h3 {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-ink);
  margin: 18px 0 8px;
}
.docs-section p {
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-body);
  margin: 6px 0;
}
.docs-section code {
  font-size: 12.5px;
  background: color-mix(in oklab, var(--color-accent) 10%, transparent);
  padding: 1px 6px;
  border-radius: 5px;
  color: var(--color-accent);
  word-break: break-all;
}
.doc-code {
  margin: 10px 0 14px;
  padding: 14px 16px;
  border-radius: 10px;
  background: #1e293b;
  color: #e2e8f0;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.7;
}
.doc-code code {
  background: none;
  color: inherit;
  padding: 0;
}
.doc-tip {
  margin: 12px 0;
  padding: 10px 14px;
  border-radius: 10px;
  background: color-mix(in oklab, #f6b93b 12%, transparent);
  border: 1px solid color-mix(in oklab, #f6b93b 35%, transparent);
  font-size: 13px;
  color: var(--color-body);
  line-height: 1.7;
}
.docs-ol,
.docs-ul {
  margin: 8px 0 8px 20px;
  padding: 0;
  font-size: 14px;
  line-height: 1.9;
  color: var(--color-body);
}
.docs-ol li,
.docs-ul li {
  margin: 4px 0;
}
.doc-table-wrap {
  margin: 12px 0;
  overflow-x: auto;
  border: 1px solid var(--color-line);
  border-radius: 10px;
}
.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.doc-table th,
.doc-table td {
  text-align: left;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-line);
}
.doc-table th {
  background: color-mix(in oklab, var(--color-accent) 6%, transparent);
  font-weight: 700;
  color: var(--color-ink);
}
.doc-table tr:last-child td {
  border-bottom: none;
}
</style>
