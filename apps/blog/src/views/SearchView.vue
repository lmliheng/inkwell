<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const keyword = ref(String(route.query.keyword || ''))
</script>

<template>
  <div class="mx-auto max-w-5xl px-4 py-8">
    <section class="card">
      <h1 class="text-2xl font-bold text-ink">搜索</h1>
      <p class="mt-2 text-sm text-muted">搜索文章标题与内容。</p>

      <div class="mt-4 flex gap-2">
        <input v-model="keyword" type="search" class="input flex-1 sm:max-w-sm" placeholder="输入关键词…" />
        <button class="btn-accent" type="button">搜索</button>
      </div>

      <p class="mt-4 text-sm leading-relaxed text-faint">
        搜索结果由后续开发填充（复用 ArticleCard 卡片组件）。
      </p>
    </section>
  </div>
</template>
