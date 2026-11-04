<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { IconDelete, IconNotification } from '@arco-design/web-vue/es/icon'
import { api } from './api'
import { auth, logout } from './auth'

interface NotificationItem {
  id: number
  title: string
  body: string
  readAt: string | null
  createdAt: string
}

const route = useRoute()
const router = useRouter()
const unread = ref(0)
const drawerVisible = ref(false)
const notifications = ref<NotificationItem[]>([])
const loadingNotifications = ref(false)
const user = computed(() => auth.user)
const selectedKeys = computed(() => {
  const path = route.path
  if (path.startsWith('/me/resume')) return ['/me/resume']
  if (path.startsWith('/me/workbench') || path.startsWith('/me/projects') || path.startsWith('/me/applications')) {
    return ['/me/workbench']
  }
  if (path.startsWith('/ability')) return ['/ability']
  if (path.startsWith('/publisher/tasks')) return ['/publisher/tasks']
  if (path.startsWith('/publisher/projects')) return ['/publisher/inbox']
  if (path.startsWith('/admin/')) {
    const hit = adminMenus.find((item) => path.startsWith(item.key))
    return hit ? [hit.key] : [path]
  }
  return [path]
})

const studentMenus = [
  { key: '/', label: '任务广场' },
  { key: '/me/workbench', label: '工作台' },
  { key: '/ability', label: 'Top Dev' },
  { key: '/messages', label: '私信' },
]
const publisherMenus = [
  { key: '/', label: '任务广场' },
  { key: '/publisher/tasks', label: '我的任务' },
  { key: '/publisher/inbox', label: '审阅' },
  { key: '/ability', label: 'Top Dev' },
  { key: '/messages', label: '私信' },
]
const adminMenus = [
  { key: '/', label: '任务广场' },
  { key: '/ability', label: 'Top Dev' },
  { key: '/admin/users', label: '用户' },
  { key: '/admin/tasks', label: '任务' },
  { key: '/admin/dicts', label: '字典' },
  { key: '/admin/reminders', label: '催办队列' },
  { key: '/admin/settings', label: '设置' },
]
const guestMenus = [
  { key: '/', label: '任务广场' },
  { key: '/ability', label: 'Top Dev' },
]

const menus = computed(() => {
  if (user.value?.role === 'STUDENT') return studentMenus
  if (user.value?.role === 'PUBLISHER') return publisherMenus
  if (user.value?.role === 'ADMIN') return adminMenus
  return guestMenus
})

async function refreshUnread() {
  if (!auth.token) {
    unread.value = 0
    return
  }
  try {
    unread.value = (await api<{ count: number }>('/api/v1/notifications/unread-count')).count
  } catch {
    unread.value = 0
  }
}

async function loadNotifications() {
  loadingNotifications.value = true
  try {
    notifications.value = await api<NotificationItem[]>('/api/v1/notifications')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载通知失败')
  } finally {
    loadingNotifications.value = false
  }
}

async function openNotifications() {
  drawerVisible.value = true
  await loadNotifications()
}

async function markOneRead(item: NotificationItem) {
  if (item.readAt) return
  try {
    await api(`/api/v1/notifications/${item.id}/read`, { method: 'POST' })
    item.readAt = new Date().toISOString()
    unread.value = Math.max(0, unread.value - 1)
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '标记失败')
  }
}

async function markAllRead() {
  try {
    await api('/api/v1/notifications/read-all', { method: 'POST' })
    notifications.value = notifications.value.map((item) => ({
      ...item,
      readAt: item.readAt ?? new Date().toISOString(),
    }))
    unread.value = 0
    Message.success('已全部标为已读')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '操作失败')
  }
}

async function removeOne(item: NotificationItem, event?: Event) {
  event?.stopPropagation()
  try {
    await api(`/api/v1/notifications/${item.id}`, { method: 'DELETE' })
    const wasUnread = !item.readAt
    notifications.value = notifications.value.filter((row) => row.id !== item.id)
    if (wasUnread) unread.value = Math.max(0, unread.value - 1)
    Message.success('已删除')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '删除失败')
  }
}

function clearAll() {
  if (notifications.value.length === 0) return
  Modal.confirm({
    title: '清空通知',
    content: '确定删除全部通知吗？此操作不可恢复。',
    okText: '清空',
    cancelText: '取消',
    onOk: async () => {
      try {
        await api('/api/v1/notifications', { method: 'DELETE' })
        notifications.value = []
        unread.value = 0
        Message.success('已清空通知')
      } catch (cause) {
        Message.error(cause instanceof Error ? cause.message : '清空失败')
      }
    },
  })
}

