<template>
    <div>
        <div class="toolbar">
            <div class="toolbar-filters">
                <el-input
                    v-model="keyword"
                    placeholder="搜索公告标题"
                    clearable
                    style="width: 200px; margin-right: 8px"
                    @keyup.enter="handleSearch"
                    @clear="handleSearch"
                />
                <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px; margin-right: 8px" @change="handleSearch">
                    <el-option label="启用" :value="1" />
                    <el-option label="停用" :value="0" />
                </el-select>
                <el-button type="primary" @click="handleSearch">搜索</el-button>
            </div>
            <div class="toolbar-actions">
                <el-button type="primary" @click="openAdd">发布公告</el-button>
            </div>
        </div>

        <el-table :data="list" border stripe v-loading="loading">
            <el-table-column align="center" prop="id" label="ID" width="80" />
            <el-table-column align="center" prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column align="center" label="内容摘要" min-width="240" show-overflow-tooltip>
                <template #default="scope">
                    <span class="hash-text">{{ scope.row.content }}</span>
                </template>
            </el-table-column>
            <el-table-column align="center" label="状态" width="90">
                <template #default="scope">
                    <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
                        {{ scope.row.status === 1 ? '启用' : '停用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="发布时间" width="170">
                <template #default="scope">
                    {{ formatTime(scope.row.created_at) }}
                </template>
            </el-table-column>
            <el-table-column align="center" label="操作" width="220">
                <template #default="scope">
                    <el-button type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
                    <el-button :type="scope.row.status === 1 ? 'warning' : 'success'" size="small" @click="toggleStatus(scope.row)">
                        {{ scope.row.status === 1 ? '停用' : '启用' }}
                    </el-button>
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
                :page-sizes="[10, 20, 50]"
                @size-change="handleSizeChange"
                @current-change="handleCurrentChange"
            />
        </div>

        <!-- 发布/编辑弹窗 -->
        <el-dialog v-model="dialogVisible" :title="dialogMode === 'add' ? '发布公告' : '编辑公告'" width="560px">
            <el-form :model="form" label-width="70px">
                <el-form-item label="标题">
                    <el-input v-model="form.title" placeholder="公告标题（横幅上显示）" />
                </el-form-item>
                <el-form-item label="内容">
                    <el-input v-model="form.content" type="textarea" :rows="6" placeholder="公告全文（点击横幅时弹窗展示）" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitForm">{{ dialogMode === 'add' ? '发布' : '保存' }}</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted,type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    requestAnnounceList,
    requestAnnounceAdd,
    requestAnnounceUpdate,
    requestAnnounceStatus,
    requestAnnounceDelete,
    type Announce
} from '../../composables/useRequest'
import {formatTime}from '@/composables/useTool'


const loading = ref(false)
const list:Ref<Announce[]> = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const statusFilter = ref('')

const dialogVisible = ref(false)
const dialogMode = ref('add')
const saving = ref(false)
const form = reactive({ id: null, title: '', content: '' })


const fetchList = async () => {
    loading.value = true
    try {
        const res = await requestAnnounceList({
            page: page.value,
            pageSize: pageSize.value,
            keyword: keyword.value.trim() || undefined,
            status: statusFilter.value === '' ? undefined : statusFilter.value
        })
        list.value = res.data.list || []
        total.value = res.data.total || 0
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '获取公告列表失败')
    } finally {
        loading.value = false
    }
}

const handleSearch = () => {
    page.value = 1
    fetchList()
}

const handleSizeChange = (val:any) => {
    pageSize.value = val
    page.value = 1
    fetchList()
}

const handleCurrentChange = (val:any) => {
    page.value = val
    fetchList()
}

const openAdd = () => {
    dialogMode.value = 'add'
    Object.assign(form, { id: null, title: '', content: '' })
    dialogVisible.value = true
}

const openEdit = (row:Announce) => {
    dialogMode.value = 'edit'
    Object.assign(form, { id: row.id, title: row.title || '', content: row.content || '' })
    dialogVisible.value = true
}

const submitForm = async () => {
    if (!form.title.trim()) {
        ElMessage.warning('请填写公告标题')
        return
    }
    if (!form.content.trim()) {
        ElMessage.warning('请填写公告内容')
        return
    }
    saving.value = true
    try {
        if (dialogMode.value === 'add') {
            await requestAnnounceAdd({ title: form.title, content: form.content })
            ElMessage.success('公告已发布')
        } else {
            await requestAnnounceUpdate(form.id!, { title: form.title, content: form.content })
            ElMessage.success('公告已更新')
        }
        dialogVisible.value = false
        fetchList()
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '保存失败')
    } finally {
        saving.value = false
    }
}

const toggleStatus = async (row:Announce) => {
    const next = row.status === 1 ? 0 : 1
    try {
        await requestAnnounceStatus(row.id!, next)
        ElMessage.success(next === 1 ? '已启用' : '已停用')
        fetchList()
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '操作失败')
    }
}

const handleDelete = (row:Announce) => {
    ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await requestAnnounceDelete(row.id!)
            ElMessage.success('删除成功')
            fetchList()
        } catch (e:any) {
            ElMessage.error(e?.response?.data?.message || '删除失败')
        }
    }).catch(() => {})
}

onMounted(fetchList)
</script>

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
}
.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}
.hash-text {
    word-break: break-all;
}
</style>
