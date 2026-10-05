<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  HomeOutline, PersonOutline, SettingsOutline, KeyOutline,
  BookOutline, NotificationsOutline, StatsChartOutline,
  ImageOutline, MegaphoneOutline, LinkOutline,
  ChevronBackOutline, ChevronForwardOutline, MenuOutline
} from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'

import { checkTokenUsed } from '@/composables/useCheckTokenUsed'
import { loginOut } from '@/composables/useLoginOut'
import { requestUserInfo, type UserInfo as UserInfoType } from '@/composables/useRequest'

import { useAuthStore } from '@/store/auth'
import { usePathTagStore } from '@/store/pathTag'
import { useLangStore } from '@/store/lang'

import Breadcrumb from '@/components/Breadcrumb.vue'
import FullScreen from '@/components/FullScreen.vue'
import tagView from '@/components/tagView.vue'
import DriverCom from '@/components/DriverCom.vue'
import NotificationBell from '@/components/NotificationBell.vue'

import { ElMessage } from 'element-plus'

const { t } = useI18n()
const routePath = useRoute()
const router = useRouter()
const UserInfo = ref<UserInfoType>({} as UserInfoType)
const authStore = useAuthStore()
const pathTagStore = usePathTagStore()
const langStore = useLangStore()
const loading = ref(false)
const isCollapsed = ref(false)
const showAppSettings = ref(false)

function renderIcon(icon: any) {
  return () => h(NIcon, null, { default: () => h(icon) })
}

const roleId = computed(() => {
  const id = UserInfo.value?.user_detail?.role_id
  return id === undefined || id === null || id === '' ? null : Number(id)
})
const isAdmin = computed(() => roleId.value === 1)

const menuOptions = computed(() => {
  const opts: any[] = [
    {
      label: t('user_profile'),
      key: '/user-profile',
      icon: renderIcon(HomeOutline)
    },
  ]

  if (isAdmin.value) {
    opts.push({
      label: t('user'),
      key: 'user-group',
      icon: renderIcon(PersonOutline),
      children: [
        { label: t('user_manage'), key: '/user/user-manage' },
        { label: t('role_manage'), key: '/user/role-manage' },
        { label: t('permission_manage'), key: '/user/permission-manage' },
      ]
    })
  }

  opts.push(
    {
      label: t('home_setting'),
      key: '/user/home-setting',
      icon: renderIcon(SettingsOutline)
    },
    {
      label: t('api_key_manage'),
      key: '/system/api-key-manage',
      icon: renderIcon(KeyOutline)
    },
    {
      label: t('article'),
      key: 'article-group',
      icon: renderIcon(BookOutline),
      children: [
        { label: t('article_create'), key: '/article/article-create' },
        { label: t('article_manage'), key: '/article/article-manage' },
        ...(isAdmin.value ? [{ label: t('interaction_manage'), key: '/article/interaction-manage' }] : []),
      ]
    },
    {
      label: t('notification'),
      key: 'notification-group',
      icon: renderIcon(NotificationsOutline),
      children: [
        { label: t('notification_center'), key: '/notification/notification-center' },
        ...(isAdmin.value ? [{ label: t('notification_manage'), key: '/notification/notification-manage' }] : []),
      ]
    }
  )

  if (isAdmin.value) {
    opts.push(
      { label: t('system_monitor'), key: '/system/system-monitor', icon: renderIcon(StatsChartOutline) },
      { label: t('ad_manage'), key: '/system/ad-manage', icon: renderIcon(ImageOutline) },
      { label: t('announcement_manage'), key: '/system/announcement-manage', icon: renderIcon(MegaphoneOutline) },
      { label: t('oauth_manage'), key: '/system/oauth-manage', icon: renderIcon(LinkOutline) }
    )
  }

  return opts
})

const activeMenuKey = computed(() => routePath.path)

// ---- 响应式：窄屏（手机）下侧边栏收进抽屉 ----
const MOBILE_QUERY = '(max-width: 768px)'
const isMobile = ref(false)
const drawerVisible = ref(false)
let mql: MediaQueryList | null = null
const syncViewport = () => {
  isMobile.value = !!mql?.matches
  if (!isMobile.value) drawerVisible.value = false
}

const handleMenuUpdate = (key: string) => {
  if (key.startsWith('/')) {
    router.push(key)
    if (isMobile.value) drawerVisible.value = false
  }
}

const avatarOptions = [
  { type: 'group', label: '账号', key: 'account-group', children: [] as any[] },
  { type: 'divider' },
  { label: '应用设置', key: 'app-settings' },
  { type: 'divider' },
  { label: '退出登录', key: 'logout' },
]

const handleAvatarSelect = (key: string) => {
  if (key === 'logout') {
    loginOut()
  } else if (key === 'app-settings') {
    showAppSettings.value = true
  }
}

const getUserInfo = async () => {
  try {
    loading.value = true
    const res = await requestUserInfo()
    authStore.setUserInfo(res.user_info)
    UserInfo.value = res.user_info
    if (res.code == 200) {
      loading.value = false
    }
  } catch (e) {
    loading.value = false
    ElMessage.error('用户身份获取失败')
  }
}

watch(
  () => routePath,
  (to) => {
    const { name, meta, fullPath } = to
    pathTagStore.addPathTag({ name, meta, fullPath })
  },
  { deep: true, immediate: true }
)

onMounted(() => {
  getUserInfo()
  checkTokenUsed()
  mql = window.matchMedia(MOBILE_QUERY)
  syncViewport()
  mql.addEventListener('change', syncViewport)
})

