<script setup>
import { useThemeStore } from '@/stores/theme'

const theme = useThemeStore()
</script>

<template>
  <button
    type="button"
    class="theme-toggle"
    :aria-label="theme.isDark ? '切换到亮色模式' : '切换到暗色模式'"
    :title="theme.isDark ? '切换到亮色模式' : '切换到暗色模式'"
    @click="theme.toggle()"
  >
    <!-- 亮色下显示太阳，点击进入暗色 -->
    <svg
      v-if="!theme.isDark"
      class="h-5 w-5"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      stroke-width="2"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="4" />
      <path
        stroke-linecap="round"
        d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"
      />
    </svg>
    <!-- 暗色下显示月亮，点击进入亮色 -->
    <svg
      v-else
      class="h-5 w-5"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      stroke-width="2"
      aria-hidden="true"
    >
      <path stroke-linecap="round" stroke-linejoin="round" d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </svg>
  </button>
</template>

<style scoped>
@reference "../style.css";

.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 9999px;
  color: var(--color-muted);
  transition: background-color 0.2s ease, color 0.2s ease;
  cursor: pointer;
}
.theme-toggle:hover {
  background-color: color-mix(in oklab, var(--color-accent) 12%, transparent);
  color: var(--color-accent);
}
</style>
