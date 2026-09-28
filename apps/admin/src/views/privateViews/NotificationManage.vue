<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import {
    requestNotificationAdd,
    requestNotificationList,
    requestNotificationUpdate,
    requestNotificationDelete
} from '@/composables/useRequest'

const authStore = useAuthStore()
const isAdmin = computed(() => Number(authStore.userInfo?.user_detail?.role_id) === 1)

const form = reactive({
    title: '',
    content: '',
    target_type: 'all' as 'all' | 'user' | 'role',
    target_id: null as number | null,
    type: 'announcement',
    importance: 'medium' as 'low' | 'medium' | 'high'
})

const list = ref<any[]>([])
const loading = ref(false)
const publishing = ref(false)

// 编辑弹窗
const editVisible = ref(false)
const editLoading = ref(false)
const editForm = reactive({
    notification_id: null as number | null,
    title: '',
    content: '',
    target_type: 'all' as 'all' | 'user' | 'role',
    target_id: null as number | null,
    type: 'announcement',
    importance: 'medium' as 'low' | 'medium' | 'high'
})

const typeMap: Record<string, string> = { system: '系统', announcement: '公告', reminder: '提醒' }
const typeTagMap: Record<string, string> = { system: 'danger', announcement: 'success', reminder: 'warning' }
const importanceMap: Record<string, string> = { high: '高', medium: '中', low: '低' }
const importanceTagMap: Record<string, string> = { high: 'danger', medium: 'warning', low: 'info' }
const targetTypeMap: Record<string, string> = { all: 'target_all', user: 'target_user', role: 'target_role' }

const getList = async () => {
    loading.value = true
    try {
        const res = await requestNotificationList()
        if (res.code === 200 && res.data) {
            list.value = res.data.list || []
        }
    } catch (e) {
        ElMessage.error('获取通知列表失败')
    } finally {
        loading.value = false
    }
}

const handlePublish = async () => {
    if (!form.title || !form.content) {
        ElMessage.warning('标题和内容不能为空')
        return
    }
    if (form.target_type !== 'all' && !form.target_id) {
        ElMessage.warning("目标类型为「指定用户」或「指定角色」时必须填写目标ID")
        return
    }
    publishing.value = true
    try {
        const res = await requestNotificationAdd({
            title: form.title,
            content: form.content,
            target_type: form.target_type,
            target_id: form.target_type === 'all' ? null : Number(form.target_id),
            type: form.type,
            importance: form.importance
        })
        if (res.code === 200) {
            ElMessage.success('通知发布成功')
            form.title = ''
            form.content = ''
            form.target_type = 'all'
            form.target_id = null
            form.type = 'announcement'
            form.importance = 'medium'
            getList()
        } else {
            ElMessage.error(res.message || '发布失败')
        }
    } catch (e) {
        ElMessage.error('发布失败')
    } finally {
        publishing.value = false
    }
}

const openEdit = (row: any) => {
    Object.assign(editForm, {
        notification_id: row.notification_id,
        title: row.title,
        content: row.content,
        target_type: row.target_type,
        target_id: row.target_id,
        type: row.type || 'announcement',
        importance: row.importance || 'medium'
    })
    editVisible.value = true
}

const submitEdit = async () => {
    if (!editForm.title || !editForm.content) {
        ElMessage.warning('标题和内容不能为空')
        return
    }
    if (editForm.target_type !== 'all' && !editForm.target_id) {
        ElMessage.warning('目标类型为「指定用户」或「指定角色」时必须填写目标ID')
        return
    }
    editLoading.value = true
    try {
        await requestNotificationUpdate({
            notification_id: editForm.notification_id,
            title: editForm.title,
            content: editForm.content,
            target_type: editForm.target_type,
            target_id: editForm.target_type === 'all' ? null : Number(editForm.target_id),
            type: editForm.type,
            importance: editForm.importance
        })
        ElMessage.success('更新成功')
        editVisible.value = false
        getList()
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '更新失败')
    } finally {
        editLoading.value = false
    }
}

const handleDelete = (row: any) => {
    ElMessageBox.confirm('确定删除该通知吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await requestNotificationDelete(row.notification_id)
            ElMessage.success('删除成功')
            getList()
        } catch (e) {
            ElMessage.error('删除失败')
        }
    }).catch(() => {})
}

