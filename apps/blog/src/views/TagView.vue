<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <div class="mx-auto max-w-5xl px-4 py-8">
    <section class="card">
      <h1 class="text-2xl font-bold text-ink">标签</h1>
      <p class="mt-2 text-sm text-muted">当前标签：<span class="font-medium text-accent">{{ route.params.id }}</span></p>
      <p class="mt-4 text-sm leading-relaxed text-faint">
        该标签下的文章列表由后续开发填充（复用 ArticleCard 卡片组件）。
      </p>
    </section>
  </div>
</template>
