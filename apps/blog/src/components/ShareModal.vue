<script setup>
import { ref, computed, onMounted } from 'vue'
import QRCode from 'qrcode'
import html2canvas from 'html2canvas'

const props = defineProps({
  article: { type: Object, required: true },
})
const emit = defineEmits(['close'])

const cardRef = ref(null)
const qrDataUrl = ref('')
const previewUrl = ref('')
const generating = ref(false)
const generated = ref(false)

// 可选的分享卡片风格
const cardStyles = [
  { id: 'ocean', name: '深蓝' },
  { id: 'gold', name: '黑金' },
  { id: 'fresh', name: '清新' },
  { id: 'warm', name: '暖橙' },
]
const currentStyle = ref('')

// 随机取一种风格（避免与当前重复）
function randomStyle() {
  if (!currentStyle.value) {
    return cardStyles[Math.floor(Math.random() * cardStyles.length)].id
  }
  const idx = cardStyles.findIndex((s) => s.id === currentStyle.value)
  return cardStyles[(idx + 1 + Math.floor(Math.random() * (cardStyles.length - 1))) % cardStyles.length].id
}

function switchStyle(id) {
  if (id === currentStyle.value) return
  currentStyle.value = id
  generated.value = false
  previewUrl.value = ''
  generateCard()
}

// 随机换一种风格并重新生成
function shuffleStyle() {
  switchStyle(randomStyle())
}

