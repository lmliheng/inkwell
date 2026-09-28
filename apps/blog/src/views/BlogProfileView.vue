<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBlogProfile } from '@/api/blog'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import ArticleCard from '@/components/ArticleCard.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import {
  toggleFollow,
  getFollowing,
  getFollowers,
  getSocialStats,
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
} from '@/api/social'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToastStore()

const profile = ref(null) // data.user
const articles = ref([])
const total = ref(0)
const allTotal = ref(0) // 用户全部已发布文章数（「查看全部」入口）
const loading = ref(true) // 初始加载中，避免首帧渲染 profile 为 null
const notFound = ref(false)

const username = computed(() => String(route.params.username || ''))

const displayName = computed(
  () => (profile.value && (profile.value.name || profile.value.username)) || username.value || '用户',
)
const avatar = computed(() => (profile.value && profile.value.avatar) || '')
const bio = computed(() => (profile.value && profile.value.bio) || '')
const area = computed(() => (profile.value && profile.value.area) || '')
const vip = computed(() => Number(profile.value && profile.value.vip) || 0)
const monogram = computed(() => displayName.value.trim().charAt(0).toUpperCase())

// 是否当前登录用户自己的主页
const isSelf = computed(
  () => auth.isLoggedIn && username.value && username.value === auth.username,
)

// ===== 关注 / 粉丝 / 获赞统计 =====
const socialStats = ref({ following: 0, followers: 0, liked: 0, isFollowing: false })
const followingList = ref([])
const followersList = ref([])
const socialModal = ref(false) // 'following' | 'followers' | null
const socialModalMode = ref(null)
const socialModalLoading = ref(false)

const fetchSocialStats = async () => {
  try {
    const res = await getSocialStats(username.value)
    // social.js 已解包 body，res 即 stats 对象
    if (res) {
      socialStats.value = {
        following: Number(res.following) || 0,
        followers: Number(res.followers) || 0,
        liked: Number(res.liked) || 0,
        isFollowing: !!res.isFollowing,
      }
    }
  } catch (e) {
    // 统计加载失败不阻塞主页
  }
}

const openSocialModal = async (mode) => {
  if (!socialStats.value.following && !socialStats.value.followers) return
  socialModalMode.value = mode
  socialModal.value = true
  socialModalLoading.value = true
  try {
    if (mode === 'following') {
      const res = await getFollowing(username.value)
      followingList.value = (res && res.list) || []
    } else {
      const res = await getFollowers(username.value)
      followersList.value = (res && res.list) || []
    }
  } catch (e) {
    toast.error(e?.response?.data?.message || '加载失败')
  } finally {
    socialModalLoading.value = false
  }
}

const doFollow = async () => {
  if (!auth.isLoggedIn) {
    toast.error('请先登录')
    router.push('/login')
    return
  }
  if (isSelf.value) return
  try {
    const res = await toggleFollow(username.value)
    // social.js 已解包 body，res 即 { following }
    if (res) {
      socialStats.value.isFollowing = !!res.following
      socialStats.value.followers = Math.max(
        0,
        socialStats.value.followers + (res.following ? 1 : -1),
      )
      toast.success(res.following ? '关注成功' : '已取消关注')
    }
  } catch (e) {
    toast.error(e?.response?.data?.message || '操作失败')
  }
}

// ===== 互动通知（铃铛） =====
const notifOpen = ref(false)
const notifications = ref([])
const unreadCount = ref(0)
const notifLoading = ref(false)

const fetchNotifications = async () => {
  if (!auth.isLoggedIn) return
  notifLoading.value = true
  try {
    const res = await getNotifications({ page: 1, pageSize: 20 })
    notifications.value = (res && res.list) || []
  } catch (e) {
    notifications.value = []
  } finally {
    notifLoading.value = false
  }
}

const fetchUnreadCount = async () => {
  if (!auth.isLoggedIn) return
  try {
    const res = await getUnreadCount()
    unreadCount.value = (res && Number(res.count)) || 0
  } catch (e) {
    unreadCount.value = 0
  }
}

const toggleNotif = () => {
  if (!auth.isLoggedIn) return
  notifOpen.value = !notifOpen.value
  if (notifOpen.value) fetchNotifications()
}

