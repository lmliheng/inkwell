<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useAuthStore } from '../../store/auth'
import {
    requestUserInfo,
    requestSelfUpdate,
    requestSelfResetPassword,
    type UserItem
} from '../../composables/useRequest'
import { ElMessage } from 'element-plus'
import { api } from '../../composables/useAxiosConfig'
import {
    Document,
    ChatDotRound,
    Calendar,
    Medal,
    Key,
    User,
    UserFilled,
    Message,
    Location,
    Lock,
} from '@element-plus/icons-vue'

const authStore = useAuthStore()

const permissionRead = (val: string) => {
    const map: Record<string, string> = {
        'user:create': '创建用户',
        'user:read': '读取用户信息',
        'user:update': '更新用户',
        'user:delete': '删除用户',
        'article:create': '创建文章',
        'article:read': '查看文章',
        'article:update': '更新文章',
        'article:delete': '删除文章',
        'category:create': '创建分类',
        'category:read': '查看分类',
        'category:update': '更新分类',
        'category:delete': '删除分类',
    }
    return map[val] || val
}

const detail = computed<UserItem>(() => authStore.userInfo?.user_detail || ({} as UserItem))
const permissions = computed(() => authStore.userInfo?.user_permission || [])

const refreshUserInfo = async () => {
    try {
        const res = await requestUserInfo()
        if (res.code === 200) {
            authStore.setUserInfo(res.user_info)
        }
    } catch (e) {
        console.error(e)
    }
}

// 编辑资料
const editVisible = ref(false)
const editLoading = ref(false)
const editForm = reactive({
    username: '',
    email: '',
    name: '',
    area: '',
    bio: '',
    avatar: '',
    vip: 0,
    checkinDay: 0,
})

const avatarInput = ref<HTMLInputElement | null>(null)
const uploadingAvatar = ref(false)

const triggerAvatarUpload = () => {
    if (avatarInput.value) avatarInput.value.click()
}

const handleAvatarFile = async (e: Event) => {
    const target = e.target as HTMLInputElement
    const file = target.files && target.files[0]
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
    uploadingAvatar.value = true
    try {
        const formData = new FormData()
        formData.append('image', file)
        const res = await api.post('/upload/image', formData) as any
        if (res && res.data && res.data.url) {
            editForm.avatar = res.data.url
            ElMessage.success('头像上传成功')
        } else {
            ElMessage.error((res && res.message) || '上传失败')
        }
    } catch (err: any) {
        ElMessage.error(err?.response?.data?.message || '上传失败')
    } finally {
        uploadingAvatar.value = false
        target.value = ''
    }
}

const openEdit = () => {
    const d = detail.value
    editForm.username = d.username || ''
    editForm.email = d.email || ''
    editForm.name = d.name || ''
    editForm.area = d.area || ''
    editForm.bio = d.bio || ''
    editForm.avatar = d.avatar || ''
    editForm.vip = Number(d.vip) || 0
    editForm.checkinDay = Number(d.checkinDay) || 0
    editVisible.value = true
}

const submitEdit = async () => {
    if (!editForm.username || !editForm.email) {
        ElMessage.warning('用户名和邮箱不能为空')
        return
    }
    const id = detail.value.id
    editLoading.value = true
    try {
        await requestSelfUpdate({
            id,
            username: editForm.username,
            email: editForm.email,
            name: editForm.name,
            area: editForm.area,
            bio: editForm.bio,
            avatar: editForm.avatar || '',
            vip: Number(editForm.vip) || 0,
            checkinDay: Number(editForm.checkinDay) || 0,
        })
        ElMessage.success('资料更新成功')
        editVisible.value = false
        await refreshUserInfo()
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '更新失败')
    } finally {
        editLoading.value = false
    }
}

// 重置密码
const pwdVisible = ref(false)
const pwdForm = reactive({ password: '', confirm: '' })

const submitPwd = async () => {
    if (!pwdForm.password) {
        ElMessage.warning('请输入新密码')
        return
    }
    if (pwdForm.password !== pwdForm.confirm) {
        ElMessage.warning('两次输入的密码不一致')
        return
    }
    try {
        await requestSelfResetPassword(pwdForm.password)
        ElMessage.success('密码重置成功')
        pwdVisible.value = false
        pwdForm.password = ''
        pwdForm.confirm = ''
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '重置失败')
    }
}