// 提取正文前 3-4 段纯文本，截断约 180 字
function extractExcerpt(content, maxLen = 180) {
  const blocks = String(content || '').split(/\n{2,}/)
  const lines = []
  for (const b of blocks) {
    const text = b
      .replace(/^#{1,6}\s+/gm, '')
      .replace(/^\s*[-*+]\s+/gm, '')
      .replace(/^\s*\d+\.\s+/gm, '')
      .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
      .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
      .replace(/`{1,3}/g, '')
      .replace(/[*_>~|]/g, '')
      .replace(/\s+/g, ' ')
      .trim()
    if (text) lines.push(text)
    if (lines.length >= 4) break
  }
  const full = lines.join(' ')
  return full.length > maxLen ? full.slice(0, maxLen) + '…' : full
}

const excerpt = computed(() => extractExcerpt(props.article?.content))
const shareUrl = computed(() => `${window.location.origin}/article/${props.article?.article_id}`)
const authorName = computed(() => props.article?.author_name || 'JScreator')
const authorAvatar = computed(() => props.article?.author_avatar || '')
const authorInitial = computed(() => (authorName.value || 'J').trim().charAt(0).toUpperCase())

// 生成卡片图片（html2canvas）
async function generateCard() {
  if (!cardRef.value || generated.value) return previewUrl.value
  generating.value = true
  try {
    if (!qrDataUrl.value) {
      qrDataUrl.value = await QRCode.toDataURL(shareUrl.value, {
        width: 180,
        margin: 1,
        color: { dark: '#111827', light: '#ffffff' },
      })
      await new Promise((r) => setTimeout(r, 50))
    }
    const canvas = await html2canvas(cardRef.value, {
      width: 750,
      height: 1000,
      scale: 3, // 3x 高清，缩小预览/下载更锐利
      backgroundColor: null,
      useCORS: true,
      logging: false,
    })
    previewUrl.value = canvas.toDataURL('image/png')
    generated.value = true
    return previewUrl.value
  } catch (e) {
    console.error('生成分享图失败:', e)
    return ''
  } finally {
    generating.value = false
  }
}

// 下载图片
async function download() {
  const url = await generateCard()
  if (!url) return
  const a = document.createElement('a')
  a.href = url
  a.download = `分享-${props.article?.title || '文章'}.png`
  a.click()
}

onMounted(() => {
  currentStyle.value = randomStyle()
  generateCard()
})
</script>

<template>
  <div class="share-mask" @click.self="emit('close')">
    <div class="share-panel">
      <div class="share-head">
        <span class="share-title">分享到社交平台</span>
        <button class="share-close" @click="emit('close')">✕</button>
      </div>

      <!-- 随机风格 -->
      <div class="style-picker">
        <span class="style-label">风格</span>
        <button class="style-shuffle" :disabled="generating" title="随机换风格" aria-label="随机换风格" @click="shuffleStyle">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M21 12a9 9 0 1 1-2.64-6.36" />
            <polyline points="21 3 21 9 15 9" />
          </svg>
        </button>
      </div>

      <!-- 预览图 -->
      <div class="share-preview">
        <img v-if="previewUrl" :src="previewUrl" alt="分享图预览" />
        <div v-else class="share-loading">{{ generating ? '生成中…' : '准备中…' }}</div>
      </div>
      <p class="share-tip">手机上长按图片即可保存分享；电脑上点击下方按钮下载。</p>

      <div class="share-actions">
        <button class="share-btn primary" :disabled="generating" @click="download">
          {{ generating ? '生成中…' : '保存图片' }}
        </button>
        <button class="share-btn ghost" @click="emit('close')">关闭</button>
      </div>
    </div>

    <!-- 渲染目标：移出视口，供 html2canvas 绘制（750×1000） -->
    <div ref="cardRef" class="render-target">
      <div class="share-card" :class="'style-' + currentStyle">
        <div class="card-brand">JScreator</div>
        <h2 class="card-title">{{ article.title }}</h2>
        <p class="card-excerpt">{{ excerpt }}</p>
        <div class="card-bottom">
          <div class="card-author">
            <img v-if="authorAvatar" :src="authorAvatar" class="card-avatar" alt="" />
            <span v-else class="card-avatar card-avatar-fallback">{{ authorInitial }}</span>
            <div class="card-author-info">
              <div class="card-author-name">{{ authorName }}</div>
              <div class="card-author-site">JScreator 博客</div>
            </div>
          </div>
          <div class="card-qr">
            <img v-if="qrDataUrl" :src="qrDataUrl" alt="二维码" />
            <div class="card-qr-text">扫码阅读全文</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.share-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

/* 半透明毛玻璃面板 */
.share-panel {
  width: 100%;
  max-width: 400px;
  background-color: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 14px;
  padding: 18px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.3);
}
.dark .share-panel {
  background-color: rgba(24, 26, 34, 0.55);
  border-color: rgba(255, 255, 255, 0.12);
}

.share-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.share-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-ink);
}

.share-close {
  border: none;
  background: transparent;
  font-size: 16px;
  color: var(--color-muted);
  cursor: pointer;
  padding: 4px;
}

/* 风格选择器 */
.style-picker {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
}
.style-label {
  font-size: 13px;
  color: var(--color-muted);
  margin-right: 2px;
}
.style-shuffle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}
.style-shuffle:hover {
  background-color: color-mix(in oklab, var(--color-accent) 10%, transparent);
  color: var(--color-accent);
}
.style-shuffle:disabled {
  opacity: 0.5;
  cursor: default;
}
.style-shuffle svg {
  width: 16px;
  height: 16px;
}

.share-preview {
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
  background-color: var(--color-page);
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
}

.share-preview img {
  width: 100%;
  display: block;
  image-rendering: auto;
}

.share-loading {
  font-size: 14px;
  color: var(--color-muted);
  padding: 60px 0;
}

.share-tip {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-faint);
  text-align: center;
}

.share-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.share-btn {
  flex: 1;
  padding: 10px 0;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: opacity 0.2s;
}

.share-btn.primary {
  background-color: var(--color-accent);
  color: var(--color-on-accent);
}

.share-btn.ghost {
  background-color: transparent;
  color: var(--color-body);
  border: 1px solid var(--color-line);
}

.share-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

/* ---- 渲染目标（移出视口，html2canvas 使用） ---- */
.render-target {
  position: fixed;
  left: -9999px;
  top: 0;
  z-index: -1;
}

/* ---- 分享卡片（风格由 CSS 变量驱动） ---- */
.share-card {
  --card-bg: linear-gradient(160deg, #16213e 0%, #1a3a6b 45%, #0f2027 100%);
  --card-text: #ffffff;
  --card-muted: rgba(255, 255, 255, 0.55);
  --card-soft: rgba(255, 255, 255, 0.88);

  width: 750px;
  height: 1000px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  padding: 64px 52px 44px;
  color: var(--card-text);
  background: var(--card-bg);
  position: relative;
  overflow: hidden;
}

.share-card::after {
  content: '';
  position: absolute;
  right: -120px;
  top: -120px;
  width: 380px;
  height: 380px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.12) 0%, transparent 70%);
}

/* 风格：黑金 */
.style-gold {
  --card-bg: linear-gradient(160deg, #1a1a2e 0%, #16213e 60%, #0f0f1a 100%);
  --card-text: #f5f0e6;
  --card-muted: rgba(245, 240, 230, 0.55);
  --card-soft: rgba(245, 240, 230, 0.9);
}
.style-gold .card-brand {
  color: #d4af37;
}

/* 风格：清新浅色 */
.style-fresh {
  --card-bg: linear-gradient(160deg, #eef4ff 0%, #d9e8ff 55%, #f7fbff 100%);
  --card-text: #1e3a5f;
  --card-muted: rgba(30, 58, 95, 0.55);
  --card-soft: rgba(30, 58, 95, 0.88);
}
.style-fresh::after {
  background: radial-gradient(circle, rgba(42, 111, 176, 0.15) 0%, transparent 70%);
}
.style-fresh .card-brand {
  color: #2a6fb0;
}
.style-fresh .card-avatar-fallback {
  background-color: #2a6fb0;
  color: #ffffff;
}

/* 风格：暖橙 */
.style-warm {
  --card-bg: linear-gradient(160deg, #ff6b6b 0%, #ff8e53 48%, #f7b733 100%);
  --card-text: #ffffff;
  --card-muted: rgba(255, 255, 255, 0.7);
  --card-soft: rgba(255, 255, 255, 0.95);
}
.style-warm::after {
  background: radial-gradient(circle, rgba(255, 255, 255, 0.18) 0%, transparent 70%);
}

.card-brand {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--card-muted);
  margin-bottom: 36px;
}

.card-title {
  font-size: 42px;
  font-weight: 800;
  line-height: 1.3;
  margin: 0 0 28px;
  color: var(--card-text);
}

.card-excerpt {
  flex: 1;
  font-size: 23px;
  line-height: 1.9;
  color: var(--card-soft);
  overflow: hidden;
}

.card-bottom {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 28px;
}

.card-author {
  display: flex;
  align-items: center;
  gap: 18px;
}

.card-avatar {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  object-fit: cover;
  background-color: #ffffff;
}

.card-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  font-weight: 800;
  color: #16213e;
}

.card-author-name {
  font-size: 24px;
  font-weight: 700;
  color: var(--card-text);
}

.card-author-site {
  font-size: 16px;
  color: var(--card-muted);
  margin-top: 4px;
}

.card-qr {
  text-align: center;
}

.card-qr img {
  width: 158px;
  height: 158px;
  background: #ffffff;
  padding: 10px;
  border-radius: 10px;
  box-sizing: border-box;
}

.card-qr-text {
  font-size: 15px;
  color: var(--card-muted);
  margin-top: 8px;
}
</style>
