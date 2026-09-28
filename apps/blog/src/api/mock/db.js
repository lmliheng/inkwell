// 前端 mock 数据源（内存态 + localStorage 持久化）。
// 仅在后端未就绪时使用，数据结构与 blog-api.md 契约保持一致。

const DB_VERSION = 2
const LS_KEY = 'blog_mock_db'

function iso(offsetDays, offsetMinutes = 0) {
  const d = new Date()
  d.setDate(d.getDate() - offsetDays)
  d.setMinutes(d.getMinutes() - offsetMinutes)
  return d.toISOString()
}

function seed() {
  return {
    version: DB_VERSION,
    users: [
      // 角色：1=admin、2=user（普通用户）、3=editor（编辑）
      {
        id: 'u1',
        username: 'admin',
        email: 'admin@demo.com',
        password: 'admin123',
        role_id: 1,
        nickname: 'JScreator',
        bio: '技术随笔',
        region: '中国 · 上海',
        avatar: '',
      },
      {
        id: 'u2',
        username: 'demo',
        email: 'demo@demo.com',
        password: 'demo123',
        role_id: 2,
        nickname: 'Demo 同学',
        bio: '记录学习、写作与生活。',
        region: '中国 · 北京',
        avatar: '',
      },
      {
        id: 'u3',
        username: 'editor',
        email: 'editor@demo.com',
        password: 'editor123',
        role_id: 3,
        nickname: '编辑小站',
        bio: '审稿、写作与内容整理。',
        region: '',
        avatar: '',
      },
    ],
    categories: [
      { category_id: '1', name: '技术', user_id: 'u1' },
      { category_id: '2', name: '前端', user_id: 'u1' },
      { category_id: '3', name: '随笔', user_id: 'u2' },
    ],
    articles: [
      {
        article_id: 'a1',
        title: '用 Vue 3 + Vite 搭建现代前端工程',
        status: 1,
        user_id: 'u1',
        category_ids: ['1'],
        created_at: iso(1, 20),
        updated_at: iso(1, 20),
        content: [
          'Vue 3 的 `<script setup>` 组合式 API 让组件写法更简洁，配合 Vite 能获得极快的冷启动与热更新体验。',
          '',
          '## 快速开始',
          '',
          '```bash',
          'npm create vite@latest my-app -- --template vue',
          'cd my-app',
          'npm install',
          'npm run dev',
          '```',
          '',
          '## 核心概念',
          '',
          '1. **响应式**：用 `ref` 与 `reactive` 管理状态。',
          '2. **组合函数**：把可复用逻辑抽到 `composables` 目录。',
          '3. **生命周期**：`onMounted`、`onUnmounted` 等钩子清晰直观。',
          '',
          '> 提示：组件命名用 PascalCase，目录与文件名使用 kebab-case。',
          '',
          '## 小结',
          '',
          '这套技术栈上手快、生态成熟，是当前社区的主流选择。',
        ].join('\n'),
      },
      {
        article_id: 'a2',
        title: 'Tailwind CSS 4 快速上手',
        status: 1,
        user_id: 'u1',
        category_ids: ['2'],
        created_at: iso(2, 45),
        updated_at: iso(2, 45),
        content: [
          'Tailwind CSS 4 引入了全新的 CSS-first 配置方式，无需 `tailwind.config.js`，配置更少、构建更快。',
          '',
          '## 安装',
          '',
          '```bash',
          'npm install tailwindcss @tailwindcss/vite',
          '```',
          '',
          '然后在 CSS 入口文件里写一行：',
          '',
          '```css',
          '@import "tailwindcss";',
          '```',
          '',
          '## 与 Vite 集成',
          '',
          '在 `vite.config.js` 中注册插件即可：',
          '',
          '```js',
          'import tailwindcss from "@tailwindcss/vite"',
          '',
          'export default {',
          '  plugins: [tailwindcss()],',
          '}',
          '```',
          '',
          '## 小结',
          '',
          '强烈建议新项目直接使用 v4，省去大量样板配置。',
        ].join('\n'),
      },
      {
        article_id: 'a3',
        title: 'Markdown 写作指南',
        status: 1,
        user_id: 'u2',
        category_ids: ['1'],
        created_at: iso(4, 10),
        updated_at: iso(4, 10),
        content: [
          'Markdown 是一种轻量标记语言，适合技术博客的日常写作。',
          '',
          '## 常用语法',
          '',
          '- 标题：`#` 到 `######`',
          '- 加粗：`**粗体**`',
          '- 行内代码：`code`',
          '- 链接：`[文字](url)`',
          '',
          '## 引用',
          '',
          '> 简洁是终极的复杂。—— 达·芬奇',
          '',
          '## 表格示例',
          '',
          '| 语法 | 说明 |',
          '| --- | --- |',
          '| `#` | 一级标题 |',
          '| `>` | 引用 |',
          '| `-` | 无序列表 |',
          '',
          '## 小结',
          '',
          '掌握基础语法即可覆盖 90% 的写作场景。',
        ].join('\n'),
      },
      {
        article_id: 'a4',
        title: '关于简洁设计的一些思考',
        status: 1,
        user_id: 'u2',
        category_ids: ['3'],
        created_at: iso(6, 30),
        updated_at: iso(6, 30),
        content: [
          '好的设计，是尽可能少的“设计”。',
          '',
          '## 少即是多',
          '',
          '克制地用色、留足空白、统一圆角与字号，比堆砌阴影和渐变更能建立信任感。',
          '',
          '## 内容优先',
          '',
          '读者真正需要的是易读的排版，而不是花哨的装饰。让正文成为页面的主角。',
          '',
          '## 一致性',
          '',
          '颜色、间距、字号的复用，让整个站点看起来像一个整体，而不是零散页面的拼凑。',
        ].join('\n'),
      },
      {
        article_id: 'a5',
        title: 'Pinia 状态管理最佳实践',
        status: 1,
        user_id: 'u1',
        category_ids: ['2'],
        created_at: iso(8, 15),
        updated_at: iso(8, 15),
        content: [
          'Pinia 是 Vue 官方推荐的状态管理库，API 简洁且类型友好。',
          '',
          '## 定义 Store',
          '',
          '```js',
          'import { defineStore } from "pinia"',
          '',
          'export const useCounterStore = defineStore("counter", {',
          '  state: () => ({ count: 0 }),',
          '  actions: {',
          '    increment() { this.count++ },',
          '  },',
          '})',
          '```',
          '',
          '## 在组件中使用',
          '',
          '```js',
          'const store = useCounterStore()',
          'store.increment()',
          '```',
          '',
          '## 小结',
          '',
          '把共享状态放进 store，把页面局部状态留在组件内，职责分明。',
        ].join('\n'),
      },
    ],
    comments: [
      {
        comment_id: 'c1',
        article_id: 'a1',
        user_id: 'u2',
        nickname: 'demo',
        content: '写得很清晰，正好要开新项目，收藏了！',
        parent_id: null,
        created_at: iso(1, 10),
      },
      {
        comment_id: 'c2',
        article_id: 'a1',
        user_id: 'u1',
        nickname: 'admin',
        content: '谢谢支持，有问题欢迎在评论区交流。',
        parent_id: 'c1',
        created_at: iso(1, 5),
      },
      {
        comment_id: 'c3',
        article_id: 'a1',
        user_id: 'u2',
        nickname: 'demo',
        content: '好的，已经跑起来了，热更新确实快。',
        parent_id: 'c2',
        created_at: iso(0, 300),
      },
      {
        comment_id: 'c4',
        article_id: 'a1',
        user_id: null,
        nickname: '路人甲',
        content: '请问匿名评论也可以填昵称吗？',
        parent_id: null,
        created_at: iso(0, 180),
      },
      {
        comment_id: 'c5',
        article_id: 'a1',
        user_id: 'u2',
        nickname: 'demo',
        content: '可以，游客填写昵称即可发表。',
        parent_id: 'c4',
        created_at: iso(0, 150),
      },
    ],
  }
}

function load() {
  try {
    const raw = localStorage.getItem(LS_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (parsed && parsed.version === DB_VERSION) return parsed
  } catch {
    /* ignore */
  }
  return null
}

function save(db) {
  try {
    localStorage.setItem(LS_KEY, JSON.stringify(db))
  } catch {
    /* ignore */
  }
}

export const db = load() || seed()
save(db)

let idSeq = 100

export function nextId(prefix) {
  idSeq += 1
  return `${prefix}${idSeq}-${Date.now().toString(36)}`
}

export function persist() {
  save(db)
}

export function resetDb() {
  Object.assign(db, seed())
  save(db)
}