onMounted(() => {
    if (isAdmin.value) {
        getList()
    }
})

watch(isAdmin, (val) => {
    if (val) {
        getList()
    }
})
</script>

<template>
    <div v-if="!isAdmin">
        <el-empty description="暂无权限" />
    </div>

    <div v-else>
        <el-card class="publish-card">
            <template #header>
                <span class="card-title">发布通知</span>
            </template>

            <el-form :model="form" label-width="90px">
                <el-form-item label="通知标题">
                    <el-input v-model="form.title" placeholder="通知标题" maxlength="100" show-word-limit />
                </el-form-item>

                <el-form-item label="通知内容">
                    <el-input v-model="form.content" type="textarea" :rows="4" placeholder="通知内容" />
                </el-form-item>

                <el-form-item label="通知类型">
                    <el-radio-group v-model="form.type">
                        <el-radio value="announcement">公告</el-radio>
                        <el-radio value="reminder">提醒</el-radio>
                        <el-radio value="system">系统</el-radio>
                    </el-radio-group>
                </el-form-item>

                <el-form-item label="重要性">
                    <el-radio-group v-model="form.importance">
                        <el-radio value="high">高</el-radio>
                        <el-radio value="medium">中</el-radio>
                        <el-radio value="low">低</el-radio>
                    </el-radio-group>
                </el-form-item>

                <el-form-item label="目标类型">
                    <el-radio-group v-model="form.target_type">
                        <el-radio value="all">全体用户</el-radio>
                        <el-radio value="user">指定用户</el-radio>
                        <el-radio value="role">指定角色</el-radio>
                    </el-radio-group>
                </el-form-item>

                <el-form-item v-if="form.target_type !== 'all'" label="目标ID">
                    <el-input
                        v-model="form.target_id"
                        :placeholder="form.target_type === 'user' ? '用户 ID' : '角色 ID (1=admin, 2=editor, 3=user)'"
                    />
                </el-form-item>

                <el-form-item>
                    <el-button type="primary" :loading="publishing" @click="handlePublish">发布</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <el-card class="list-card">
            <template #header>
                <span class="card-title">通知列表</span>
            </template>

            <el-table :data="list" border stripe v-loading="loading" style="width: 100%">
                <el-table-column align="center" prop="notification_id" label="ID" width="70" />
                <el-table-column align="center" prop="title" label="通知标题" />
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
                <el-table-column align="center" label="目标" width="110">
                    <template #default="scope">
                        <el-tag size="small" :type="scope.row.target_type === 'all' ? 'success' : 'primary'">
                            {{ targetTypeMap[scope.row.target_type] === 'target_all' ? '全体' : (scope.row.target_type === 'user' ? '用户' : '角色') }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column align="center" prop="created_at" label="创建时间" width="170" />
                <el-table-column align="center" label="操作" width="150">
                    <template #default="scope">
                        <el-button type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
                        <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 编辑通知弹窗 -->
        <el-dialog v-model="editVisible" title="编辑通知" width="560px">
            <el-form :model="editForm" label-width="90px">
                <el-form-item label="通知标题">
                    <el-input v-model="editForm.title" placeholder="通知标题" />
                </el-form-item>
                <el-form-item label="通知内容">
                    <el-input v-model="editForm.content" type="textarea" :rows="4" placeholder="通知内容" />
                </el-form-item>
                <el-form-item label="通知类型">
                    <el-radio-group v-model="editForm.type">
                        <el-radio value="announcement">公告</el-radio>
                        <el-radio value="reminder">提醒</el-radio>
                        <el-radio value="system">系统</el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item label="重要性">
                    <el-radio-group v-model="editForm.importance">
                        <el-radio value="high">高</el-radio>
                        <el-radio value="medium">中</el-radio>
                        <el-radio value="low">低</el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item label="目标类型">
                    <el-radio-group v-model="editForm.target_type">
                        <el-radio value="all">全体用户</el-radio>
                        <el-radio value="user">指定用户</el-radio>
                        <el-radio value="role">指定角色</el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item v-if="editForm.target_type !== 'all'" label="目标ID">
                    <el-input v-model="editForm.target_id" placeholder="用户/角色 ID" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="editLoading" @click="submitEdit">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style scoped>
.publish-card {
    margin-bottom: 16px;
}

.card-title {
    font-weight: bold;
}
</style>
