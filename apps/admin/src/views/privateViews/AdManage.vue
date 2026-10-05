<template>
    <div>
        <div class="toolbar">
            <div class="toolbar-filters">
                <el-input v-model="keyword" placeholder="搜索广告标题" clearable style="width: 200px; margin-right: 8px"
                    @keyup.enter="handleSearch" @clear="handleSearch" />
                <el-select v-model="positionFilter" placeholder="投放位置" clearable style="width: 160px; margin-right: 8px"
                    @change="handleSearch">
                    <el-option label="文章正文顶部" value="article_top" />
                    <el-option label="文章评论区上方" value="article_bottom" />
                    <el-option label="首页中部横幅" value="home_mid" />
                </el-select>
                <el-button type="primary" @click="handleSearch">搜索</el-button>
            </div>
            <div class="toolbar-actions">
                <el-button type="primary" @click="openAdd">新增广告</el-button>
            </div>
        </div>

        <el-table :data="list" border stripe v-loading="loading">
            <el-table-column align="center" prop="id" label="ID" width="80" />
            <el-table-column align="center" prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column align="center" label="类型" width="90">
                <template #default="scope">
                    <el-tag :type="scope.row.type === 'image' ? 'primary' : 'warning'" effect="plain">
                        {{ scope.row.type === 'image' ? '图片' : '文字' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="投放位置" width="140">
                <template #default="scope">
                    {{ positionLabel(scope.row.position) }}
                </template>
            </el-table-column>
            <el-table-column align="center" label="排序" width="70" prop="sort_order" />
            <el-table-column align="center" label="状态" width="90">
                <template #default="scope">
                    <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
                        {{ scope.row.status === 1 ? '启用' : '停用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="点击量" width="90" prop="click_count" />
            <el-table-column align="center" label="创建时间" width="170">
                <template #default="scope">
                    {{ formatTime(scope.row.created_at) }}
                </template>
            </el-table-column>
            <el-table-column align="center" label="操作" width="240">
                <template #default="scope">
                    <el-button type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
                    <el-button :type="scope.row.status === 1 ? 'warning' : 'success'" size="small"
                        @click="toggleStatus(scope.row)">
                        {{ scope.row.status === 1 ? '停用' : '启用' }}
                    </el-button>
                    <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <div class="pagination">
            <el-pagination background layout="total, sizes, prev, pager, next, jumper" :total="total"
                :current-page="page" :page-size="pageSize" :page-sizes="[10, 20, 50]" @size-change="handleSizeChange"
                @current-change="handleCurrentChange" />
        </div>

        <!-- 新增/编辑弹窗 -->
        <el-dialog v-model="dialogVisible" :title="dialogMode === 'add' ? '新增广告' : '编辑广告'" width="560px">
            <el-form :model="form" label-width="90px">
                <el-form-item label="标题">
                    <el-input v-model="form.title" placeholder="广告标题（后台标识）" />
                </el-form-item>
                <el-form-item label="类型">
                    <el-radio-group v-model="form.type">
                        <el-radio value="image">图片广告</el-radio>
                        <el-radio value="text">文字广告</el-radio>
                    </el-radio-group>
                </el-form-item>
                <template v-if="form.type === 'image'">
                    <el-form-item label="图片">
                        <div class="avatar-edit-row">
                            <el-image v-if="form.image_url" :src="form.image_url"
                                style="width: 120px; height: 60px; border-radius: 6px" fit="cover" />
                            <el-input v-model="form.image_url" placeholder="图片 URL 或上传" clearable style="flex: 1" />
                            <el-button :loading="uploading" @click="triggerUpload">上传</el-button>
                            <input ref="uploadInput" type="file" accept="image/jpeg,image/png,image/webp,image/gif"
                                class="hidden-file" @change="handleUpload" />
                        </div>
                    </el-form-item>
                </template>
                <template v-else>
                    <el-form-item label="文字标题">
                        <el-input v-model="form.text_title" placeholder="广告文字标题" />
                    </el-form-item>
                    <el-form-item label="文字描述">
                        <el-input v-model="form.text_desc" type="textarea" :rows="2" placeholder="一句话描述" />
                    </el-form-item>
                </template>
                <el-form-item label="跳转链接">
                    <el-input v-model="form.link_url" placeholder="https://…（点击广告跳转，可留空）" />
                </el-form-item>
                <el-form-item label="投放位置">
                    <el-select v-model="form.position" style="width: 100%">
                        <el-option label="文章正文顶部" value="article_top" />
                        <el-option label="文章评论区上方" value="article_bottom" />
                        <el-option label="首页中部横幅" value="home_mid" />
                    </el-select>
                </el-form-item>
                <el-form-item label="排序号">
                    <el-input-number v-model="form.sort_order" :min="0" style="width: 100%" />
                </el-form-item>
                <el-form-item label="状态">
                    <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用"
                        inactive-text="停用" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">

import { ref, reactive, onMounted, type Reactive, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    requestAdList,
    requestAdDetail,
    requestAdAdd,
    requestAdUpdate,
    requestAdStatus,
    requestAdDelete,
    type AdDetail,type PositionKey
} from '../../composables/useRequest'
import { api } from '../../composables/useAxiosConfig'
import {formatTime}from '@/composables/useTool'

const loading = ref(false)
const list:Ref<AdDetail[]> = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const positionFilter = ref('')

const dialogVisible = ref(false)
const dialogMode = ref('add')
const saving = ref(false)

// 敲定defalt='scope'的类型
interface AdItem {
  id: number
  title: string
  type: 'image' | 'text'
  position: string
  sort_order: number
  status: number
  click_count: number
  created_at: string
  // 可能有其他字段，按需添加
}


const form: Reactive<AdDetail> = reactive({
    id: null,
    title: '',
    type: 'image',
    image_url: '',
    text_title: '',
    text_desc: '',
    link_url: '',
    position: 'article_top',
    sort_order: 0,
    status: 1
})


const POSITION_LABELS: Record<PositionKey, string> = {
    article_top: '文章正文顶部',
    article_bottom: '文章评论区上方',
    home_mid: '首页中部横幅',
}

const positionLabel = (p: string) => POSITION_LABELS[p as PositionKey] || p



const fetchList = async () => {
    loading.value = true
    try {
        const res = await requestAdList({
            page: page.value,
            pageSize: pageSize.value,
            keyword: keyword.value.trim() || undefined,
            position: positionFilter.value || undefined
        })

        list.value = res.data.list || []
        total.value = res.data.total || 0
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '获取广告列表失败')
    } finally {
        loading.value = false
    }
}


const handleSearch = () => {
    page.value = 1
    fetchList()
}

const handleSizeChange = (val: number) => {
    pageSize.value = val
    page.value = 1
    fetchList()
}


const handleCurrentChange = (val: number) => {
    page.value = val
    fetchList()
}

const resetForm = () => {
    Object.assign(form, {
        id: null,
        title: '',
        type: 'image',
        image_url: '',
        text_title: '',
        text_desc: '',
        link_url: '',
        position: 'article_top',
        sort_order: 0,
        status: 1
    })
}

const openAdd = () => {
    dialogMode.value = 'add'
    resetForm()
    dialogVisible.value = true
}

const openEdit = async (row:AdItem) => {
    dialogMode.value = 'edit'
    resetForm()
    dialogVisible.value = true
    try {
        const res = await requestAdDetail(row.id)
        const d = res.data || {}
        Object.assign(form, {
            id: d.id,
            title: d.title || '',
            type: d.type || 'image',
            image_url: d.image_url || '',
            text_title: d.text_title || '',
            text_desc: d.text_desc || '',
            link_url: d.link_url || '',
            position: d.position || 'article_top',
            sort_order: Number(d.sort_order) || 0,
            status: Number(d.status) === 1 ? 1 : 0
        })
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '加载广告详情失败')
    }
}

const submitForm = async () => {
    if (!form.title.trim()) {
        ElMessage.warning('请填写广告标题')
        return
    }
    if (form.type === 'image' && !form.image_url!.trim()) {
        ElMessage.warning('图片广告请填写图片')
        return
    }
    if (form.type === 'text' && !form.text_title.trim()) {
        ElMessage.warning('文字广告请填写文字标题')
        return
    }
    saving.value = true
    try {
        const payload = {
            title: form.title,
            type: form.type,
            image_url: form.image_url,
            text_title: form.text_title,
            text_desc: form.text_desc,
            link_url: form.link_url,
            position: form.position,
            sort_order: Number(form.sort_order) || 0,
            status: form.status
        }
        if (dialogMode.value === 'add') {
            await requestAdAdd(payload)
            ElMessage.success('新增广告成功')
        } else {
            await requestAdUpdate(form.id!, payload)
            ElMessage.success('更新广告成功')
        }
        dialogVisible.value = false
        fetchList()
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '保存失败')
    } finally {
        saving.value = false
    }
}



const toggleStatus = async (row:AdItem) => {
    const next = row.status === 1 ? 0 : 1
    try {
        await requestAdStatus(row.id, next)
        ElMessage.success(next === 1 ? '已启用' : '已停用')
        fetchList()
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '操作失败')
    }
}



