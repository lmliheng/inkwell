<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticle } from '@/api/article'
import { renderMarkdown } from '@/utils/markdown'
import { formatDate } from '@/utils/format'
import { useToastStore } from '@/stores/toast'
import { useAuthorStore } from '@/stores/author'
import { useAuthStore } from '@/stores/auth'
import CommentSection from '@/components/CommentSection.vue'
import ShareModal from '@/components/ShareModal.vue'
import AdSlot from '@/components/AdSlot.vue'
import { toggleLike, toggleFavorite, getSocialStatus } from '@/api/social'
import http from '@/api/http'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()
const authorStore = useAuthorStore()
const auth = useAuthStore()

const article = ref(null)
const loading = ref(true)
const notFound = ref(false)

const html = computed(() => renderMarkdown(article.value?.content))
const shareVisible = ref(false)

// ===== 点赞 / 收藏 =====
const liked = ref(false)
const favorited = ref(false)
const likeCount = ref(0)
const favCount = ref(0)
const actionLoading = ref(false)

const loadSocialStatus = async () => {
  if (!auth.isLoggedIn || !article.value) return
  try {
    const res = await getSocialStatus([article.value.article_id])
    if (res && res.data) {
      liked.value = res.data.likes.includes(Number(article.value.article_id))
      favorited.value = res.data.favorites.includes(Number(article.value.article_id))
    }
  } catch (e) {
    // 状态查询失败不阻塞
  }
}

