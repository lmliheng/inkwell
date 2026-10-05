<script setup lang="ts">

import { ref, reactive, computed, onMounted, watch,type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import {
    requestArticleList,
    requestArticleMine,
    requestArticleDelete,
    requestArticleCategoryList,
    type CategoryItem,
    type ArticleItem
} from '@/composables/useRequest'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Notebook, FolderOpened, ChatDotRound } from '@element-plus/icons-vue'
import CategoryManage from './CategoryManage.vue'
import CommentManage from './CommentManage.vue'

import {formatTime} from '@/composables/useTool.ts'


const router = useRouter()
const authStore = useAuthStore()

// 角色：1=admin(超级管理员)、2=user(普通用户)、3=editor(编辑)
// admin/editor 管理所有人文章（调 /article/list），普通用户管理自己的（调 /article/mine）
const isAdminOrEditor = computed(() => {
    const detail = authStore.userInfo?.user_detail! || {}
    const id = Number(detail.role_id)
    if (id === 1 || id === 3) return true
    const name = String(detail.role_name || '').trim()
    return ['admin', 'editor', '超级管理员', '编辑'].includes(name)
})

// 仅管理员（role_id = 1）
const isAdmin = computed(() => {
    const detail = authStore.userInfo?.user_detail! || {}
    const id = Number(detail.role_id)
    if (id === 1) return true
    return String(detail.role_name || '').trim() === '超级管理员'
})

// 内嵌 tab：articles 文章列表 / categories 分类管理 / comments 评论管理（记忆上次选择）
const activeTab = ref(localStorage.getItem('article_manage_tab') || 'articles')
watch(activeTab, (v) => localStorage.setItem('article_manage_tab', v))

const loading = ref(false)
const articleList:Ref<ArticleItem[]> = ref([])
const total = ref(0)

const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const author = ref('')
const categoryId = ref(null)
const statusFilter = ref('') // ''=全部，0=草稿，1=已发布，2=仅自己可见

// 博客前端地址
const blogBase = import.meta.env.BLOG_ADDRESS

const categoryList:Ref<CategoryItem[]> = ref([])

// 普通用户：/article/mine 不支持 keyword/category 查询，故拉取全量后本地过滤分页
const allMineList:Ref<ArticleItem[]> = ref([])

// 状态映射：0-草稿，1-已发布，2-仅自己可见
const statusMap:Record<string,{label:string,type:string}> = {
    0: { label: '草稿', type: 'info' },
    1: { label: '已发布', type: 'success' },
    2: { label: '仅自己可见', type: 'warning' }
}

const getCategories = async () => {
    try {
        const res = await requestArticleCategoryList()
        categoryList.value = res.data?.list || []
    } catch (e) {
        // 分类获取失败不阻塞列表
        console.error(e)
    }
}


// 普通用户模式下的本地过滤 + 分页
const applyMineFilter = () => {
    let arr = allMineList.value || []
    const kw = keyword.value.trim()
    if (kw) {
        arr = arr.filter((a) => String(a.title || '').includes(kw) || String(a.content || '').includes(kw))
    }
    if (author.value.trim()) {
        const aw = author.value.trim()
        arr = arr.filter((a) => String(a.author_name || a.user_id || '').includes(aw))
    }
    if (categoryId.value != null && categoryId.value !== '') {
        const cid = Number(categoryId.value)
        arr = arr.filter((a) => (a.category_ids || []).includes(cid))
    }
    if (statusFilter.value !== '') {
        arr = arr.filter((a) => Number(a.status) === Number(statusFilter.value))
    }
    total.value = arr.length
    const start = (page.value - 1) * pageSize.value
    articleList.value = arr.slice(start, start + pageSize.value)
}

const loadList = async () => {
    loading.value = true
    try {
        if (isAdminOrEditor.value) {
            const res = await requestArticleList({
                page: page.value,
                pageSize: pageSize.value,
                keyword: keyword.value.trim() || undefined,
                author: author.value.trim() || undefined,
                category_id: categoryId.value || undefined,
                status: statusFilter.value === '' ? 'all' : statusFilter.value
            })
            const d = res.data || {}
            articleList.value = d.list || []
            total.value = d.total || 0
        } else {
            const res = await requestArticleMine({ page: 1, pageSize: 10000 })
            const d = res.data || {}
            allMineList.value = d.list || []
            applyMineFilter()
        }
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '获取文章列表失败')
    } finally {
        loading.value = false
    }
}

const handleSearch = () => {
    page.value = 1
    loadList()
}

const handleReset = () => {
    keyword.value = ''
    author.value = ''
    categoryId.value = null
    statusFilter.value = ''
    page.value = 1
    loadList()
}

const handleSizeChange = (val:number) => {
    pageSize.value = val
    page.value = 1
    if (isAdminOrEditor.value) {
        loadList()
    } else {
        applyMineFilter()
    }
}

const handleCurrentChange = (val:number) => {
    page.value = val
    if (isAdminOrEditor.value) {
        loadList()
    } else {
        applyMineFilter()
    }
}

const handleCreate = () => {
    router.push('/article/article-create')
}

const handleEdit = (row:ArticleItem) => {
    router.push({ path: '/article/article-create', query: { id: row.article_id } })
}

