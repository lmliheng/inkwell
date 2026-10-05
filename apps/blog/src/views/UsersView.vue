<script setup>
import { ref, onMounted } from 'vue'
import { getBlogUsers } from '@/api/blog'
import { useToastStore } from '@/stores/toast'
import Pagination from '@/components/Pagination.vue'
import SiteFooter from '@/components/SiteFooter.vue'

const toast = useToastStore()

const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 24
const loading = ref(false)

async function fetchUsers() {
  loading.value = true
  try {
    const data = await getBlogUsers({ page: page.value, pageSize })
    list.value = (data && data.list) || []
    total.value = Number(data && data.total) || 0
  } catch (e) {
    toast.error(e.message || '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

function changePage(p) {
  page.value = p
  fetchUsers()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function userName(u) {
  return u.name || u.username || '匿名'
}

function monogram(u) {
  return userName(u).trim().charAt(0).toUpperCase()
}

onMounted(fetchUsers)
</script>

<template>
  <div class="mx-auto max-w-5xl px-4 py-8">
    <header class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight text-ink">全部博主</h1>
        <p class="mt-2 text-muted">发现并关注你感兴趣的博主。</p>
      </div>
      <RouterLink to="/" class="ghost-btn">← 返回首页</RouterLink>
    </header>

    <!-- 用户列表 -->
    <p v-if="loading" class="py-16 text-center text-faint">加载中…</p>
    <div v-else-if="list.length" class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <RouterLink
        v-for="u in list"
        :key="u.id"
        :to="`/${u.username}`"
        class="group flex flex-col gap-4 rounded-card border border-line bg-card p-6 transition-shadow hover:shadow-sm"
      >
        <div class="flex items-center gap-4">
          <span v-if="u.avatar" class="h-14 w-14 shrink-0 overflow-hidden rounded-full">
            <img :src="u.avatar" :alt="userName(u)" class="h-full w-full object-cover" />
          </span>
          <span v-else class="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-accent text-xl font-bold text-on-accent">
            {{ monogram(u) }}
          </span>
          <div class="min-w-0">
            <div class="truncate text-lg font-bold text-ink group-hover:text-accent">{{ userName(u) }}</div>
            <div class="truncate text-xs text-faint">@{{ u.username }}</div>
          </div>
        </div>

        <p v-if="u.bio" class="line-clamp-2 min-h-[2.5rem] text-sm leading-relaxed text-muted">{{ u.bio }}</p>
        <p v-else class="min-h-[2.5rem] text-sm text-faint">这位博主还没有写简介。</p>

        <div class="flex items-center justify-between text-xs text-faint">
          <span v-if="u.area">{{ u.area }}</span>
          <span v-else></span>
          <span class="rounded-tag bg-accent/10 px-2 py-0.5 text-accent">{{ u.article_count }} 篇文章</span>
        </div>
      </RouterLink>
    </div>
    <div v-else class="card py-16 text-center text-faint">还没有博主</div>

    <Pagination :page="page" :page-size="pageSize" :total="total" unit="位" @change="changePage" />

    <SiteFooter />
  </div>
</template>

<style scoped>
@reference "../style.css";
.ghost-btn {
  @apply inline-flex items-center rounded-tag border border-line px-4 py-2 text-sm text-body hover:bg-card;
}
</style>