const doLike = async () => {
  if (!auth.isLoggedIn) {
    toast.error('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  actionLoading.value = true
  try {
    const res = await toggleLike(article.value.article_id)
    // social.js 已解包 body，res 即 { liked, count }
    liked.value = !!res?.liked
    likeCount.value = Number(res?.count) || 0
  } catch (e) {
    toast.error(e?.response?.data?.message || e.message || '操作失败')
  } finally {
    actionLoading.value = false
  }
}

const doFavorite = async () => {
  if (!auth.isLoggedIn) {
    toast.error('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  actionLoading.value = true
  try {
    const res = await toggleFavorite(article.value.article_id)
    // social.js 已解包 body，res 即 { favorited, count }
    favorited.value = !!res?.favorited
    favCount.value = Number(res?.count) || 0
    toast.success(res?.favorited ? '已收藏' : '已取消收藏')
  } catch (e) {
    toast.error(e?.response?.data?.message || e.message || '操作失败')
  } finally {
    actionLoading.value = false
  }
}

// ===== AI 总结（打字机逐字展示已存内容） =====
const aiOpen = ref(false)
const aiSummary = ref(null)
const aiTyping = ref(false)
const aiShowSummary = ref('')
const aiKeyPoints = ref([])
const aiShowAnalysis = ref([])
const aiShowAdvice = ref([])
const aiGenerator = ref(null)

// 打开面板：已预生成则打字机展示；否则请求生成接口
const openAiSummary = async () => {
  aiOpen.value = true
  if (article.value?.ai_summary) {
    startTyping(article.value.ai_summary)
    return
  }
  aiTyping.value = true
  aiShowSummary.value = 'AI 总结生成中…'
  try {
    const res = await http.post(`/article/ai-summary/regenerate/${article.value.article_id}`)
    const s = res?.data?.ai_summary
    if (s) {
      article.value.ai_summary = s
      startTyping(s)
    } else {
      aiShowSummary.value = '暂无 AI 总结'
      aiTyping.value = false
    }
  } catch (e) {
    aiShowSummary.value = e?.response?.data?.message || 'AI 总结生成失败'
    aiTyping.value = false
  }
}

const startTyping = (s) => {
  aiSummary.value = s
  aiTyping.value = true
  aiShowSummary.value = ''
  aiKeyPoints.value = []
  aiShowAnalysis.value = []
  aiShowAdvice.value = []
  if (aiGenerator.value) {
    clearInterval(aiGenerator.value)
    aiGenerator.value = null
  }
  const kp = s.key_points || []
  const an = s.analysis || []
  const av = s.advice || []
  // 逐字推进：摘要单行，其余按行组依次展开
  let summaryChars = 0
  const totalKp = kp.length
  const totalAn = an.length
  let curKp = 0
  let curAn = 0
  let curAv = 0
  // 行内进度
  let kpIdx = 0
  let anIdx = 0
  let avIdx = 0
  const tick = () => {
    if (summaryChars < s.summary.length) {
      summaryChars = Math.min(summaryChars + 3, s.summary.length)
      aiShowSummary.value = s.summary.slice(0, summaryChars)
      return
    }
    if (curKp < totalKp) {
      kpIdx = Math.min(kpIdx + 2, kp[curKp].length)
      aiKeyPoints.value[curKp] = kp[curKp].slice(0, kpIdx)
      aiKeyPoints.value = [...aiKeyPoints.value]
      if (kpIdx >= kp[curKp].length) { curKp++; kpIdx = 0 }
      return
    }
    if (curAn < totalAn) {
      anIdx = Math.min(anIdx + 2, an[curAn].length)
      aiShowAnalysis.value[curAn] = an[curAn].slice(0, anIdx)
      aiShowAnalysis.value = [...aiShowAnalysis.value]
      if (anIdx >= an[curAn].length) { curAn++; anIdx = 0 }
      return
    }
    if (curAv < av.length) {
      avIdx = Math.min(avIdx + 2, av[curAv].length)
      aiShowAdvice.value[curAv] = av[curAv].slice(0, avIdx)
      aiShowAdvice.value = [...aiShowAdvice.value]
      if (avIdx >= av[curAv].length) { curAv++; avIdx = 0 }
      return
    }
    aiTyping.value = false
    clearInterval(aiGenerator.value)
    aiGenerator.value = null
  }
  aiGenerator.value = setInterval(tick, 16)
}

const closeAiSummary = () => {
  aiOpen.value = false
  if (aiGenerator.value) {
    clearInterval(aiGenerator.value)
    aiGenerator.value = null
  }
}

onMounted(async () => {
  loading.value = true
  try {
    article.value = await getArticle(route.params.id)
    // 把文章作者写入全局作者上下文，供左侧 Sidebar 展示该作者信息
    authorStore.setAuthor({
      username: article.value.author_username || '',
      name: article.value.author_name || '',
      avatar: article.value.author_avatar || '',
      bio: article.value.author_bio || '',
    })
    // tab 标题 + 头像 favicon（跟随文章作者）
    const authorDisplay = article.value.author_name || article.value.author_username || ''
    document.title = authorDisplay ? `${authorDisplay}的博客` : '博客'
    if (article.value.author_avatar) {
      setFavicon(article.value.author_avatar)
    }
    loadSocialStatus()
  } catch (e) {
    notFound.value = true
    toast.error(e.message || '文章加载失败')
  } finally {
    loading.value = false
    await nextTick()
    enhanceCodeBlocks()
    bindCodeEvents()
  }
})

// 内容变化（如后续切换文章）时重新增强代码块
watch(html, async () => {
  await nextTick()
  enhanceCodeBlocks()
})

// 给 .prose pre.hljs 包上 mac 风格标题栏，并处理折叠/复制
function enhanceCodeBlocks() {
  document.querySelectorAll('.prose pre.hljs').forEach((pre) => {
    if (pre.closest('.code-block')) return
    const wrapper = document.createElement('div')
    wrapper.className = 'code-block'
    const code = pre.querySelector('code')
    const lang = pre.getAttribute('data-lang') || 'text'
    const lines = code ? code.textContent.split('\n').filter((l) => l.trim()).length : 0

    const bar = document.createElement('div')
    bar.className = 'code-bar'
    bar.innerHTML =
      '<span class="code-dots"><i class="dot-r"></i><i class="dot-y"></i><i class="dot-g"></i></span>' +
      '<span class="code-lang"></span>' +
      '<span class="code-actions">' +
      (lines >= 6 ? '<button class="code-fold" type="button">折叠</button>' : '') +
      '<button class="code-copy" type="button">复制</button>' +
      '</span>'
    bar.querySelector('.code-lang').textContent = lang

    pre.parentNode.insertBefore(wrapper, pre)
    wrapper.appendChild(bar)
    wrapper.appendChild(pre)
  })
}

// 复制文本（clipboard API + 降级 execCommand）
function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) {
    return navigator.clipboard.writeText(text)
  }
  return new Promise((resolve, reject) => {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy') ? resolve() : reject(new Error('copy failed'))
    } catch (e) {
      reject(e)
    } finally {
      document.body.removeChild(ta)
    }
  })
}

// 复制 + 折叠（事件委托，一次绑定）
function bindCodeEvents() {
  const proseEl = document.querySelector('.prose')
  if (!proseEl) return
  proseEl.addEventListener('click', (e) => {
    const foldBtn = e.target.closest('.code-fold')
    if (foldBtn) {
      const block = foldBtn.closest('.code-block')
      block.classList.toggle('folded')
      foldBtn.textContent = block.classList.contains('folded') ? '展开' : '折叠'
      return
    }
    const copyBtn = e.target.closest('.code-copy')
    if (copyBtn) {
      const block = copyBtn.closest('.code-block')
      const code = block.querySelector('pre code')
      const text = code ? code.textContent : ''
      copyText(text)
        .then(() => {
          copyBtn.textContent = '已复制 ✓'
          setTimeout(() => (copyBtn.textContent = '复制'), 1500)
        })
        .catch(() => {})
    }
  })
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

onBeforeUnmount(() => {
  authorStore.clear()
  const link = document.querySelector("link[rel='icon']")
  if (link) link.href = '/vite.svg'
})
</script>

<template>
  <div class="mx-auto max-w-3xl px-4 py-8">
    <p v-if="loading" class="py-16 text-center text-faint">加载中…</p>

    <div v-else-if="notFound || !article" class="card py-16 text-center">
      <p class="text-faint">文章不存在或未公开</p>
      <RouterLink to="/" class="mt-4 inline-block text-sm text-accent hover:text-accent-strong">返回首页</RouterLink>
    </div>

    <article v-else>
      <header class="mb-8">
        <h1 class="text-3xl font-bold leading-tight tracking-tight text-ink">{{ article.title }}</h1>
        <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
          <div class="flex flex-wrap items-center gap-3 text-sm text-muted">
            <span class="font-medium text-body">{{ article.author_name || '匿名' }}</span>
            <span class="text-faint">·</span>
            <time>{{ formatDate(article.created_at) }}</time>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <!-- 点赞 -->
            <button
              class="action-btn"
              :class="{ 'action-btn-active': liked }"
              type="button"
              :disabled="actionLoading"
              :title="liked ? '取消点赞' : '点赞'"
              @click="doLike"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M7 10v12M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z" />
              </svg>
              <span>{{ liked ? '已赞' : '点赞' }}</span>
              <span v-if="likeCount" class="action-count">{{ likeCount }}</span>
            </button>
            <!-- 收藏 -->
            <button
              class="action-btn"
              :class="{ 'action-btn-active': favorited }"
              type="button"
              :disabled="actionLoading"
              :title="favorited ? '取消收藏' : '收藏'"
              @click="doFavorite"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
              </svg>
              <span>{{ favorited ? '已收藏' : '收藏' }}</span>
              <span v-if="favCount" class="action-count">{{ favCount }}</span>
            </button>
            <button class="share-icon-btn" type="button" title="分享" aria-label="分享" @click="shareVisible = true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="18" cy="5" r="3" />
                <circle cx="6" cy="12" r="3" />
                <circle cx="18" cy="19" r="3" />
                <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
                <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
              </svg>
            </button>
          </div>
        </div>
        <div v-if="(article.category_names || []).length" class="mt-4 flex flex-wrap gap-2">
          <span
            v-for="name in article.category_names"
            :key="name"
            class="rounded-full bg-card px-3 py-1 text-sm text-muted"
          >
            {{ name }}
          </span>
        </div>
      </header>

      <!-- 正文顶部广告位 -->
      <AdSlot position="article_top" />

      <!-- Markdown 正文 -->
      <div class="prose" v-html="html"></div>

      <!-- AI 总结 -->
      <div class="mt-10">
        <button v-if="!aiOpen" class="ai-summary-toggle" type="button" @click="openAiSummary">
          <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 2a7 7 0 0 0-4 12.7V17h8v-2.3A7 7 0 0 0 12 2zM9 20h6M10 23h4" />
          </svg>
          AI 总结 · 文章导读
        </button>

        <div v-else class="ai-summary-panel">
          <div class="ai-summary-head">
            <span class="ai-summary-title">
              <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M12 2a7 7 0 0 0-4 12.7V17h8v-2.3A7 7 0 0 0 12 2zM9 20h6M10 23h4" />
              </svg>
              AI 总结
            </span>
            <button class="ai-close-btn" type="button" aria-label="收起" @click="closeAiSummary">
              <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="ai-summary-body">
            <template v-if="aiShowSummary">
              <p class="ai-summary-text">
                <span class="ai-section-tag">概述</span>{{ aiShowSummary }}
                <span v-if="aiTyping" class="ai-cursor"></span>
              </p>
            </template>

            <template v-if="aiKeyPoints.length">
              <div class="ai-section-title">核心要点</div>
              <ul class="ai-list">
                <li v-for="(kp, i) in aiKeyPoints" :key="'kp' + i">{{ kp }}</li>
              </ul>
            </template>

            <template v-if="aiShowAnalysis.length">
              <div class="ai-section-title">分析评估</div>
              <ul class="ai-list">
                <li v-for="(a, i) in aiShowAnalysis" :key="'an' + i">{{ a }}</li>
              </ul>
            </template>

            <template v-if="aiShowAdvice.length">
              <div class="ai-section-title">读者建议</div>
              <ul class="ai-list">
                <li v-for="(a, i) in aiShowAdvice" :key="'av' + i">{{ a }}</li>
              </ul>
            </template>

            <p v-if="aiTyping && !aiShowSummary" class="ai-loading">生成中…</p>
          </div>
          <div v-if="aiSummary && !aiTyping" class="ai-summary-foot">本文 AI 导读由 DeepSeek 生成</div>
        </div>
      </div>

      <!-- 评论区上方广告位 -->
      <AdSlot position="article_bottom" />

      <CommentSection :article-id="article.article_id" />
    </article>

    <ShareModal v-if="shareVisible" :article="article" @close="shareVisible = false" />
  </div>
</template>

<style scoped>
@reference "../style.css";
.card {
  @apply bg-card border border-line rounded-card p-6;
}

.share-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: transparent;
  border: none;
  color: var(--color-muted);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}
.share-icon-btn:hover {
  background-color: color-mix(in oklab, var(--color-accent) 10%, transparent);
  color: var(--color-accent);
}
.share-icon-btn svg {
  width: 18px;
  height: 18px;
}

/* 点赞 / 收藏按钮 */
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid var(--color-line);
  background: transparent;
  color: var(--color-muted);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;
}
.action-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
.action-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.action-btn svg {
  width: 16px;
  height: 16px;
}
.action-count {
  font-size: 12px;
  font-weight: 700;
  opacity: 0.85;
}
.action-btn-active {
  border-color: var(--color-accent);
  background-color: color-mix(in oklab, var(--color-accent) 10%, transparent);
  color: var(--color-accent);
}

