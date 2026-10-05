<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 9 },
  total: { type: Number, default: 0 },
  unit: { type: String, default: '篇' },
})

const emit = defineEmits(['change'])

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

// 生成带省略号的页码列表
const pages = computed(() => {
  const cur = props.page
  const tp = totalPages.value
  if (tp <= 7) return Array.from({ length: tp }, (_, i) => i + 1)
  const set = new Set([1, tp, cur - 1, cur, cur + 1])
  const sorted = [...set].filter((p) => p >= 1 && p <= tp).sort((a, b) => a - b)
  const out = []
  let prev = 0
  for (const p of sorted) {
    if (p - prev > 1) out.push('...')
    out.push(p)
    prev = p
  }
  return out
})

function go(p) {
  if (p === '...' || p < 1 || p > totalPages.value || p === props.page) return
  emit('change', p)
}
</script>

<template>
  <div class="flex items-center justify-between gap-3 pt-6">
    <span class="text-sm text-muted">共 {{ total }} {{ unit }}</span>
    <div v-if="totalPages > 1" class="flex items-center gap-1">
      <button class="page-btn" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
      <button
        v-for="(p, i) in pages"
        :key="i"
        class="page-btn"
        :class="{ 'is-active': p === page }"
        :disabled="p === '...'"
        @click="go(p)"
      >
        {{ p }}
      </button>
      <button class="page-btn" :disabled="page >= totalPages" @click="go(page + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
@reference "../style.css";
.page-btn {
  @apply rounded-tag border border-line px-3 py-1.5 text-sm text-accent hover:bg-accent/10 disabled:cursor-not-allowed disabled:opacity-40;
}
.page-btn.is-active {
  @apply border-accent bg-accent text-on-accent hover:bg-accent-strong;
}
</style>
