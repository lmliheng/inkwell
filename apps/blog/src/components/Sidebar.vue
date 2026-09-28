<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAuthorStore } from '@/stores/author'
import ThemeToggle from './ThemeToggle.vue'

const props = defineProps({
  avatar: { type: String, default: '' },
  nickname: { type: String, default: 'JScreator' },
  bio: { type: String, default: '技术随笔' },
  github: { type: String, default: 'https://github.com/lmliheng/JScreator' },
  rss: { type: String, default: '#' },
  email: { type: String, default: 'mailto:hello@example.com' },
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const authorStore = useAuthorStore()

const open = ref(false)

// 后台入口地址：本地开发指向 8085，生产部署在同域 /panel/ 下
const adminUrl = import.meta.env.DEV ? 'http://localhost:8085/' : '/panel/'

// 侧边栏展示的信息优先级：当前浏览的作者 > 登录用户 > props 默认（JScreator）
const displayName = computed(
  () => authorStore.current?.name || authorStore.current?.username || auth.displayName || props.nickname || 'JScreator',
)

const avatarUrl = computed(
  () => authorStore.current?.avatar || auth.user?.avatar || props.avatar || '',
)

const bioText = computed(
  () => authorStore.current?.bio || auth.user?.bio || props.bio || '',
)

const monogram = computed(() => (displayName.value || 'J').trim().charAt(0).toUpperCase())

// 导航：正在浏览某个作者时展示「作者视角」导航；否则展示站点导航。
const nav = computed(() => {
  const u = authorStore.current?.username
  if (u) {
    return [
      { to: `/${u}`, label: '作者主页' },
      { to: `/${u}/articles`, label: '文章' },
      { to: `/${u}/archive`, label: '归档' },
      { to: `/${u}`, label: '分类' },
    ]
  }
  return [
    { to: '/', label: '首页' },
    { to: '/archive', label: '归档' },
    { to: '/search', label: '搜索' },
  ]
})

const socials = [
  { key: 'github', label: 'GitHub', href: props.github },
  { key: 'rss', label: 'RSS', href: props.rss },
  { key: 'mail', label: '邮箱', href: props.email },
]

function isActive(to) {
  if (to === '/') return route.path === '/'
  return route.path === to || route.path.startsWith(to + '/')
}

function closeMenu() {
  open.value = false
}

function logout() {
  auth.logout()
  closeMenu()
  router.push('/')
}

// 路由切换时收起移动端菜单
watch(() => route.fullPath, closeMenu)
</script>

<template>
  <!-- ===================== 桌面侧边栏（lg 及以上固定左侧） ===================== -->
  <aside class="sidebar-desktop">
    <div class="flex min-h-full flex-col px-7 py-10">
      <!-- 头像 -->
      <RouterLink to="/" class="mx-auto block" aria-label="返回首页">
        <span v-if="avatarUrl" class="avatar-ring">
          <img :src="avatarUrl" :alt="displayName" class="avatar-img" />
        </span>
        <span v-else class="avatar-ring avatar-monogram">{{ monogram }}</span>
      </RouterLink>

      <!-- 昵称 & 简介 -->
      <RouterLink to="/" class="mt-5 block">
        <h1 class="text-center text-xl font-bold leading-tight text-ink">{{ displayName }}</h1>
      </RouterLink>
      <p class="mt-2 text-center text-sm leading-relaxed text-muted">{{ bioText }}</p>

      <!-- 导航菜单 -->
      <nav class="mt-8 flex flex-col gap-0.5">
        <RouterLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="nav-link"
          :class="{ 'is-active': isActive(item.to) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <!-- 底部：登录态操作 + 社交图标 + 主题切换 -->
      <div class="mt-auto pt-8">
        <div class="mb-5">
          <template v-if="auth.isLoggedIn">
            <p class="mb-2 text-sm text-faint">你好，{{ displayName }}</p>
            <a
              :href="adminUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="nav-link nav-link-accent mb-1 w-full justify-center"
            >进入后台</a>
            <button class="nav-link w-full text-left" @click="logout">退出登录</button>
          </template>
          <template v-else>
            <div class="flex items-center gap-2">
              <RouterLink to="/login" class="nav-link flex-1 justify-center">登录</RouterLink>
              <RouterLink to="/register" class="nav-link nav-link-accent flex-1 justify-center">注册</RouterLink>
            </div>
          </template>
        </div>

        <div class="flex items-center justify-center gap-2">
          <a
            v-for="s in socials"
            :key="s.key"
            class="social-btn"
            :href="s.href"
            :aria-label="s.label"
            :title="s.label"
            :target="s.href.startsWith('http') ? '_blank' : undefined"
            rel="noopener noreferrer"
          >
            <!-- GitHub -->
            <svg v-if="s.key === 'github'" class="h-4 w-4" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.56 0-.28-.01-1.02-.02-2-3.2.7-3.88-1.54-3.88-1.54-.52-1.33-1.28-1.68-1.28-1.68-1.04-.71.08-.7.08-.7 1.15.08 1.76 1.18 1.76 1.18 1.02 1.75 2.69 1.25 3.35.95.1-.74.4-1.25.72-1.54-2.55-.29-5.23-1.28-5.23-5.68 0-1.26.45-2.29 1.18-3.09-.12-.29-.51-1.46.11-3.05 0 0 .96-.31 3.15 1.18a10.9 10.9 0 0 1 5.74 0c2.19-1.49 3.15-1.18 3.15-1.18.62 1.59.23 2.76.11 3.05.73.8 1.18 1.83 1.18 3.09 0 4.41-2.69 5.38-5.25 5.67.41.35.78 1.05.78 2.12 0 1.53-.01 2.76-.01 3.14 0 .31.21.67.8.56A11.52 11.52 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5z"
              />
            </svg>
            <!-- RSS -->
            <svg v-else-if="s.key === 'rss'" class="h-4 w-4" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path
                d="M6.18 15.64a2.18 2.18 0 0 1 2.18 2.18c0 1.2-.98 2.18-2.18 2.18A2.18 2.18 0 0 1 4 17.82c0-1.2.98-2.18 2.18-2.18zM4 4.44A15.56 15.56 0 0 1 19.56 20h-2.83A12.73 12.73 0 0 0 4 7.27V4.44zm0 5.66a9.9 9.9 0 0 1 9.9 9.9h-2.83A7.07 7.07 0 0 0 4 12.93V10.1z"
              />
            </svg>
            <!-- Mail -->
            <svg v-else class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M3 8l7.89 5.26a2 2 0 0 0 2.22 0L21 8M5 19h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2z"
              />
            </svg>
          </a>
          <ThemeToggle />
        </div>
      </div>
    </div>
  </aside>

  <!-- ===================== 移动端顶部菜单（lg 以下） ===================== -->
  <header class="sidebar-mobile">
    <RouterLink to="/" class="flex min-w-0 items-center gap-3" @click="closeMenu">
      <span v-if="avatarUrl" class="avatar-ring avatar-sm">
        <img :src="avatarUrl" :alt="displayName" class="avatar-img" />
      </span>
      <span v-else class="avatar-ring avatar-monogram avatar-sm">{{ monogram }}</span>
      <span class="truncate font-bold text-ink">{{ displayName }}</span>
    </RouterLink>

    <div class="flex items-center gap-1">
      <ThemeToggle />
      <button
        class="mobile-hamburger"
        :aria-expanded="open"
        aria-label="打开菜单"
        @click="open = !open"
      >
        <svg v-if="!open" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h16M4 18h16" />
        </svg>
        <svg v-else class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  </header>

  <!-- 移动端折叠面板 -->
  <Transition name="menu">
    <div v-if="open" class="sidebar-mobile-panel lg:hidden">
      <nav class="flex flex-col gap-0.5 px-2">
        <RouterLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="nav-link"
          :class="{ 'is-active': isActive(item.to) }"
          @click="closeMenu"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="mt-4 border-t border-line px-2 pt-4">
        <template v-if="auth.isLoggedIn">
          <p class="mb-2 text-sm text-faint">你好，{{ displayName }}</p>
          <a
            :href="adminUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="nav-link nav-link-accent mb-1 w-full justify-center"
            @click="closeMenu"
          >进入后台</a>
          <button class="nav-link w-full text-left" @click="logout">退出登录</button>
        </template>
        <template v-else>
          <div class="flex items-center gap-2">
            <RouterLink to="/login" class="nav-link flex-1 justify-center" @click="closeMenu">登录</RouterLink>
            <RouterLink to="/register" class="nav-link nav-link-accent flex-1 justify-center" @click="closeMenu">
              注册
            </RouterLink>
          </div>
        </template>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
@reference "../style.css";

/* ---- 桌面侧边栏 ---- */
.sidebar-desktop {
  display: none;
}
@media (min-width: 1024px) {
  .sidebar-desktop {
    display: block;
    position: sticky;
    top: 0;
    height: 100vh;
    width: var(--sidebar-width);
    flex-shrink: 0;
    overflow-y: auto;
  }
}

/* ---- 头像 ---- */
.avatar-ring {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 5.5rem;
  height: 5.5rem;
  border-radius: 9999px;
  overflow: hidden;
  background-color: var(--color-accent);
  color: var(--color-on-accent);
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--color-accent) 15%, transparent);
}
.avatar-monogram {
  font-size: 2rem;
  font-weight: 700;
}
.avatar-sm {
  width: 2.25rem;
  height: 2.25rem;
  font-size: 0.875rem;
  box-shadow: none;
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ---- 导航链接 ---- */
.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  border-radius: var(--radius-tag);
  font-size: 0.9375rem;
  font-weight: 500;
  color: var(--color-body);
  transition: background-color 0.2s ease, color 0.2s ease;
}
.nav-link:hover {
  background-color: color-mix(in oklab, var(--color-accent) 8%, transparent);
  color: var(--color-ink);
}
.nav-link.is-active {
  background-color: color-mix(in oklab, var(--color-accent) 12%, transparent);
  color: var(--color-accent);
  font-weight: 700;
}
.nav-link-accent {
  background-color: var(--color-accent);
  color: var(--color-on-accent);
}
.nav-link-accent:hover {
  background-color: var(--color-accent-strong);
  color: var(--color-on-accent);
}

