import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由结构（扁平）。
 * 布局由 App.vue 统一包裹：<AppLayout><RouterView /></AppLayout>，
 * 因此这里不需要再嵌套 AppLayout 父路由，避免双重渲染侧边栏。
 */
const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    // 门户首页：全宽无侧边栏，与个人主页一致（Hero 内置导航）
    meta: { layout: 'blank' },
  },
  {
    path: '/users',
    name: 'users',
    component: () => import('@/views/UsersView.vue'),
    // 全部博主列表：全宽无侧边栏（沿用首页观感）
    meta: { layout: 'blank' },
  },
  {
    path: '/docs/mcp',
    name: 'docs-mcp',
    component: () => import('@/views/DocsMcpView.vue'),
    // MCP/开放 API 调用文档（公开）：全宽无侧边栏
    meta: { layout: 'blank' },
  },
  {
    path: '/me/favorites',
    name: 'my-favorites',
    component: () => import('@/views/FavoritesView.vue'),
    // 我的收藏：全宽无侧边栏
    meta: { layout: 'blank' },
  },
  {
    path: '/assistant',
    name: 'assistant',
    component: () => import('@/views/AssistantView.vue'),
    // 消息中心（AI Agent + 私信）：全宽无侧边栏
    meta: { layout: 'blank', requiresAuth: true },
  },
  {
    path: '/article/:id',
    name: 'article-detail',
    component: () => import('@/views/ArticleDetailView.vue'),
  },
  {
    path: '/category/:id',
    name: 'category',
    component: () => import('@/views/CategoryView.vue'),
  },
  {
    path: '/tag/:id',
    name: 'tag',
    component: () => import('@/views/TagView.vue'),
  },
  {
    path: '/archive',
    name: 'archive',
    component: () => import('@/views/ArchiveView.vue'),
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('@/views/SearchView.vue'),
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guestOnly: true },
  },
  // 某用户的全部文章列表（公开）。
  // 必须放在 /:username 之前，因为 /:username 会匹配单段路径。
  {
    path: '/:username/articles',
    name: 'user-articles',
    component: () => import('@/views/UserArticlesView.vue'),
    meta: { layout: 'blank' },
  },
  {
    path: '/:username/archive',
    name: 'user-archive',
    component: () => import('@/views/ArchiveView.vue'),
    meta: { layout: 'blank' },
  },
  // 博客个人主页：/:username（公开）。
  // 必须放在所有具体路由（/article、/login 等）之后、通配 404 之前，
  // 由 Vue Router 按声明顺序优先匹配具体路径，避免吞掉 /article/:id 等。
  {
    path: '/:username',
    name: 'blog-profile',
    component: () => import('@/views/BlogProfileView.vue'),
    // 个人主页是给访客看的公开展示页，脱离 AppLayout（无左侧 Sidebar）
    meta: { layout: 'blank' },
  },

  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const auth = useAuthStore(pinia)

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return { name: 'home' }
  }
  return true
})

// 动态标题：默认「{当前用户昵称}的博客」，未登录回退「JScreator的博客」。
// 博客个人主页（BlogProfileView）在拿到 profile 数据后会覆盖为被访问者的昵称。
router.afterEach((to) => {
  const auth = useAuthStore(pinia)
  const name = auth.displayName || 'JScreator'
  if (to.name === 'blog-profile') {
    // 由视图异步覆盖为对方昵称；此处先给出用户名占位
    document.title = `${String(to.params.username || '')}的博客`
    return
  }
  document.title = `${name}的博客`
})

export default router