/* ---- AI 总结 ---- */
.ai-summary-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #2c3e50, #405f7d);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(44, 62, 80, 0.3);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.ai-summary-toggle:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(44, 62, 80, 0.4);
}
.ai-summary-panel {
  border: 1px solid var(--color-line);
  border-radius: 14px;
  background: linear-gradient(160deg, color-mix(in oklab, var(--color-accent) 5%, transparent), transparent 40%);
  overflow: hidden;
}
.ai-summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-line);
}
.ai-summary-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 800;
  color: var(--color-ink);
}
.ai-close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
}
.ai-close-btn:hover {
  background: color-mix(in oklab, var(--color-accent) 10%, transparent);
  color: var(--color-accent);
}
.ai-summary-body {
  padding: 16px 18px 8px;
}
.ai-summary-text {
  font-size: 14px;
  line-height: 1.9;
  color: var(--color-body);
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-section-tag {
  display: inline-block;
  margin-right: 8px;
  padding: 1px 8px;
  border-radius: 4px;
  background: color-mix(in oklab, var(--color-accent) 14%, transparent);
  color: var(--color-accent);
  font-size: 12px;
  font-weight: 700;
  vertical-align: 1px;
}
.ai-section-title {
  margin-top: 14px;
  font-size: 13px;
  font-weight: 800;
  color: var(--color-ink);
}
.ai-list {
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
}
.ai-list li {
  position: relative;
  padding-left: 16px;
  margin: 4px 0;
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--color-body);
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-list li::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 10px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-accent);
  opacity: 0.55;
}
.ai-cursor {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--color-accent);
  animation: aiBlink 0.8s step-end infinite;
}
@keyframes aiBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
.ai-loading {
  padding: 8px 0 14px;
  font-size: 13px;
  color: var(--color-faint);
}
.ai-summary-foot {
  padding: 6px 18px 12px;
  font-size: 11px;
  color: var(--color-faint);
  text-align: right;
}
</style>