const markAllRead = async () => {
  try {
    await markAllNotificationsRead()
    unreadCount.value = 0
    notifications.value = notifications.value.map((n) => ({ ...n, is_read: 1 }))
    toast.success('已全部标记为已读')
  } catch (e) {
    toast.error(e?.response?.data?.message || '操作失败')
  }
}

const notifText = (n) => {
  const actor = n.actor_name || n.actor_username || '有人'
  if (n.type === 'follow') return `${actor} 关注了你`
  if (n.type === 'like') return `${actor} ${n.content || '点赞了你的文章'}`
  if (n.type === 'favorite') return `${actor} ${n.content || '收藏了你的文章'}`
  return n.content || ''
}

const formatNotifTime = (v) => {
  if (!v) return ''
  const d = new Date(String(v).replace('T', ' ').replace(/-/g, '/'))
  const now = Date.now()
  const diff = Math.floor((now - d.getTime()) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} 天前`
  return String(v).slice(0, 10)
}

// ===== 社交媒体（URL 自动识别类型 + 品牌图标） =====
import {
  siGithub, siTelegram, siQq, siWechat, siLeetcode,
  siNpm, siBilibili, siRss, siGmail, siZhihu, siDouban,
} from 'simple-icons'

const PHONE_PATH =
  'M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92z'
const LINK_PATH =
  'M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71'
// 中南大学（CSU）校徽风格盾形
const CSU_PATH =
  'M12 2l7 2.5v7c0 4.5-3 7.6-7 9.5-4-1.9-7-5-7-9.5v-7L12 2zm0 2.6L8.2 6.7V11c0 3.1 1.6 5.2 3.8 6.5 2.2-1.3 3.8-3.4 3.8-6.5V6.7L12 4.6z'

const ICON_MAP = {
  github: { label: 'GitHub', path: siGithub.path },
  telegram: { label: 'Telegram', path: siTelegram.path },
  qq: { label: 'QQ', path: siQq.path },
  wechat: { label: '微信', path: siWechat.path },
  leetcode: { label: '力扣', path: siLeetcode.path },
  npm: { label: 'npm', path: siNpm.path },
  bilibili: { label: 'B站', path: siBilibili.path },
  rss: { label: 'RSS', path: siRss.path },
  email: { label: '邮箱', path: siGmail.path },
  phone: { label: '电话', path: PHONE_PATH },
  zhihu: { label: '知乎', path: siZhihu.path },
  douban: { label: '豆瓣', path: siDouban.path },
  csu: { label: '中南大学', path: CSU_PATH },
  custom: { label: '链接', path: LINK_PATH },
}
const socialIconPath = (t) => (ICON_MAP[t] || ICON_MAP.custom).path
const socialLabel = (t) => (ICON_MAP[t] || ICON_MAP.custom).label

// 根据 URL 自动识别社交类型
function detectSocialType(url) {
  const u = String(url || '').toLowerCase()
  if (u.includes('github')) return 'github'
  if (u.includes('t.me') || u.includes('telegram')) return 'telegram'
  if (u.includes('qq.com')) return 'qq'
  if (u.includes('weixin') || u.includes('wechat')) return 'wechat'
  if (u.includes('leetcode')) return 'leetcode'
  if (u.includes('npmjs')) return 'npm'
  if (u.includes('bilibili')) return 'bilibili'
  if (u.includes('zhihu')) return 'zhihu'
  if (u.includes('douban')) return 'douban'
  if (u.includes('csu.edu.cn') || u.includes('csu')) return 'csu'
  if (u.startsWith('tel:')) return 'phone'
  if (u.startsWith('mailto:')) return 'email'
  if (u.includes('rss') || u.includes('feed')) return 'rss'
  return 'custom'
}

// 主页展示（公开）
const socialsDisplay = computed(() => profile.value?.socials || [])

// 点击社交项：图片类型弹二维码大图，否则跳转
const qrVisible = ref(false)
const qrUrl = ref('')
const openSocial = (s) => {
  if (s.image || (s.type === 'wechat' && /^https?:\/\//.test(s.url) && !/^(www\.)?(weixin|wechat)/.test(s.url))) {
    qrUrl.value = s.url
    qrVisible.value = true
    return
  }
  window.open(s.url, '_blank', 'noopener')
}

async function fetchProfile() {
  if (!username.value) return
  loading.value = true
  notFound.value = false
  try {
    const data = await getBlogProfile(username.value, { page: 1, pageSize: 4 })
    profile.value = (data && data.user) || null
    const arts = (data && data.articles) || {}
    articles.value = (arts && arts.list) || []
    total.value = Number(arts && arts.total) || 0
    allTotal.value = Number(data && data.all_total) || total.value
    document.title = `${displayName.value}的博客`
    if (profile.value && profile.value.avatar) {
      setFavicon(profile.value.avatar)
    }
  } catch (e) {
    notFound.value = true
    profile.value = null
    articles.value = []
    total.value = 0
    toast.error(e.message || '主页加载失败')
  } finally {
    loading.value = false
  }
}

function logout() {
  auth.logout()
  router.push('/')
}

function setFavicon(url) {
  if (!url) return
  let link = document.querySelector("link[rel='icon']")
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.href = url
}

watch(username, () => {
  fetchProfile()
  fetchSocialStats()
})

onMounted(() => {
  fetchProfile()
  fetchSocialStats()
  fetchUnreadCount()
})

onBeforeUnmount(() => {
  const link = document.querySelector("link[rel='icon']")
  if (link) link.href = '/vite.svg'
})
</script>

<template>
  <div class="min-h-screen bg-page text-body antialiased">
    <!-- 顶部导航条 -->
    <header class="profile-topbar">
      <div class="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
        <RouterLink to="/" class="ghost-btn">← 返回首页</RouterLink>
        <div class="flex items-center gap-2">
          <!-- 通知铃铛（登录后） -->
          <div v-if="auth.isLoggedIn" class="relative">
            <button class="ghost-btn relative" aria-label="通知" @click="toggleNotif">
              <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M15 17h5l-1.4-1.4A2 2 0 0118 14.2V11a6 6 0 10-12 0v3.2c0 .53-.21 1.04-.6 1.4L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
              <span v-if="unreadCount > 0" class="notif-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </button>
            <!-- 通知下拉 -->
            <div v-if="notifOpen" class="notif-panel" @click.stop>
              <div class="notif-panel-head">
                <span class="font-bold">通知</span>
                <button v-if="unreadCount > 0" class="notif-read-all" @click="markAllRead">全部已读</button>
              </div>
              <div v-if="notifLoading" class="notif-empty">加载中…</div>
              <div v-else-if="notifications.length" class="notif-list">
                <RouterLink
                  v-for="n in notifications"
                  :key="n.id"
                  :to="n.article_id ? `/article/${n.article_id}` : `/${n.actor_username}`"
                  class="notif-item"
                  :class="{ 'notif-unread': !n.is_read }"
                  @click="notifOpen = false"
                >
                  <span class="min-w-0 flex-1 truncate text-sm">{{ notifText(n) }}</span>
                  <span class="shrink-0 text-xs text-faint">{{ formatNotifTime(n.created_at) }}</span>
                </RouterLink>
              </div>
              <div v-else class="notif-empty">暂无通知</div>
            </div>
          </div>
          <template v-if="auth.isLoggedIn">
            <button class="ghost-btn" @click="logout">退出登录</button>
          </template>
          <template v-else>
            <RouterLink to="/login" class="ghost-btn">登录</RouterLink>
            <RouterLink to="/register" class="btn-accent">注册</RouterLink>
          </template>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-5xl px-4 pb-12">
      <!-- 加载中 -->
      <p v-if="loading" class="py-16 text-center text-faint">加载中…</p>

      <!-- 用户不存在 -->
      <div v-else-if="notFound" class="card py-16 text-center">
        <p class="text-lg font-bold text-ink">用户不存在</p>
        <p class="mt-2 text-sm text-muted">没有找到名为「{{ username }}」的博客主页。</p>
        <RouterLink to="/" class="btn-accent mt-6">返回首页</RouterLink>
      </div>

      <template v-else>
        <!-- Hero 横幅 -->
        <section class="profile-hero">
          <span class="hero-particle p1"></span>
          <span class="hero-particle p2"></span>
          <span class="hero-particle p3"></span>

          <div class="hero-avatar-wrap">
            <img v-if="avatar" :src="avatar" :alt="displayName" class="hero-avatar" />
            <span v-else class="hero-avatar hero-monogram">{{ monogram }}</span>
          </div>

          <h1 class="hero-name">{{ displayName }}</h1>
          <p class="hero-username">
            @{{ username }}<span v-if="area" class="hero-username-area"> · {{ area }}</span>
          </p>
          <p v-if="bio" class="hero-bio">{{ bio }}</p>

          <!-- 关注/粉丝/获赞统计 -->
          <div class="hero-stats">
            <button class="hero-stat" :disabled="!socialStats.following" @click="openSocialModal('following')">
              <span class="hero-stat-num">{{ socialStats.following }}</span>
              <span class="hero-stat-label">关注</span>
            </button>
            <button class="hero-stat" :disabled="!socialStats.followers" @click="openSocialModal('followers')">
              <span class="hero-stat-num">{{ socialStats.followers }}</span>
              <span class="hero-stat-label">粉丝</span>
            </button>
            <span class="hero-stat">
              <span class="hero-stat-num">{{ socialStats.liked }}</span>
              <span class="hero-stat-label">获赞</span>
            </span>
          </div>

          <!-- 关注按钮 / 我的收藏 / 私信（本人） -->
          <div v-if="isSelf" class="hero-actions hero-actions-wrap">
            <RouterLink to="/me/favorites" class="hero-btn-ghost">☆ 我的收藏</RouterLink>
            <RouterLink to="/assistant" class="hero-btn-ghost">✉ 消息中心</RouterLink>
          </div>
          <div v-else class="hero-actions hero-actions-wrap">
            <button
              class="hero-btn"
              :class="socialStats.isFollowing ? 'hero-btn-following' : ''"
              @click="doFollow"
            >
              {{ socialStats.isFollowing ? '已关注' : '+ 关注' }}
            </button>
            <RouterLink
              v-if="auth.isLoggedIn"
              :to="{ path: '/assistant', query: { dmto: profile?.id || '', dmname: profile?.username || username } }"
              class="hero-btn-ghost"
            >✉ 发私信</RouterLink>
          </div>

          <!-- 社交链接 -->
          <div v-if="socialsDisplay.length" class="hero-socials">
            <a
              v-for="(s, i) in socialsDisplay"
              :key="i"
              :href="s.image ? undefined : s.url"
              :target="s.image ? undefined : '_blank'"
              :rel="s.image ? undefined : 'noopener noreferrer'"
              class="hero-social-btn"
              :title="s.label || s.url"
              :data-label="s.label || s.url"
              @click.prevent="openSocial(s)"
            >
              <svg class="social-svg" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="socialIconPath(s.type)" fill="currentColor" />
              </svg>
            </a>
          </div>

          <!-- VIP 缎带横幅（挂在 Hero 顶部，被框边缘裁剪出"挂"的效果） -->
          <div v-if="vip" class="hero-ribbon" title="VIP 等级">
            <svg class="ribbon-svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <circle cx="12" cy="8" r="6" />
              <path d="M15.5 13.5 17 22l-5-3-5 3 1.5-8.5" />
            </svg>
            VIP {{ vip }}
          </div>
        </section>

        <!-- 文章区 -->
        <section class="mt-10">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="text-sm font-bold uppercase tracking-wide text-faint">
              {{ profile?.featured_articles?.length ? '精选文章' : '最新文章' }}
            </h2>
            <RouterLink
              v-if="allTotal > 4"
              :to="`/${username}/articles`"
              class="text-sm font-medium text-accent hover:text-accent-strong"
            >查看全部 {{ allTotal }} 篇 →</RouterLink>
          </div>
          <div v-if="articles.length" class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            <ArticleCard v-for="a in articles" :key="a.article_id" :article="a" compact />
          </div>
          <div v-else class="card py-16 text-center text-faint">还没有发布任何文章</div>
        </section>
      </template>
    </main>

    <!-- 底部注册引导（仅对访客展示） -->
    <footer v-if="!isSelf" class="mx-auto max-w-5xl px-4 pb-6 pt-2 text-center">
      <p class="text-sm text-muted">
        你也想创建你的博客？
        <RouterLink to="/register" class="font-medium text-accent hover:text-accent-strong">请注册JScreate吧~</RouterLink>
      </p>
    </footer>

    <SiteFooter />

    <!-- 关注/粉丝列表弹窗 -->
    <div
      v-if="socialModal"
      class="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4"
      @click.self="socialModal = false"
    >
      <div class="w-full max-w-md rounded-card border border-line bg-card p-5 max-h-[80vh] overflow-y-auto">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="text-lg font-bold text-ink">{{ socialModalMode === 'following' ? '关注' : '粉丝' }}</h3>
          <button class="ghost-btn" @click="socialModal = false">关闭</button>
        </div>
        <p v-if="socialModalLoading" class="py-8 text-center text-faint">加载中…</p>
        <div v-else-if="(socialModalMode === 'following' ? followingList : followersList).length" class="space-y-2">
          <RouterLink
            v-for="u in (socialModalMode === 'following' ? followingList : followersList)"
            :key="u.id"
            :to="`/${u.username}`"
            class="flex items-center gap-3 rounded-tag border border-line p-2.5 transition hover:border-accent"
            @click="socialModal = false"
          >
            <span v-if="u.avatar" class="h-10 w-10 shrink-0 overflow-hidden rounded-full">
              <img :src="u.avatar" :alt="u.name || u.username" class="h-full w-full object-cover" />
            </span>
            <span v-else class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent text-base font-bold text-on-accent">
              {{ (u.name || u.username || '?').charAt(0).toUpperCase() }}
            </span>
            <span class="min-w-0">
              <span class="block truncate text-sm font-bold text-ink">{{ u.name || u.username }}</span>
              <span class="block truncate text-xs text-faint">@{{ u.username }}</span>
            </span>
          </RouterLink>
        </div>
        <p v-else class="py-8 text-center text-faint">还没有{{ socialModalMode === 'following' ? '关注' : '粉丝' }}</p>
      </div>
    </div>

    <!-- 二维码大图弹窗 -->
    <div
      v-if="qrVisible"
      class="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4"
      @click.self="qrVisible = false"
    >
      <div class="rounded-card bg-card p-5 text-center">
        <img :src="qrUrl" alt="二维码" class="mx-auto max-h-[70vh] max-w-full rounded-tag" />
        <p class="mt-3 text-sm text-muted">长按保存 · 扫码添加</p>
        <button class="btn-accent mt-3" @click="qrVisible = false">关闭</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
@reference "../style.css";

.profile-topbar {
  position: sticky;
  top: 0;
  z-index: 40;
  background-color: var(--color-page);
  border-bottom: 1px solid var(--color-line);
}

.ghost-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem 1rem;
  border-radius: var(--radius-tag);
  border: 1px solid var(--color-line);
  font-size: 0.875rem;
  font-weight: 700;
  line-height: 1.25rem;
  color: var(--color-body);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}
.ghost-btn:hover {
  background-color: color-mix(in oklab, var(--color-accent) 8%, transparent);
  color: var(--color-accent);
}
.ghost-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

.card {
  @apply bg-card border border-line rounded-card p-6;
}

/* ---- Hero 横幅（动态渐变 + 漂浮光斑 + 浮动粒子） ---- */
.profile-hero {
  position: relative;
  margin-top: 24px;
  padding: 48px 32px 42px;
  border-radius: 18px;
  text-align: center;
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

.profile-hero::before {
  content: '';
  position: absolute;
  right: -60px;
  top: -60px;
  width: 340px;
  height: 340px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.16) 0%, transparent 70%);
  animation: heroOrb1 6s ease-in-out infinite;
}
.profile-hero::after {
  content: '';
  position: absolute;
  left: -40px;
  bottom: -80px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
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

/* 浮动粒子：分布在四角边缘，避开中央内容 */
.hero-particle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  pointer-events: none;
  z-index: 1;
}
.hero-particle.p1 {
  width: 10px;
  height: 10px;
  left: 9%;
  top: 18%;
  animation: floatParticle 4.5s ease-in-out infinite;
}
.hero-particle.p2 {
  width: 7px;
  height: 7px;
  right: 11%;
  top: 24%;
  animation: floatParticle 5.5s ease-in-out 0.8s infinite;
}
.hero-particle.p3 {
  width: 12px;
  height: 12px;
  right: 15%;
  bottom: 14%;
  animation: floatParticle 6s ease-in-out 1.6s infinite;
}
@keyframes floatParticle {
  0%, 100% { transform: translateY(0) scale(1); opacity: 0.5; }
  50% { transform: translateY(-22px) scale(1.25); opacity: 1; }
}

.hero-avatar-wrap {
  position: relative;
  display: inline-block;
  z-index: 1;
}
.hero-avatar {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
  background-color: #fff;
}
.hero-monogram {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  font-weight: 800;
  color: #34495e;
}

.hero-name {
  margin-top: 18px;
  font-size: 30px;
  font-weight: 800;
  color: #fff;
  position: relative;
  z-index: 1;
}
.hero-username {
  margin-top: 4px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
  position: relative;
  z-index: 1;
}
.hero-username-area {
  color: rgba(255, 255, 255, 0.55);
}

/* VIP 缎带横幅：斜挂在 Hero 顶部，上部被框顶边缘裁剪出"挂"的效果 */
.hero-ribbon {
  position: absolute;
  top: 15px;
  right: -50px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 70px;
  border-radius: 0 0 10px 10px;
  background: linear-gradient(135deg, #f6b93b, #e58e26);
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  white-space: nowrap;
  pointer-events: none;
  transform: rotate(45deg);
}
.ribbon-svg {
  width: 15px;
  height: 15px;
}
.hero-bio {
  margin: 14px auto 0;
  max-width: 560px;
  font-size: 14px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.85);
  position: relative;
  z-index: 1;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 社交图标行 */
.hero-socials {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 10px;
  position: relative;
  z-index: 1;
}
.hero-social-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.16);
  color: #fff;
  text-decoration: none;
  transition: background-color 0.2s ease, transform 0.2s ease;
}
.hero-social-btn:hover {
  background-color: rgba(255, 255, 255, 0.32);
  transform: translateY(-2px);
}
.hero-social-btn .social-svg {
  width: 18px;
  height: 18px;
}
/* 社交名 tip */
.hero-social-btn::after {
  content: attr(data-label);
  position: absolute;
  top: -28px;
  left: 50%;
  transform: translateX(-50%);
  background: #303133;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease;
  z-index: 10;
}
.hero-social-btn:hover::after {
  opacity: 1;
}

/* ---- 关注/粉丝/获赞统计 ---- */
.hero-stats {
  margin-top: 14px;
  display: flex;
  justify-content: center;
  gap: 22px;
  position: relative;
  z-index: 1;
}
.hero-stat {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  background: none;
  border: none;
  color: #fff;
  cursor: pointer;
  padding: 2px 6px;
}
.hero-stat:disabled {
  cursor: default;
}
.hero-stat-num {
  font-size: 18px;
  font-weight: 800;
  line-height: 1.1;
}
.hero-stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
}

/* ---- 关注按钮 / 我的收藏 ---- */
.hero-actions {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  position: relative;
  z-index: 1;
}
.hero-actions-wrap {
  gap: 10px;
  flex-wrap: wrap;
}
.hero-btn {
  padding: 8px 26px;
  border: none;
  border-radius: 999px;
  background: #fff;
  color: #2c3e50;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.2s ease, opacity 0.2s ease, box-shadow 0.2s ease;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.25);
}
.hero-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.3);
}
.hero-btn-following {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  box-shadow: none;
}
.hero-btn-ghost {
  padding: 8px 22px;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 999px;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  transition: background-color 0.2s ease;
}
.hero-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.15);
}

/* ---- 通知铃铛与下拉 ---- */
.notif-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 999px;
  background: #e74c3c;
  color: #fff;
  font-size: 10px;
  font-weight: 800;
  line-height: 17px;
  text-align: center;
}
.notif-panel {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  width: 320px;
  max-height: 420px;
  display: flex;
  flex-direction: column;
  background: var(--color-card);
  border: 1px solid var(--color-line);
  border-radius: 12px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.18);
  z-index: 60;
  overflow: hidden;
}
.notif-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-line);
  font-size: 14px;
  color: var(--color-ink);
}
.notif-read-all {
  font-size: 12px;
  color: var(--color-accent);
  background: none;
  border: none;
  cursor: pointer;
}
.notif-list {
  overflow-y: auto;
}
.notif-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-line);
  color: var(--color-body);
  text-decoration: none;
}
.notif-item:hover {
  background: color-mix(in oklab, var(--color-accent) 6%, transparent);
}
.notif-unread {
  background: color-mix(in oklab, var(--color-accent) 8%, transparent);
}
.notif-empty {
  padding: 24px 14px;
  text-align: center;
  font-size: 13px;
  color: var(--color-faint);
}
</style>
