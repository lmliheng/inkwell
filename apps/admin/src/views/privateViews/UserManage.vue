<script setup lang="ts">
import { onMounted, ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import {
    requestUser,
    requestUserAdd,
    requestUpdateUserFull,
    requestUserDelete,
    requestUserDeleteBatch,
    requestUserDetail,
    requestUserResetPassword,
    requestRoleList,
    type UserItem
} from '../../composables/useRequest'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

// 当前登录用户是否为超级管理员（role_id = 1）
const isAdmin = computed(() => Number(authStore.userInfo?.user_detail?.role_id) === 1)

const loading = ref(false)
const userList = ref<UserItem[]>([])
const total = ref(0)
const roleList = ref<any[]>([])
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const dialogMode = ref('add') // 'add' | 'edit'
const editLoading = ref(false) // 编辑时拉取详情补全扩展字段
const form = reactive({
    id: null,
    username: '',
    email: '',
    password: '',
    role_id: null,
    vip: 0,
    area: '',
    bio: '',
    name: '',
    avatar: '',
    checkinDay: 0
})

// 重置密码弹窗
const passwordDialogVisible = ref(false)
const passwordLoading = ref(false)
const resetPasswordForm = reactive({
    id: null,
    username: '',
    password: '',
    confirm: ''
})

const formatTime = (v: any) => (v ? String(v).replace('T', ' ').slice(0, 19) : '')

const getUser = async () => {
    loading.value = true
    try {
        const res = await requestUser({
            page: page.value,
            pageSize: pageSize.value,
            keyword: keyword.value.trim() || ''
        })
        userList.value = res.data.list || []
        total.value = res.data.total || 0
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '获取用户列表失败')
    } finally {
        loading.value = false
    }
}

const handleSearch = () => {
    page.value = 1
    getUser()
}

const handleSizeChange = (val: number) => {
    pageSize.value = val
    page.value = 1
    getUser()
}

const handleCurrentChange = (val: number) => {
    page.value = val
    getUser()
}

const getRoles = async () => {
    try {
        const res = await requestRoleList()
        roleList.value = (res as any).list || (res as any).data?.list || []
    } catch (e) {
        // 角色列表获取失败不阻塞页面
        console.error(e)
    }
}

const openAdd = () => {
    dialogMode.value = 'add'
    Object.assign(form, {
        id: null,
        username: '',
        email: '',
        password: '',
        role_id: null,
        vip: 0,
        area: '',
        bio: '',
        name: '',
        avatar: '',
        checkinDay: 0
    })
    dialogVisible.value = true
}

const openEdit = async (row: any) => {
    dialogMode.value = 'edit'
    editLoading.value = true
    // 先用列表行数据填充基础字段
    Object.assign(form, {
        id: row.id,
        username: row.username,
        email: row.email,
        password: '',
        role_id: row.role_id,
        vip: row.vip ?? 0,
        area: row.area || '',
        bio: row.bio || '',
        name: row.name || '',
        avatar: row.avatar || '',
        checkinDay: row.checkinDay ?? row.checkin_day ?? 0
    })
    dialogVisible.value = true
    // 列表可能不含扩展字段，拉取详情补全，避免提交时用空值覆盖
    try {
        const res = await requestUserDetail(row.id)
        const d = (res as any).data || res || {}
        Object.assign(form, {
            id: d.id ?? form.id,
            username: d.username ?? form.username,
            email: d.email ?? form.email,
            role_id: d.role_id ?? form.role_id,
            vip: d.vip ?? 0,
            area: d.area || '',
            bio: d.bio || '',
            name: d.name || '',
            avatar: d.avatar || '',
            checkinDay: d.checkinDay ?? d.checkin_day ?? 0
        })
    } catch (e) {
        // 详情拉取失败时使用列表已有数据
    } finally {
        editLoading.value = false
    }
}

