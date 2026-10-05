<script setup lang="ts">
import { onMounted } from 'vue'
import { useLangStore } from '@/store/lang'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()
const langStore = useLangStore()

const handleChangeLang = (val: string) => {
  locale.value = val
  langStore.setLang(val)
}

onMounted(() => {
  locale.value = langStore.lang
})
</script>

<template>
  <n-tooltip placement="bottom" trigger="hover">
    <template #trigger>
      <n-dropdown trigger="click" @select="handleChangeLang" :options="[
        { label: $t('language_cn'), key: 'cn', disabled: langStore.lang === 'cn' },
        { label: $t('language_en'), key: 'en', disabled: langStore.lang === 'en' },
        { label: $t('language_jp'), key: 'jp', disabled: langStore.lang === 'jp' },
        { label: $t('language_ru'), key: 'ru', disabled: langStore.lang === 'ru' },
      ]">
        <n-button quaternary circle>
          <template #icon>
            <n-icon :size="18">
              <svg viewBox="0 0 1024 1024" fill="currentColor">
                <path
                  d="M549.12 643.008L440.768 535.936l1.28-1.28A747.52 747.52 0 0 0 600.32 256h125.056V170.688H426.688V85.376H341.376v85.312H42.688v84.928h476.608A670.4 670.4 0 0 1 384 484.288a667.52 667.52 0 0 1-98.56-142.912H200.128a749.312 749.312 0 0 0 127.168 194.56L110.08 750.08l60.608 60.544L384 597.376l132.736 132.672 32.384-87.04z m240.256-216.32H704l-192 512h85.376l47.744-128h202.688l48.192 128h85.376l-192-512z m-111.808 298.688l69.12-184.768 69.12 184.768h-138.24z" />
              </svg>
            </n-icon>
          </template>
        </n-button>
      </n-dropdown>
    </template>
    {{ $t('language_switch') }}
  </n-tooltip>
</template>
