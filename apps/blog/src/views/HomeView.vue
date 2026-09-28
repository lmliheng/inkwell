<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getBlogUsers, getBlogFeed, getBlogHot } from '@/api/blog'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import ArticleCard from '@/components/ArticleCard.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import AdSlot from '@/components/AdSlot.vue'

const router = useRouter()
const auth = useAuthStore()
const toast = useToastStore()

const displayName = computed(() => auth.displayName || 'JS creator')

// ===== 数据 =====
const latest = ref([])
const hot = ref([])
const bloggers = ref([])
const loading = ref(true)

const adminUrl = import.meta.env.VITE_ADMIN_ADDRESS

async function fetchAll() {
  loading.value = true
  try {
    const [feed, hotRes, users] = await Promise.allSettled([
      getBlogFeed({ limit: 6 }),
      getBlogHot({ limit: 6 }),
      getBlogUsers({ page: 1, pageSize: 6 }),
    ])
    if (feed.status === 'fulfilled' && feed.value) {
      latest.value = (feed.value.list) || []
    }
    if (hotRes.status === 'fulfilled' && hotRes.value) {
      hot.value = (hotRes.value.list) || []
    }
    if (users.status === 'fulfilled' && users.value) {
      bloggers.value = (users.value.list) || []
    }
  } catch (e) {
    toast.error(e.message || '首页加载失败')
  } finally {
    loading.value = false
  }
}

// ===== 搜索 =====
const keyword = ref('')
function doSearch() {
  const kw = keyword.value.trim()
  router.push({ path: '/search', query: kw ? { keyword: kw } : {} })
}

// ===== 博主卡片辅助 =====
function userName(u) {
  return u.name || u.username || '匿名'
}

function monogram(u) {
  return userName(u).trim().charAt(0).toUpperCase()
}

// 关注按钮（需求 15 上线前为占位）
function onFollow(u) {
  if (!auth.isLoggedIn) {
    toast.error('请先登录')
    router.push('/login')
    return
  }
  toast.info('关注功能即将上线')
}

function logout() {
  auth.logout()
  router.push('/')
}

onMounted(fetchAll)
</script>

