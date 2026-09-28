<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'

const props = defineProps({
  UserInfo: {
    type: Object,
    default: () => ({})
  },
  routePath: {
    type: String,
    default: '/user-profile',
  },
  isCollapse: {
    type: Boolean,
    default: () => false
  }}
)

// 当前登录用户的 role_id（来自 /sys/profile 的 user_detail）
const roleId = computed(() => {
  const id = props.UserInfo?.user_detail?.role_id
  return id === undefined || id === null || id === '' ? null : Number(id)
})

// 是否超级管理员（role_id = 1）
const isAdmin = computed(() => roleId.value === 1)

// 是否管理员或编辑（role_id 1 / 3）
const isAdminOrEditor = computed(() => roleId.value === 1 || roleId.value === 3)

console.log(props.UserInfo)
import {
  User,
  Menu as IconMenu,
  Operation,
  Notebook,
  Bell,
  Monitor,
  Setting,
  HomeFilled,
  Key,
  Folder,
  MessageBox,
  Iphone
} from '@element-plus/icons-vue'


onMounted(() => {

})
</script>

<template>
 
  <el-menu
    class="el-menu-vertical-demo side-menu"
    :router=true
    :default-active="props.routePath"  
    :collapse="props.isCollapse"
  >
  
  <div id="aside-logo">
  <el-avatar id="header-avatar" shape="square" size="default" :src="props.UserInfo?.user_detail?.avatar" />
  <span id="logo-text" v-if="!props.isCollapse">{{ props.UserInfo?.user_detail?.username }}</span>
  </div>
  
   <el-menu-item index="/user-profile">
    <el-icon><HomeFilled /></el-icon>
    
      <template #title>{{ $t('user_profile') }}</template>
    </el-menu-item>

    <el-sub-menu index="2" v-if="isAdmin">
      <template #title>
        <el-icon><User /></el-icon>
        <span>{{ $t('user') }}</span>
      </template>
        <el-menu-item index="/user/user-manage">{{ $t('user_manage') }}</el-menu-item>  
        <el-menu-item index="/user/role-manage">{{ $t('role_manage') }}</el-menu-item>
        <el-menu-item index="/user/permission-manage">{{ $t('permission_manage') }}</el-menu-item>
    </el-sub-menu>

    <!-- 主页设置：独立顶级菜单，所有角色可见（普通用户设置自己，admin 可设置所有人） -->
    <el-menu-item index="/user/home-setting">
      <el-icon><Setting /></el-icon>
      <template #title>{{ $t('home_setting') }}</template>
    </el-menu-item>

    <!-- API Keys：所有登录用户可管理自己的外部接口 Key -->
    <el-menu-item index="/system/api-key-manage">
      <el-icon><Key /></el-icon>
      <template #title>{{ $t('api_key_manage') }}</template>
    </el-menu-item>

    <el-sub-menu index="3">
      <template #title>
       <el-icon><Notebook /></el-icon>
        <span>{{ $t('article') }}</span>
      </template>
        <el-menu-item index="/article/article-create">{{ $t('article_create') }}</el-menu-item>
        <el-menu-item index="/article/article-manage">{{ $t('article_manage') }}</el-menu-item>
        <el-menu-item v-if="isAdmin" index="/article/interaction-manage">{{ $t('interaction_manage') }}</el-menu-item>
    </el-sub-menu>

    <el-sub-menu index="4">
      <template #title>
        <el-icon><Bell /></el-icon>
        <span>{{ $t('notification') }}</span>
      </template>
        <el-menu-item index="/notification/notification-center">{{ $t('notification_center') }}</el-menu-item>
        <el-menu-item v-if="isAdmin" index="/notification/notification-manage">{{ $t('notification_manage') }}</el-menu-item>
    </el-sub-menu>

    <el-menu-item v-if="isAdmin" index="/system/system-monitor">
      <el-icon><Monitor /></el-icon>
      <template #title>{{ $t('system_monitor') }}</template>
    </el-menu-item>

    <el-menu-item v-if="isAdmin" index="/system/ad-manage">
      <el-icon><Folder /></el-icon>
      <template #title>{{ $t('ad_manage') }}</template>
    </el-menu-item>

    <el-menu-item v-if="isAdmin" index="/system/announcement-manage">
      <el-icon><MessageBox /></el-icon>
      <template #title>{{ $t('announcement_manage') }}</template>
    </el-menu-item>

    <el-menu-item v-if="isAdmin" index="/system/oauth-manage">
      <el-icon><Iphone /></el-icon>
      <template #title>{{ $t('oauth_manage') }}</template>
    </el-menu-item>
  </el-menu>
</template>

<style scoped>
::deep(.el-menu-vertical-demo:not(.el-menu--collapse)) {
  width: 200px;
  min-height: 400px;
}
#aside-logo {
height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  
}

#logo-text {
  font-size: 16px;
  font-weight: bold;
  font-family: 'logo-en';
}


.side-menu {
  height: 100vh;
  overflow-y: auto;
  scrollbar-width: none; /* Firefox */
}

.side-menu::-webkit-scrollbar {
  display: none; /* Chrome / Safari */
}

</style>