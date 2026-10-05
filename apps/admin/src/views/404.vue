<template>
  <div class="not-found">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>

    <div class="content">
      <!-- 404 数字动画 -->
      <div class="error-code">
        <span class="digit">4</span>
        <span class="digit zero">0</span>
        <span class="digit">4</span>
      </div>

      <!-- 图标 -->
      <div class="icon-wrapper">
        <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/>
          <line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
      </div>

      <!-- 提示文字 -->
      <h1 class="title">页面未找到</h1>
      <p class="description">您访问的页面好像飘走了～</p>
      <p class="hint">试试检查链接是否正确，或者返回首页看看</p>

      <!-- 操作按钮 -->
      <div class="actions">
        <button class="btn btn-primary" @click="goHome">
          <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/>
          </svg>
          返回首页
        </button>
        <button class="btn btn-outline" @click="goBack">
          <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
          返回上一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/')
}

function goBack() {
  router.back()
}
</script>

<style scoped>
.not-found {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  overflow: hidden;
  background: linear-gradient(135deg, #e8f4fd 0%, #d4edfa 50%, #bee3f8 100%);
}

/* 背景装饰圆 */
.bg-decoration {
  position: absolute;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
}

.circle-1 {
  width: 400px;
  height: 400px;
  top: -100px;
  right: -100px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  animation: float 6s ease-in-out infinite;
}

.circle-2 {
  width: 250px;
  height: 250px;
  bottom: 50px;
  left: -50px;
  background: linear-gradient(135deg, #60a5fa, #3b82f6);
  animation: float 8s ease-in-out infinite reverse;
}

.circle-3 {
  width: 150px;
  height: 150px;
  top: 35%;
  left: 70%;
  background: linear-gradient(135deg, #93c5fd, #60a5fa);
  animation: float 10s ease-in-out infinite 2s;
}

@keyframes float {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-20px) rotate(180deg); }
}

.content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 48px;
}

/* 404 数字 */
.error-code {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin-bottom: 28px;
}

.digit {
  font-size: 140px;
  font-weight: 900;
  line-height: 1;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  text-shadow: none;
}

.zero {
  position: relative;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.05); opacity: 0.85; }
}

/* 搜索图标 */
.icon-wrapper {
  margin-bottom: 24px;
}

.search-icon {
  width: 56px;
  height: 56px;
  color: #3b82f6;
  opacity: 0.6;
}

/* 文字 */
.title {
  font-size: 36px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 12px;
}

.description {
  font-size: 18px;
  color: #475569;
  margin: 0 0 8px;
}

.hint {
  font-size: 14px;
  color: #94a3b8;
  margin: 0 0 36px;
}

/* 按钮 */
.actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 28px;
  border-radius: 999px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s ease;
  border: none;
  outline: none;
}

.btn-icon {
  width: 20px;
  height: 20px;
}

.btn-primary {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.35);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(59, 130, 246, 0.45);
}

.btn-primary:active {
  transform: translateY(0);
}

.btn-outline {
  background: transparent;
  color: #3b82f6;
  border: 2px solid #3b82f6;
}

.btn-outline:hover {
  background: rgba(59, 130, 246, 0.06);
  transform: translateY(-2px);
}

.btn-outline:active {
  transform: translateY(0);
}

/* 响应式 */
@media (max-width: 640px) {
  .digit {
    font-size: 90px;
  }

  .title {
    font-size: 26px;
  }

  .content {
    padding: 24px;
  }

  .actions {
    flex-direction: column;
    align-items: center;
  }

  .btn {
    width: 220px;
    justify-content: center;
  }
}
</style>