<template>
  <div class="min-h-screen bg-page text-body antialiased">
   
    <header class="hero-topbar">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-4">
        <RouterLink to="/" class="flex items-center gap-2 text-white">
          <span class="hero-logo">TS</span>
          <!-- <span class="font-extrabold tracking-wide">JScreator</span> -->
        </RouterLink>
        <nav class="hidden items-center gap-1 sm:flex">
          <RouterLink to="/" class="hero-nav-link" active-class="hero-nav-active" exact-active-class="hero-nav-active">首页</RouterLink>
          <RouterLink to="/archive" class="hero-nav-link">归档</RouterLink>
          <RouterLink to="/search" class="hero-nav-link">搜索</RouterLink>
          <RouterLink to="/docs/mcp" class="hero-nav-link">API 文档</RouterLink>
        </nav>
        <div class="flex items-center gap-2">
          <template v-if="auth.isLoggedIn">
            <RouterLink
              :to="`/${auth.username}`"
              class="hidden items-center gap-2 rounded-full border border-white/30 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-white/10 md:flex"
            >
              <span v-if="auth.user?.avatar" class="h-6 w-6 overflow-hidden rounded-full">
                <img :src="auth.user.avatar" :alt="displayName" class="h-full w-full object-cover" />
              </span>
              <span v-else class="flex h-6 w-6 items-center justify-center rounded-full bg-white/20 text-xs font-bold">
                {{ displayName.charAt(0).toUpperCase() }}
              </span>
              {{ displayName }}
            </RouterLink>
            <a
              :href="adminUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="rounded-full bg-white px-3.5 py-1.5 text-sm font-bold text-[#2c3e50] transition hover:bg-white/90"
            >进入后台</a>
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

    <!-- ===== Hero 横幅 ===== -->
    <section class="home-hero">
      <span class="hero-particle hp1"></span>
      <span class="hero-particle hp2"></span>
      <span class="hero-particle hp3"></span>
      <span class="hero-particle hp4"></span>

      <div class="relative z-10 mx-auto max-w-3xl px-4 pb-20 pt-14 text-center sm:pt-20">
        <h1 class="text-4xl font-extrabold leading-tight text-white sm:text-5xl">JS creator</h1>
        <p class="mt-4 text-base text-white/75 sm:text-lg">发现优秀博主，阅读优质内容</p>

        <!-- 搜索框 -->
        <form class="mx-auto mt-8 flex max-w-xl items-center gap-2" @submit.prevent="doSearch">
          <div class="relative flex-1">
            <svg class="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-white/50" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-4.35-4.35M17 10.5a6.5 6.5 0 11-13 0 6.5 6.5 0 0113 0z" />
            </svg>
            <input
              v-model="keyword"
              type="search"
              class="w-full rounded-full border border-white/20 bg-white/10 py-3 pl-11 pr-4 text-sm text-white placeholder-white/50 backdrop-blur transition focus:border-white/50 focus:bg-white/15 focus:outline-none"
              placeholder="搜索文章…"
            />
          </div>
          <button type="submit" class="shrink-0 rounded-full bg-white px-6 py-3 text-sm font-bold text-[#2c3e50] transition hover:bg-white/90">
            搜索
          </button>
        </form>

        <!-- 未登录 CTA -->
        <div v-if="!auth.isLoggedIn" class="mt-6 flex items-center justify-center gap-3 text-sm text-white/70">
          <span>登录后参与评论、点赞与收藏</span>
          <RouterLink to="/login" class="font-bold text-white underline-offset-4 hover:underline">立即登录 →</RouterLink>
        </div>
        <p v-else class="mt-6 text-sm text-white/70">欢迎回来，<span class="font-bold text-white">{{ displayName }}</span></p>
      </div>
    </section>

    <!-- ===== 内容区 ===== -->
    <main class="mx-auto max-w-6xl px-4 pb-16">
      <div v-if="loading" class="py-20 text-center text-faint">加载中…</div>

      <template v-else>
        <!-- 最新文章流 -->
        <section v-if="latest.length" class="mt-12">
          <div class="mb-5 flex items-end justify-between">
            <div>
              <h2 class="section-title">最新文章</h2>
              <p class="mt-1 text-sm text-muted">全站最新发布的优质内容</p>
            </div>
            <RouterLink to="/archive" class="section-more">查看全部 →</RouterLink>
          </div>
          <div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            <ArticleCard v-for="a in latest" :key="a.article_id" :article="a" compact />
          </div>
        </section>

        <!-- 首页中部广告位 -->
        <AdSlot position="home_mid" />

    

        <!-- 博主推荐 -->
        <section v-if="bloggers.length" class="mt-14">
          <div class="mb-5 flex items-end justify-between">
            <div>
              <h2 class="section-title">博主推荐</h2>
              <p class="mt-1 text-sm text-muted">关注你感兴趣的博主</p>
            </div>
            <RouterLink to="/users" class="section-more">查看全部博主 →</RouterLink>
          </div>
          <div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            <div
              v-for="u in bloggers"
              :key="u.id"
              class="group flex flex-col gap-4 rounded-card border border-line bg-card p-6 transition-shadow hover:shadow-lg"
            >
              <div class="flex items-center gap-4">
                <RouterLink :to="`/${u.username}`" class="shrink-0">
                  <span v-if="u.avatar" class="block h-14 w-14 overflow-hidden rounded-full ring-2 ring-line transition group-hover:ring-accent">
                    <img :src="u.avatar" :alt="userName(u)" class="h-full w-full object-cover" />
                  </span>
                  <span v-else class="flex h-14 w-14 items-center justify-center rounded-full bg-accent text-xl font-bold text-on-accent">
                    {{ monogram(u) }}
                  </span>
                </RouterLink>
                <div class="min-w-0">
                  <RouterLink :to="`/${u.username}`" class="truncate text-lg font-bold text-ink group-hover:text-accent">
                    {{ userName(u) }}
                  </RouterLink>
                  <div class="truncate text-xs text-faint">@{{ u.username }}</div>
                </div>
              </div>

              <p v-if="u.bio" class="line-clamp-2 min-h-10 text-sm leading-relaxed text-muted">{{ u.bio }}</p>
              <p v-else class="min-h-10 text-sm text-faint">这位博主还没有写简介。</p>

              <div class="mt-auto flex items-center justify-between">
                <span class="rounded-tag bg-accent/10 px-2 py-0.5 text-xs text-accent">{{ u.article_count }} 篇文章</span>
                <button
                  class="rounded-tag border border-line px-3 py-1 text-xs font-bold text-body transition hover:border-accent hover:text-accent"
                  @click="onFollow(u)"
                >+ 关注</button>
              </div>
            </div>
          </div>
        </section>

    <!-- 热门文章榜 -->
        <section v-if="hot.length" class="mt-14">
          <div class="mb-5 flex items-end justify-between">
            <div>
              <h2 class="section-title">热门文章</h2>
              <p class="mt-1 text-sm text-muted">评论最多的热议文章</p>
            </div>
          </div>
          <div class="grid gap-4 md:grid-cols-2">
            <RouterLink
              v-for="(a, i) in hot"
              :key="a.article_id"
              :to="`/article/${a.article_id}`"
              class="group flex items-center gap-4 rounded-card border border-line bg-card p-4 transition-shadow hover:shadow-lg"
            >
              <span class="hot-rank" :class="i < 3 ? 'hot-rank-top' : ''">{{ i + 1 }}</span>
              <div class="min-w-0 flex-1">
                <h3 class="truncate font-bold text-ink group-hover:text-accent">{{ a.title }}</h3>
                <p class="mt-1 truncate text-xs text-faint">
                  {{ a.author_name }}<span v-if="a.category_names?.length"> · {{ a.category_names.join(' / ') }}</span>
                </p>
              </div>
              <span class="shrink-0 rounded-tag bg-accent/10 px-2.5 py-1 text-xs font-bold text-accent">
                💬 {{ a.comment_count }} 评论
              </span>
            </RouterLink>
          </div>
        </section>


      </template>
    </main>

    <SiteFooter />
  </div>
</template>

<style scoped>
@reference "../style.css";

/* ---- 顶部导航 ---- */
.hero-topbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 20;
}
.hero-logo {
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
.hero-nav-link {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.8);
  transition: background-color 0.2s ease, color 0.2s ease;
}
.hero-nav-link:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}
.hero-nav-active {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

/* ---- Hero（与个人主页同风格：动态渐变 + 光斑 + 粒子） ---- */
.home-hero {
  position: relative;
  padding: 96px 0 0;
  color: #fff;
  background: linear-gradient(135deg, #2c3e50, #405f7d, #1e3a5f, #4a6b8a, #2c3e50);
  background-size: 300% 300%;
  animation: heroGradient 8s ease infinite;
  overflow: hidden;
}
@keyframes heroGradient {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

.home-hero::before {
  content: '';
  position: absolute;
  right: -80px;
  top: -80px;
  width: 380px;
  height: 380px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.13) 0%, transparent 70%);
  animation: heroOrb1 6s ease-in-out infinite;
}
.home-hero::after {
  content: '';
  position: absolute;
  left: -60px;
  bottom: -120px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.09) 0%, transparent 70%);
  animation: heroOrb2 7s ease-in-out infinite;
}
@keyframes heroOrb1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(-46px, 34px) scale(1.25); }
}
@keyframes heroOrb2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(38px, -24px) scale(1.2); }
}