onMounted(refreshUnread)
watch(() => auth.token, refreshUnread)
watch(() => route.fullPath, () => {
  if (!drawerVisible.value) refreshUnread()
})

function onMenuClick(key: string) {
  router.push(key)
}

function onLogout() {
  logout()
  unread.value = 0
  notifications.value = []
  drawerVisible.value = false
  router.push('/auth/login')
}

function formatTime(value: string) {
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <a-layout style="min-height: 100vh; background: var(--bg-page, #f9fafb)">
    <a-layout-header :style="{ background: '#fff', borderBottom: '1px solid #eef2f7', padding: 0, height: 'auto', boxShadow: '0 1px 0 rgba(15,23,42,0.03)' }">
      <div class="layout-header-inner">
        <router-link to="/" class="brand-link">
          <span class="brand-mark">WHU</span>
          <span>
            <div class="brand-title"><span class="brand-accent">TOP</span> Developer</div>
            <div class="brand-sub">WHUDEV</div>
          </span>
        </router-link>

        <div class="header-nav">
          <a-menu
            mode="horizontal"
            :selected-keys="selectedKeys"
            :style="{ background: 'transparent', width: '100%' }"
            @menu-item-click="onMenuClick"
          >
            <a-menu-item v-for="item in menus" :key="item.key">{{ item.label }}</a-menu-item>
          </a-menu>
        </div>

        <div class="header-actions" v-if="user">
          <a-button
            v-if="user.role === 'STUDENT'"
            type="text"
            size="small"
            class="resume-link"
            :class="{ active: route.path.startsWith('/me/resume') }"
            @click="router.push('/me/resume')"
          >
            简历
          </a-button>
          <a-tag color="arcoblue" size="small">
            {{
              user.role === 'STUDENT'
                ? user.abilityLabel || user.abilityLevel || 'LV0'
                : user.role === 'PUBLISHER'
                  ? '发布方'
                  : '管理'
            }}
          </a-tag>
          <span style="font-size: 13px; color: var(--color-text-2)">{{ user.displayName }}</span>
          <a-badge :count="unread" :max-count="99">
            <a-button type="text" shape="circle" @click="openNotifications">
              <icon-notification />
            </a-button>
          </a-badge>
          <a-button size="small" @click="onLogout">退出</a-button>
        </div>
        <div class="header-actions" v-else>
          <a-button type="text" @click="router.push('/auth/login')">登录</a-button>
          <a-button type="primary" @click="router.push('/auth/register')">注册</a-button>
        </div>
      </div>
    </a-layout-header>

    <a-layout-content>
      <div class="page">
        <router-view />
      </div>
    </a-layout-content>

    <a-drawer
      v-model:visible="drawerVisible"
      title="通知中心"
      :width="420"
      unmount-on-close
    >
      <template #footer>
        <a-space>
          <a-button status="danger" :disabled="notifications.length === 0" @click="clearAll">清空全部</a-button>
          <a-button :disabled="!unread" @click="markAllRead">全部标为已读</a-button>
          <a-button type="primary" @click="drawerVisible = false">关闭</a-button>
        </a-space>
      </template>

      <a-spin :loading="loadingNotifications" style="width: 100%">
        <a-empty v-if="!loadingNotifications && notifications.length === 0" description="暂无通知" />
        <a-list v-else :bordered="false">
          <a-list-item
            v-for="item in notifications"
            :key="item.id"
            class="notification-item"
            :class="{ unread: !item.readAt }"
            @click="markOneRead(item)"
          >
            <a-list-item-meta :title="item.title" :description="item.body">
              <template #avatar>
                <a-badge :dot="!item.readAt" status="danger">
                  <a-avatar :size="28" style="background: rgb(var(--primary-6))">通</a-avatar>
                </a-badge>
              </template>
            </a-list-item-meta>
            <template #actions>
              <a-typography-text type="secondary" style="font-size: 12px">
                {{ formatTime(item.createdAt) }}
              </a-typography-text>
              <a-button
                type="text"
                status="danger"
                size="mini"
                @click="(event: MouseEvent) => removeOne(item, event)"
              >
                <icon-delete />
              </a-button>
            </template>
          </a-list-item>
        </a-list>
      </a-spin>
    </a-drawer>
  </a-layout>
</template>

<style scoped>
.resume-link {
  color: #4b5563;
  font-weight: 500;
}
.resume-link.active,
.resume-link:hover {
  color: #111827;
  font-weight: 700;
}
.notification-item {
  cursor: pointer;
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 4px;
}
.notification-item:hover {
  background: var(--color-fill-2);
}
.notification-item.unread {
  background: var(--color-primary-light-1);
}
</style>
