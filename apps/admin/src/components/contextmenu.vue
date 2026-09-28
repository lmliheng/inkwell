<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { usePathTagStore } from '@/store/pathTag'
import { useRoute } from 'vue-router'

const route = useRoute()
const props = defineProps({
  isShow: Boolean
})
const pathTagStore = usePathTagStore()
const emit = defineEmits<{
  (e: 'close'): void
}>()

const closeAllTag = () => {
  pathTagStore.removeAllPathTags(route)
  emit('close')
}

const handleClickOutside = (e: MouseEvent) => {
  if (props.isShow && !(e.target as Element).closest('.contextmenu-container')) {
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="contextmenu-container">
    <n-card size="small" embedded :bordered="true" style="padding: 0;">
      <n-button text block @click="closeAllTag" style="justify-content: flex-start; padding: 6px 12px;">
        关闭所有
      </n-button>
    </n-card>
  </div>
</template>

<style scoped>
.contextmenu-container {
  position: fixed;
  z-index: 9999;
}
</style>
