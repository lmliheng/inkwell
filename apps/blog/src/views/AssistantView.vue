<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import SiteFooter from '@/components/SiteFooter.vue'
import {
  getDmConversations, getDmMessages, getDmUnreadCount, markDmRead, getMyArticlesList,
} from '@/api/dm'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToastStore()

const activeTab = ref('agent') // agent | dm
const wsUrl = computed(() => {
  const base = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:7000'
  const rawToken = String(auth.token || '').replace(/^Bearer\s+/i, '')
  return base.replace(/^http/, 'ws') + '/ws?token=' + encodeURIComponent(rawToken)
})

let socket = null
const wsConnected = ref(false)

// ============ Agent 聊天 ============
const agentMode = ref('profile') // profile | article
const agentMessages = ref([]) // { role: 'user'|'assistant', text }
const agentInput = ref('')
const agentBusy = ref(false)
const agentAccum = ref('')
const myArticles = ref([])

const AGENT_HINTS = {
  profile: ['把简介改成 xxx', '地区改成 北京', '我的昵称改成 xx', '给我换成 GitHub 头像链接'],
  article: ['优化我的文章（选下方文章）', '帮我分析下面这段文字…', '改写这段内容'],
}

async function loadMyArticles() {
  try {
    const res = await getMyArticlesList({ page: 1, pageSize: 50 })
    myArticles.value = (res && res.list) || []
  } catch (e) {
    myArticles.value = []
  }
}

function switchAgent(mode) {
  agentMode.value = mode
  if (mode === 'article' && !myArticles.value.length) loadMyArticles()
}

function pickArticle(id) {
  const a = myArticles.value.find((x) => Number(x.article_id) === Number(id))
  if (!a) return
  agentMessages.value.push({ role: 'user', text: `请优化我的文章《${a.title}》` })
  sendAgent({ agent: 'article', articleId: a.article_id, content: '' })
}

function sendAgentMessage() {
  const content = agentInput.value.trim()
  if (!content || agentBusy.value) return
  agentMessages.value.push({ role: 'user', text: content })
  agentInput.value = ''
  sendAgent({ agent: agentMode.value, content })
}

function sendAgent(payload) {
  if (!socket || socket.readyState !== 1) {
    toast.error('连接未就绪，请稍候')
    return
  }
  agentBusy.value = true
  agentAccum.value = ''
  agentMessages.value.push({ role: 'assistant', text: '', typing: true })
  socket.send(JSON.stringify({ type: 'agent', ...payload }))
}

function pickHint(h) {
  agentInput.value = h
}

// ============ 私信 DM ============
const conversations = ref([])
const dmUnread = ref(0)
const activeConv = ref(null) // { id, username, name, avatar }
const dmMessages = ref([])
const dmInput = ref('')
const dmBusy = ref(false)

async function refreshConvs() {
  try {
    const data = await getDmConversations()
    conversations.value = (data && data.list) || []
    const un = await getDmUnreadCount()
    dmUnread.value = Number(un && un.count) || 0
  } catch (e) {
    /* ignore */
  }
}

async function openConv(other) {
  activeConv.value = other
  try {
    const data = await getDmMessages(other.id)
    dmMessages.value = (data && data.list) || []
    markDmRead(other.id).catch(() => {})
    // 本地清未读
    const c = conversations.value.find((x) => Number(x.other_username ? other.id : 0))
    void c
    refreshConvs()
    scrollDm()
  } catch (e) {
    /* ignore */
  }
}

function sendDm() {
  const content = dmInput.value.trim()
  if (!content || !activeConv.value || dmBusy.value) return
  dmBusy.value = true
  dmMessages.value.push({ content, sender_id: auth.userId, created_at: new Date().toISOString(), mine: true })
  socket.send(JSON.stringify({ type: 'dm', to: activeConv.value.id, content }))
  dmInput.value = ''
  dmBusy.value = false
  scrollDm()
}

function onDmReceived(m) {
  if (activeConv.value && Number(m.from) === Number(activeConv.value.id)) {
    dmMessages.value.push({ ...m, mine: false })
    markDmRead(m.from).catch(() => {})
    scrollDm()
  } else {
    dmUnread.value++
  }
  refreshConvs()
}

function scrollAgent() {
  nextTick(() => {
    const el = document.querySelector('.agent-messages')
    if (el) el.scrollTop = el.scrollHeight
  })
}
function scrollDm() {
  nextTick(() => {
    const el = document.querySelector('.dm-messages')
    if (el) el.scrollTop = el.scrollHeight
  })
}

