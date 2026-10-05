<template>
    <div>
        <div class="toolbar">
            <div class="toolbar-info">
                <p class="page-desc">
                    外部 API Key 用于通过开放接口访问本系统数据（<code>/api/v1/*</code>）。
                    明文只在创建时显示一次，请妥善保存；数据库仅存哈希。
                </p>
            </div>
            <div class="toolbar-actions">
                <el-button type="primary" @click="openCreate">新建 API Key</el-button>
            </div>
        </div>

        <el-table :data="list" border stripe v-loading="loading">
            <el-table-column align="center" prop="id" label="ID" width="70" />
            <el-table-column align="center" prop="name" label="名称" min-width="120" />
            <el-table-column align="center" label="Key 前缀" width="140">
                <template #default="scope">
                    <code>{{ scope.row.key_prefix }}…</code>
                </template>
            </el-table-column>
            <el-table-column align="center" label="权限" width="120">
                <template #default="scope">
                    <el-tag :type="scope.row.scopes.includes('write') ? 'warning' : 'info'" effect="plain">
                        {{ scope.row.scopes }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="状态" width="90">
                <template #default="scope">
                    <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
                        {{ scope.row.status === 1 ? '启用' : '禁用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="最后使用" width="170">
                <template #default="scope">
                    {{ formatTime(scope.row.last_used_at) }}
                </template>
            </el-table-column>
            <el-table-column align="center" label="创建时间" width="170">
                <template #default="scope">
                    {{ formatTime(scope.row.created_at) }}
                </template>
            </el-table-column>
            <el-table-column align="center" label="操作" width="160">
                <template #default="scope">
                    <el-button :type="scope.row.status === 1 ? 'warning' : 'success'" size="small" @click="toggleStatus(scope.row)">
                        {{ scope.row.status === 1 ? '禁用' : '启用' }}
                    </el-button>
                    <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 新建 -->
        <el-dialog v-model="createVisible" title="新建 API Key" width="480px">
            <el-form :model="form" label-width="80px">
                <el-form-item label="名称">
                    <el-input v-model="form.name" placeholder="如：我的博客客户端 / CI 脚本" />
                </el-form-item>
                <el-form-item label="权限">
                    <el-checkbox v-model="form.writeScope" :disabled="!canWrite">
                        可写（发布文章）— 仅管理员/编辑可用
                    </el-checkbox>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="createVisible = false">取消</el-button>
                <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
            </template>
        </el-dialog>



        <!-- 创建成功：显示明文一次 -->
        <el-dialog v-model="plainVisible" title="API Key 已创建（请立即保存）" width="520px">
            <el-alert type="warning" :closable="false" show-icon
                title="明文只显示这一次，关闭后将无法再次查看；请复制保存到安全位置。"
                style="margin-bottom: 14px" />

            <el-input :model-value="plainKey" readonly>
                <template #append>
                    <el-button @click="copyPlain">复制</el-button>
                </template>
            </el-input>

            <div class="usage">
                <p>调用方式：</p>
                <pre>Authorization: Bearer {{ plainKey }}</pre>
                <p>示例(Bash执行)</p>
                <pre>curl -H "Authorization: Bearer {{ plainKey }}" {{base_api}}/api/v1/articles</pre>
                
            </div>
            <template #footer>
                <el-button type="primary" @click="plainVisible = false; loadList()">我已保存</el-button>
            </template>
        </el-dialog>


    </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted,type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import {
    requestApiKeyList,
    requestApiKeyCreate,
    requestApiKeyStatus,
    requestApiKeyDelete,
    type ApiKeyItem
} from '../../composables/useRequest'

import {formatTime} from '@/composables/useTool'

const authStore = useAuthStore()
const roleId = computed(() => Number(authStore.userInfo?.user_detail?.role_id))
// 仅 admin(1)/editor(3) 可建写权限 key
const canWrite = computed(() => roleId.value === 1 || roleId.value === 3)

const base_api=import.meta.env.API_BASE

const loading = ref(false)
const list:Ref<ApiKeyItem[]> = ref([])
const createVisible = ref(false)
const creating = ref(false)
const form = reactive({ name: '', writeScope: false })

const plainVisible = ref(false)
const plainKey = ref('')


const loadList = async () => {
    loading.value = true
    try {
        const res = await requestApiKeyList()
        list.value = res.data.list || []
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '获取 API Key 失败')
    } finally {
        loading.value = false
    }
}

const openCreate = () => {
    form.name = ''
    form.writeScope = false
    createVisible.value = true
}

const submitCreate = async () => {
    creating.value = true
    try {
        const res = await requestApiKeyCreate({
            name: form.name.trim() || '未命名',
            scopes: form.writeScope ? 'write' : 'read'
        })
        plainKey.value = res.data.plain
        createVisible.value = false
        plainVisible.value = true
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '创建失败')
    } finally {
        creating.value = false
    }
}

const copyPlain = async () => {
    try {
        await navigator.clipboard.writeText(plainKey.value)
        ElMessage.success('已复制')
    } catch (e) {
        ElMessage.warning('复制失败，请手动选择复制')
    }
}

const toggleStatus = async (row:ApiKeyItem) => {
    const next = row.status === 1 ? 0 : 1
    try {
        await requestApiKeyStatus(row.id!, next)
        ElMessage.success(next === 1 ? '已启用' : '已禁用')
        loadList()
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '操作失败')
    }
}

const handleDelete = (row:ApiKeyItem) => {
    ElMessageBox.confirm(`确定删除 API Key「${row.name}」（${row.key_prefix}…）吗？删除后立即失效。`, '提示', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await requestApiKeyDelete(row.id!)
            ElMessage.success('已删除')
            loadList()
        } catch (e:any) {
            ElMessage.error(e?.response?.data?.message || '删除失败')
        }
    }).catch(() => {})
}

onMounted(loadList)
</script>

<style scoped>
.toolbar {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
    margin-bottom: 16px;
}
.page-desc {
    font-size: 13px;
    color: #909399;
    margin: 0;
    line-height: 1.7;
}
.page-desc code {
    background: #f0f0f5;
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 12px;
}
.toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}
.usage {
    margin-top: 14px;
    font-size: 12px;
    color: #606266;
}
.usage pre {
    background: #f7f8fa;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    padding: 10px 12px;
    font-size: 12px;
    line-height: 1.6;
    overflow-x: auto;
}
</style>