// ===== TOTP 两步验证 =====
const totpBound = ref(false)
const totpVisible = ref(false) // 绑定/解绑弹窗
const totpStep = ref('bind') // bind: 展示二维码 | confirm: 输入动态码 | disable
const totpSecret = ref('')
const totpQrUrl = ref('')
const totpCodeInput = ref('')
const totpBusy = ref(false)

const loadTotpStatus = async () => {
    try {
        const res = await api.get('/totp/status')
        totpBound.value = !!res.data?.bound
    } catch (e) {
        totpBound.value = false
    }
}

// 打开 TOTP 弹窗
const openTotp = async () => {
    totpVisible.value = true
    if (!totpBound.value) {
        // 未绑定：开始绑定流程 → 拉取 secret + 生成二维码
        totpStep.value = 'bind'
        totpBusy.value = true
        try {
            const res = await api.post('/totp/setup')
            totpSecret.value = res.data?.secret || ''
            const uri = res.data?.uri || ''
            totpQrUrl.value = await toDataURL(uri)
        } catch (e: any) {
            ElMessage.error(e?.response?.data?.message || '获取绑定信息失败')
        } finally {
            totpBusy.value = false
        }
    } else {
        totpStep.value = 'disable'
    }
}

// otpauth URI → dataURL 二维码
const toDataURL = (text: string): Promise<string> => {
    return new Promise((resolve) => {
        if (!text) return resolve('')
        import('qrcode').then((QRCode) => {
            QRCode.toDataURL(text, { width: 180, margin: 1 })
                .then(resolve)
                .catch(() => resolve(''))
        }).catch(() => resolve(''))
    })
}

const copySecret = async () => {
    try {
        await navigator.clipboard.writeText(totpSecret.value)
        ElMessage.success('密钥已复制')
    } catch (e) {
        ElMessage.warning('复制失败，请手动选择复制')
    }
}

// 确认绑定
const confirmTotp = async () => {
    if (!totpCodeInput.value.trim()) {
        ElMessage.warning('请输入 6 位动态码')
        return
    }
    totpBusy.value = true
    try {
        await api.post('/totp/confirm', { secret: totpSecret.value, code: totpCodeInput.value.trim() })
        ElMessage.success('TOTP 绑定成功')
        totpBound.value = true
        totpVisible.value = false
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '绑定失败')
    } finally {
        totpBusy.value = false
    }
}

// 解绑
const disableTotp = async () => {
    if (!totpCodeInput.value.trim()) {
        ElMessage.warning('请输入当前 6 位动态码以确认解绑')
        return
    }
    totpBusy.value = true
    try {
        await api.post('/totp/disable', { code: totpCodeInput.value.trim() })
        ElMessage.success('已解绑 TOTP')
        totpBound.value = false
        totpVisible.value = false
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '解绑失败')
    } finally {
        totpBusy.value = false
    }
}

onMounted(() => {
    // 进首页总是刷新用户信息（含文章/评论统计）
    refreshUserInfo()
    loadTotpStatus()
})
</script>

