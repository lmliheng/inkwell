<script setup lang="ts">
import { reactive, ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { sendEmailCode } from '@/composables/useRequest'
import { api } from '@/composables/useAxiosConfig'
import { useMessage } from 'naive-ui'

const message = useMessage()
const router = useRouter()

const step = ref(1)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let sendTimer: number = 0

const form = reactive({
    email: '',
    code: '',
    newPassword: '',
    confirmPassword: ''
})

async function sendCode() {
    if (!form.email.trim()) {
        message.warning('请输入邮箱')
        return
    }
    if (countdown.value > 0) return
    sending.value = true
    try {
        await sendEmailCode(form.email.trim())
        message.success('验证码已发送，请查收邮件')
        countdown.value = 60
        sendTimer = setInterval(() => {
            countdown.value--
            if (countdown.value <= 0) clearInterval(sendTimer)
        }, 1000)
    } catch (e) {
        message.error('发送失败')
    } finally {
        sending.value = false
    }
}

function nextStep() {
    if (step.value === 1) {
        if (!form.email.trim()) {
            message.warning('请输入邮箱')
            return
        }
        if (!form.code.trim()) {
            message.warning('请输入验证码')
            return
        }
        step.value = 2
    }
}

async function submitReset() {
    if (!form.newPassword || !form.confirmPassword) {
        message.warning('请输入新密码')
        return
    }
    if (form.newPassword !== form.confirmPassword) {
        message.warning('两次输入的密码不一致')
        return
    }
    if (form.newPassword.length < 6) {
        message.warning('密码长度不能少于 6 位')
        return
    }
    loading.value = true
    try {
        await api({
            url: '/email/reset-password',
            method: 'post',
            data: {
                email: form.email,
                code: form.code,
                password: form.newPassword
            }
        })
        message.success('密码重置成功，请重新登录')
        router.push('/auth')
    } catch (e) {
        message.error('密码重置失败，请检查邮箱和验证码')
    } finally {
        loading.value = false
    }
}

function goBack() {
    router.push('/auth')
}

onBeforeUnmount(() => {
    if (sendTimer) clearInterval(sendTimer)
})
</script>

<template>
    <div class="forgot-container">
        <n-card style="width: 420px;" embedded :bordered="false">
            <n-page-header :title="$t('forgot_password')" @back="goBack" style="margin-bottom: 16px;" />

            <n-steps :current="step" size="small" style="margin-bottom: 24px;">
                <n-step title="验证邮箱" />
                <n-step title="设置新密码" />
            </n-steps>

            <n-spin :show="loading">
                <template v-if="step === 1">
                    <n-form>
                        <n-form-item label="邮箱">
                            <n-input v-model:value="form.email" placeholder="请输入注册时使用的邮箱"
                                size="large" />
                        </n-form-item>
                        <n-form-item label="验证码">
                            <div style="display: flex; gap: 8px; width: 100%;">
                                <n-input v-model:value="form.code" placeholder="请输入 6 位数字" maxlength="6"
                                    size="large" style="flex: 1;" @keyup.enter="nextStep" />
                                <n-button :disabled="sending || countdown > 0" :loading="sending"
                                    @click="sendCode" size="large">
                                    {{ countdown > 0 ? countdown + 's' : (sending ? '发送中…' : '发送验证码') }}
                                </n-button>
                            </div>
                        </n-form-item>
                    </n-form>
                    <n-button type="primary" block size="large" @click="nextStep" style="margin-top: 8px;">
                        下一步
                    </n-button>
                </template>

                <template v-else>
                    <n-form>
                        <n-form-item label="新密码">
                            <n-input v-model:value="form.newPassword" type="password" show-password-on="click"
                                placeholder="请输入新密码（至少 6 位）" size="large" />
                        </n-form-item>
                        <n-form-item label="确认密码">
                            <n-input v-model:value="form.confirmPassword" type="password"
                                show-password-on="click" placeholder="请再次输入新密码" size="large"
                                @keyup.enter="submitReset" />
                        </n-form-item>
                    </n-form>
                    <n-button type="primary" block size="large" :loading="loading" @click="submitReset"
                        style="margin-top: 8px;">
                        重置密码
                    </n-button>
                </template>
            </n-spin>
        </n-card>
    </div>
</template>

<style scoped>
.forgot-container {
    width: 100vw;
    height: 100vh;
    display: flex;
    justify-content: center;
    align-items: center;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
</style>
