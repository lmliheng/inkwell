<script setup lang="ts">
import { ref, computed } from 'vue'
import { usePathTagStore, type PathTagItem } from '@/store/pathTag'
import { useRoute, useRouter } from 'vue-router'
import contextmenu from './contextmenu.vue'

const router = useRouter()
const route = useRoute()
const pathTagStore = usePathTagStore()
const pathTagsList = computed(() => pathTagStore.pathTagsList)

const isShow = ref(false)
const contextmenuLocationStyle = ref({
  top: '0',
  left: '0',
  position: 'absolute' as const
})

const removeTag = (tag: PathTagItem) => {
  pathTagStore.removePathTag(tag)
}
const toTag = (tag: PathTagItem) => {
  router.push(tag.fullPath)
}

const handleContextMenu = (e: MouseEvent, tag: PathTagItem) => {
  contextmenuLocationStyle.value.top = e.y + 'px'
  contextmenuLocationStyle.value.left = e.x + 'px'
  isShow.value = true
}
</script>

<template>
  <div class="tag-view-container">
    <n-tag v-for="tag in pathTagsList" :key="tag.name" :type="tag.name === route.name ? 'primary' : 'default'"
      :bordered="false" closable size="medium" round
      :class="['tag-item', { active: tag.name === route.name }]" @click="toTag(tag)"
      @close="removeTag(tag)" @contextmenu.prevent="handleContextMenu($event, tag)">
      {{ $t(String(tag.meta.title || '')) }}
    </n-tag>
  </div>

  <contextmenu v-show="isShow" :style="contextmenuLocationStyle" :isShow="isShow" @close="isShow = false" />
</template>

<style scoped>
.tag-view-container {
  display: flex;
  gap: 6px;
  flex-wrap: nowrap;
  overflow-x: auto;
  scrollbar-width: none;
}

.tag-view-container::-webkit-scrollbar {
  display: none;
}

.tag-item {
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}

.tag-item:hover {
  opacity: 0.85;
}
</style>