<template>
    <div class="profile-page">
        <!-- 顶部横幅 -->
        <div class="profile-hero">
            <div class="hero-left">
                <el-avatar :size="96" :src="detail.avatar" class="hero-avatar">
                    {{ (detail.name || detail.username || 'U')[0] }}
                </el-avatar>
                <div class="hero-info">
                    <div class="hero-name-row">
                        <span class="hero-name">{{ detail.name || detail.username }}</span>
                        <el-tag v-if="detail.role_name" size="small" effect="dark" class="hero-tag">
                            {{ detail.role_name }}
                        </el-tag>
                        <el-tag v-if="detail.vip" size="small" type="warning" effect="dark" class="hero-tag">
                            VIP {{ detail.vip }}
                        </el-tag>
                    </div>
                    <div class="hero-sub">
                        <span>@{{ detail.username }}</span>
                        <span v-if="detail.area">· {{ detail.area }}</span>
                    </div>
                    <div v-if="detail.bio" class="hero-bio">{{ detail.bio }}</div>
                </div>
            </div>
            <div class="hero-actions">
                <el-button type="primary" @click="openEdit">编辑资料</el-button>
                <el-button plain @click="pwdVisible = true">重置密码</el-button>
                <el-button plain @click="openTotp">
                    <el-tag v-if="totpBound" size="small" type="success" effect="plain" style="margin-right: 6px">已启用</el-tag>
                    TOTP 验证
                </el-button>
            </div>
        </div>

        <!-- 统计卡片 -->
        <el-row :gutter="16" class="stats-row">
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-icon stat-icon-article"><el-icon :size="26"><Document /></el-icon></div>
                    <div class="stat-num">{{ detail.article_count ?? '-' }}</div>
                    <div class="stat-label">已发布文章</div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-icon stat-icon-comment"><el-icon :size="26"><ChatDotRound /></el-icon></div>
                    <div class="stat-num">{{ detail.comment_count ?? '-' }}</div>
                    <div class="stat-label">我的评论</div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-icon stat-icon-checkin"><el-icon :size="26"><Calendar /></el-icon></div>
                    <div class="stat-num">{{ detail.checkinDay ?? '-' }}</div>
                    <div class="stat-label">签到天数</div>
                </el-card>
            </el-col>
            <el-col :span="6">
                <el-card shadow="hover" class="stat-card">
                    <div class="stat-icon stat-icon-vip"><el-icon :size="26"><Medal /></el-icon></div>
                    <div class="stat-num">VIP {{ detail.vip ?? 0 }}</div>
                    <div class="stat-label">会员等级</div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 双栏内容 -->
        <el-row :gutter="16">
            <el-col :span="14">
                <el-card shadow="never" header="基本信息">
                    <div class="info-grid">
                        <div class="info-item">
                            <el-icon class="info-icon" :size="20"><Key /></el-icon>
                            <div class="info-body">
                                <div class="info-label">用户 ID</div>
                                <div class="info-value">{{ detail.id }}</div>
                            </div>
                        </div>
                        <div class="info-item">
                            <el-icon class="info-icon" :size="20"><User /></el-icon>
                            <div class="info-body">
                                <div class="info-label">用户名</div>
                                <div class="info-value">{{ detail.username || '-' }}</div>
                            </div>
                        </div>
                        <div class="info-item">
                            <el-icon class="info-icon" :size="20"><UserFilled /></el-icon>
                            <div class="info-body">
                                <div class="info-label">姓名</div>
                                <div class="info-value">{{ detail.name || '-' }}</div>
                            </div>
                        </div>
                        <div class="info-item">
                            <el-icon class="info-icon" :size="20"><Message /></el-icon>
                            <div class="info-body">
                                <div class="info-label">邮箱</div>
                                <div class="info-value">{{ detail.email || '-' }}</div>
                            </div>
                        </div>
                        <div class="info-item">
                            <el-icon class="info-icon" :size="20"><Location /></el-icon>
                            <div class="info-body">
                                <div class="info-label">地区</div>
                                <div class="info-value">{{ detail.area || '-' }}</div>
                            </div>
                        </div>
                        <div class="info-item">
                            <el-icon class="info-icon" :size="20"><Lock /></el-icon>
                            <div class="info-body">
                                <div class="info-label">角色</div>
                                <div class="info-value">{{ detail.role_name || '-' }}</div>
                            </div>
                        </div>
                    </div>
                </el-card>
            </el-col>
            <el-col :span="10">
                <el-card shadow="never" header="我的权限">
                    <div class="perm-grid">
                        <el-tag v-for="(item, index) in permissions" :key="index" class="perm-tag" effect="plain">
                            {{ permissionRead(item.permission_name) }}
                        </el-tag>
                        <p v-if="!permissions.length" class="perm-empty">暂无权限记录</p>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 编辑弹窗 -->
        <el-dialog v-model="editVisible" title="编辑资料" width="520px">
            <el-form :model="editForm" label-width="90px">
                <el-form-item label="头像">
                    <div class="avatar-edit-row">
                        <el-avatar :size="48" :src="editForm.avatar || undefined">
                            {{ (editForm.name || editForm.username || 'U')[0] }}
                        </el-avatar>
                        <el-input v-model="editForm.avatar" placeholder="粘贴头像图片 URL 或上传" clearable />
                        <el-button type="primary" size="small" :loading="uploadingAvatar" @click="triggerAvatarUpload">
                            上传
                        </el-button>
                        <input
                            ref="avatarInput"
                            type="file"
                            accept="image/jpeg,image/png,image/webp,image/gif"
                            style="display: none"
                            @change="handleAvatarFile"
                        />
                    </div>
                </el-form-item>
                <el-row :gutter="12">
                    <el-col :span="12">
                        <el-form-item label="用户名">
                            <el-input v-model="editForm.username" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="姓名">
                            <el-input v-model="editForm.name" />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-form-item label="邮箱">
                    <el-input v-model="editForm.email" />
                </el-form-item>
                <el-row :gutter="12">
                    <el-col :span="12">
                        <el-form-item label="地区">
                            <el-input v-model="editForm.area" placeholder="如：上海" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="VIP 等级">
                            <el-input-number v-model="editForm.vip" :min="0" controls-position="right" style="width: 100%" />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row :gutter="12">
                    <el-col :span="12">
                        <el-form-item label="签到天数">
                            <el-input-number v-model="editForm.checkinDay" :min="0" controls-position="right" style="width: 100%" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12"></el-col>
                </el-row>
                <el-form-item label="简介">
                    <el-input v-model="editForm.bio" type="textarea" :rows="3" placeholder="介绍一下自己…" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="editLoading" @click="submitEdit">保存</el-button>
            </template>
        </el-dialog>

        <!-- 重置密码弹窗 -->
        <el-dialog v-model="pwdVisible" title="重置密码" width="420px">
            <el-form :model="pwdForm" label-width="80px">
                <el-form-item label="新密码">
                    <el-input v-model="pwdForm.password" type="password" show-password />
                </el-form-item>
                <el-form-item label="确认密码">
                    <el-input v-model="pwdForm.confirm" type="password" show-password />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="pwdVisible = false">取消</el-button>
                <el-button type="primary" @click="submitPwd">保存</el-button>
            </template>
        </el-dialog>

        <!-- TOTP 绑定/解绑弹窗 -->
        <el-dialog v-model="totpVisible" :title="totpBound ? 'TOTP 动态验证' : '绑定 TOTP 动态验证'" width="420px">
            <!-- 绑定：展示二维码 -->
            <div v-if="!totpBound" class="totp-bind">
                <p class="totp-tip">用 Google Authenticator / 微信小程序「动态口令」等扫描二维码，或手动输入密钥：</p>
                <div class="totp-qr">
                    <img v-if="totpQrUrl" :src="totpQrUrl" alt="TOTP 二维码" width="180" height="180" />
                    <el-skeleton v-else animated style="width: 180px">
                        <template #template>
                            <el-skeleton-item variant="image" style="width: 180px; height: 180px" />
                        </template>
                    </el-skeleton>
                </div>
                <el-input :model-value="totpSecret" readonly class="totp-secret">
                    <template #append>
                        <el-button @click="copySecret">复制密钥</el-button>
                    </template>
                </el-input>
                <el-divider />
                <p class="totp-tip">扫描后输入 Authenticator 上显示的 6 位动态码完成绑定：</p>
                <el-input
                    v-model="totpCodeInput"
                    placeholder="输入 6 位动态码"
                    maxlength="6"
                    style="margin-top: 8px"
                    @keyup.enter="confirmTotp"
                />
            </div>
            <!-- 解绑 -->
            <div v-else>
                <el-alert type="warning" :closable="false" show-icon
                    title="解绑后需重新扫码绑定；解绑需要验证当前动态码"
                    style="margin-bottom: 12px" />
                <el-input
                    v-model="totpCodeInput"
                    placeholder="输入当前 6 位动态码"
                    maxlength="6"
                    @keyup.enter="disableTotp"
                />
            </div>
            <template #footer>
                <el-button @click="totpVisible = false">取消</el-button>
                <el-button
                    v-if="!totpBound"
                    type="primary"
                    :loading="totpBusy"
                    @click="confirmTotp"
                >确认绑定</el-button>
                <el-button
                    v-else
                    type="danger"
                    :loading="totpBusy"
                    @click="disableTotp"
                >确认解绑</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style scoped>
