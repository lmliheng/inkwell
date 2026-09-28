<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getBlogProfile, getBlogArticles } from '@/api/blog'
import { listCategories } from '@/api/category'
import { useToastStore } from '@/stores/toast'
import { formatDate } from '@/utils/format'
import ArticleCard from '@/components/ArticleCard.vue'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const toast = useToastStore()

const username = computed(() => String(route.params.username || ''))

const profile = ref(null)
const articles = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const loading = ref(false)

// 三件套
const keyword = ref('')
const categoryId = ref(null)
const sort = ref('desc') // desc 最新 / asc 最早
const view = ref('card') // card 卡片网格 / list 紧凑列表
const categories = ref([])

const displayName = computed(
  () => (profile.value && (profile.value.name || profile.value.username)) || username.value || '用户',
)

async function fetchCategories() {
  try {
    const data = await listCategories()
    categories.value = (data && data.list) || []
  } catch (e) {
    // 分类获取失败不阻塞列表
  }
}

async function fetchProfile() {
  try {
    const data = await getBlogProfile(username.value, { page: 1, pageSize: 1 })
    profile.value = (data && data.user) || null
  } catch (e) {
    profile.value = null
  }
}

async function fetchArticles() {
  if (!username.value) return
  loading.value = true
  try {
    const data = await getBlogArticles(username.value, {
      page: page.value,
      pageSize,
      keyword: keyword.value.trim() || undefined,
      category_id: categoryId.value || undefined,
      sort: sort.value,
    })
    articles.value = (data && data.list) || []
    total.value = Number(data && data.total) || 0
    document.title = `${displayName.value}的全部文章`
  } catch (e) {
    toast.error(e.message || '文章加载失败')
  } finally {
    loading.value = false
  }
}

function changePage(p) {
  page.value = p
  fetchArticles()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleSearch() {
  page.value = 1
  fetchArticles()
}

function handleReset() {
  keyword.value = ''
  categoryId.value = null
  sort.value = 'desc'
  page.value = 1
  fetchArticles()
}

function selectCategory(id) {
  categoryId.value = categoryId.value === id ? null : id
  page.value = 1
  fetchArticles()
}

function toggleSort() {
  sort.value = sort.value === 'desc' ? 'asc' : 'desc'
  page.value = 1
  fetchArticles()
}

function toggleView(v) {
  view.value = v
}

watch(username, () => {
  page.value = 1
  keyword.value = ''
  categoryId.value = null
  sort.value = 'desc'
  fetchProfile()
  fetchArticles()
})

onMounted(() => {
  fetchProfile()
  fetchArticles()
  fetchCategories()
})
</script>

<template>
  <div class="min-h-screen bg-page text-body antialiased">
    <!-- 顶部导航条 -->
    <header class="profile-topbar">
      <div class="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
        <RouterLink :to="`/${username}`" class="ghost-btn">← 返回主页</RouterLink>
        <span class="truncate text-sm text-muted">@{{ username }}</span>
      </div>
    </header>

    <main class="mx-auto max-w-5xl px-4 py-8">
      <!-- 标题 -->
      <header class="mb-4">
        <h1 class="text-2xl font-bold tracking-tight text-ink">{{ displayName }} 的全部文章</h1>
        <p class="mt-1 text-sm text-muted">共 {{ total }} 篇</p>
      </header>

      <!-- 工具栏：搜索 + 排序 + 视图切换 -->
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <input
          v-model="keyword"
          class="input"
          style="max-width: 260px"
          placeholder="搜索文章标题…"
          @keyup.enter="handleSearch"
        />
        <button class="ghost-btn" @click="handleSearch">搜索</button>
        <button class="ghost-btn" @click="handleReset">重置</button>

        <span class="ml-auto flex items-center gap-2">
          <button class="ghost-btn" @click="toggleSort" title="切换排序">
            {{ sort === 'desc' ? '最新优先' : '最早优先' }}
          </button>
          <button
            class="icon-btn"
            :class="{ active: view === 'card' }"
            title="卡片视图"
            @click="toggleView('card')"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="7" height="7" rx="1" />
              <rect x="14" y="3" width="7" height="7" rx="1" />
              <rect x="3" y="14" width="7" height="7" rx="1" />
              <rect x="14" y="14" width="7" height="7" rx="1" />
            </svg>
          </button>
          <button
            class="icon-btn"
            :class="{ active: view === 'list' }"
            title="列表视图"
            @click="toggleView('list')"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="8" y1="6" x2="21" y2="6" />
              <line x1="8" y1="12" x2="21" y2="12" />
              <line x1="8" y1="18" x2="21" y2="18" />
              <line x1="3" y1="6" x2="3.01" y2="6" />
              <line x1="3" y1="12" x2="3.01" y2="12" />
              <line x1="3" y1="18" x2="3.01" y2="18" />
            </svg>
          </button>
        </span>
      </div>

      <!-- 分类筛选 chips -->
      <div class="mb-6 flex flex-wrap items-center gap-2">
        <button
          class="cat-chip"
          :class="{ active: categoryId === null }"
          @click="selectCategory(null)"
        >
          全部
        </button>
        <button
          v-for="c in categories"
          :key="c.category_id"
          class="cat-chip"
          :class="{ active: categoryId === c.category_id }"
          @click="selectCategory(c.category_id)"
        >
          {{ c.category_name }}
        </button>
      </div>

      <!-- 文章列表 -->
      <p v-if="loading" class="py-16 text-center text-faint">加载中…</p>

      <!-- 卡片视图 -->
      <div v-else-if="articles.length && view === 'card'" class="grid gap-6 sm:grid-cols-2">
        <ArticleCard v-for="a in articles" :key="a.article_id" :article="a" />
      </div>

      <!-- 列表视图 -->
      <div v-else-if="articles.length && view === 'list'" class="space-y-2">
        <RouterLink
          v-for="a in articles"
          :key="a.article_id"
          :to="`/article/${a.article_id}`"
          class="flex items-center justify-between gap-4 rounded-card border border-line bg-card px-4 py-3 transition-colors hover:border-accent"
        >
          <div class="min-w-0">
            <div class="truncate font-medium text-ink">{{ a.title }}</div>
            <div v-if="(a.category_names || []).length" class="mt-1 flex flex-wrap gap-1">
              <span v-for="cn in a.category_names" :key="cn" class="tag">{{ cn }}</span>
            </div>
          </div>
          <time class="shrink-0 text-xs text-faint">{{ formatDate(a.created_at) }}</time>
        </RouterLink>
      </div>

      <div v-else class="card py-16 text-center text-faint">还没有匹配的文章</div>

      <Pagination :page="page" :page-size="pageSize" :total="total" @change="changePage" />
    </main>
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

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-tag);
  border: 1px solid var(--color-line);
  background-color: transparent;
  color: var(--color-muted);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}
.icon-btn:hover {
  color: var(--color-accent);
}
.icon-btn.active {
  background-color: color-mix(in oklab, var(--color-accent) 12%, transparent);
  color: var(--color-accent);
  border-color: var(--color-accent);
}
.icon-btn svg {
  width: 18px;
  height: 18px;
}

.cat-chip {
  border: 1px solid var(--color-line);
  background-color: var(--color-card);
  color: var(--color-body);
  font-size: 13px;
  padding: 5px 14px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.cat-chip:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
.cat-chip.active {
  background-color: var(--color-accent);
  border-color: var(--color-accent);
  color: var(--color-on-accent);
}

.card {
  @apply bg-card border border-line rounded-card p-6;
}
</style>