const handleDelete = (row: AdItem) => {
    ElMessageBox.confirm(`确定删除广告「${row.title}」吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await requestAdDelete(row.id)
            ElMessage.success('删除成功')
            fetchList()
        } catch (e:any) {
            ElMessage.error(e?.response?.data?.message || '删除失败')
        }
    }).catch(() => { })
}

/**
 * @图片上传（复用 OSS 接口）
 */
const uploadInput: Ref<HTMLInputElement | null> = ref(null)
const uploading: Ref<boolean> = ref(false)
const triggerUpload = () => uploadInput.value && uploadInput.value.click()
const handleUpload = async (e: Event) => {
    const target = e.target as HTMLInputElement
    const file = target.files?.[0]
    if (!file) return
    if (!/^image\/(jpeg|png|webp|gif)$/.test(file.type)) {
        ElMessage.warning('仅支持 jpg/png/webp/gif 图片')
        target.value = ''
        return
    }
    if (file.size > 5 * 1024 * 1024) {
        ElMessage.warning('图片不能超过 5MB')
        target.value = ''
        return
    }
    uploading.value = true
    try {
        const fd = new FormData()
        fd.append('image', file)
        // 不手动设置 Content-Type：浏览器自动生成带 boundary 的 multipart 头（axios 推荐）

        interface UploadResponse {
            data?: {
                url?: string
            }
            message?: string
        }

        const res: UploadResponse = await api.post('/upload/image', fd)

        if (res && res.data && res.data.url) {
            form.image_url = res.data.url
            ElMessage.success('图片上传成功')
        } else {
            ElMessage.error((res && res.message) || '上传失败')
        }
    } catch (err:any) {
        ElMessage.error(err?.response?.data?.message || '上传失败')
    } finally {
        uploading.value = false
        target.value = ''
    }
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

.hidden-file {
    display: none;
}

.avatar-edit-row {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
}
</style>
