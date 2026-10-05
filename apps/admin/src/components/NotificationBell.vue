<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { NotificationsOutline } from '@vicons/ionicons5'
import {
  requestNotificationList,
  requestNotificationUnreadCount,
  requestNotificationRead
} from '@/composables/useRequest'

interface NotificationItem {
  notification_id: number
  title: string
  content: string
  type: string
  importance: string
  is_read: number | string
  created_at: string
}

const message = useMessage()
const router = useRouter()
const unreadCount = ref(0)
const list = ref<NotificationItem[]>([])
let timer: number = 0

const showDetail = ref(false)
const current = ref<NotificationItem | null>(null)

const typeMap: Record<string, string> = { system: '系统', announcement: '公告', reminder: '提醒' }
const typeTagType: Record<string, 'error' | 'success' | 'warning' | 'info'> = {
  system: 'error', announcement: 'success', reminder: 'warning'
}
const importanceMap: Record<string, string> = { high: '高', medium: '中', low: '低' }

const hasUnread = computed(() => unreadCount.value > 0)

const fetchData = async () => {
  try {
    const countRes = await requestNotificationUnreadCount()
    if (countRes.code === 200 && countRes.data) {
      unreadCount.value = (countRes.data as any).unread_count || 0
    }
    const listRes = await requestNotificationList()
    if (listRes.code === 200 && listRes.data) {
      list.value = ((listRes.data as any).list || []).slice(0, 8)
      checkSystemNotif(list.value)
    }
  } catch (e) {
    // silent
  }
}

const checkSystemNotif = (items: NotificationItem[]) => {
  const systemNotif = items.find((item) => item.type === 'system' && Number(item.is_read) === 0)
  if (!systemNotif) return
  const key = 'system_notif_shown_' + systemNotif.notification_id
  if (sessionStorage.getItem(key)) return
  sessionStorage.setItem(key, '1')
  current.value = systemNotif
  showDetail.value = true
  requestNotificationRead(systemNotif.notification_id).then(() => fetchData()).catch(() => { })
}

const openDetail = async (row: NotificationItem) => {
  current.value = row
  showDetail.value = true
  if (Number(row.is_read) === 0) {
    try {
      await requestNotificationRead(row.notification_id)
      row.is_read = 1
      await fetchData()
    } catch (e) {
      message.error('标记已读失败')
    }
  }
}

const goAll = () => {
  router.push('/notification/notification-center')
}

onMounted(() => {
  fetchData()
  timer = setInterval(fetchData, 30000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <n-popover trigger="click" placement="bottom-end" :width="340" style="padding: 0;" @after-enter="fetchData">
    <template #trigger>
      <n-badge :value="unreadCount" :hidden="!hasUnread" :max="99">
        <n-button quaternary circle id="header-notification">
          <template #icon>
            <n-icon :size="18">
              <NotificationsOutline />
            </n-icon>
          </template>
        </n-button>
      </n-badge>
    </template>

    <div class="notification-panel">
      <div class="notification-head">
        <span class="notification-title">通知</span>
        <n-tag v-if="hasUnread" type="error" size="small" round>{{ unreadCount }} 未读</n-tag>
      </div>

      <n-empty v-if="list.length === 0" description="暂无通知" size="small" style="padding: 20px 0;" />

      <div v-for="item in list" :key="item.notification_id" class="notification-item"
        :class="{ unread: Number(item.is_read) === 0 }" @click="openDetail(item)">
        <div class="notification-item-title">
          <n-tag :type="typeTagType[item.type] || 'info'" size="tiny" round :bordered="false">
            {{ typeMap[item.type] || item.type || '-' }}
          </n-tag>
          <n-badge v-if="Number(item.is_read) === 0" dot type="error"
            style="margin-left: 4px; margin-right: 4px;" />
          <span class="item-title-text">{{ item.title }}</span>
        </div>
        <div class="notification-item-time">{{ item.created_at }}</div>
      </div>

      <div class="notification-footer">
        <n-button text type="primary" size="small" @click="goAll">查看全部</n-button>
      </div>
    </div>
  </n-popover>

  <n-modal v-model:show="showDetail" preset="card" title="通知详情" style="width: 520px;">
    <div v-if="current">
      <h3 style="margin: 0 0 8px 0;">{{ current.title }}</h3>
      <n-space>
        <n-tag :type="typeTagType[current.type] || 'info'" size="small">
          {{ typeMap[current.type] || current.type || '-' }}
        </n-tag>
        <n-tag type="warning" size="small">
          重要性：{{ importanceMap[current.importance] || current.importance || '-' }}
        </n-tag>
        <n-text depth="3" style="font-size: 12px;">{{ current.created_at }}</n-text>
      </n-space>
      <n-divider />
      <div class="detail-content">{{ current.content }}</div>
    </div>
  </n-modal>
</template>

<style scoped>
.notification-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--n-border-color, #eee);
}

.notification-title {
  font-weight: bold;
  font-size: 14px;
}

.notification-item {
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.2s;
}

.notification-item:hover {
  background-color: #f5f7fa;
}

.notification-item-title {
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.item-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.notification-item.unread .item-title-text {
  font-weight: bold;
}

.notification-item-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  padding-left: 2px;
}

.notification-footer {
  text-align: center;
  padding: 8px 0;
}

.detail-content {
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