.profile-page {
    max-width: 1100px;
}

/* ---- 顶部横幅 ---- */
.profile-hero {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 28px 32px;
    border-radius: 14px;
    color: #fff;
    background: linear-gradient(135deg, #2c3e50 0%, #34495e 55%, #1e3a5f 100%);
    overflow: hidden;
    margin-bottom: 16px;
}
.profile-hero::after {
    content: '';
    position: absolute;
    right: -80px;
    top: -80px;
    width: 260px;
    height: 260px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.14) 0%, transparent 70%);
}

.hero-left {
    display: flex;
    align-items: center;
    gap: 22px;
    position: relative;
    z-index: 1;
}

.hero-avatar {
    border: 3px solid rgba(255, 255, 255, 0.6);
    background-color: #fff;
    color: #34495e;
    font-size: 34px;
    font-weight: 700;
    flex-shrink: 0;
}

.hero-name-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}
.hero-name {
    font-size: 26px;
    font-weight: 800;
    color: #fff;
}
.hero-tag {
    border-radius: 999px;
}

.hero-sub {
    margin-top: 4px;
    font-size: 14px;
    color: rgba(255, 255, 255, 0.75);
}

.hero-bio {
    margin-top: 10px;
    font-size: 13px;
    line-height: 1.6;
    color: rgba(255, 255, 255, 0.85);
    max-width: 560px;
}

