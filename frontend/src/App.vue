<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { IconDelete, IconNotification } from '@arco-design/web-vue/es/icon'
import { api } from './api'
import { auth, logout } from './auth'
import { provideDevice } from './composables/useDevice'
import { readCachedSiteConfig, writeCachedSiteConfig } from './siteConfigCache'

interface NotificationItem {
  id: number
  title: string
  body: string
  readAt: string | null
  createdAt: string
}

type TabIcon = 'jobs' | 'topdev' | 'workbench' | 'me' | 'tasks' | 'inbox' | 'messages' | 'login' | 'users' | 'settings'

interface TabItem {
  key: string
  label: string
  icon: TabIcon
  match?: (path: string) => boolean
}

const route = useRoute()
const router = useRouter()
const { isMobile } = provideDevice()
const unread = ref(0)
const pendingApplications = ref(0)
const drawerVisible = ref(false)
const notifications = ref<NotificationItem[]>([])
const loadingNotifications = ref(false)
const cachedSite = readCachedSiteConfig()
const tutorialDocUrlStudent = ref(cachedSite?.tutorialDocUrlStudent || '')
const tutorialDocUrlPublisher = ref(cachedSite?.tutorialDocUrlPublisher || '')
const tutorialBannerText = ref(cachedSite?.tutorialBannerText || '查看使用教程')
const user = computed(() => auth.user)
const tutorialDocUrl = computed(() => {
  if (user.value?.role === 'PUBLISHER') return tutorialDocUrlPublisher.value
  return tutorialDocUrlStudent.value
})
const showMobileTutorial = computed(
  () => isMobile.value && route.path === '/plaza' && Boolean(tutorialDocUrl.value),
)

const selectedKeys = computed(() => {
  const path = route.path
  if (path === '/' || path.startsWith('/ability')) return ['/']
  if (path.startsWith('/plaza') || path.startsWith('/tasks/')) return ['/plaza']
  if (path.startsWith('/me/resume') || path === '/me') return ['/me']
  if (path.startsWith('/me/workbench') || path.startsWith('/me/projects') || path.startsWith('/me/applications')) {
    return ['/me/workbench']
  }
  if (path.startsWith('/publisher/tasks')) return ['/publisher/tasks']
  if (path.startsWith('/publisher/inbox') || path.startsWith('/publisher/projects')) return ['/publisher/inbox']
  if (path.startsWith('/admin/')) {
    const hit = [...adminMenus]
      .sort((a, b) => b.key.length - a.key.length)
      .find((item) => path === item.key || path.startsWith(`${item.key}/`))
    return hit ? [hit.key] : [path]
  }
  return [path]
})

const studentMenus = [
  { key: '/', label: '首页' },
  { key: '/plaza', label: '任务广场' },
  { key: '/me/workbench', label: '工作台' },
]
const publisherMenus = computed(() => [
  { key: '/', label: '首页' },
  { key: '/plaza', label: '任务广场' },
  { key: '/publisher/tasks', label: '我的任务' },
  {
    key: '/publisher/inbox',
    label: pendingApplications.value > 0 ? `审阅 (${pendingApplications.value})` : '审阅',
  },
])
const showDesktopMessages = computed(
  () => user.value?.role === 'STUDENT' || user.value?.role === 'PUBLISHER',
)
const adminMenus = [
  { key: '/', label: '首页' },
  { key: '/plaza', label: '任务广场' },
  { key: '/admin/users', label: '用户' },
  { key: '/admin/tasks', label: '任务' },
  { key: '/admin/dicts', label: '字典' },
  { key: '/admin/reminders', label: '催办队列' },
  { key: '/admin/settings', label: '设置' },
]
const guestMenus = [
  { key: '/', label: '首页' },
  { key: '/plaza', label: '任务广场' },
]

const menus = computed(() => {
  if (user.value?.role === 'STUDENT') return studentMenus
  if (user.value?.role === 'PUBLISHER') return publisherMenus.value
  if (user.value?.role === 'ADMIN') return adminMenus
  return guestMenus
})

const studentTabs: TabItem[] = [
  { key: '/plaza', label: '职位', icon: 'jobs', match: (path) => path === '/plaza' || path.startsWith('/tasks/') },
  {
    key: '/me/workbench',
    label: '工作台',
    icon: 'workbench',
    match: (path) => path.startsWith('/me/workbench') || path.startsWith('/me/projects'),
  },
  {
    key: '/me',
    label: '我的',
    icon: 'me',
    match: (path) =>
      path === '/me' || path.startsWith('/me/resume') || path.startsWith('/messages'),
  },
]