function logout() {
  auth.logout()
  router.push('/')
}

// ============ WS 生命周期 ============
function connectWs() {
  if (!auth.isLoggedIn) return
  if (socket) { socket.close(); socket = null }
  socket = new WebSocket(wsUrl.value)
  socket.onopen = () => {
    wsConnected.value = true
  }
  socket.onclose = () => {
    wsConnected.value = false
    socket = null
  }
  socket.onerror = () => {
    wsConnected.value = false
  }
  socket.onmessage = (e) => {
    let m
    try { m = JSON.parse(e.data) } catch (err) { return }
    if (m.type === 'agent_start') {
      agentAccum.value = ''
    } else if (m.type === 'agent_delta') {
      const last = agentMessages.value[agentMessages.value.length - 1]
      if (last && last.typing) {
        last.text += m.delta
        scrollAgent()
      }
    } else if (m.type === 'agent_done') {
      const last = agentMessages.value[agentMessages.value.length - 1]
      if (last && last.typing) { last.typing = false; last.text = m.reply || last.text }
      agentBusy.value = false
      scrollAgent()
    } else if (m.type === 'agent_error') {
      const last = agentMessages.value[agentMessages.value.length - 1]
      if (last && last.typing) { last.typing = false }
      if (last && last.text === '') last.text = m.message || '出错了'
      agentBusy.value = false
    } else if (m.type === 'dm') {
      onDmReceived(m)
    } else if (m.type === 'connected') {
      // ok
    }
  }
}

onMounted(() => {
  if (!auth.isLoggedIn) {
    router.replace({ path: '/login', query: { redirect: '/assistant' } })
    return
  }
  connectWs()
  refreshConvs()
  loadMyArticles()
  const q = route.query
  if (q.dmto && q.dmname) {
    activeTab.value = 'dm'
    openConv({ id: Number(q.dmto), username: String(q.dmname) })
  }
})

onBeforeUnmount(() => {
  if (socket) socket.close()
})
</script>

