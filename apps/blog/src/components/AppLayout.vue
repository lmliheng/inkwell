<script setup>
import Sidebar from './Sidebar.vue'
</script>

<template>
  <div class="mx-auto flex min-h-screen max-w-[1200px] flex-col bg-page text-body antialiased lg:flex-row">
    <!-- 侧边栏（桌面端 sticky 在左侧，随整体居中；移动端折叠为顶部菜单） -->
    <Sidebar />

    <!-- 主内容区 -->
    <main class="min-w-0 flex-1">
      <slot />
    </main>
  </div>
</template>
