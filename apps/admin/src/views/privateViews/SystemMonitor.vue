<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { requestSystemMonitor, requestApiStats, requestBackupDownload } from '@/composables/useRequest'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const data = ref<any>(null)
const apiStats = ref<any[]>([])
let timer: ReturnType<typeof setInterval> | null = null

// 数据库备份下载状态
const backupLoading = ref(false)

const downloadBackup = async () => {
    backupLoading.value = true
    try {
        const res = await requestBackupDownload()
        if (res && res.blob) {
            const url = URL.createObjectURL(res.blob)
            const a = document.createElement('a')
            a.href = url
            a.download = res.filename || `backup-${Date.now()}.zip`
            document.body.appendChild(a)
            a.click()
            document.body.removeChild(a)
            URL.revokeObjectURL(url)
            ElMessage.success('数据库备份已下载')
        } else {
            ElMessage.error('备份下载失败')
        }
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '备份下载失败')
    } finally {
        backupLoading.value = false
    }
}

const formatBytes = (bytes: any) => {
    if (bytes == null) return '-'
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
    return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

const formatUptime = (seconds: any) => {
    if (seconds == null) return '-'
    const d = Math.floor(seconds / 86400)
    const h = Math.floor((seconds % 86400) / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    const s = Math.floor(seconds % 60)
    if (d > 0) return `${d} 天 ${h} 小时 ${m} 分`
    if (h > 0) return `${h} 小时 ${m} 分`
    if (m > 0) return `${m} 分 ${s} 秒`
    return `${s} 秒`
}

const formatTime = (v: any) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')

// 接口路径拆分：path 形如 "GET /article/list"
const methodOf = (p: any) => String(p || '').split(' ')[0] || ''
const pathOf = (p: any) => String(p || '').split(' ').slice(1).join(' ') || String(p || '')
const methodTag = (p: any) => {
    const map: Record<string, string> = { GET: 'success', POST: 'primary', PUT: 'warning', DELETE: 'danger', PATCH: 'warning' }
    return map[methodOf(p)] || 'info'
}

const loadText = computed(() => {
    const la = data.value?.cpu?.loadavg
    if (!Array.isArray(la)) return '-'
    return la.map((n) => n.toFixed(2)).join(' / ')
})

const cpuText = computed(() => {
    const u = data.value?.cpu?.usage
    return u == null ? '-' : `${u}%`
})

const loadData = async () => {
    try {
        const res = await requestSystemMonitor()
        if (res.code === 200) {
            data.value = res.data
        } else {
            ElMessage.error(res.message || '获取系统监控失败')
        }
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '获取系统监控失败')
    } finally {
        loading.value = false
    }
}

const loadApiStats = async () => {
    try {
        const res = await requestApiStats()
        if (res.code === 200 && res.data) {
            apiStats.value = res.data.list || []
        }
    } catch (e) {
        // 静默失败，不阻塞主监控
    }
}

onMounted(() => {
    loading.value = true
    loadData()
    loadApiStats()
    timer = setInterval(() => {
        loadData()
        loadApiStats()
    }, 5000)
})

onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
})
</script>

