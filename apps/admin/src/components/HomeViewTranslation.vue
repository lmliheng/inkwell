<script setup lang="ts">
// Transition wrapper component - currently a placeholder
</script>

<template>
  <Transition name="my-transition">
    <slot></slot>
  </Transition>
</template>

<style>
.my-transition-enter-active,
.my-transition-leave-active {
  transition: opacity 0.3s ease;
}

.my-transition-enter-from,
.my-transition-leave-to {
  opacity: 0;
}
</style>