const handleDelete = (row:ArticleItem) => {
    ElMessageBox.confirm(`确定删除文章「${row.title}」吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await requestArticleDelete(row.article_id!)
            ElMessage.success('删除文章成功')
            loadList()
        } catch (e:any) {
            ElMessage.error(e?.response?.data?.message || '删除失败')
        }
    }).catch(() => {})
}

const viewArticle = (row:ArticleItem) => {
    console.log(blogBase)
    window.open(`${blogBase}/article/${row.article_id}`, '_blank')
}

onMounted(() => {
    getCategories()
    loadList()
})

// userInfo 异步加载完成后，根据角色重新拉取（刷新后 store 可能尚未就绪）
watch(isAdminOrEditor, () => {
    page.value = 1
    loadList()
})
</script>

<template>
    <div>
        <el-tabs v-model="activeTab" class="article-tabs">
            <el-tab-pane name="articles">
                <template #label>
                    <span class="tab-label"><el-icon><Notebook /></el-icon><span>文章列表</span></span>
                </template>
        <!-- 工具栏：搜索 + 分类筛选 + 写文章 -->
        <div class="toolbar">
            <div class="toolbar-filters">
                <el-input
                    v-model="keyword"
                    placeholder="输入标题/内容关键词"
                    clearable
                    style="width: 200px; margin-right: 8px"
                    @keyup.enter="handleSearch"
                    @clear="handleSearch"
                />
                <el-input
                    v-model="author"
                    placeholder="输入作者"
                    clearable
                    style="width: 160px; margin-right: 8px"
                    @keyup.enter="handleSearch"
                    @clear="handleSearch"
                />
                <el-select
                    v-model="categoryId"
                    placeholder="全部分类"
                    clearable
                    style="width: 180px; margin-right: 8px"
                    @change="handleSearch"
                >
                    <el-option
                        v-for="c in categoryList"
                        :key="c.category_id"
                        :label="c.category_name"
                        :value="c.category_id"
                    />
                </el-select>
                <el-select
                    v-model="statusFilter"
                    placeholder="全部状态"
                    clearable
                    style="width: 140px; margin-right: 8px"
                    @change="handleSearch"
                >
                    <el-option label="全部状态" value="" />
                    <el-option label="草稿" :value="0" />
                    <el-option label="已发布" :value="1" />
                    <el-option label="仅自己可见" :value="2" />
                </el-select>
                <el-button type="primary" @click="handleSearch">搜索</el-button>
                <el-button @click="handleReset">重置</el-button>
            </div>
            <el-button type="primary" @click="handleCreate">写文章</el-button>
        </div>

        <!-- 文章列表 -->
        <el-table :data="articleList" border stripe v-loading="loading">
            <el-table-column align="center" prop="article_id" label="ID" width="80" />
            <el-table-column align="center" prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column align="center" label="作者" width="140">
                <template #default="scope">
                    {{ scope.row.author_name || scope.row.user_id }}
                </template>
            </el-table-column>
            <el-table-column align="center" label="分类" min-width="160">
                <template #default="scope">
                    <el-tag
                        v-for="(name, idx) in scope.row.category_names"
                        :key="idx"
                        size="small"
                        type="primary"
                        style="margin: 0 2px"
                    >
                        {{ name }}
                    </el-tag>
                    <span v-if="!scope.row.category_names || scope.row.category_names.length === 0">-</span>
                </template>
            </el-table-column>
            <el-table-column align="center" label="状态" width="120">
                <template #default="scope">
                    <el-tag :type="statusMap[scope.row.status]?.type || 'info'">
                        {{ statusMap[scope.row.status]?.label || scope.row.status }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="点赞" width="80">
                <template #default="scope">
                    <span>{{ scope.row.like_count || 0 }}</span>
                </template>
            </el-table-column>
            <el-table-column align="center" label="收藏" width="80">
                <template #default="scope">
                    <span>{{ scope.row.favorite_count || 0 }}</span>
                </template>
            </el-table-column>
            <el-table-column align="center" label="创建时间" width="180">
                <template #default="scope">
                    {{ formatTime(scope.row.created_at) }}
                </template>
            </el-table-column>
            <el-table-column align="center" label="操作" width="220">
                <template #default="scope">
                    <el-button type="primary" size="small" @click="handleEdit(scope.row)">编辑</el-button>
                    <el-button type="success" size="small" @click="viewArticle(scope.row)">查看</el-button>
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
            </el-tab-pane>

            <el-tab-pane v-if="isAdminOrEditor" name="categories">
                <template #label>
                    <span class="tab-label"><el-icon><FolderOpened /></el-icon><span>分类管理</span></span>
                </template>
                <CategoryManage />
            </el-tab-pane>

            <el-tab-pane v-if="isAdmin" name="comments">
                <template #label>
                    <span class="tab-label"><el-icon><ChatDotRound /></el-icon><span>评论管理</span></span>
                </template>
                <CommentManage />
            </el-tab-pane>
        </el-tabs>
    </div>
</template>

<style scoped>
/* ---- Tabs 视觉升级 ---- */
.article-tabs :deep(.el-tabs__header) {
    margin-bottom: 0;
    padding: 0 12px;
    background-color: #f5f7fa;
    border-radius: 10px 10px 0 0;
    border-bottom: 1px solid #e4e7ed;
}
.article-tabs :deep(.el-tabs__item) {
    height: 44px;
    line-height: 44px;
    padding: 0 18px;
    font-weight: 500;
    color: #606266;
    transition: color 0.2s ease;
}
.article-tabs :deep(.el-tabs__item .el-icon) {
    margin-right: 6px;
    vertical-align: -2px;
}
.article-tabs :deep(.el-tabs__item.is-active) {
    color: #409eff;
    font-weight: 700;
}
.article-tabs :deep(.el-tabs__item:hover) {
    color: #409eff;
}
.article-tabs :deep(.el-tabs__active-bar) {
    height: 3px;
    border-radius: 3px 3px 0 0;
    background-color: #409eff;
}
.article-tabs :deep(.el-tabs__content) {
    padding-top: 16px;
}
.tab-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

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