const publisherTabs = computed<TabItem[]>(() => [
  { key: '/plaza', label: '职位', icon: 'jobs', match: (path) => path === '/plaza' || path.startsWith('/tasks/') },
  { key: '/publisher/tasks', label: '任务', icon: 'tasks', match: (path) => path.startsWith('/publisher/tasks') },
  {
    key: '/publisher/inbox',
    label: pendingApplications.value > 0 ? `审阅·${pendingApplications.value}` : '审阅',
    icon: 'inbox',
    match: (path) => path.startsWith('/publisher/inbox') || path.startsWith('/publisher/projects'),
  },
])

const guestTabs: TabItem[] = [
  { key: '/plaza', label: '职位', icon: 'jobs', match: (path) => path === '/plaza' || path.startsWith('/tasks/') },
  { key: '/auth/login', label: '登录', icon: 'login', match: (path) => path.startsWith('/auth') },
]

const mobileTabs = computed(() => {
  if (user.value?.role === 'STUDENT') return studentTabs
  if (user.value?.role === 'PUBLISHER') return publisherTabs.value
  if (user.value?.role === 'ADMIN') {
    return [
      { key: '/plaza', label: '广场', icon: 'jobs' as const, match: (path: string) => path === '/plaza' || path.startsWith('/tasks/') },
      { key: '/admin/dicts', label: '字典', icon: 'tasks' as const, match: (path: string) => path.startsWith('/admin/dicts') },
      { key: '/admin/users', label: '用户', icon: 'users' as const, match: (path: string) => path.startsWith('/admin/users') },
      {
        key: '/admin/settings',
        label: '设置',
        icon: 'settings' as const,
        match: (path: string) =>
          path.startsWith('/admin/settings') ||
          path.startsWith('/admin/tasks') ||
          path.startsWith('/admin/reminders'),
      },
    ]
  }
  return guestTabs
})

const activeTabKey = computed(() => {
  const path = route.path
  const hit = mobileTabs.value.find((item) => (item.match ? item.match(path) : path === item.key))
  return hit?.key || ''
})

const hideMobileTab = computed(() => {
  const path = route.path
  if (path.startsWith('/tasks/')) return true
  if (path.startsWith('/me/projects/')) return true
  if (path.startsWith('/publisher/projects/')) return true
  if (path === '/publisher/tasks/new' || /^\/publisher\/tasks\/\d+/.test(path)) return true
  if (path.startsWith('/auth/')) return true
  if (path === '/ability/rubric') return true
  if (path.startsWith('/messages') && route.query.taskId) return true
  return false
})

const mobileTitle = computed(() => {
  const path = route.path
  if (path === '/' || path.startsWith('/ability')) return 'WHU Dev'
  if (path === '/plaza') return '任务广场'
  if (path.startsWith('/messages')) return '消息'
  if (path.startsWith('/me/workbench')) return '工作台'
  if (path === '/me' || path.startsWith('/me/resume')) return '我的'
  if (path.startsWith('/publisher/tasks')) return '我的任务'
  if (path.startsWith('/publisher/inbox')) return '审阅'
  if (path.startsWith('/tasks/')) return '职位详情'
  return 'WHU Dev'
})

async function refreshPendingApplications() {
  if (!auth.token || auth.user?.role !== 'PUBLISHER') {
    pendingApplications.value = 0
    return
  }
  try {
    pendingApplications.value = (
      await api<{ count: number }>('/api/v1/publisher/applications/pending-count')
    ).count
  } catch {
    pendingApplications.value = 0
  }
}

