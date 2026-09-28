<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import {
    requestRoleList,
    requestRoleAdd,
    requestRoleUpdate,
    requestRoleDelete
} from '../../composables/useRequest'
import { ElMessage, ElMessageBox } from 'element-plus'
import RolePermissionDialog from '../../components/RolePermissionDialog.vue'

const roleList = ref<any[]>([])
const loading = ref(false)

const permDialogVisible = ref(false)
const currentRole = ref(null)

// 新增 / 重命名共用一个弹窗
const nameDialogVisible = ref(false)
const nameDialogMode = ref('add') // 'add' | 'edit'
const nameForm = reactive({ role_id: null, role_name: '' })

const getRoleList = async () => {
    loading.value = true
    try {
        const res = await requestRoleList()
        roleList.value = res.list || []
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '获取角色列表失败')
    } finally {
        loading.value = false
    }
}

const handleAssign = (row: any) => {
    currentRole.value = row
    permDialogVisible.value = true
}

const openAdd = () => {
    nameDialogMode.value = 'add'
    Object.assign(nameForm, { role_id: null, role_name: '' })
    nameDialogVisible.value = true
}

const openEdit = (row: any) => {
    nameDialogMode.value = 'edit'
    Object.assign(nameForm, { role_id: row.role_id, role_name: row.role_name })
    nameDialogVisible.value = true
}

const submitName = async () => {
    if (!nameForm.role_name) {
        ElMessage.warning('请填写角色名')
        return
    }
    try {
        if (nameDialogMode.value === 'add') {
            await requestRoleAdd(nameForm.role_name)
            ElMessage.success('新增角色成功')
        } else {
            await requestRoleUpdate(nameForm.role_id as unknown as number, nameForm.role_name)
            ElMessage.success('修改角色成功')
        }
        nameDialogVisible.value = false
        getRoleList()
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '操作失败')
    }
}

const handleDelete = (row: any) => {
    ElMessageBox.confirm('确定删除该角色吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await requestRoleDelete(row.role_id)
            ElMessage.success('删除角色成功')
            getRoleList()
        } catch (e: any) {
            ElMessage.error(e?.response?.data?.message || '删除失败')
        }
    }).catch(() => {})
}

onMounted(() => {
    getRoleList()
})
</script>

<template>
    <div>
        <div class="toolbar">
            <el-button type="primary" @click="openAdd">新增角色</el-button>
        </div>

        <el-table :data="roleList" border stripe v-loading="loading">
            <el-table-column align="center" prop="role_id" label="角色ID" width="120" />
            <el-table-column align="center" prop="role_name" label="角色名称" />
            <el-table-column align="center" label="操作" width="280">
                <template #default="scope">
                    <el-button type="primary" size="small" @click="handleAssign(scope.row)">分配权限</el-button>
                    <el-button size="small" @click="openEdit(scope.row)">重命名</el-button>
                    <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <RolePermissionDialog
            v-model="permDialogVisible"
            :role="currentRole"
            @success="getRoleList"
        />

        <el-dialog v-model="nameDialogVisible" :title="nameDialogMode === 'add' ? '新增角色' : '重命名角色'" width="420px">
            <el-form :model="nameForm" label-width="80px">
                <el-form-item label="角色名称">
                    <el-input v-model="nameForm.role_name" placeholder="请输入角色名称" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="nameDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitName">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style scoped>
.toolbar {
    text-align: right;
    margin-bottom: 16px;
}
</style>
