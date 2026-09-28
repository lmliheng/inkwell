<template>
    <div>
        <div class="toolbar">
            <div class="toolbar-info">
                <p class="page-desc">
                    OAuth 2.0 授权服务器：第三方应用注册为客户端后可让用户通过本站授权登录（授权码+PKCE），
                    或直接使用 client_secret 获取机器访问令牌（Client Credentials）。
                </p>
            </div>
            <el-button type="primary" @click="openCreate">注册应用</el-button>
        </div>

        <el-table :data="list" border stripe v-loading="loading">
            <el-table-column align="center" prop="id" label="ID" width="60" />
            <el-table-column align="center" prop="name" label="应用名" min-width="130" />
            <el-table-column align="center" label="Client ID" min-width="220">
                <template #default="scope">
                    <code class="code-cell">{{ scope.row.client_id }}</code>
                </template>
            </el-table-column>
            <el-table-column align="center" label="回调 URI" min-width="180" show-overflow-tooltip>
                <template #default="scope">
                    <span class="hash-text">{{ scope.row.redirect_uris || '—' }}</span>
                </template>
            </el-table-column>
            <el-table-column align="center" label="授权类型" width="150">
                <template #default="scope">
                    <el-tag v-for="g in String(scope.row.grant_types || '').split(',')" :key="g" size="small" effect="plain" style="margin-right: 4px">
                        {{ g === 'authorization_code' ? '授权码' : '客户端凭证' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="范围" width="90">
                <template #default="scope">
                    <code>{{ scope.row.scopes }}</code>
                </template>
            </el-table-column>
            <el-table-column align="center" label="状态" width="80">
                <template #default="scope">
                    <el-tag :type="scope.row.status === 1 ? 'success' : 'info'" size="small">
                        {{ scope.row.status === 1 ? '启用' : '停用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column align="center" label="操作" width="230">
                <template #default="scope">
                    <el-button type="primary" size="small" @click="openEdit(scope.row)">编辑</el-button>
                    <el-button :type="scope.row.status === 1 ? 'warning' : 'success'" size="small" @click="toggleStatus(scope.row)">
                        {{ scope.row.status === 1 ? '停用' : '启用' }}
                    </el-button>
                    <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 注册/编辑 -->
        <el-dialog v-model="dialogVisible" :title="dialogMode === 'add' ? '注册 OAuth 应用' : '编辑 OAuth 应用'" width="560px">
            <el-form :model="form" label-width="100px">
                <el-form-item label="应用名称">
                    <el-input v-model="form.name" placeholder="如：我的第三方网站" />
                </el-form-item>
                <el-form-item label="描述">
                    <el-input v-model="form.description" placeholder="应用用途说明" />
                </el-form-item>
                <el-form-item label="回调 URI">
                    <el-input v-model="form.redirectUrisText" type="textarea" :rows="2" placeholder="授权回调地址（逗号分隔多个）" />
                </el-form-item>
                <el-form-item label="授权类型">
                    <el-checkbox v-model="form.useAuthCode">授权码 + PKCE（用户授权登录）</el-checkbox>
                    <el-checkbox v-model="form.useClientCreds">Client Credentials（机器访问）</el-checkbox>
                </el-form-item>
                <el-form-item label="权限范围">
                    <el-input v-model="form.scopes" placeholder="read 或 read,write" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitForm">
                    {{ dialogMode === 'add' ? '注册' : '保存' }}
                </el-button>
            </template>
        </el-dialog>

        <!-- 创建成功：展示 client_id / secret -->
        <el-dialog v-model="secretVisible" title="应用注册成功（请保存凭证）" width="560px">
            <el-alert type="warning" :closable="false" show-icon
                title="client_secret 只显示这一次（仅客户端凭证模式需要）。请复制保存到安全位置。"
                style="margin-bottom: 14px" />
            <div class="cred-row">
                <span class="cred-label">Client ID</span>
                <el-input :model-value="newCreds.client_id" readonly>
                    <template #append><el-button @click="copyText(newCreds.client_id)">复制</el-button></template>
                </el-input>
            </div>
            <div v-if="newCreds.client_secret" class="cred-row">
                <span class="cred-label">Client Secret</span>
                <el-input :model-value="newCreds.client_secret" readonly>
                    <template #append><el-button @click="copyText(newCreds.client_secret)">复制</el-button></template>
                </el-input>
            </div>
            <div v-if="!newCreds.client_secret" class="cred-note">
                该应用仅用授权码模式，无需 client_secret。
            </div>
            <div class="usage">
                <p>授权码登录 URL：</p>
                <pre>{{ oauthAuthorizeUrl(newCreds.client_id) }}</pre>
            </div>
            <template #footer>
                <el-button type="primary" @click="secretVisible = false; loadList()">我已保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    requestOAuthClientList,
    requestOAuthClientCreate,
    requestOAuthClientUpdate,
    requestOAuthClientStatus,
    requestOAuthClientDelete
} from '../../composables/useRequest'

const loading = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const dialogMode = ref('add')
const saving = ref(false)
const form = reactive({
    id: null,
    name: '',
    description: '',
    redirectUrisText: '',
    useAuthCode: true,
    useClientCreds: false,
    scopes: 'read'
})

const secretVisible = ref(false)
const newCreds = reactive({ client_id: '', client_secret: '' })
const API_BASE = import.meta.env.API_BASE || 'http://127.0.0.1:7000'

const loadList = async () => {
    loading.value = true
    try {
        const res = await requestOAuthClientList()
        list.value = res.data.list || []
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '获取失败')
    } finally {
        loading.value = false
    }
}

const resetForm = () => {
    Object.assign(form, {
        id: null,
        name: '', description: '',
        redirectUrisText: '',
        useAuthCode: true, useClientCreds: false,
        scopes: 'read'
    })
}

const openCreate = () => {
    dialogMode.value = 'add'
    resetForm()
    dialogVisible.value = true
}

const openEdit = (row:any) => {
    dialogMode.value = 'edit'
    Object.assign(form, {
        id: row.id,
        name: row.name || '',
        description: row.description || '',
        redirectUrisText: (row.redirect_uris || '').split(',').filter(Boolean).join('\n'),
        useAuthCode: String(row.grant_types || '').includes('authorization_code'),
        useClientCreds: String(row.grant_types || '').includes('client_credentials'),
        scopes: row.scopes || 'read'
    })
    dialogVisible.value = true
}

const grantTypesOf = () => {
    const arr = []
    if (form.useAuthCode) arr.push('authorization_code')
    if (form.useClientCreds) arr.push('client_credentials')
    return arr.join(',')
}

const submitForm = async () => {
    if (!form.name.trim()) {
        ElMessage.warning('请填写应用名称')
        return
    }
    const grantTypes = grantTypesOf()
    if (!grantTypes) {
        ElMessage.warning('请至少选择一种授权类型')
        return
    }
    if (form.useAuthCode && !form.redirectUrisText.trim()) {
        ElMessage.warning('授权码模式需要填写回调 URI')
        return
    }
    saving.value = true
    try {
        const payload = {
            name: form.name.trim(),
            description: form.description,
            redirect_uris: form.redirectUrisText.split('\n').map(s => s.trim()).filter(Boolean).join(','),
            grant_types: grantTypes,
            scopes: form.scopes || 'read'
        }
        if (dialogMode.value === 'add') {
            const res = await requestOAuthClientCreate(payload)
            Object.assign(newCreds, {
                client_id: res.data.client_id || '',
                client_secret: res.data.client_secret || ''
            })
            dialogVisible.value = false
            secretVisible.value = true
        } else {
            await requestOAuthClientUpdate(form.id!, payload)
            ElMessage.success('更新成功')
            dialogVisible.value = false
            loadList()
        }
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '保存失败')
    } finally {
        saving.value = false
    }
}

const toggleStatus = async (row:any) => {
    const next = row.status === 1 ? 0 : 1
    try {
        await requestOAuthClientStatus(row.id, next)
        ElMessage.success(next === 1 ? '已启用' : '已停用')
        loadList()
    } catch (e:any) {
        ElMessage.error(e?.response?.data?.message || '操作失败')
    }
}

const handleDelete = (row:any) => {
    ElMessageBox.confirm(`确定删除应用「${row.name}」吗？删除后该应用的授权全部失效。`, '提示', {
        confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    }).then(async () => {
        try {
            await requestOAuthClientDelete(row.id)
            ElMessage.success('已删除')
            loadList()
        } catch (e:any) {
            ElMessage.error(e?.response?.data?.message || '删除失败')
        }
    }).catch(() => {})
}

const copyText = async (text:string) => {
    try {
        await navigator.clipboard.writeText(text)
        ElMessage.success('已复制')
    } catch (e) {
        ElMessage.warning('复制失败，请手动选择复制')
    }
}

const oauthAuthorizeUrl = (clientId:string) => {
    return `${API_BASE}/oauth/authorize?client_id=${clientId}&redirect_uri=你的回调地址&response_type=code`
}

onMounted(loadList)
</script>

<style scoped>
.toolbar {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
    margin-bottom: 16px;
}
.page-desc {
    font-size: 13px;
    color: #909399;
    margin: 0;
    line-height: 1.7;
}
.code-cell {
    font-size: 12px;
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 4px;
}
.hash-text {
    word-break: break-all;
    font-size: 12px;
}
.cred-row {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
}
.cred-label {
    width: 90px;
    flex-shrink: 0;
    font-size: 13px;
    color: #606266;
    text-align: right;
}
.cred-note {
    font-size: 12px;
    color: #909399;
    margin: 4px 0 10px 100px;
}
.usage {
    margin-top: 12px;
    font-size: 12px;
    color: #606266;
}
.usage pre {
    background: #f7f8fa;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    padding: 10px 12px;
    font-size: 12px;
    line-height: 1.6;
    overflow-x: auto;
}
</style>
