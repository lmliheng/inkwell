<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { requestPermissionList, requestPermissionUpdate } from '../../composables/useRequest'
import { ElMessage } from 'element-plus'

const permissionList = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const form = reactive({
    permission_id: null,
    permission_name: '',
    permission_description: ''
})

const getPermissionList = async () => {
    loading.value = true
    try {
        const res = await requestPermissionList()
        permissionList.value = res.data.list || []
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '获取权限列表失败')
    } finally {
        loading.value = false
    }
}

const openEdit = (row: any) => {
    Object.assign(form, {
        permission_id: row.permission_id,
        permission_name: row.permission_name,
        permission_description: row.permission_description
    })
    dialogVisible.value = true
}

const submitForm = async () => {
    if (!form.permission_name) {
        ElMessage.warning('请填写权限名称')
        return
    }
    try {
        await requestPermissionUpdate({
            permission_id: form.permission_id,
            permission_name: form.permission_name,
            permission_description: form.permission_description
        })
        ElMessage.success('修改权限成功')
        dialogVisible.value = false
        getPermissionList()
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '修改失败')
    }
}

onMounted(() => {
    getPermissionList()
})
</script>

<template>
    <div>
        <el-table :data="permissionList" border stripe v-loading="loading">
            <el-table-column align="center" prop="permission_id" label="权限ID" width="120" />
            <el-table-column align="center" prop="permission_name" label="权限名称" />
            <el-table-column align="center" prop="permission_description" label="权限描述" />
            <el-table-column align="center" label="操作" width="140">
                <template #default="scope">
                    <el-button type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
                </template>
            </el-table-column>
        </el-table>

        <el-dialog v-model="dialogVisible" title="编辑权限" width="480px">
            <el-form :model="form" label-width="80px">
                <el-form-item label="权限名称">
                    <el-input v-model="form.permission_name" placeholder="请输入权限名称" />
                </el-form-item>
                <el-form-item label="权限描述">
                    <el-input v-model="form.permission_description" type="textarea" placeholder="请输入权限描述" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitForm">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>
