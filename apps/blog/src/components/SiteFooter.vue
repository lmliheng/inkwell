<script setup>
import {ref} from 'vue'
import { siGithub } from 'simple-icons'

// 全宽页面底部栏：版权 + 托管信息 + GitHub 跳转
const year = new Date().getFullYear()
const githubUrl = 'https://github.com/lmliheng'

let foot_text=ref(import.meta.env.VITE_FOOT_TEXT)


</script>

<template>
  <footer class="site-footer">
    <div class="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-2 gap-y-1 px-4 py-6 text-center">
      <span class="text-sm text-faint">JS creator © {{ year }}</span>
      <span class="text-faint">·</span>
      <span class="text-sm text-faint">{{ foot_text }}</span>
      <a
        :href="githubUrl"
        target="_blank"
        rel="noopener noreferrer"
        class="footer-github"
        :title="githubUrl"
        aria-label="GitHub"
      >
        <svg viewBox="0 0 24 24" class="h-4 w-4" fill="currentColor" aria-hidden="true">
          <path :d="siGithub.path" />
        </svg>
      </a>
    </div>
  </footer>
</template>

<style scoped>
@reference "../style.css";

.site-footer {
  border-top: 1px solid var(--color-line);
  background-color: transparent;
}
.footer-github {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-left: 4px;
  border-radius: 999px;
  color: var(--color-muted);
  transition: background-color 0.2s ease, color 0.2s ease;
}
.footer-github:hover {
  background-color: color-mix(in oklab, var(--color-accent) 12%, transparent);
  color: var(--color-accent);
}
</style>
