<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { HomeOutline } from '@vicons/ionicons5'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const items = computed(() => {
  const matched = route.matched.filter(r => r.meta?.title)
  const result: { label: string; path?: string }[] = [
    { label: t('home'), path: '/' }
  ]
  for (const r of matched) {
    const title = t(String(r.meta.title || ''))
    if (title && title !== result[result.length - 1]?.label) {
      result.push({ label: title, path: r.path })
    }
  }
  return result
})
</script>

<template>
  <n-breadcrumb>
    <n-breadcrumb-item v-for="(item, index) in items" :key="index"
      :clickable="!!item.path && index < items.length - 1"
      @click="item.path && index < items.length - 1 ? router.push(item.path) : undefined">
      <n-icon v-if="index === 0" :component="HomeOutline" style="margin-right: 4px;" />
      {{ item.label }}
    </n-breadcrumb-item>
  </n-breadcrumb>
</template>
