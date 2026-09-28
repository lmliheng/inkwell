<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
    requestNotificationList,
    requestNotificationRead
} from '@/composables/useRequest'

const loading = ref(false)
const list = ref<any[]>([])

// 详情弹窗
const dialogVisible = ref(false)
const current = ref<any>(null)

const typeMap: Record<string, string> = { system: '系统', announcement: '公告', reminder: '提醒' }
const typeTagMap: Record<string, string> = { system: 'danger', announcement: 'success', reminder: 'warning' }
const importanceMap: Record<string, string> = { high: '高', medium: '中', low: '低' }
const importanceTagMap: Record<string, string> = { high: 'danger', medium: 'warning', low: 'info' }

const unreadCount = computed(() => list.value.filter(item => Number(item.is_read) === 0).length)

const getList = async () => {
    loading.value = true
    try {
        const res = await requestNotificationList()
        if (res.code === 200 && res.data) {
            list.value = res.data.list || []
        } else {
            ElMessage.error(res.message || '获取通知列表失败')
        }
    } catch (e) {
        ElMessage.error('获取通知列表失败')
    } finally {
        loading.value = false
    }
}

// 点击查看：弹 dialog 并标记已读
const openDetail = async (row: any) => {
    current.value = row
    dialogVisible.value = true
    if (Number(row.is_read) === 0) {
        try {
            const res = await requestNotificationRead(row.notification_id)
            if (res.code === 200) {
                row.is_read = 1
            }
        } catch (e) {
            // 标记失败不阻塞查看
        }
    }
}

const handleReadAll = async () => {
    const unread = list.value.filter(item => Number(item.is_read) === 0)
    if (unread.length === 0) {
        ElMessage.info('没有未读通知')
        return
    }
    for (const item of unread) {
        try {
            await requestNotificationRead(item.notification_id)
            item.is_read = 1
        } catch (e) {
            // 忽略单条失败
        }
    }
    ElMessage.success('已全部标记为已读')
}

onMounted(getList)
</script>

<template>
    <div>
        <div class="head">
            <div>
                <span class="title">通知中心</span>
                <el-tag v-if="unreadCount > 0" type="danger" size="small">
                    {{ unreadCount }} 未读
                </el-tag>
            </div>
            <el-button type="primary" size="small" @click="handleReadAll">全部已读</el-button>
        </div>

        <el-table :data="list" border stripe v-loading="loading" style="width: 100%">
            <el-table-column align="center" prop="notification_id" label="ID" width="80" />
            <el-table-column align="center" label="通知标题">
                <template #default="scope">
                    <span :style="{ fontWeight: Number(scope.row.is_read) === 0 ? 'bold' : 'normal' }">
                        {{ scope.row.title }}
                    </span>
                </template>
            </el-table-column>
            <el-table-column align="center" prop="content" label="通知内容" show-overflow-tooltip />
            <el-table-column align="center" label="类型" width="80">
                <template #default="scope">
                    <el-tag size="small" :type="typeTagMap[scope.row.type] || 'info'">
                        {{ typeMap[scope.row.type] || scope.row.type || '-' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="重要性" width="80">
                <template #default="scope">
                    <el-tag size="small" :type="importanceTagMap[scope.row.importance] || 'info'">
                        {{ importanceMap[scope.row.importance] || scope.row.importance || '-' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="状态" width="80">
                <template #default="scope">
                    <el-tag size="small" :type="Number(scope.row.is_read) === 1 ? 'info' : 'danger'">
                        {{ Number(scope.row.is_read) === 1 ? '已读' : '未读' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" prop="created_at" label="时间" width="170" />
            <el-table-column align="center" label="操作" width="100">
                <template #default="scope">
                    <el-button type="primary" size="small" @click="openDetail(scope.row)">查看</el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 通知详情弹窗 -->
        <el-dialog v-model="dialogVisible" title="通知详情" width="520px">
            <div v-if="current">
                <div class="detail-title">{{ current.title }}</div>
                <div class="detail-meta">
                    <el-tag size="small" :type="typeTagMap[current.type] || 'info'">
                        {{ typeMap[current.type] || current.type || '-' }}
                    </el-tag>
                    <el-tag size="small" :type="importanceTagMap[current.importance] || 'info'">
                        重要性：{{ importanceMap[current.importance] || current.importance || '-' }}
                    </el-tag>
                    <span class="detail-time">{{ current.created_at }}</span>
                </div>
                <div class="detail-content">{{ current.content }}</div>
            </div>
            <template #footer>
                <el-button @click="dialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style scoped>
.head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
}

.title {
    font-size: 16px;
    font-weight: bold;
    margin-right: 8px;
}

.detail-title {
    font-size: 18px;
    font-weight: bold;
    color: #303133;
}

.detail-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 8px;
}

.detail-time {
    font-size: 12px;
    color: #909399;
}

.detail-content {
    margin-top: 16px;
    padding: 12px;
    background-color: #f5f7fa;
    border-radius: 4px;
    font-size: 14px;
    color: #606266;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
}
</style>