.hero-actions {
    display: flex;
    gap: 10px;
    position: relative;
    z-index: 1;
    flex-shrink: 0;
}

/* ---- 统计卡片 ---- */
.stats-row {
    margin-bottom: 16px;
}
.stat-card {
    text-align: center;
    transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.stat-card:hover {
    transform: translateY(-3px);
}
.stat-icon {
    width: 52px;
    height: 52px;
    margin: 0 auto;
    display: flex;
    align-items: center;
    justify-content: center;
    background: transparent;
}
.stat-icon-article {
    color: #409eff;
}
.stat-icon-comment {
    color: #67c23a;
}
.stat-icon-checkin {
    color: #e6a23c;
}
.stat-icon-vip {
    color: #f56c6c;
}
.stat-num {
    margin-top: 10px;
    font-size: 30px;
    font-weight: 800;
    color: #303133;
}
.stat-label {
    margin-top: 4px;
    font-size: 13px;
    color: #909399;
}

/* ---- 基本信息字段卡 ---- */
.info-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
}
.info-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 16px;
    border: 1px solid #ebeef5;
    border-radius: 10px;
    background-color: #fafbfc;
    transition: border-color 0.2s ease, background-color 0.2s ease;
}
.info-item:hover {
    border-color: #c0c4cc;
    background-color: #f5f7fa;
}
.info-icon {
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    border-radius: 10px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background-color: #ecf5ff;
    color: #409eff;
}
.info-label {
    font-size: 12px;
    color: #909399;
}
.info-value {
    margin-top: 2px;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    word-break: break-all;
}

/* ---- 权限 tag ---- */
.perm-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}
.perm-tag {
    font-size: 13px;
    padding: 4px 12px;
    height: auto;
}
.perm-empty {
    color: #909399;
    font-size: 13px;
}

/* ---- 编辑弹窗头像行 ---- */
.avatar-edit-row {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
}

/* ---- TOTP 绑定 ---- */
.totp-bind {
    text-align: center;
}
.totp-tip {
    font-size: 13px;
    color: #909399;
    line-height: 1.7;
    text-align: left;
}
.totp-qr {
    margin: 14px 0;
    display: flex;
    justify-content: center;
}
.totp-qr img {
    border: 1px solid #ebeef5;
    border-radius: 8px;
}
.totp-secret {
    margin: 4px 0 0;
}
.totp-foot {
    margin-top: 16px;
    text-align: right;
}

/* ---- 手机（≤768px）：横幅竖排、字段单列 ---- */
@media (max-width: 768px) {
    .profile-page {
        max-width: 100%;
    }

    .profile-hero {
        flex-direction: column;
        align-items: flex-start;
        gap: 18px;
        padding: 20px 18px;
    }

    .hero-left {
        gap: 14px;
    }

    .hero-name {
        font-size: 20px;
    }

    .hero-bio {
        max-width: 100%;
    }

    .hero-actions {
        width: 100%;
        flex-wrap: wrap;
    }

    /* element-plus 默认给相邻按钮加 margin-left，换行后要归零，统一交给 gap */
    .hero-actions .el-button + .el-button {
        margin-left: 0;
    }

    .info-grid {
        grid-template-columns: 1fr;
    }

    .stat-num {
        font-size: 24px;
    }

    .stat-icon {
        width: 40px;
        height: 40px;
    }

    .avatar-edit-row {
        flex-wrap: wrap;
    }

    .avatar-edit-row .el-button {
        margin-left: 0;
    }
}
</style>
