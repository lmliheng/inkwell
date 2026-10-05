<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
    requestPermissionList,
    requestRolePermission,
    requestRoleSetPermission
} from '../composables/useRequest'

interface PermissionItem {
    permission_id: number
    permission_name: string
    permission_description?: string
}

interface RoleItem {
    role_id: number
    role_name: string
}

const props = defineProps({
    modelValue: {
        type: Boolean,
        default: false
    },
    role: {
        type: Object as () => RoleItem | null,
        default: () => null
    }
})

const emit = defineEmits<{
    (e: 'update:modelValue', val: boolean): void
    (e: 'success'): void
}>()

const visible = computed({
    get: () => props.modelValue,
    set: (val) => emit('update:modelValue', val)
})

const permissionList = ref<PermissionItem[]>([])
const checkedIds = ref<number[]>([])
const loading = ref(false)

const loadData = async () => {
    if (!props.role) return
    loading.value = true
    try {
        const [permsRes, currentRes] = await Promise.all([
            requestPermissionList(),
            requestRolePermission(props.role.role_id)
        ])
        permissionList.value = ((permsRes as any).data?.list) || []
        checkedIds.value = ((currentRes as any).data?.permission_ids) || []
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '加载权限失败')
    } finally {
        loading.value = false
    }
}

watch(
    () => props.modelValue,
    (val) => {
        if (val) loadData()
    }
)

const save = async () => {
    if (!props.role) return
    loading.value = true
    try {
        await requestRoleSetPermission(props.role.role_id, checkedIds.value)
        ElMessage.success('分配权限成功')
        visible.value = false
        emit('success')
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '分配权限失败')
    } finally {
        loading.value = false
    }
}
</script>

<template>
    <el-dialog
        :title="'分配权限 - ' + (role?.role_name || '')"
        v-model="visible"
        width="480px"
        align-center
    >
        <div v-loading="loading">
            <el-checkbox-group v-model="checkedIds">
                <div v-for="p in permissionList" :key="p.permission_id" class="perm-item">
                    <el-checkbox :value="p.permission_id">
                        {{ p.permission_name }}
                        <span class="perm-desc">（{{ p.permission_description }}）</span>
                    </el-checkbox>
                </div>
            </el-checkbox-group>
            <p v-if="permissionList.length === 0" class="empty-tip">暂无权限可分配</p>
        </div>
        <template #footer>
            <el-button @click="visible = false">取消</el-button>
            <el-button type="primary" :loading="loading" @click="save">保存</el-button>
        </template>
    </el-dialog>
</template>

<style scoped>
.perm-item {
    margin: 6px 0;
}
.perm-desc {
    color: #909399;
    font-size: 12px;
}
.empty-tip {
    color: #909399;
    text-align: center;
}
</style>
