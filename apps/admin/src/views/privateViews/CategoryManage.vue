<script setup lang="ts">
import { ref, reactive, onMounted,type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    requestArticleCategoryList,
    requestArticleCategoryAdd,
    requestArticleCategoryUpdate,
    requestArticleCategoryDelete,
    type CategoryItem

} from '@/composables/useRequest'

import { formatTime } from '@/composables/useTool'

const loading = ref(false)
const list:Ref<CategoryItem[]> = ref([])

const getList = async () => {
    loading.value = true
    try {
        const res = await requestArticleCategoryList()
        if (res.code === 200 && res.data) {
            list.value = res.data.list || []
        } else {
            ElMessage.error(res.message || '获取分类列表失败')
        }
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '获取分类列表失败')
    } finally {
        loading.value = false
    }
}

// 新增 / 编辑
const dialogVisible = ref(false)
const dialogTitle = ref('新增分类')
const saving = ref(false)
const form = reactive({ category_id:0, category_name: '' })

const openAdd = () => {
    form.category_id =0
    form.category_name = ''
    dialogTitle.value = '新增分类'
    dialogVisible.value = true
}

const openEdit = (row:CategoryItem) => {
    form.category_id = row.category_id
    form.category_name = row.category_name
    dialogTitle.value = '编辑分类'
    dialogVisible.value = true
}

const submit = async () => {
    const name = form.category_name.trim()
    if (!name) {
        ElMessage.warning('请输入分类名称')
        return
    }
    saving.value = true
    try {
        let res
        if (form.category_id == null) {
            res = await requestArticleCategoryAdd(name)
        } else {
            res = await requestArticleCategoryUpdate(form.category_id, name)
        }
        if (res.code === 200) {
            ElMessage.success(form.category_id == null ? '添加成功' : '更新成功')
            dialogVisible.value = false
            getList()
        } else {
            ElMessage.error(res.message || '操作失败')
        }
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '操作失败')
    } finally {
        saving.value = false
    }
}

// 删除
const handleDelete = (row:CategoryItem) => {
    ElMessageBox.confirm(`确定删除分类「${row.category_name}」吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
    })
        .then(async () => {
            try {
                const res = await requestArticleCategoryDelete(row.category_id)
                if (res.code === 200) {
                    ElMessage.success('删除成功')
                    getList()
                } else {
                    ElMessage.error(res.message || '删除失败')
                }
            } catch (e:any) {
                ElMessage.error(e?.response?.data?.message || '删除失败')
            }
        })
        .catch(() => {})
}


onMounted(getList)
</script>

<template>
    <div>
        <!-- 工具栏 -->
        <div class="toolbar">
            <el-button type="primary" @click="openAdd">新增分类</el-button>
        </div>

        <!-- 分类列表 -->
        <el-table :data="list" border stripe v-loading="loading">
            <el-table-column align="center" prop="category_id" label="ID" width="80" />
            <el-table-column align="center" prop="category_name" label="分类名称" min-width="200" />
            <el-table-column align="center" label="创建者" width="160">
                <template #default="scope">
                    {{ scope.row.author_name || `用户 #${scope.row.user}` }}
                </template>
            </el-table-column>
            <el-table-column align="center" prop="created_at" label="创建时间" width="180">
                <template #default="scope">{{ formatTime(scope.row.created_at) }}</template>
            </el-table-column>
            <el-table-column align="center" label="操作" width="150">
                <template #default="scope">
                    <el-button type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
                    <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 新增/编辑弹窗 -->
        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="420px">
            <el-form :model="form" label-width="90px">
                <el-form-item label="分类名称">
                    <el-input v-model="form.category_name" placeholder="输入分类名称" maxlength="50" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
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
</style>