/* 粒子（轻量版：比个人主页少而淡） */
.hero-particle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  pointer-events: none;
  z-index: 1;
}
.hero-particle.hp1 {
  width: 9px;
  height: 9px;
  left: 12%;
  top: 22%;
  animation: floatParticle 4.5s ease-in-out infinite;
}
.hero-particle.hp2 {
  width: 6px;
  height: 6px;
  right: 14%;
  top: 26%;
  animation: floatParticle 5.5s ease-in-out 0.8s infinite;
}
.hero-particle.hp3 {
  width: 10px;
  height: 10px;
  right: 22%;
  bottom: 18%;
  animation: floatParticle 6s ease-in-out 1.6s infinite;
}
.hero-particle.hp4 {
  width: 5px;
  height: 5px;
  left: 24%;
  bottom: 24%;
  animation: floatParticle 5s ease-in-out 2.2s infinite;
}
@keyframes floatParticle {
  0%, 100% { transform: translateY(0) scale(1); opacity: 0.45; }
  50% { transform: translateY(-22px) scale(1.25); opacity: 0.9; }
}

/* ---- 区块标题 ---- */
.section-title {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--color-ink);
}
.section-more {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-accent);
  white-space: nowrap;
}
.section-more:hover {
  color: var(--color-accent-strong);
}

/* ---- 热门榜排名徽标 ---- */
.hot-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  flex-shrink: 0;
  background: var(--color-zinc-100);
  color: var(--color-muted);
  font-size: 15px;
  font-weight: 800;
}
.hot-rank-top {
  background: linear-gradient(135deg, #f6b93b, #e58e26);
  color: #fff;
  box-shadow: 0 4px 10px rgba(229, 142, 38, 0.35);
}
</style>
