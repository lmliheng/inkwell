<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getArticleArchive } from '@/api/article'
import { formatDate } from '@/utils/format'

const route = useRoute()

// 有 username = 单用户归档（blank 布局）；无 = 全站归档（AppLayout 布局）
const username = computed(() => String(route.params.username || ''))
const isUserArchive = computed(() => !!username.value)

const list = ref([])
const loading = ref(false)

const title = computed(() => (username.value ? `${username.value} 的归档` : '归档'))

// 折叠状态（Set 存展开的 key）
const openYears = ref(new Set())
const openMonths = ref(new Set())
const openUsers = ref(new Set())

function toggleYear(year) {
  const s = new Set(openYears.value)
  s.has(year) ? s.delete(year) : s.add(year)
  openYears.value = s
}
function toggleMonth(key) {
  const s = new Set(openMonths.value)
  s.has(key) ? s.delete(key) : s.add(key)
  openMonths.value = s
}
function toggleUser(key) {
  const s = new Set(openUsers.value)
  s.has(key) ? s.delete(key) : s.add(key)
  openUsers.value = s
}

// 分组：年 → 月 → 用户 → 文章
const groups = computed(() => {
  const yearMap = new Map()
  for (const a of list.value) {
    const d = new Date(a.created_at)
    if (Number.isNaN(d.getTime())) continue
    const year = d.getFullYear()
    const month = d.getMonth() + 1
    const author = a.author_username || '未知'
    if (!yearMap.has(year)) yearMap.set(year, new Map())
    const monthMap = yearMap.get(year)
    if (!monthMap.has(month)) monthMap.set(month, new Map())
    const userMap = monthMap.get(month)
    if (!userMap.has(author)) userMap.set(author, [])
    userMap.get(author).push(a)
  }
  return Array.from(yearMap.entries())
    .sort((a, b) => b[0] - a[0])
    .map(([year, monthMap]) => ({
      year,
      months: Array.from(monthMap.entries())
        .sort((a, b) => b[0] - a[0])
        .map(([month, userMap]) => ({
          month,
          users: Array.from(userMap.entries()).map(([uname, items]) => ({
            username: uname,
            name: items[0].author_name || uname,
            items,
          })),
        })),
    }))
})

function monthCount(m) {
  return m.users.reduce((sum, u) => sum + u.items.length, 0)
}

function cats(a) {
  const v = a.category_names
  return v ? String(v).split(',').filter(Boolean) : []
}

async function fetchArchive() {
  loading.value = true
  try {
    const data = await getArticleArchive(username.value ? { username: username.value } : {})
    list.value = (data && data.list) || []
    document.title = title.value
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

watch(username, fetchArchive)
onMounted(fetchArchive)
</script>

<template>
  <div class="min-h-screen bg-page text-body antialiased">
    <!-- 单用户归档：顶部条（返回用户主页） -->
    <header v-if="username" class="profile-topbar">
      <div class="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
        <RouterLink :to="`/${username}`" class="ghost-btn">← 返回主页</RouterLink>
        <span class="truncate text-sm text-muted">@{{ username }}</span>
      </div>
    </header>

    <main class="mx-auto max-w-5xl px-4 py-8">
      <header class="mb-6">
        <h1 class="text-2xl font-bold tracking-tight text-ink">{{ title }}</h1>
        <p class="mt-1 text-sm text-muted">共 {{ list.length }} 篇</p>
      </header>

      <p v-if="loading" class="py-16 text-center text-faint">加载中…</p>

      <div v-else-if="groups.length" class="space-y-2">
        <!-- 年份层 -->
        <div v-for="g in groups" :key="g.year" class="rounded-card border border-line bg-card">
          <button
            class="flex w-full items-center justify-between px-4 py-3 text-left"
            @click="toggleYear(g.year)"
          >
            <span class="text-lg font-bold text-ink">
              {{ openYears.has(g.year) ? '▾' : '▸' }} {{ g.year }} 年
            </span>
            <span class="text-xs text-faint">
              {{ g.months.reduce((s, m) => s + monthCount(m), 0) }} 篇
            </span>
          </button>

          <div v-if="openYears.has(g.year)" class="border-t border-line px-2 py-2">
            <!-- 月份层 -->
            <div v-for="m in g.months" :key="m.month" class="mb-1">
              <button
                class="flex w-full items-center justify-between rounded-tag px-3 py-2 text-left hover:bg-accent/5"
                @click="toggleMonth(`${g.year}-${m.month}`)"
              >
                <span class="font-semibold text-body">
                  {{ openMonths.has(`${g.year}-${m.month}`) ? '▾' : '▸' }} {{ g.year }} 年 {{ m.month }} 月
                </span>
                <span class="text-xs text-faint">{{ monthCount(m) }} 篇</span>
              </button>

              <div v-if="openMonths.has(`${g.year}-${m.month}`)" class="ml-4 mt-1 space-y-1 border-l border-line pl-3">
                <!-- 单用户归档：直接列文章 -->
                <template v-if="isUserArchive">
                  <ul class="space-y-1 pt-1">
                    <li
                      v-for="a in m.users[0].items"
                      :key="a.article_id"
                      class="flex items-center justify-between gap-3 rounded-tag px-3 py-2 hover:bg-accent/5"
                    >
                      <RouterLink :to="`/article/${a.article_id}`" class="min-w-0 flex-1 truncate text-body hover:text-accent">
                        {{ a.title }}
                      </RouterLink>
                      <time class="shrink-0 text-xs text-faint">{{ formatDate(a.created_at) }}</time>
                    </li>
                  </ul>
                </template>
                <!-- 全站归档：按用户折叠 -->
                <template v-else>
                  <div v-for="u in m.users" :key="u.username" class="pt-1">
                    <button
                      class="flex w-full items-center justify-between rounded-tag px-3 py-1.5 text-left hover:bg-accent/5"
                      @click="toggleUser(`${g.year}-${m.month}-${u.username}`)"
                    >
                      <span class="text-sm font-medium text-body">
                        {{ openUsers.has(`${g.year}-${m.month}-${u.username}`) ? '▾' : '▸' }} @{{ u.username }}
                      </span>
                      <span class="text-xs text-faint">{{ u.items.length }} 篇</span>
                    </button>
                    <ul v-if="openUsers.has(`${g.year}-${m.month}-${u.username}`)" class="ml-4 space-y-1 border-l border-line pl-3 pt-1">
                      <li
                        v-for="a in u.items"
                        :key="a.article_id"
                        class="flex items-center justify-between gap-3 rounded-tag px-3 py-2 hover:bg-accent/5"
                      >
                        <RouterLink :to="`/article/${a.article_id}`" class="min-w-0 flex-1 truncate text-body hover:text-accent">
                          {{ a.title }}
                        </RouterLink>
                        <time class="shrink-0 text-xs text-faint">{{ formatDate(a.created_at) }}</time>
                      </li>
                    </ul>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="card py-16 text-center text-faint">暂无归档文章</div>
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

.card {
  @apply bg-card border border-line rounded-card p-6;
}
</style>
