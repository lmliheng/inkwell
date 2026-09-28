<script setup>
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import ToastContainer from '@/components/ToastContainer.vue'
import AnnouncementBar from '@/components/AnnouncementBar.vue'
import { useThemeStore } from '@/stores/theme'

// 挂载主题切换：setup 阶段同步应用持久化主题，避免首屏闪烁
const theme = useThemeStore()
theme.init()

// 路由 meta.layout 控制布局：
//   layout: 'blank' → 独立访客页（无 AppLayout / 侧边栏），直接渲染 RouterView
const route = useRoute()
const isBlank = computed(() => route.meta.layout === 'blank')
</script>

<template>
  <!-- 站点公告横幅：全站所有页面顶部 -->
  <AnnouncementBar />
  <AppLayout v-if="!isBlank">
    <RouterView />
  </AppLayout>
  <RouterView v-else />
  <ToastContainer />
</template>
