<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { sendEmailCode, emailLogin } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const toast = useToastStore()

const mode = ref('password') // 'password' | 'email'

// 密码注册
const username = ref('')
const email = ref('')
const password = ref('')
const confirm = ref('')
const loading = ref(false)

// 邮箱验证码注册
const email2 = ref('')
const code = ref('')
const emailLoading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let sendTimer = null

async function submit() {
  if (!username.value.trim() || !email.value.trim() || !password.value) {
    toast.error('请完整填写注册信息')
    return
  }
  if (password.value !== confirm.value) {
    toast.error('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await auth.register({
      username: username.value.trim(),
      email: email.value.trim(),
      password: password.value,
    })
    toast.success('注册成功')
    router.push('/')
  } catch (e) {
    toast.error(e.message || '注册失败')
  } finally {
    loading.value = false
  }
}

async function sendCode() {
  if (!email2.value.trim()) {
    toast.error('请输入邮箱')
    return
  }
  if (countdown.value > 0) return
  sending.value = true
  try {
    await sendEmailCode(email2.value.trim())
    toast.success('验证码已发送，请查收邮件')
    countdown.value = 60
    sendTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(sendTimer)
    }, 1000)
  } catch (e) {
    toast.error(e.message || '发送失败')
  } finally {
    sending.value = false
  }
}

async function submitEmail() {
  if (!email2.value.trim() || !code.value.trim()) {
    toast.error('请输入邮箱和验证码')
    return
  }
  emailLoading.value = true
  try {
    const { token, user } = await emailLogin(email2.value.trim(), code.value.trim())
    auth.setSession(token, user)
    toast.success('注册成功')
    router.push('/')
  } catch (e) {
    toast.error(e.message || '注册失败')
  } finally {
    emailLoading.value = false
  }
}

const API_BASE = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:7000'

function githubRegister() {
  const redirect = window.location.origin + '/login'
  window.location.href = API_BASE + '/auth/github?redirect=' + encodeURIComponent(redirect)
}

// GitHub 授权回调后，后端会重定向到 /login?token=...
onMounted(() => {
  const token = typeof route?.query?.token === 'string' ? route.query.token : ''
  if (token) {
    const u = typeof route.query.username === 'string' ? route.query.username : ''
    auth.setSession(token, { username: u })
    toast.success('GitHub 登录成功')
    router.replace({ path: '/', query: {} })
  }
})

onBeforeUnmount(() => {
  if (sendTimer) clearInterval(sendTimer)
})
</script>

<template>
  <div class="mx-auto max-w-md px-4 py-12">
    <div class="card">
      <h1 class="text-2xl font-bold text-ink">注册</h1>
      <p class="mt-2 text-sm text-muted">创建账号后即可发表评论、撰写文章。</p>

      <!-- 注册方式切换 -->
      <div class="mt-5 flex gap-1 rounded-tag bg-line/40 p-1">
        <button
          type="button"
          class="flex-1 rounded-tag px-3 py-1.5 text-sm font-medium"
          :class="mode === 'password' ? 'bg-card text-ink shadow-sm' : 'text-muted'"
          @click="mode = 'password'"
        >密码注册</button>
        <button
          type="button"
          class="flex-1 rounded-tag px-3 py-1.5 text-sm font-medium"
          :class="mode === 'email' ? 'bg-card text-ink shadow-sm' : 'text-muted'"
          @click="mode = 'email'"
        >邮箱注册</button>
      </div>

      <!-- 密码注册 -->
      <form v-if="mode === 'password'" class="mt-6 space-y-4" @submit.prevent="submit">
        <div>
          <label class="field-label" for="username">用户名</label>
          <input id="username" v-model="username" type="text" autocomplete="username" class="input" placeholder="你的用户名" />
        </div>
        <div>
          <label class="field-label" for="email">邮箱</label>
          <input id="email" v-model="email" type="email" autocomplete="email" class="input" placeholder="you@example.com" />
        </div>
        <div>
          <label class="field-label" for="password">密码</label>
          <input id="password" v-model="password" type="password" autocomplete="new-password" class="input" placeholder="••••••••" />
        </div>
        <div>
          <label class="field-label" for="confirm">确认密码</label>
          <input id="confirm" v-model="confirm" type="password" autocomplete="new-password" class="input" placeholder="再次输入密码" />
        </div>
        <button type="submit" class="btn-accent w-full disabled:opacity-60" :disabled="loading">
          {{ loading ? '注册中…' : '注册' }}
        </button>
      </form>

      <!-- 邮箱验证码注册 -->
      <form v-else class="mt-6 space-y-4" @submit.prevent="submitEmail">
        <div>
          <label class="field-label" for="email2">邮箱</label>
          <input id="email2" v-model="email2" type="email" autocomplete="email" class="input" placeholder="you@example.com" />
          <p class="mt-1 text-xs text-faint">用户名将自动使用邮箱前缀</p>
        </div>
        <div>
          <label class="field-label" for="code">验证码</label>
          <div class="flex gap-2">
            <input id="code" v-model="code" type="text" inputmode="numeric" maxlength="6" class="input flex-1" placeholder="请输入 6 位数字" />
            <button
              type="button"
              class="ghost-btn shrink-0 whitespace-nowrap"
              :disabled="sending || countdown > 0"
              @click="sendCode"
            >
              {{ countdown > 0 ? `${countdown}s` : (sending ? '发送中…' : '发送验证码') }}
            </button>
          </div>
        </div>
        <button type="submit" class="btn-accent w-full disabled:opacity-60" :disabled="emailLoading">
          {{ emailLoading ? '注册中…' : '注册' }}
        </button>
      </form>

      <div class="my-4 flex items-center gap-3 text-xs text-faint">
        <span class="h-px flex-1 bg-line"></span>
        <span>或</span>
        <span class="h-px flex-1 bg-line"></span>
      </div>

      <button type="button" class="btn-accent w-full" @click="githubRegister">
        <svg class="mr-2 inline-block h-4 w-4" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.56 0-.28-.01-1.02-.02-2-3.2.7-3.88-1.54-3.88-1.54-.52-1.33-1.28-1.68-1.28-1.68-1.04-.71.08-.7.08-.7 1.15.08 1.76 1.18 1.76 1.18 1.02 1.75 2.69 1.25 3.35.95.1-.74.4-1.25.72-1.54-2.55-.29-5.23-1.28-5.23-5.68 0-1.26.45-2.29 1.18-3.09-.12-.29-.51-1.46.11-3.05 0 0 .96-.31 3.15 1.18a10.9 10.9 0 0 1 5.74 0c2.19-1.49 3.15-1.18 3.15-1.18.62 1.59.23 2.76.11 3.05.73.8 1.18 1.83 1.18 3.09 0 4.41-2.69 5.38-5.25 5.67.41.35.78 1.05.78 2.12 0 1.53-.01 2.76-.01 3.14 0 .31.21.67.8.56A11.52 11.52 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5z" />
        </svg>
        GitHub 注册
      </button>

      <p class="mt-6 text-center text-sm text-body">
        已有账号？
        <RouterLink to="/login" class="text-accent hover:text-accent-strong">去登录</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.field-label {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-muted);
}

.ghost-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem 1rem;
  border-radius: var(--radius-tag);
  border: 1px solid var(--color-line);
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--color-body);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}
.ghost-btn:hover {
  background-color: color-mix(in oklab, var(--color-accent) 8%, transparent);
  color: var(--color-accent);
}
.ghost-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