const submitForm = async () => {
    if (!form.username || !form.email) {
        ElMessage.warning('请填写用户名和邮箱')
        return
    }
    if (dialogMode.value === 'add' && !form.password) {
        ElMessage.warning('请填写密码')
        return
    }
    try {
        if (dialogMode.value === 'add') {
            await requestUserAdd({
                username: form.username,
                email: form.email,
                password: form.password,
                role_id: form.role_id ?? undefined
            } as any)
            ElMessage.success('新增用户成功')
        } else {
            await requestUpdateUserFull({
                id: form.id ?? undefined,
                username: form.username,
                email: form.email,
                role_id: form.role_id ?? undefined,
                vip: Number(form.vip) || 0,
                area: form.area || '',
                bio: form.bio || '',
                name: form.name || '',
                avatar: form.avatar || '',
                checkinDay: Number(form.checkinDay) || 0
            })
            ElMessage.success('更新用户成功')
        }
        dialogVisible.value = false
        getUser()
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '操作失败')
    }
}

const handleResetPassword = (row: any) => {
    resetPasswordForm.id = row.id
    resetPasswordForm.username = row.username
    resetPasswordForm.password = ''
    resetPasswordForm.confirm = ''
    passwordDialogVisible.value = true
}

const submitResetPassword = async () => {
    if (!resetPasswordForm.password) {
        ElMessage.warning('请输入新密码')
        return
    }
    if (resetPasswordForm.password !== resetPasswordForm.confirm) {
        ElMessage.warning('两次输入的密码不一致')
        return
    }
    passwordLoading.value = true
    try {
        await requestUserResetPassword({
            id: resetPasswordForm.id as unknown as number,
            password: resetPasswordForm.password
        })
        ElMessage.success('重置密码成功')
        passwordDialogVisible.value = false
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '重置密码失败')
    } finally {
        passwordLoading.value = false
    }
}

const handleDelete = (row: any) => {
    ElMessageBox.confirm('确定删除该用户吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await requestUserDelete(row.id)
            ElMessage.success('删除用户成功')
            getUser()
        } catch (e: any) {
            ElMessage.error(e?.response?.data?.message || '删除失败')
        }
    }).catch(() => {})
}

// 批量删除
const selectedIds = ref<number[]>([])
const handleSelectionChange = (rows: any[]) => {
    selectedIds.value = rows.map((r: any) => r.id)
}

const handleBatchDelete = () => {
    if (!selectedIds.value.length) {
        ElMessage.warning('请先选择要删除的用户')
        return
    }
    ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个用户吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await requestUserDeleteBatch(selectedIds.value)
            ElMessage.success('批量删除成功')
            selectedIds.value = []
            getUser()
        } catch (e: any) {
            ElMessage.error(e?.response?.data?.message || '批量删除失败')
        }
    }).catch(() => {})
}

const handleDetail = (row: any) => {
    router.push('/user/user-info/' + row.id)
}

// 跳转「主页设置」页并预选该用户（admin 可设置任意用户的主页）
const handleHomeSetting = (row: any) => {
    router.push({ path: '/user/home-setting', query: { username: row.username } })
}

onMounted(() => {
    getUser()
    getRoles()
})
</script>

