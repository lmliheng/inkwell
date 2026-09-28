<script setup>
import { ref, onMounted } from 'vue'
import { getMyFavorites } from '@/api/social'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useRouter } from 'vue-router'
import ArticleCard from '@/components/ArticleCard.vue'
import SiteFooter from '@/components/SiteFooter.vue'

const auth = useAuthStore()
const toast = useToastStore()
const router = useRouter()

const list = ref([])
const loading = ref(true)

async function fetchFavorites() {
  loading.value = true
  try {
    const res = await getMyFavorites()
    // social.js 已解包 body，res 即 { list }
    list.value = (res && res.list) || []
  } catch (e) {
    toast.error(e?.response?.data?.message || '收藏列表加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!auth.isLoggedIn) {
    router.replace({ path: '/login', query: { redirect: '/me/favorites' } })
    return
  }
  fetchFavorites()
})
</script>

<template>
  <div class="mx-auto max-w-5xl px-4 py-8">
    <header class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight text-ink">我的收藏</h1>
        <p class="mt-2 text-muted">只对你自己可见的收藏文章。</p>
      </div>
      <RouterLink to="/" class="ghost-btn">← 返回首页</RouterLink>
    </header>

    <p v-if="loading" class="py-16 text-center text-faint">加载中…</p>
    <div v-else-if="list.length" class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      <ArticleCard v-for="a in list" :key="a.article_id" :article="a" compact />
    </div>
    <div v-else class="card py-16 text-center">
      <p class="text-lg font-bold text-ink">还没有收藏</p>
      <p class="mt-2 text-sm text-muted">在文章详情页点击「收藏」即可加入这里。</p>
      <RouterLink to="/" class="btn-accent mt-6">去逛逛文章</RouterLink>
    </div>

    <SiteFooter />
  </div>
</template>

<style scoped>
@reference "../style.css";
.ghost-btn {
  @apply inline-flex items-center rounded-tag border border-line px-4 py-2 text-sm text-body hover:bg-card;
}
</style>
