<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { requestUserDetail, type UserItem } from '../composables/useRequest'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const id = ref(route.params.id as string)
const loading = ref(false)
const userInfo = ref<UserItem>({} as UserItem)

const formatTime = (v: string | undefined) => (v ? String(v).replace('T', ' ').slice(0, 19) : '')

const getUserDetail = async () => {
    loading.value = true
    try {
        const res = await requestUserDetail(Number(id.value))
        userInfo.value = (res as any).data || {}
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '获取用户详情失败')
    } finally {
        loading.value = false
    }
}

onMounted(() => {
    getUserDetail()
})
</script>

<template>
    <div class="user-info-container">
        <el-card class="card" v-loading="loading">
            <template #header>
                <div class="head">
                    <el-avatar :src="userInfo.avatar" :size="48" />
                    <span class="name">{{ userInfo.username }}</span>
                </div>
            </template>
            <el-descriptions :column="2" border>
                <el-descriptions-item label="用户ID">{{ userInfo.id }}</el-descriptions-item>
                <el-descriptions-item label="用户名">{{ userInfo.username }}</el-descriptions-item>
                <el-descriptions-item label="邮箱">{{ userInfo.email }}</el-descriptions-item>
                <el-descriptions-item label="角色">
                    <el-tag size="small">{{ userInfo.role_name }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="简介">{{ userInfo.bio || '-' }}</el-descriptions-item>
                <el-descriptions-item label="姓名">{{ userInfo.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="地区">{{ userInfo.area || '-' }}</el-descriptions-item>
                <el-descriptions-item label="VIP">{{ userInfo.vip ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ formatTime(userInfo.created_at) }}</el-descriptions-item>
                <el-descriptions-item label="更新时间">{{ formatTime(userInfo.updated_at) }}</el-descriptions-item>
            </el-descriptions>
        </el-card>

        <div class="footer">
            <el-button type="primary" @click="router.back()">返回</el-button>
        </div>
    </div>
</template>

<style scoped>
.user-info-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 100%;
}
.card {
    width: 100%;
    max-width: 800px;
}
.head {
    display: flex;
    align-items: center;
    gap: 12px;
}
.name {
    font-size: 16px;
    font-weight: bold;
}
.footer {
    margin-top: 16px;
}
</style>
