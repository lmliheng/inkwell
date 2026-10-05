<template>
  <div class="mx-auto max-w-md px-4 py-24 text-center">
    <p class="text-6xl font-bold text-faint">404</p>
    <h1 class="mt-4 text-xl font-semibold text-ink">页面不存在</h1>
    <p class="mt-2 text-sm text-muted">你访问的页面可能已被移除或地址有误。</p>
    <RouterLink to="/" class="primary-btn mt-6 inline-block">返回首页</RouterLink>
  </div>
</template>

<style scoped>
@reference "../style.css";
.primary-btn {
  @apply rounded-tag bg-accent px-4 py-2 text-sm text-on-accent hover:bg-accent-strong;
}
</style>
