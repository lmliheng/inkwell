<script setup lang="ts">
import { ref, reactive, onMounted ,type Ref} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    requestCommentManageList,
    requestCommentManageUpdate,
    requestCommentManageDelete,
    type CommentItem
} from '@/composables/useRequest'

import { formatTime } from '@/composables/useTool'
const loading = ref(false)
const list:Ref<CommentItem[]> = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const selected:Ref<CommentItem[]|number[]> = ref([])

const getList = async () => {
    loading.value = true
    try {
        const res = await requestCommentManageList({
            page: page.value,
            pageSize: pageSize.value,
            keyword: keyword.value.trim() || undefined,
        })
        if (res.code === 200 && res.data) {
            list.value = res.data.list || []
            total.value = res.data.total || 0
        } else {
            ElMessage.error(res.message || '获取评论列表失败')
        }
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '获取评论列表失败')
    } finally {
        loading.value = false
    }
}

const handleSearch = () => {
    page.value = 1
    getList()
}

const handleReset = () => {
    keyword.value = ''
    page.value = 1
    getList()
}

const handleSizeChange = (val:number) => {
    pageSize.value = val
    page.value = 1
    getList()
}

const handleCurrentChange = (val:number) => {
    page.value = val
    getList()
}

// 编辑
const editVisible = ref(false)
const editLoading = ref(false)
const editForm = reactive({ comment_id: 0, nickname: '', content: '' })

const openEdit = (row:CommentItem) => {
    editForm.comment_id = row.comment_id
    editForm.nickname = row.nickname || ''
    editForm.content = row.content || ''
    editVisible.value = true
}

const submitEdit = async () => {
    if (!editForm.content.trim()) {
        ElMessage.warning('评论内容不能为空')
        return
    }
    editLoading.value = true
    try {
        const res = await requestCommentManageUpdate({
            comment_id: editForm.comment_id,
            content: editForm.content.trim(),
            nickname: editForm.nickname.trim()!,
        })
        if (res.code === 200) {
            ElMessage.success('更新成功')
            editVisible.value = false
            getList()
        } else {
            ElMessage.error(res.message || '更新失败')
        }
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '更新失败')
    } finally {
        editLoading.value = false
    }
}

// 单条删除（级联删子评论）
const handleDelete = (row:CommentItem) => {
    ElMessageBox.confirm('确定删除该评论吗？（其楼中楼回复会一并删除）', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
    })
        .then(async () => {
            try {
                const res = await requestCommentManageDelete([row.comment_id])
                if (res.code === 200) {
                    ElMessage.success(res.message || '删除成功')
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

// 批量删除
const handleBatchDelete = () => {
    if (!selected.value.length) {
        ElMessage.warning('请先勾选要删除的评论')
        return
    }
    ElMessageBox.confirm(`确定删除选中的 ${selected.value.length} 条评论吗？（含其楼中楼回复）`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
    })
        .then(async () => {
            try {
                const res = await requestCommentManageDelete(selected.value as number[])
                if (res.code === 200) {
                    ElMessage.success(res.message || '删除成功')
                    selected.value = []
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
            <div class="toolbar-filters">
                <el-input
                    v-model="keyword"
                    placeholder="搜索评论内容/昵称"
                    clearable
                    style="width: 260px; margin-right: 8px"
                    @keyup.enter="handleSearch"
                    @clear="handleSearch"
                />
                <el-button type="primary" @click="handleSearch">搜索</el-button>
                <el-button @click="handleReset">重置</el-button>
                <el-button type="danger" :disabled="!selected.length" @click="handleBatchDelete">
                    批量删除{{ selected.length ? `（${selected.length}）` : '' }}
                </el-button>
            </div>
        </div>

        <!-- 评论列表 -->
        <el-table :data="list" border stripe v-loading="loading" @selection-change="(val:CommentItem[]) => (selected = val.map((i) => i.comment_id))">
            <!-- 把 selected 类型变成了number[]-->
            <el-table-column type="selection" width="45" />
            <el-table-column align="center" prop="comment_id" label="ID" width="80" />
            <el-table-column align="center" label="所属文章" min-width="160" show-overflow-tooltip>
                <template #default="scope">
                    {{ scope.row.article_title || `文章 #${scope.row.article_id}` }}
                </template>
            </el-table-column>
            <el-table-column align="center" label="评论者" width="130">
                <template #default="scope">
                    <span>{{ scope.row.display_name || scope.row.nickname || '匿名' }}</span>
                    <el-tag v-if="scope.row.parent_id" size="small" type="info" style="margin-left: 4px">回复</el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="内容" min-width="240" show-overflow-tooltip>
                <template #default="scope">{{ scope.row.content }}</template>
            </el-table-column>
            <el-table-column align="center" prop="created_at" label="时间" width="170">
                <template #default="scope">{{ formatTime(scope.row.created_at) }}</template>
            </el-table-column>
            <el-table-column align="center" label="操作" width="150">
                <template #default="scope">
                    <el-button type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
                    <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 分页 -->
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

        <!-- 编辑弹窗 -->
        <el-dialog v-model="editVisible" title="编辑评论" width="480px">
            <el-form :model="editForm" label-width="70px">
                <el-form-item label="昵称">
                    <el-input v-model="editForm.nickname" placeholder="评论者昵称" />
                </el-form-item>
                <el-form-item label="内容">
                    <el-input v-model="editForm.content" type="textarea" :rows="4" placeholder="评论内容" />
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

.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}
</style>
