<script setup lang="ts">
import { ref } from 'vue'
import LoginCom from '@/components/LoginCom.vue'
import RegisterCom from '@/components/RegisterCom.vue'
import i18nCom from '@/components/i18nCom.vue'

const isLogin = ref(true)
</script>

<template>
    <div class="auth-page">
        <div class="auth-left">
            <div class="auth-brand">
                <h1>Mind-Admin</h1>
                <p>简洁、高效的后台管理系统</p>
            </div>
        </div>
        <div class="auth-right">
            <div class="auth-form-wrapper">
                <div class="auth-form-header">
                    <h2>{{ isLogin ? '欢迎回来' : '创建账号' }}</h2>
                    <p>{{ isLogin ? '登录您的账号以继续' : '注册新账号开始使用' }}</p>
                </div>

                <n-card embedded :bordered="false" style="width: 100%;">
                    <LoginCom v-if="isLogin" />
                    <RegisterCom v-else />
                </n-card>

                <div class="auth-switch">
                    <n-button text type="primary" @click="isLogin = !isLogin">
                        {{ isLogin ? '没有账号？去注册' : '已有账号？去登录' }}
                    </n-button>
                </div>
            </div>
        </div>
        <div class="auth-i18n">
            <i18nCom />
        </div>
    </div>
</template>

<style scoped>
.auth-page {
    width: 100vw;
    height: 100vh;
    display: flex;
    overflow: hidden;
}

.auth-left {
    flex: 1;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    min-width: 0;
}

.auth-brand {
    text-align: center;
    padding: 40px;
}

.auth-brand h1 {
    font-size: 42px;
    font-weight: 700;
    margin-bottom: 12px;
    letter-spacing: 2px;
}

.auth-brand p {
    font-size: 16px;
    opacity: 0.85;
}

.auth-right {
    width: 480px;
    min-width: 400px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px;
    background: var(--n-color);
}

.auth-form-wrapper {
    width: 100%;
    max-width: 380px;
}

.auth-form-header {
    margin-bottom: 24px;
}

.auth-form-header h2 {
    font-size: 24px;
    font-weight: 600;
    margin-bottom: 8px;
}

.auth-form-header p {
    font-size: 14px;
    color: #999;
}

.auth-switch {
    text-align: center;
    margin-top: 16px;
}

.auth-i18n {
    position: fixed;
    top: 20px;
    right: 24px;
    z-index: 10;
}

@media (max-width: 768px) {
    .auth-left {
        display: none;
    }

    .auth-right {
        width: 100%;
        min-width: unset;
    }
}
</style>