<template>
  <div class="min-h-screen bg-page text-body antialiased">
    <!-- 顶部栏 -->
    <header class="as-topbar">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3">
        <div class="flex items-center gap-2">
          <RouterLink to="/" class="as-btn">← 返回</RouterLink>
          <span class="font-bold text-ink">消息中心</span>
        </div>
        <div class="flex items-center gap-2">
          <span v-if="wsConnected" class="as-dot-online"></span>
          <span v-else class="as-dot-offline"></span>
          <span class="text-xs text-faint">{{ wsConnected ? '实时连接中' : '未连接' }}</span>
          <button class="as-btn" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-6xl px-4 py-6">
      <!-- tab 切换 -->
      <div class="as-tabs">
        <button type="button" class="as-tab" :class="{ active: activeTab === 'agent' }" @click="activeTab = 'agent'">AI 助手</button>
        <button type="button" class="as-tab" :class="{ active: activeTab === 'dm' }" @click="activeTab = 'dm'">
          私信
          <span v-if="dmUnread > 0" class="as-unread">{{ dmUnread }}</span>
        </button>
      </div>

      <div class="as-body">
        <!-- ============ Agent 面板 ============ -->
        <div v-if="activeTab === 'agent'" class="as-panel">
          <div class="as-mode-switch">
            <button type="button" :class="{ on: agentMode === 'profile' }" @click="switchAgent('profile')">资料编辑 Agent</button>
            <button type="button" :class="{ on: agentMode === 'article' }" @click="switchAgent('article')">文章优化 Agent</button>
          </div>

          <!-- 文章优化：可选自己的文章 -->
          <div v-if="agentMode === 'article' && myArticles.length" class="as-article-pick">
            <span class="text-xs text-faint">选自己的文章优化：</span>
            <select class="as-select" @change="pickArticle($event.target.value)">
              <option value="">— 选择文章（或下方直接粘贴文本）—</option>
              <option v-for="a in myArticles" :key="a.article_id" :value="a.article_id">{{ a.title }}</option>
            </select>
          </div>

          <div class="agent-messages">
            <div v-for="(m, i) in agentMessages" :key="i" class="msg-row" :class="m.role">
              <div class="msg-bubble" :class="m.role">
                <span v-if="m.typing" class="msg-typing"></span>
                <span class="msg-text">{{ m.text }}</span>
              </div>
            </div>
            <p v-if="!agentMessages.length" class="as-empty">
              我是你的 AI 助手{{ agentMode === 'profile' ? '：告诉我你想怎么改资料' : '：粘贴文章或选一篇文章让我优化' }}
            </p>
          </div>

          <!-- 快捷提示 -->
          <div class="as-hints">
            <button v-for="h in AGENT_HINTS[agentMode]" :key="h" type="button" class="as-hint" @click="pickHint(h)">{{ h }}</button>
          </div>

          <div class="as-input-row">
            <textarea
              v-model="agentInput"
              class="as-textarea"
              rows="2"
              placeholder="输入你的需求…（Enter 发送，Shift+Enter 换行）"
              @keydown.enter.exact.prevent="sendAgentMessage"
            />
            <button class="as-send" :disabled="agentBusy" @click="sendAgentMessage">{{ agentBusy ? '思考中…' : '发送' }}</button>
          </div>
        </div>

        <!-- ============ 私信面板 ============ -->
        <div v-else class="as-panel dm-panel">
          <div class="dm-layout">
            <!-- 会话列表 -->
            <div class="dm-list">
              <p v-if="!conversations.length" class="as-empty">暂无私信会话。去博主主页点「发私信」开始聊天。</p>
              <button v-for="c in conversations" :key="c.id" type="button" class="dm-conv" @click="openConv({ id: c.sender_id === auth.userId ? c.receiver_id : c.sender_id, name: c.other_name, username: c.other_username, avatar: c.other_avatar })">
                <span v-if="c.other_avatar" class="dm-avatar"><img :src="c.other_avatar" alt="" /></span>
                <span v-else class="dm-avatar dm-avatar-text">{{ (c.other_name || c.other_username || '?').charAt(0) }}</span>
                <span class="dm-conv-body">
                  <span class="dm-conv-name">{{ c.other_name || c.other_username }}</span>
                  <span class="dm-conv-last">{{ c.content }}</span>
                </span>
                <span v-if="Number(c.unread) > 0" class="as-unread">{{ c.unread }}</span>
              </button>
            </div>

            <!-- 对话区 -->
            <div class="dm-main">
              <template v-if="activeConv">
                <div class="dm-head">{{ activeConv.name || activeConv.username }}</div>
                <div class="dm-messages">
                  <div v-for="(m, i) in dmMessages" :key="m.id || i" class="msg-row" :class="Number(m.sender_id) === Number(auth.userId) ? 'me' : 'other'">
                    <div class="msg-bubble" :class="Number(m.sender_id) === Number(auth.userId) ? 'me' : 'other'">{{ m.content }}</div>
                  </div>
                  <p v-if="!dmMessages.length" class="as-empty">开始和 {{ activeConv.name || activeConv.username }} 聊天吧</p>
                </div>
                <div class="as-input-row">
                  <textarea v-model="dmInput" class="as-textarea" rows="2" placeholder="输入消息… (Enter 发送)" @keydown.enter.exact.prevent="sendDm" />
                  <button class="as-send" @click="sendDm">发送</button>
                </div>
              </template>
              <div v-else class="dm-placeholder">← 选择左侧会话开始聊天</div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <SiteFooter />
  </div>
</template>