async function refreshUnread() {
  if (!auth.token) {
    unread.value = 0
    pendingApplications.value = 0
    return
  }
  try {
    unread.value = (await api<{ count: number }>('/api/v1/notifications/unread-count')).count
  } catch {
    unread.value = 0
  }
  await refreshPendingApplications()
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

async function loadSiteConfig() {
  try {
    const config = await api<{
      tutorialDocUrlStudent?: string
      tutorialDocUrlPublisher?: string
      tutorialDocUrl?: string
      tutorialBannerText: string
    }>('/api/v1/site-config')
    const studentUrl = config.tutorialDocUrlStudent || config.tutorialDocUrl || ''
    const publisherUrl = config.tutorialDocUrlPublisher || ''
    const bannerText = config.tutorialBannerText?.trim() || '查看使用教程'
    tutorialDocUrlStudent.value = studentUrl
    tutorialDocUrlPublisher.value = publisherUrl
    tutorialBannerText.value = bannerText
    writeCachedSiteConfig({
      tutorialDocUrlStudent: studentUrl,
      tutorialDocUrlPublisher: publisherUrl,
      tutorialBannerText: bannerText,
    })
  } catch {
    /* optional */
  }
}

onMounted(() => {
  refreshUnread()
  void loadSiteConfig()
  if (isMobile.value && (route.path === '/' || route.path === '/ability')) {
    void router.replace('/plaza')
  }
})
watch(() => auth.token, refreshUnread)
watch(
  () => route.path,
  (path) => {
    if (isMobile.value && (path === '/' || path === '/ability')) {
      void router.replace('/plaza')
      return
    }
    if (path === '/' || path === '/plaza') void loadSiteConfig()
    if (!drawerVisible.value) refreshUnread()
  },
)
watch(isMobile, (mobile) => {
  document.documentElement.classList.toggle('is-mobile', mobile)
  if (mobile && (route.path === '/' || route.path.startsWith('/ability'))) {
    void router.replace('/plaza')
  }
})

function publisherTasksTarget() {
  const roleType = String(sessionStorage.getItem('publisher.tasks.roleType') || '').toUpperCase()
  const status = String(sessionStorage.getItem('publisher.tasks.status') || '').toUpperCase()
  return {
    path: '/publisher/tasks',
    query: {
      ...(roleType === 'TA' || roleType === 'INTERN' || roleType === 'RA' ? { roleType } : {}),
      ...(status === 'OPEN' || status === 'DRAFT' || status === 'CLOSED' || status === 'ALL' ? { status } : {}),
    },
  }
}

function goNav(key: string) {
  if (key === '/publisher/tasks') {
    router.push(publisherTasksTarget())
    return
  }
  router.push(key)
}

function onMenuClick(key: string) {
  goNav(key)
}

function onLogout() {
  logout()
  unread.value = 0
  pendingApplications.value = 0
  notifications.value = []
  drawerVisible.value = false
  router.push('/auth/login')
}

function formatTime(value: string) {
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <a-layout class="app-shell" :class="{ mobile: isMobile, 'tab-pad': isMobile && !hideMobileTab }">
    <!-- Desktop header -->
    <a-layout-header v-if="!isMobile" class="desktop-header">
      <div class="layout-header-inner">
        <router-link to="/" class="brand-link">
          <span class="brand-mark">WHU</span>
          <span>
            <div class="brand-title"><span class="brand-accent">TOP</span> Developer</div>
            <div class="brand-sub">WHU Dev</div>
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

        <div v-if="user" class="header-actions">
          <a-button
            v-if="user.role === 'STUDENT'"
            type="text"
            size="small"
            class="personal-link"
            :class="{ active: route.path.startsWith('/me/resume') }"
            @click="router.push('/me/resume')"
          >
            简历
          </a-button>
          <a-button
            v-if="showDesktopMessages"
            type="text"
            size="small"
            class="personal-link"
            :class="{ active: route.path.startsWith('/messages') }"
            @click="router.push('/messages')"
          >
            消息
          </a-button>
          <a-tag v-if="user.role !== 'STUDENT'" color="arcoblue" size="small">
            {{ user.role === 'PUBLISHER' ? '发布方' : '管理' }}
          </a-tag>
          <span style="font-size: 13px; color: var(--color-text-2)">{{ user.displayName }}</span>
          <a-badge class="header-notify-badge" :count="unread" :max-count="99">
            <a-button type="text" shape="circle" @click="openNotifications">
              <icon-notification />
            </a-button>
          </a-badge>
          <a-button size="small" @click="onLogout">退出</a-button>
        </div>
        <div v-else class="header-actions">
          <a-button type="text" @click="router.push('/auth/login')">登录</a-button>
          <a-button type="primary" @click="router.push('/auth/register')">注册</a-button>
        </div>
      </div>
    </a-layout-header>

    <!-- Mobile compact header -->
    <template v-else>
      <header class="mobile-header">
        <div class="mobile-header-main">
          <div class="mobile-title">{{ mobileTitle }}</div>
        </div>
        <div class="mobile-header-actions">
          <template v-if="user">
            <a-badge class="header-notify-badge" :count="unread" :max-count="99">
              <a-button type="text" shape="circle" size="small" @click="openNotifications">
                <icon-notification />
              </a-button>
            </a-badge>
            <a-button type="text" size="small" class="mobile-logout" @click="onLogout">退出</a-button>
          </template>
          <a-button v-else type="text" size="small" @click="router.push('/auth/login')">登录</a-button>
        </div>
      </header>
      <a
        v-if="showMobileTutorial"
        class="mobile-tutorial-hint"
        :href="tutorialDocUrl"
        target="_blank"
        rel="noopener"
      >
        {{ tutorialBannerText }}
      </a>
    </template>

    <a-layout-content>
      <div class="page" :class="{ 'page-mobile': isMobile, 'desktop-admin': !isMobile && route.path.startsWith('/admin/') }">
        <nav v-if="!isMobile && user?.role === 'ADMIN' && route.path.startsWith('/admin/')" class="desktop-list admin-nav" aria-label="管理后台">
          <router-link v-for="item in adminMenus.filter(item => item.key.startsWith('/admin/'))" :key="item.key"
            :to="item.key" class="desktop-list-item">{{ item.label }}</router-link>
        </nav>
        <main class="page-content">
          <router-view />
        </main>
      </div>
    </a-layout-content>

    <!-- Mobile bottom tabs -->
    <nav
      v-if="isMobile && !hideMobileTab"
      class="mobile-tabbar"
      aria-label="主导航"
      :style="{ gridTemplateColumns: `repeat(${mobileTabs.length}, 1fr)` }"
    >
      <button
        v-for="item in mobileTabs"
        :key="item.key"
        type="button"
        class="tab-item"
        :class="{ active: activeTabKey === item.key }"
        @click="goNav(item.key)"
      >
        <span class="tab-icon" aria-hidden="true">
          <!-- 职位 / 广场 -->
          <svg v-if="item.icon === 'jobs'" viewBox="0 0 24 24" width="22" height="22">
            <path
              d="M8 7V6a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v1h3a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h3zm2-1h4v1h-4V6z"
              :fill="activeTabKey === item.key ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
          </svg>
          <!-- WHU Dev / 排行榜 -->
          <svg v-else-if="item.icon === 'topdev'" viewBox="0 0 24 24" width="22" height="22">
            <path
              d="M6.5 11h3.2v8H6.5v-8zm3.9-5h3.2v13h-3.2V6zm3.9 3h3.2v10h-3.2V9z"
              :fill="activeTabKey === item.key ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
            <path
              d="M5 20.5h14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
            />
          </svg>
          <!-- 工作台 -->
          <svg v-else-if="item.icon === 'workbench'" viewBox="0 0 24 24" width="22" height="22">
            <path
              d="M4 4h7v7H4V4zm9 0h7v7h-7V4zM4 13h7v7H4v-7zm9 0h7v7h-7v-7z"
              :fill="activeTabKey === item.key ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
          </svg>
          <!-- 我的 / 登录 -->
          <svg v-else-if="item.icon === 'me' || item.icon === 'login'" viewBox="0 0 24 24" width="22" height="22">
            <circle
              cx="12"
              cy="8"
              r="3.2"
              :fill="activeTabKey === item.key ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.7"
            />
            <path
              d="M5 19.2c1.8-3 4.1-4.5 7-4.5s5.2 1.5 7 4.5"
              :fill="activeTabKey === item.key ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
            />
          </svg>
          <!-- 任务 -->
          <svg v-else-if="item.icon === 'tasks'" viewBox="0 0 24 24" width="22" height="22">
            <path
              d="M8 4h8a2 2 0 0 1 2 2v14l-6-2.5L6 20V6a2 2 0 0 1 2-2z"
              :fill="activeTabKey === item.key ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
          </svg>
          <!-- 审阅 -->
          <svg v-else-if="item.icon === 'inbox'" viewBox="0 0 24 24" width="22" height="22">
            <path
              d="M4 6h16v12H4V6zm0 7h4.2l1.3 2h5l1.3-2H20"
              :fill="activeTabKey === item.key ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
          </svg>
          <!-- 消息 -->
          <svg v-else-if="item.icon === 'messages'" viewBox="0 0 24 24" width="22" height="22">
            <path
              d="M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-4 3v-3H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z"
              :fill="activeTabKey === item.key ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
          </svg>
          <!-- 用户 -->
          <svg v-else-if="item.icon === 'users'" viewBox="0 0 24 24" width="22" height="22">
            <circle cx="9" cy="9" r="3" :fill="activeTabKey === item.key ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.7" />
            <circle cx="16.5" cy="10.5" r="2.4" :fill="activeTabKey === item.key ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.7" />
            <path d="M3.5 19c1.4-2.4 3.2-3.6 5.5-3.6s4.1 1.2 5.5 3.6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
          </svg>
          <!-- 设置 -->
          <svg v-else viewBox="0 0 24 24" width="22" height="22">
            <circle cx="12" cy="12" r="3" :fill="activeTabKey === item.key ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.7" />
            <path
              d="M12 3.5v2.2m0 12.6v2.2M4.9 6.5l1.6 1.6m11 11 1.6 1.6M3.5 12h2.2m12.6 0h2.2M4.9 17.5l1.6-1.6m11-11 1.6-1.6"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
            />
          </svg>
        </span>
        <span class="tab-label">{{ item.label }}</span>
      </button>
    </nav>

    <a-drawer
      v-model:visible="drawerVisible"
      title="通知中心"
      :width="isMobile ? '100%' : 420"
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
.app-shell {
  min-height: 100vh;
  background: var(--bg-page, #f9fafb);
}
.desktop-header {
  background: #fff;
  border-bottom: 1px solid #eef2f7;
  padding: 0;
  height: auto;
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.03);
}
.personal-link {
  color: #4b5563;
  font-weight: 500;
}
.personal-link.active,
.personal-link:hover {
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

.mobile-header {
  position: sticky;
  top: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 48px;
  padding: 0 14px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid #eef2f7;
  box-sizing: border-box;
}
.mobile-header-main {
  min-width: 0;
  flex: 1;
}
.mobile-title {
  font-size: 17px;
  font-weight: 750;
  color: #111827;
  line-height: 1.2;
}
.mobile-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  height: 32px;
  overflow: visible;
}
.mobile-header-actions :deep(.arco-badge) {
  line-height: 1;
  overflow: visible;
}
.mobile-header-actions :deep(.arco-btn) {
  width: 32px;
  height: 32px;
}
/* Keep the unread pill inside the bell, not past the header edge. */
.header-notify-badge {
  overflow: visible;
}
.header-notify-badge :deep(.arco-badge-number),
.header-notify-badge :deep(.arco-badge-dot) {
  top: 2px !important;
  right: 2px !important;
  left: auto !important;
  min-width: 12px;
  height: 12px;
  padding: 0 3px;
  font-size: 9px;
  font-weight: 700;
  line-height: 12px;
  transform: none !important;
  box-shadow: 0 0 0 1px #fff;
  box-sizing: border-box;
}
.mobile-logout {
  color: #6b7280;
  font-weight: 600;
  padding: 0 6px !important;
  font-size: 13px;
  width: auto !important;
  height: 32px !important;
}
.mobile-tutorial-hint {
  display: block;
  margin: 0;
  padding: 8px 16px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #9ca3af;
  font-size: 12px;
  font-weight: 450;
  line-height: 1.4;
  text-decoration: none;
}
.mobile-tutorial-hint:active {
  color: #6b7280;
}
.page-mobile {
  width: 100% !important;
  margin: 0 !important;
  padding: 12px 12px 24px !important;
}
.app-shell.tab-pad .page-mobile {
  padding-bottom: calc(56px + var(--bottom-inset, 0px)) !important;
}
.mobile-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  min-height: 50px;
  padding: 4px 0 calc(4px + var(--bottom-inset, 0px));
  background: rgba(255, 255, 255, 0.98);
  border-top: 1px solid #eef2f7;
  box-shadow: 0 -4px 20px rgba(15, 23, 42, 0.04);
}
.tab-item {
  border: 0;
  background: transparent;
  color: #9ca3af;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  padding: 4px 2px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.tab-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  line-height: 0;
}
.tab-icon svg {
  display: block;
}
.tab-label {
  font-size: 10px;
  font-weight: 650;
  line-height: 1.2;
  letter-spacing: 0.01em;
}
.tab-item.active {
  color: #2563eb;
}
.tab-item.active .tab-label {
  font-weight: 750;
}
</style>