onUnmounted(() => {
  mql?.removeEventListener('change', syncViewport)
})
</script>

<template>
  <n-layout has-sider class="app-layout">
    <n-layout-sider v-if="!isMobile" bordered :collapsed-width="64" :width="220" :collapsed="isCollapsed"
      collapse-mode="width" :native-scrollbar="false" class="app-sider">
      <div class="sider-logo">
        <n-avatar round :size="32" :src="UserInfo.user_detail?.avatar">
          {{ UserInfo.user_detail?.username?.charAt(0) }}
        </n-avatar>
        <span v-if="!isCollapsed" class="logo-text">{{ UserInfo.user_detail?.username }}</span>
      </div>
      <n-menu :collapsed="isCollapsed" :collapsed-width="64" :collapsed-icon-size="22" :options="menuOptions"
        :value="activeMenuKey" @update:value="handleMenuUpdate" />
    </n-layout-sider>

    <!-- 窄屏：同一份菜单放进左侧抽屉 -->
    <n-drawer v-model:show="drawerVisible" :width="236" placement="left" class="app-drawer">
      <n-drawer-content :native-scrollbar="false" body-content-style="padding: 0;">
        <div class="sider-logo">
          <n-avatar round :size="32" :src="UserInfo.user_detail?.avatar">
            {{ UserInfo.user_detail?.username?.charAt(0) }}
          </n-avatar>
          <span class="logo-text">{{ UserInfo.user_detail?.username }}</span>
        </div>
        <n-menu :collapsed-icon-size="22" :options="menuOptions" :value="activeMenuKey"
          @update:value="handleMenuUpdate" />
      </n-drawer-content>
    </n-drawer>

    <n-layout>
      <n-layout-header bordered class="app-header">
        <div class="header-content">
          <div class="header-left">
            <div class="header-top-row">
              <n-button v-if="isMobile" quaternary circle class="nav-toggle" style="margin-right: 8px;"
                @click="drawerVisible = true">
                <template #icon>
                  <n-icon :size="20"><MenuOutline /></n-icon>
                </template>
              </n-button>
              <n-button v-else quaternary circle @click="isCollapsed = !isCollapsed" style="margin-right: 8px;">
                <template #icon>
                  <n-icon :size="18">
                    <ChevronForwardOutline v-if="!isCollapsed" />
                    <ChevronBackOutline v-else />
                  </n-icon>
                </template>
              </n-button>
              <Breadcrumb />
            </div>
            <div v-if="!isMobile" class="header-tag-row" id="header-tag-row">
              <tagView />
            </div>
          </div>

          <div class="header-right">
            <div class="header-tools">
              <DriverCom />
              <FullScreen />
            </div>
            <NotificationBell />

            <n-dropdown :options="avatarOptions" trigger="click" @select="handleAvatarSelect"
              placement="bottom-end">
              <n-avatar round :size="32" :src="UserInfo.user_detail?.avatar" class="header-avatar"
                style="cursor: pointer;">
                {{ UserInfo.user_detail?.username?.charAt(0) }}
              </n-avatar>
            </n-dropdown>
          </div>
        </div>
      </n-layout-header>

      <n-layout-content :content-style="isMobile ? 'padding: 10px;' : 'padding: 16px;'" :native-scrollbar="false"
        class="app-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </n-layout-content>
    </n-layout>
  </n-layout>

  <n-modal v-model:show="showAppSettings" preset="card" title="应用设置" style="width: 440px;">
    <n-space vertical :size="16">
      <n-divider title-placement="start">语言</n-divider>
      <n-radio-group :value="langStore.lang"
        @update:value="(v: string) => { langStore.setLang(v); $i18n.locale = v }">
        <n-space>
          <n-radio value="cn">中文</n-radio>
          <n-radio value="en">English</n-radio>
          <n-radio value="jp">日本語</n-radio>
          <n-radio value="ru">Русский</n-radio>
        </n-space>
      </n-radio-group>

      <n-divider title-placement="start">主题</n-divider>
      <n-text depth="3">主题设置开发中…</n-text>
    </n-space>
  </n-modal>
</template>

<style scoped>
.app-layout {
  height: 100vh;
  overflow: hidden;
}

.app-sider {
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.06);
}

.sider-logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--n-border-color);
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-header {
  height: 100px;
  padding: 0 16px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  height: 100%;
}

.header-left {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.header-top-row {
  display: flex;
  align-items: center;
  height: 56px;
}

.header-tag-row {
  height: 40px;
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
  height: 56px;
  gap: 4px;
}

.header-tools {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-avatar {
  margin-left: 8px;
}

.app-content {
  height: calc(100vh - 100px);
}

/* 窄屏（手机）：侧边栏进抽屉，只留一条 56px 的顶栏 */
@media (max-width: 768px) {
  .app-header {
    height: 56px;
    padding: 0 8px;
  }

  .header-top-row,
  .header-right {
    height: 56px;
  }

  .header-left {
    min-width: 0;
    overflow: hidden;
  }

  /* 全屏/引导这类桌面工具在手机上收起来，把宽度留给面包屑和通知 */
  .header-tools {
    display: none;
  }

  /* dvh：iOS Safari 的 100vh 含地址栏，会把底部内容顶出屏幕 */
  .app-layout {
    height: 100dvh;
  }

  .app-content {
    height: calc(100vh - 56px);
    height: calc(100dvh - 56px);
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease-in-out;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
