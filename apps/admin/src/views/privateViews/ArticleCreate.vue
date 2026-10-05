<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownCom from '@/components/MarkdownCom.vue'
import { ElMessage } from 'element-plus'
import {
    requestArticleAdd,
    requestArticleUpdate,
    requestArticleDetail,
    requestArticleCategoryList,
    type CategoryItem
} from '@/composables/useRequest'
import type { st } from 'vue-router/dist/index-D7ja2BKs.js'

const route = useRoute()
const router = useRouter()

// 编辑模式：路由带 id（query 优先，兼容 params）
const articleId = computed(() => {
    const id = route.query.id ?? route.params.id
    return id ? Number(id) : null
})
const isEdit = computed(() => !!articleId.value)

const loading = ref(false)
const submitting = ref(false)
const categoryList: Ref<CategoryItem[]> = ref([])

interface ArticleCreateForm {
    title: string,
    content: string
    category_ids: number[],
    status: 0 | 1 | 2 // 0-草稿，1-已发布，2-仅自己可见
}

const form: ArticleCreateForm = reactive({
    title: '',
    content: '',
    category_ids: [],
    status: 1
})

const statusOptions = [
    { value: 0, label: '草稿' },
    { value: 1, label: '已发布' },
    { value: 2, label: '仅自己可见' }
]

// 全屏书写状态
const fullscreen = ref(false)

const getCategories = async () => {
    try {
        const res = await requestArticleCategoryList()
        categoryList.value = res.data?.list || []
    } catch (e) {
        console.error(e)
    }
}

const getDetail = async () => {
    if (!articleId.value) return
    loading.value = true
    try {
        const res = await requestArticleDetail(articleId.value)
        const d = res.data || {}
        form.title = d.title || ''
        form.content = d.content || ''
        form.category_ids = Array.isArray(d.category_ids) ? d.category_ids : []
        form.status = d.status ?? 1
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '获取文章详情失败')
    } finally {
        loading.value = false
    }
}

const handleSubmit = async () => {
    if (!form.title || !form.title.trim()) {
        ElMessage.warning('请输入文章标题')
        return
    }
    if (!form.content || !form.content.trim()) {
        ElMessage.warning('请输入文章内容')
        return
    }
    submitting.value = true
    try {
        const payload = {
            title: form.title.trim(),
            content: form.content,
            category_ids: form.category_ids || [],
            status: Number(form.status) as (0 | 1 | 2)
        }
        if (isEdit.value) {
            await requestArticleUpdate(articleId.value!, payload)
            ElMessage.success('文章更新成功')
        } else {
            await requestArticleAdd(payload)
            ElMessage.success('文章创建成功')
        }
        router.push('/article/article-manage')
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '提交失败')
    } finally {
        submitting.value = false
    }
}

const handleBack = () => {
    router.push('/article/article-manage')
}

const onFullscreenChange = (v:boolean) => {
    fullscreen.value = v
}

// 全屏时 Esc 退出（父组件统一处理，避免子组件实例状态不一致）
const handleGlobalEsc = (e:KeyboardEvent) => {
    if (e.key === 'Escape' && fullscreen.value) {
        fullscreen.value = false
    }
}

onMounted(() => {
    getCategories()
    getDetail()
    window.addEventListener('keydown', handleGlobalEsc)
})

onBeforeUnmount(() => {
    window.removeEventListener('keydown', handleGlobalEsc)
})
</script>

<template>
    <div v-loading="loading">

        
        <!-- ================= 普通模式：左右分栏 ================= -->
        <template v-if="!fullscreen">
            <div class="article-create-header">
                <span class="page-title">{{ isEdit ? '编辑文章' : '写文章' }}</span>
                <el-button @click="handleBack">返回列表</el-button>
            </div>

            <div class="create-layout">
                <!-- 左侧：标题 + 编辑器 -->
                <div class="create-main">
                    <el-input v-model="form.title" placeholder="请输入文章标题" maxlength="200" show-word-limit size="large"
                        class="title-input" />
                    <MarkdownCom v-model="form.content" height="70vh" @fullscreen-change="onFullscreenChange" />
                </div>

                <!-- 右侧：属性栏（可折叠） -->
                <div class="create-side">
                    <el-card shadow="never" class="side-card">
                        <template #header>
                            <span class="side-title">文章属性</span>
                        </template>
                        <div class="side-field">
                            <div class="side-label">分类</div>
                            <el-select v-model="form.category_ids" multiple clearable collapse-tags placeholder="选择分类"
                                style="width: 100%">
                                <el-option v-for="c in categoryList" :key="c.category_id" :label="c.category_name"
                                    :value="c.category_id" />
                            </el-select>
                        </div>
                        <div class="side-field">
                            <div class="side-label">状态</div>
                            <el-radio-group v-model="form.status">
                                <el-radio v-for="s in statusOptions" :key="s.value" :value="s.value">
                                    {{ s.label }}
                                </el-radio>
                            </el-radio-group>
                        </div>
                        <div class="side-actions">
                            <el-button type="primary" style="width: 100%" size="large" :loading="submitting"
                                @click="handleSubmit">
                                {{ isEdit ? '保存修改' : '发布' }}
                            </el-button>
                            <el-button style="width: 100%" @click="handleBack">保存草稿返回</el-button>
                        </div>
                    </el-card>
                </div>
            </div>
        </template>

        <!-- ================= 全屏书写模式 ================= -->
        <div v-else class="fs-overlay">
            <!-- 全屏顶部工具条 -->
            <div class="fs-toolbar">
                <el-button size="small" @click="handleBack">← 返回</el-button>
                <el-input v-model="form.title" placeholder="请输入文章标题" maxlength="200" class="fs-title" />
                <el-button type="primary" :loading="submitting" @click="handleSubmit">
                    {{ isEdit ? '保存修改' : '发布' }}
                </el-button>
                <el-button size="small" @click="fullscreen = false">退出全屏</el-button>
            </div>
            <!-- 全屏编辑器（铺满剩余高度，左编辑右预览） -->
            <div class="fs-editor">
                <MarkdownCom v-model="form.content" height="calc(100vh - 56px)"
                    @fullscreen-change="onFullscreenChange" />
            </div>
        </div>
    </div>
</template>

<style scoped>
.article-create-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.page-title {
    font-size: 18px;
    font-weight: bold;
}

/* ---- 左右分栏 ---- */
.create-layout {
    display: flex;
    gap: 16px;
    align-items: flex-start;
}

.create-main {
    flex: 1;
    min-width: 0;
}

.create-side {
    width: 280px;
    flex-shrink: 0;
    position: sticky;
    top: 16px;
}

.title-input {
    margin-bottom: 14px;
}

.side-card {
    border-radius: 8px;
}

.side-title {
    font-weight: 600;
}

.side-field {
    margin-bottom: 16px;
}

.side-label {
    font-size: 13px;
    color: #909399;
    margin-bottom: 6px;
}

.side-actions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 4px;
}

/* ---- 全屏模式 ---- */
.fs-overlay {
    position: fixed;
    inset: 0;
    z-index: 2000;
    display: flex;
    flex-direction: column;
    background: #fff;
}

.fs-toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 16px;
    border-bottom: 1px solid #e4e7ed;
    background: #fafafa;
}

.fs-title {
    flex: 1;
}

.fs-editor {
    flex: 1;
    min-height: 0;
    padding: 14px 16px;
    overflow: hidden;
}
</style>