/* ---- 社交图标 ---- */
.social-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: 9999px;
  color: var(--color-muted);
  transition: background-color 0.2s ease, color 0.2s ease;
}
.social-btn:hover {
  background-color: color-mix(in oklab, var(--color-accent) 12%, transparent);
  color: var(--color-accent);
}

/* ---- 移动端顶栏 ---- */
.sidebar-mobile {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background-color: var(--color-page);
  border-bottom: 1px solid var(--color-line);
}
@media (min-width: 1024px) {
  .sidebar-mobile {
    display: none;
  }
}

.mobile-hamburger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 9999px;
  color: var(--color-body);
  cursor: pointer;
}
.mobile-hamburger:hover {
  background-color: color-mix(in oklab, var(--color-accent) 12%, transparent);
  color: var(--color-ink);
}

/* ---- 移动端折叠面板 ---- */
.sidebar-mobile-panel {
  position: fixed;
  top: 3.75rem;
  left: 0;
  right: 0;
  z-index: 30;
  padding: 0.75rem 1rem 1.25rem;
  background-color: var(--color-page);
  border-bottom: 1px solid var(--color-line);
  box-shadow: 0 12px 24px -12px rgba(0, 0, 0, 0.25);
}

/* 过渡 */
.menu-enter-active,
.menu-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
