<script setup lang="ts">
import { ref } from 'vue'
import { NTooltip, NButton, NIcon } from 'naive-ui'
import { ExpandOutline, ContractOutline } from '@vicons/ionicons5'
import screenfull from 'screenfull'

const isFullScreen = ref(false)
const toggleFullScreen = () => {
  isFullScreen.value = !isFullScreen.value
  screenfull.toggle()
}
</script>

<template>
  <n-tooltip placement="bottom" trigger="hover">
    <template #trigger>
      <n-button quaternary circle @click="toggleFullScreen">
        <template #icon>
          <n-icon :size="18">
            <ExpandOutline v-if="!isFullScreen" />
            <ContractOutline v-else />
          </n-icon>
        </template>
      </n-button>
    </template>
    全屏/退出全屏
  </n-tooltip>
</template>