<template>
    <div v-loading="loading">
        <!-- 顶部指标卡片 -->
        <el-row :gutter="16" class="mb16">
            <el-col :span="6">
                <el-card shadow="never">
                    <div class="metric">
                        <div class="metric-label">CPU 使用率</div>
                        <div class="metric-value">{{ cpuText }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="never">
                    <div class="metric">
                        <div class="metric-label">内存使用率</div>
                        <div class="metric-value">{{ data ? data.memory.usagePercent + '%' : '-' }}</div>
                        <el-progress
                            :percentage="data ? data.memory.usagePercent : 0"
                            :show-text="false"
                            :stroke-width="8"
                        />
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="never">
                    <div class="metric">
                        <div class="metric-label">系统负载 (1/5/15 分钟)</div>
                        <div class="metric-value metric-small">{{ loadText }}</div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="never">
                    <div class="metric">
                        <div class="metric-label">数据库</div>
                        <div class="metric-value">
                            <el-tag :type="data?.db?.connected ? 'success' : 'danger'" size="large">
                                {{ data?.db?.connected ? '连接正常' : '连接异常' }}
                            </el-tag>
                        </div>
                        <div v-if="data?.db?.version" class="metric-sub">MySQL {{ data.db.version }}</div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 数据库备份 -->
        <el-card shadow="never" class="backup-card">
            <div class="backup-row">
                <div class="backup-info">
                    <div class="backup-title">数据库备份下载</div>
                    <div class="backup-desc">
                        导出云数据库全部表的结构与数据，打包为 zip（含 .sql + README）。
                        <br />兼容 MySQL 5.7（utf8mb4_general_ci），含敏感数据请妥善保管。
                    </div>
                </div>
                <el-button
                    type="primary"
                    size="large"
                    :loading="backupLoading"
                    @click="downloadBackup"
                >
                    <template v-if="!backupLoading">⬇ 下载备份 ZIP</template>
                    <template v-else>正在导出…</template>
                </el-button>
            </div>
        </el-card>

        <!-- 详情 -->
        <el-row :gutter="16">
            <el-col :span="12">
                <el-card shadow="never" header="系统信息">
                    <el-descriptions :column="1" border>
                        <el-descriptions-item label="主机名">{{ data?.system?.hostname || '-' }}</el-descriptions-item>
                        <el-descriptions-item label="操作系统">
                            {{ data?.system?.osType || '-' }} {{ data?.system?.osRelease || '' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="平台 / 架构">
                            {{ data?.system?.platform || '-' }} / {{ data?.system?.arch || '-' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="CPU">
                            {{ data?.system?.cpuModel || '-' }}（{{ data?.system?.cpuCores || 0 }} 核）
                        </el-descriptions-item>
                        <el-descriptions-item label="系统运行时长">{{ formatUptime(data?.system?.uptime) }}</el-descriptions-item>
                    </el-descriptions>
                </el-card>
            </el-col>
            <el-col :span="12">
                <el-card shadow="never" header="内存与进程">
                    <el-descriptions :column="1" border>
                        <el-descriptions-item label="内存总量">{{ formatBytes(data?.memory?.total) }}</el-descriptions-item>
                        <el-descriptions-item label="已用 / 空闲">
                            {{ formatBytes(data?.memory?.used) }} / {{ formatBytes(data?.memory?.free) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="Node 版本">{{ data?.process?.nodeVersion || '-' }}</el-descriptions-item>
                        <el-descriptions-item label="进程 PID">{{ data?.process?.pid || '-' }}</el-descriptions-item>
                        <el-descriptions-item label="进程运行时长">{{ formatUptime(data?.process?.uptime) }}</el-descriptions-item>
                        <el-descriptions-item label="进程内存 (RSS)">{{ formatBytes(data?.process?.memoryRss) }}</el-descriptions-item>
                        <el-descriptions-item label="堆内存">
                            {{ formatBytes(data?.process?.heapUsed) }} / {{ formatBytes(data?.process?.heapTotal) }}
                        </el-descriptions-item>
                    </el-descriptions>
                </el-card>
            </el-col>
        </el-row>

        <!-- 接口监控 -->
        <el-card shadow="never" header="接口调用统计" class="mt16">
            <el-table :data="apiStats" border stripe size="small" max-height="420">
                <el-table-column type="index" label="序号" width="70" align="center" />
                <el-table-column align="left" label="接口" min-width="280">
                    <template #default="scope">
                        <el-tag :type="methodTag(scope.row.path)" size="small" style="margin-right: 6px">
                            {{ methodOf(scope.row.path) }}
                        </el-tag>
                        <span>{{ pathOf(scope.row.path) }}</span>
                    </template>
                </el-table-column>
                <el-table-column align="center" prop="count" label="调用次数" width="100" sortable />
                <el-table-column align="center" label="平均耗时" width="110" sortable>
                    <template #default="scope">
                        {{ scope.row.count ? scope.row.avgTime + ' ms' : '-' }}
                    </template>
                </el-table-column>
                <el-table-column align="center" label="错误数" width="100" sortable>
                    <template #default="scope">
                        <el-tag :type="scope.row.errorCount > 0 ? 'danger' : 'success'" size="small">
                            {{ scope.row.errorCount }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column align="center" label="最后调用" width="170">
                    <template #default="scope">
                        {{ scope.row.lastAt ? formatTime(new Date(scope.row.lastAt).toISOString()) : '-' }}
                    </template>
                </el-table-column>
            </el-table>
            <div v-if="!apiStats.length" class="empty-tip">暂无接口调用记录（后端启动后开始统计）</div>
        </el-card>

        <div class="footer-note">每 5 秒自动刷新 · 上次更新：{{ data?.timestamp ? formatTime(data.timestamp) : '-' }}</div>
    </div>
</template>

<style scoped>
.mb16 {
    margin-bottom: 16px;
}

.mt16 {
    margin-top: 16px;
}

.empty-tip {
    padding: 24px 0;
    text-align: center;
    color: #909399;
    font-size: 13px;
}

.metric {
    min-height: 92px;
    display: flex;
    flex-direction: column;
    justify-content: center;
}

.metric-label {
    font-size: 13px;
    color: #909399;
    margin-bottom: 8px;
}

.metric-value {
    font-size: 26px;
    font-weight: bold;
    color: #303133;
    line-height: 1.2;
}

.metric-small {
    font-size: 16px;
}

.metric-sub {
    margin-top: 6px;
    font-size: 12px;
    color: #909399;
}

.footer-note {
    margin-top: 16px;
    text-align: center;
    font-size: 12px;
    color: #909399;
}

/* 数据库备份卡片 */
.backup-card {
    margin-top: 16px;
    border-radius: 8px;
}
.backup-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
}
.backup-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
}
.backup-desc {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.7;
    color: #909399;
}
</style>
