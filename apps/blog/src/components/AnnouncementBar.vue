<script setup>
import { ref, onMounted } from 'vue'
import http from '@/api/http'

/**
 * 站点公告横幅（参考 Vue 官网顶部公告条）。
 * 显示最新一条启用公告；点击看全文弹窗；关闭后记 localStorage 不再显示。
 */
const announcement = ref(null)
const detailVisible = ref(false)
const STORAGE_KEY = 'announcement_closed'

function getClosedId() {
  try {
    return Number(localStorage.getItem(STORAGE_KEY)) || 0
  } catch {
    return 0
  }
}

async function fetchAnnouncement() {
  try {
    const res = await http.get('/announcement/latest')
    const item = (res && res.data && res.data.announcement) || null
    // 关闭过的最新公告不再显示
    if (item && Number(item.id) !== getClosedId()) {
      announcement.value = item
    } else {
      announcement.value = null
    }
  } catch (e) {
    announcement.value = null
  }
}

function openDetail() {
  if (announcement.value) detailVisible.value = true
}

function closeBar() {
  if (announcement.value) {
    try {
      localStorage.setItem(STORAGE_KEY, String(announcement.value.id))
    } catch (e) {}
  }
  announcement.value = null
}

onMounted(fetchAnnouncement)
</script>

<template>
  <div v-if="announcement" class="announce-bar">
    <button class="announce-inner" type="button" @click="openDetail">
      <span class="announce-badge">公告</span>
      <span class="announce-title">{{ announcement.title }}</span>
      <span class="announce-arrow">查看详情 →</span>
    </button>
    <button class="announce-close" type="button" aria-label="关闭公告" title="关闭" @click="closeBar">
      <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
        <path d="M18 6L6 18M6 6l12 12" />
      </svg>
    </button>

    <!-- 公告全文弹窗 -->
    <div
      v-if="detailVisible"
      class="announce-modal"
      @click.self="detailVisible = false"
    >
      <div class="announce-modal-card">
        <div class="announce-modal-head">
          <h3 class="text-lg font-bold">{{ announcement.title }}</h3>
          <button class="announce-modal-close" type="button" aria-label="关闭" @click="detailVisible = false">
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div class="announce-modal-body">{{ announcement.content }}</div>
        <div class="announce-modal-foot">
          <button class="announce-modal-btn" type="button" @click="detailVisible = false">知道了</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@reference "../style.css";

.announce-bar {
  position: relative;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 7px 44px 7px 16px;
  background: linear-gradient(90deg, #1e3a5f, #2c3e50);
  color: #fff;
  font-size: 13px;
}
.announce-inner {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: calc(100vw - 120px);
  background: none;
  border: none;
  color: #fff;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
  overflow: hidden;
}
.announce-badge {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f6b93b;
  color: #2c3e50;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
}
.announce-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.announce-arrow {
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
}
.announce-arrow:hover {
  color: #fff;
}
.announce-close {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  cursor: pointer;
  transition: background-color 0.2s ease;
}
.announce-close:hover {
  background: rgba(255, 255, 255, 0.28);
}

/* 全文弹窗 */
.announce-modal {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(0, 0, 0, 0.5);
}
.announce-modal-card {
  width: 100%;
  max-width: 480px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  background: var(--color-card);
  color: var(--color-ink);
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.3);
}
.announce-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-line);
}
.announce-modal-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
}
.announce-modal-close:hover {
  background: color-mix(in oklab, var(--color-accent) 10%, transparent);
}
.announce-modal-body {
  padding: 18px 20px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-body);
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.announce-modal-foot {
  padding: 12px 20px;
  border-top: 1px solid var(--color-line);
  text-align: right;
}
.announce-modal-btn {
  padding: 7px 22px;
  border: none;
  border-radius: 999px;
  background: var(--color-accent);
  color: var(--color-on-accent);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.announce-modal-btn:hover {
  background: var(--color-accent-strong);
}
</style>