<template>
    <div>
        <div class="toolbar">
            <div class="toolbar-filters">
                <el-input
                    v-model="keyword"
                    placeholder="搜索用户名/邮箱/姓名"
                    clearable
                    style="width: 260px; margin-right: 8px"
                    @keyup.enter="handleSearch"
                    @clear="handleSearch"
                />
                <el-button type="primary" @click="handleSearch">搜索</el-button>
            </div>
            <div class="toolbar-actions">
                <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">
                    批量删除{{ selectedIds.length ? `(${selectedIds.length})` : '' }}
                </el-button>
                <el-button type="primary" @click="openAdd">新增用户</el-button>
            </div>
        </div>

        <el-table :data="userList" border stripe v-loading="loading" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="50" align="center" />
            <el-table-column align="center" prop="id" label="ID" width="140" />
            <el-table-column align="center" label="头像" width="80">
                <template #default="scope">
                    <el-avatar :src="scope.row.avatar" />
                </template>
            </el-table-column>
            <el-table-column align="center" prop="username" label="用户名" />
            <el-table-column align="center" prop="email" label="邮箱" />
            <el-table-column align="center" label="角色">
                <template #default="scope">
                    <el-tag type="primary">{{ scope.row.role_name }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="创建时间">
                <template #default="scope">
                    {{ formatTime(scope.row.created_at) }}
                </template>
            </el-table-column>
            <el-table-column align="center" label="操作" width="440">
                <template #default="scope">
                    <el-button type="success" size="small" @click="handleDetail(scope.row)">详情</el-button>
                    <el-button type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
                    <el-button type="warning" size="small" @click="handleHomeSetting(scope.row)">主页设置</el-button>
                    <el-button v-if="isAdmin" type="danger" size="small" @click="handleResetPassword(scope.row)">重置密码</el-button>
                    <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <div class="pagination">
            <el-pagination
                background
                layout="total, sizes, prev, pager, next, jumper"
                :total="total"
                :current-page="page"
                :page-size="pageSize"
                :page-sizes="[10, 20, 50, 100]"
                @size-change="handleSizeChange"
                @current-change="handleCurrentChange"
            />
        </div>

        <el-dialog v-model="dialogVisible" :title="dialogMode === 'add' ? '新增用户' : '编辑用户'" width="480px">
            <el-form :model="form" label-width="80px">
                <el-form-item label="用户名">
                    <el-input v-model="form.username" placeholder="请输入用户名" />
                </el-form-item>
                <el-form-item label="邮箱">
                    <el-input v-model="form.email" placeholder="请输入邮箱" />
                </el-form-item>
                <el-form-item v-if="dialogMode === 'add'" label="密码">
                    <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
                </el-form-item>
                <el-form-item label="角色">
                    <el-select v-model="form.role_id" placeholder="请选择角色" clearable style="width: 100%">
                        <el-option v-for="r in roleList" :key="r.role_id" :label="r.role_name" :value="r.role_id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="头像">
                    <div class="avatar-edit-row">
                        <el-avatar :size="48" :src="form.avatar || undefined">{{ (form.name || form.username || '?').charAt(0) }}</el-avatar>
                        <el-input v-model="form.avatar" placeholder="粘贴头像图片 URL" clearable />
                    </div>
                </el-form-item>
                <el-form-item label="姓名">
                    <el-input v-model="form.name" placeholder="请输入姓名" />
                </el-form-item>
                <el-form-item label="VIP">
                    <el-input-number v-model="form.vip" :min="0" :step="1" style="width: 100%" />
                </el-form-item>
                <el-form-item label="签到天数">
                    <el-input-number v-model="form.checkinDay" :min="0" :step="1" style="width: 100%" />
                </el-form-item>
                <el-form-item label="地区">
                    <el-input v-model="form.area" placeholder="请输入地区" />
                </el-form-item>
                <el-form-item label="简介">
                    <el-input v-model="form.bio" type="textarea" :rows="3" placeholder="请输入简介" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="editLoading" @click="submitForm">保存</el-button>
            </template>
        </el-dialog>

        <el-dialog v-model="passwordDialogVisible" title="重置密码" width="480px">
            <el-alert
                type="warning"
                :closable="false"
                show-icon
                title="将修改该用户的登录密码，请谨慎操作"
                style="margin-bottom: 12px"
            />
            <el-form :model="resetPasswordForm" label-width="80px">
                <el-form-item label="用户名">
                    <el-input :model-value="resetPasswordForm.username" disabled />
                </el-form-item>
                <el-form-item label="新密码">
                    <el-input v-model="resetPasswordForm.password" type="password" show-password placeholder="请输入新密码" />
                </el-form-item>
                <el-form-item label="确认密码">
                    <el-input v-model="resetPasswordForm.confirm" type="password" show-password placeholder="请再次输入新密码" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="passwordDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="passwordLoading" @click="submitResetPassword">确定</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style scoped>
.toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.toolbar-filters {
    display: flex;
    align-items: center;
}

.toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
}

.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}

.hash-text {
    word-break: break-all;
    font-family: monospace;
}

.avatar-edit-row {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
}
</style>
