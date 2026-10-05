<script setup>
import { ref, onMounted } from 'vue'
import http from '@/api/http'

/**
 * 广告位组件 —— 按位置拉取并展示广告（卡片 + 「广告」角标）。
 * 用法：<AdSlot position="article_top" />
 * 无广告时渲染空（不占位）。
 */
const props = defineProps({
  position: { type: String, required: true },
})

const ad = ref(null)

async function fetchAd() {
  try {
    const res = await http.get('/ad/slots', { params: { position: props.position } })
    // 拦截器已解包 body，res.data.ad 为广告对象或 null
    ad.value = (res && res.data && res.data.ad) || null
  } catch (e) {
    ad.value = null
  }
}

// 点击：先记录统计再跳转
function onClickAd() {
  if (!ad.value) return
  if (ad.value.id) {
    http.post(`/ad/click/${ad.value.id}`).catch(() => {})
  }
  if (ad.value.link_url) {
    window.open(ad.value.link_url, '_blank', 'noopener')
  }
}

onMounted(fetchAd)
</script>

<template>
  <div v-if="ad" class="ad-slot">
    <a
      :href="ad.link_url || undefined"
      :target="ad.link_url ? '_blank' : undefined"
      :rel="ad.link_url ? 'noopener noreferrer' : undefined"
      class="ad-card group"
      @click="onClickAd"
    >
      <!-- 图片广告 -->
      <div v-if="ad.type === 'image' && ad.image_url" class="ad-image-wrap">
        <img :src="ad.image_url" :alt="ad.title" loading="lazy" class="ad-image" />
      </div>
      <!-- 文字广告 -->
      <div v-else class="ad-text">
        <div class="ad-text-title">{{ ad.text_title || ad.title }}</div>
        <div v-if="ad.text_desc" class="ad-text-desc">{{ ad.text_desc }}</div>
      </div>
      <span class="ad-badge">广告</span>
    </a>
  </div>
</template>

<style scoped>
@reference "../style.css";

.ad-slot {
  margin: 18px 0;
}
.ad-card {
  position: relative;
  display: block;
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: 12px;
  background: var(--color-card);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}
.ad-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.ad-image-wrap {
  width: 100%;
}
.ad-image {
  display: block;
  width: 100%;
  max-height: 120px;
  object-fit: cover;
}
.ad-text {
  padding: 14px 18px;
}
.ad-text-title {
  font-size: 15px;
  font-weight: 800;
  color: var(--color-ink);
}
.ad-text-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--color-muted);
  line-height: 1.6;
}
.ad-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  pointer-events: none;
}
</style>
