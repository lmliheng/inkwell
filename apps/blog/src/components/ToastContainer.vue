<script setup>
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()

const styles = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  error: 'border-red-200 bg-red-50 text-red-700',
  info: 'border-accent/40 bg-accent/10 text-accent',
}
</script>

<template>
  <div class="pointer-events-none fixed right-4 top-16 z-50 flex w-72 flex-col gap-2">
    <TransitionGroup name="toast">
      <div
        v-for="t in toast.items"
        :key="t.id"
        class="pointer-events-auto rounded-lg border px-4 py-3 text-sm shadow-sm"
        :class="styles[t.type] || styles.info"
      >
        {{ t.message }}
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
</style>
