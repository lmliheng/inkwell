<script setup lang="ts">
import { reactive, ref, onBeforeUnmount } from 'vue'
import { sendEmailCode, emailLogin } from '../composables/useRequest'
import { useRouter } from 'vue-router'
import { api } from '../composables/useAxiosConfig'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '../store/auth'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const message = useMessage()
const router = useRouter()
const authStore = useAuthStore()

const activeTab = ref('email')

const emailForm = reactive({
    email: '',
    code: ''
})
const emailLoading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let sendTimer: ReturnType<typeof setInterval> | null = null

async function sendCode() {
    if (!emailForm.email.trim()) {
        message.warning(t('email_required'))
        return
    }
    if (countdown.value > 0) return
    sending.value = true
    try {
        await sendEmailCode(emailForm.email.trim())
        message.success(t('code_sent'))
        countdown.value = 60
        sendTimer = setInterval(() => {
            countdown.value--
            if (countdown.value <= 0 && sendTimer) clearInterval(sendTimer)
        }, 1000)
    } catch (e) {
        message.error(t('send_failed'))
    } finally {
        sending.value = false
    }
}

async function submitEmail() {
    if (!emailForm.email.trim() || !emailForm.code.trim()) {
        message.warning(t('email_code_required'))
        return
    }
    emailLoading.value = true
    try {
        const res = await emailLogin(emailForm.email.trim(), emailForm.code.trim())
        if (res.code === 200) {
            authStore.setToken(res.token)
            authStore.setTokenTime(new Date().toLocaleString())
            message.success(t('register_success'))
            router.push('/')
        } else {
            message.error(res.message || t('register_failed'))
        }
    } catch (e) {
        message.error(t('register_failed'))
    } finally {
        emailLoading.value = false
    }
}

const API_BASE = api.defaults.baseURL

function githubRegister() {
    const redirect = window.location.origin + '/#/auth'
    window.location.href = API_BASE + '/auth/github?redirect=' + encodeURIComponent(redirect)
}

onBeforeUnmount(() => {
    if (sendTimer) clearInterval(sendTimer)
})
</script>

<template>
    <n-spin :show="emailLoading">
        <n-tabs v-model:value="activeTab" type="segment" animated>
            <n-tab-pane name="email" :tab="$t('email_register')">
                <n-form>
                    <n-form-item :label="$t('email')">
                        <n-input v-model:value="emailForm.email" placeholder="you@example.com" size="large" />
                    </n-form-item>
                    <n-form-item :label="$t('verify_code')">
                        <div style="display: flex; gap: 8px; width: 100%;">
                            <n-input v-model:value="emailForm.code" :placeholder="$t('code_placeholder')" maxlength="6"
                                size="large" style="flex: 1;" />
                            <n-button :disabled="sending || countdown > 0" :loading="sending" @click="sendCode"
                                size="large">
                                {{ countdown > 0 ? countdown + 's' : (sending ? $t('sending') : $t('send_code')) }}
                            </n-button>
                        </div>
                    </n-form-item>
                </n-form>
                <n-button type="primary" block size="large" :loading="emailLoading" @click="submitEmail"
                    style="margin-top: 8px;">
                    {{ $t('register') }}
                </n-button>
            </n-tab-pane>
        </n-tabs>

        <n-divider>{{ $t('other_register') }}</n-divider>
        <div class="social-login">
            <n-button quaternary circle size="large" @click="githubRegister">
                <template #icon>
                    <svg class="gh-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                        <path
                            d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.56 0-.28-.01-1.02-.02-2-3.2.7-3.88-1.54-3.88-1.54-.52-1.33-1.28-1.68-1.28-1.68-1.04-.71.08-.7.08-.7 1.15.08 1.76 1.18 1.76 1.18 1.02 1.75 2.69 1.25 3.35.95.1-.74.4-1.25.72-1.54-2.55-.29-5.23-1.28-5.23-5.68 0-1.26.45-2.29 1.18-3.09-.12-.29-.51-1.46.11-3.05 0 0 .96-.31 3.15 1.18a10.9 10.9 0 0 1 5.74 0c2.19-1.49 3.15-1.18 3.15-1.18.62 1.59.23 2.76.11 3.05.73.8 1.18 1.83 1.18 3.09 0 4.41-2.69 5.38-5.25 5.67.41.35.78 1.05.78 2.12 0 1.53-.01 2.76-.01 3.14 0 .31.21.67.8.56A11.52 11.52 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5z" />
                    </svg>
                </template>
            </n-button>
        </div>
    </n-spin>
</template>

<style scoped>
.social-login {
    display: flex;
    gap: 12px;
    justify-content: center;
}

.gh-icon {
    width: 20px;
    height: 20px;
}
</style>
