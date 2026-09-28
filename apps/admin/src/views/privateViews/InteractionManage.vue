<template>
    <div>
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
            <el-tab-pane label="点赞管理" name="likes">
                <div class="toolbar">
                    <div class="toolbar-filters">
                        <el-input
                            v-model="keyword"
                            placeholder="搜索文章标题/用户"
                            clearable
                            style="width: 260px; margin-right: 8px"
                            @keyup.enter="handleSearch"
                            @clear="handleSearch"
                        />
                        <el-button type="primary" @click="handleSearch">搜索</el-button>
                    </div>
                </div>
                <el-table :data="likeList" border stripe v-loading="likeLoading">
                    <el-table-column align="center" prop="id" label="ID" width="100" />
                    <el-table-column align="center" label="文章" min-width="220">
                        <template #default="scope">
                            <span class="hash-text">{{ scope.row.article_title }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column align="center" label="点赞用户" width="160">
                        <template #default="scope">
                            {{ scope.row.nickname || scope.row.username }}
                            <span class="sub-text">@{{ scope.row.username }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column align="center" label="点赞时间" width="180">
                        <template #default="scope">
                            {{ formatTime(scope.row.created_at) }}
                        </template>
                    </el-table-column>
                    <el-table-column align="center" label="操作" width="100">
                        <template #default="scope">
                            <el-button type="danger" size="small" @click="handleDeleteLike(scope.row)">删除</el-button>
                        </template>
                    </el-table-column>
                </el-table>
                <div class="pagination">
                    <el-pagination
                        background
                        layout="total, sizes, prev, pager, next, jumper"
                        :total="likeTotal"
                        :current-page="likePage"
                        :page-size="likePageSize"
                        :page-sizes="[10, 20, 50]"
                        @size-change="(v: number) => handleSizeChange(v, 'likes')"
                        @current-change="(v: number) => handleCurrentChange(v, 'likes')"
                    />
                </div>
            </el-tab-pane>

            <el-tab-pane label="收藏管理" name="favorites">
                <div class="toolbar">
                    <div class="toolbar-filters">
                        <el-input
                            v-model="keyword"
                            placeholder="搜索文章标题/用户"
                            clearable
                            style="width: 260px; margin-right: 8px"
                            @keyup.enter="handleSearch"
                            @clear="handleSearch"
                        />
                        <el-button type="primary" @click="handleSearch">搜索</el-button>
                    </div>
                </div>
                <el-table :data="favoriteList" border stripe v-loading="favoriteLoading">
                    <el-table-column align="center" prop="id" label="ID" width="100" />
                    <el-table-column align="center" label="文章" min-width="220">
                        <template #default="scope">
                            <span class="hash-text">{{ scope.row.article_title }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column align="center" label="收藏用户" width="160">
                        <template #default="scope">
                            {{ scope.row.nickname || scope.row.username }}
                            <span class="sub-text">@{{ scope.row.username }}</span>
                        </template>
                    </el-table-column>
                    <el-table-column align="center" label="收藏时间" width="180">
                        <template #default="scope">
                            {{ formatTime(scope.row.created_at) }}
                        </template>
                    </el-table-column>
                    <el-table-column align="center" label="操作" width="100">
                        <template #default="scope">
                            <el-button type="danger" size="small" @click="handleDeleteFavorite(scope.row)">删除</el-button>
                        </template>
                    </el-table-column>
                </el-table>
                <div class="pagination">
                    <el-pagination
                        background
                        layout="total, sizes, prev, pager, next, jumper"
                        :total="favoriteTotal"
                        :current-page="favoritePage"
                        :page-size="favoritePageSize"
                        :page-sizes="[10, 20, 50]"
                        @size-change="(v: number) => handleSizeChange(v, 'favorites')"
                        @current-change="(v: number) => handleCurrentChange(v, 'favorites')"
                    />
                </div>
            </el-tab-pane>
        </el-tabs>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    requestLikeManageList,
    requestLikeManageDelete,
    requestFavoriteManageList,
    requestFavoriteManageDelete
} from '../../composables/useRequest'

const activeTab = ref('likes')
const keyword = ref('')

// 点赞
const likeList = ref<any[]>([])
const likeTotal = ref(0)
const likePage = ref(1)
const likePageSize = ref(10)
const likeLoading = ref(false)

// 收藏
const favoriteList = ref<any[]>([])
const favoriteTotal = ref(0)
const favoritePage = ref(1)
const favoritePageSize = ref(10)
const favoriteLoading = ref(false)

const formatTime = (v: any) => (v ? String(v).replace('T', ' ').slice(0, 19) : '')

const fetchLikes = async () => {
    likeLoading.value = true
    try {
        const res = await requestLikeManageList({
            page: likePage.value,
            pageSize: likePageSize.value,
            keyword: keyword.value.trim() || undefined
        })
        likeList.value = res.data.list || []
        likeTotal.value = res.data.total || 0
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '获取点赞记录失败')
    } finally {
        likeLoading.value = false
    }
}

const fetchFavorites = async () => {
    favoriteLoading.value = true
    try {
        const res = await requestFavoriteManageList({
            page: favoritePage.value,
            pageSize: favoritePageSize.value,
            keyword: keyword.value.trim() || undefined
        })
        favoriteList.value = res.data.list || []
        favoriteTotal.value = res.data.total || 0
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '获取收藏记录失败')
    } finally {
        favoriteLoading.value = false
    }
}

const handleTabChange = () => {
    if (activeTab.value === 'likes') {
        likePage.value = 1
        fetchLikes()
    } else {
        favoritePage.value = 1
        fetchFavorites()
    }
}

const handleSearch = () => {
    likePage.value = 1
    favoritePage.value = 1
    if (activeTab.value === 'likes') fetchLikes()
    else fetchFavorites()
}

const handleSizeChange = (val: number, kind: string) => {
    if (kind === 'likes') {
        likePageSize.value = val
        likePage.value = 1
        fetchLikes()
    } else {
        favoritePageSize.value = val
        favoritePage.value = 1
        fetchFavorites()
    }
}

const handleCurrentChange = (val: number, kind: string) => {
    if (kind === 'likes') {
        likePage.value = val
        fetchLikes()
    } else {
        favoritePage.value = val
        fetchFavorites()
    }
}

const handleDeleteLike = (row: any) => {
    ElMessageBox.confirm(`确定删除「${row.article_title}」的这条点赞记录吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await requestLikeManageDelete(row.id)
            ElMessage.success('删除成功')
            fetchLikes()
        } catch (e: any) {
            ElMessage.error(e?.response?.data?.message || '删除失败')
        }
    }).catch(() => {})
}

const handleDeleteFavorite = (row: any) => {
    ElMessageBox.confirm(`确定删除「${row.article_title}」的这条收藏记录吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            await requestFavoriteManageDelete(row.id)
            ElMessage.success('删除成功')
            fetchFavorites()
        } catch (e: any) {
            ElMessage.error(e?.response?.data?.message || '删除失败')
        }
    }).catch(() => {})
}

onMounted(fetchLikes)
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
.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}
.hash-text {
    word-break: break-all;
}
.sub-text {
    color: #909399;
    font-size: 12px;
    margin-left: 4px;
}
</style>