<style scoped>
@reference "../style.css";
.as-topbar {
  background: var(--color-card);
  border-bottom: 1px solid var(--color-line);
}
.as-btn {
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid var(--color-line);
  font-size: 13px;
  color: var(--color-body);
  cursor: pointer;
}
.as-btn:hover { background: color-mix(in oklab, var(--color-accent) 8%, transparent); }
.as-dot-online { width: 8px; height: 8px; border-radius: 50%; background: #2ecc71; }
.as-dot-offline { width: 8px; height: 8px; border-radius: 50%; background: #e74c3c; }
.as-tabs { display: flex; gap: 6px; margin-bottom: 14px; }
.as-tab {
  padding: 8px 22px; border-radius: 999px; border: 1px solid var(--color-line);
  background: var(--color-card); font-size: 14px; font-weight: 700; color: var(--color-muted);
  cursor: pointer; position: relative;
}
.as-tab.active { background: var(--color-accent); color: var(--color-on-accent); }
.as-unread {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 5px; border-radius: 999px;
  background: #e74c3c; color: #fff; font-size: 11px; margin-left: 6px;
}
.as-body { min-height: 60vh; }
.as-panel {
  background: var(--color-card); border: 1px solid var(--color-line);
  border-radius: 16px; display: flex; flex-direction: column; overflow: hidden;
}
.as-mode-switch { display: flex; gap: 0; border-bottom: 1px solid var(--color-line); }
.as-mode-switch button {
  flex: 1; padding: 12px; border: none; background: transparent; font-size: 14px;
  font-weight: 700; color: var(--color-muted); cursor: pointer;
}
.as-mode-switch button.on { background: color-mix(in oklab, var(--color-accent) 8%, transparent); color: var(--color-accent); }
.as-article-pick { display: flex; align-items: center; gap: 10px; padding: 10px 16px; border-bottom: 1px solid var(--color-line); }
.as-select {
  flex: 1; padding: 6px 10px; border: 1px solid var(--color-line); border-radius: 8px;
  background: var(--color-card); color: var(--color-ink); font-size: 13px;
}
.agent-messages, .dm-messages {
  flex: 1; min-height: 320px; max-height: 46vh; overflow-y: auto; padding: 16px;
}
.msg-row { display: flex; margin: 8px 0; }
.msg-row.user, .msg-row.me { justify-content: flex-end; }
.msg-row.other { justify-content: flex-start; }
.msg-bubble {
  max-width: 76%; padding: 10px 14px; border-radius: 14px; font-size: 14px; line-height: 1.7;
  white-space: pre-wrap; word-break: break-word;
}
.msg-bubble.user, .msg-bubble.me { background: var(--color-accent); color: var(--color-on-accent); border-bottom-right-radius: 4px; }
.msg-bubble.assistant, .msg-bubble.other { background: color-mix(in oklab, var(--color-accent) 8%, transparent); color: var(--color-ink); border-bottom-left-radius: 4px; }
.msg-typing {
  display: inline-block; width: 6px; height: 14px; margin-right: 4px;
  background: var(--color-accent); vertical-align: -2px; animation: blink 0.8s infinite;
}
@keyframes blink { 50% { opacity: 0; } }
.as-empty { text-align: center; color: var(--color-faint); font-size: 13px; padding: 40px 20px; }
.as-hints { display: flex; flex-wrap: wrap; gap: 8px; padding: 10px 16px; }
.as-hint {
  padding: 4px 12px; border-radius: 999px; border: 1px dashed var(--color-line);
  background: transparent; color: var(--color-faint); font-size: 12px; cursor: pointer;
}
.as-hint:hover { border-color: var(--color-accent); color: var(--color-accent); }
.as-input-row { display: flex; gap: 10px; padding: 12px 16px; border-top: 1px solid var(--color-line); }
.as-textarea {
  flex: 1; padding: 10px 12px; border: 1px solid var(--color-line); border-radius: 10px;
  background: var(--color-card); color: var(--color-ink); font-size: 14px; resize: none;
}
.as-textarea:focus { outline: none; border-color: var(--color-accent); }
.as-send {
  padding: 0 24px; border: none; border-radius: 10px; background: var(--color-accent);
  color: var(--color-on-accent); font-size: 14px; font-weight: 700; cursor: pointer;
}
.as-send:disabled { opacity: 0.5; }
/* DM */
.dm-layout { display: flex; min-height: 60vh; }
.dm-list { width: 240px; border-right: 1px solid var(--color-line); overflow-y: auto; max-height: 68vh; }
.dm-conv { display: flex; align-items: center; gap: 10px; width: 100%; padding: 10px 14px; border: none; background: transparent; cursor: pointer; text-align: left; }
.dm-conv:hover { background: color-mix(in oklab, var(--color-accent) 6%, transparent); }
.dm-avatar { width: 36px; height: 36px; border-radius: 50%; overflow: hidden; flex-shrink: 0; }
.dm-avatar img { width: 100%; height: 100%; object-fit: cover; }
.dm-avatar-text { display: flex; align-items: center; justify-content: center; background: var(--color-accent); color: var(--color-on-accent); font-weight: 700; }
.dm-conv-body { flex: 1; min-width: 0; }
.dm-conv-name { display: block; font-size: 14px; font-weight: 700; color: var(--color-ink); }
.dm-conv-last { display: block; font-size: 12px; color: var(--color-faint); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dm-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.dm-head { padding: 12px 16px; border-bottom: 1px solid var(--color-line); font-weight: 700; color: var(--color-ink); }
.dm-placeholder { flex: 1; display: flex; align-items: center; justify-content: center; color: var(--color-faint); font-size: 14px; }
@media (max-width: 768px) { .dm-list { width: 130px; } }
</style>
