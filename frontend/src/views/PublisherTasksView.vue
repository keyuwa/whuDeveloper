<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { api } from '../api'
import { useIsMobile } from '../composables/useDevice'
import { TASK_ROLE_LABEL, TASK_ROLE_TYPES, type Project, type PublisherTaskPage, type Task } from '../types'

const PAGE_SIZE = 20
const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const tasks = ref<Task[]>([])
const projects = ref<Project[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const page = ref(0)
const total = ref(0)
const hasMore = ref(false)

const ROLE_STORAGE_KEY = 'publisher.tasks.roleType'
const STATUS_STORAGE_KEY = 'publisher.tasks.status'

function parseRoleTab(value: unknown): 'TA' | 'INTERN' | 'RA' | null {
  const normalized = String(value || '').toUpperCase()
  return normalized === 'TA' || normalized === 'INTERN' || normalized === 'RA' ? normalized : null
}

function parseStatusTab(value: unknown): 'ALL' | 'OPEN' | 'DRAFT' | 'CLOSED' | null {
  const normalized = String(value || '').toUpperCase()
  return normalized === 'OPEN' || normalized === 'DRAFT' || normalized === 'CLOSED' || normalized === 'ALL'
    ? normalized
    : null
}

function initialRoleTab(): 'TA' | 'INTERN' | 'RA' {
  return (
    parseRoleTab(route.query.roleType) ||
    parseRoleTab(sessionStorage.getItem(ROLE_STORAGE_KEY)) ||
    'INTERN'
  )
}

function initialStatusTab(): 'ALL' | 'OPEN' | 'DRAFT' | 'CLOSED' {
  return (
    parseStatusTab(route.query.status) ||
    parseStatusTab(sessionStorage.getItem(STATUS_STORAGE_KEY)) ||
    'ALL'
  )
}

const roleTab = ref<'TA' | 'INTERN' | 'RA'>(initialRoleTab())
const statusTab = ref<'ALL' | 'OPEN' | 'DRAFT' | 'CLOSED'>(initialStatusTab())
const roleCounts = ref({ TA: 0, INTERN: 0, RA: 0 })
const loadMoreSentinel = ref<HTMLElement | null>(null)
let loadMoreObserver: IntersectionObserver | null = null
let syncingQuery = false
const showPcPager = computed(() => !isMobile.value && total.value > PAGE_SIZE)

function rememberListTabs() {
  sessionStorage.setItem(ROLE_STORAGE_KEY, roleTab.value)
  sessionStorage.setItem(STATUS_STORAGE_KEY, statusTab.value)
}

function syncListQuery() {
  rememberListTabs()
  syncingQuery = true
  void router.replace({
    path: '/publisher/tasks',
    query: {
      roleType: roleTab.value,
      status: statusTab.value,
    },
  }).finally(() => {
    syncingQuery = false
  })
}

function listQuery(extra: Record<string, string> = {}) {
  return {
    roleType: roleTab.value,
    status: statusTab.value,
    ...extra,
  }
}

const statusLabel: Record<string, string> = {
  DRAFT: '草稿',
  OPEN: '已发布',
  CLOSED: '已下架',
}

const statusColor: Record<string, string> = {
  DRAFT: 'gray',
  OPEN: 'green',
  CLOSED: 'orangered',
}

const projectStatusLabel: Record<string, string> = {
  ACTIVE: '进行中',
  COMPLETING: '结项待验收',
  COMPLETED: '已结项',
  TERMINATED: '已终止',
}

const activeProjects = computed(() =>
  projects.value.filter((item) => item.status !== 'TERMINATED'),
)

const projectsByTask = computed(() => {
  const map = new Map<number, Project[]>()
  for (const item of activeProjects.value) {
    const list = map.get(item.taskId) || []
    list.push(item)
    map.set(item.taskId, list)
  }
  return map
})

const emptyDescription = computed(() => {
  const role = TASK_ROLE_LABEL[roleTab.value] || roleTab.value
  if (statusTab.value === 'OPEN') return `暂无已发布的${role}`
  if (statusTab.value === 'DRAFT') return `暂无${role}草稿`
  if (statusTab.value === 'CLOSED') return `暂无已下架的${role}`
  return `还没有${role}`
})

function statusQuery() {
  return statusTab.value === 'ALL' ? '' : statusTab.value
}

async function fetchProjects() {
  projects.value = await api<Project[]>('/api/v1/projects')
}

async function fetchPage(nextPage: number, append: boolean) {
  const status = statusQuery()
  const qs = new URLSearchParams({
    page: String(nextPage),
    size: String(PAGE_SIZE),
    roleType: roleTab.value,
  })
  if (status) qs.set('status', status)
  const result = await api<PublisherTaskPage>(`/api/v1/publisher/tasks?${qs}`)
  tasks.value = append ? [...tasks.value, ...result.items] : result.items
  page.value = result.page
  total.value = result.total
  hasMore.value = result.hasMore
  roleCounts.value = {
    TA: result.totalTa,
    INTERN: result.totalIntern,
    RA: result.totalRa,
  }
}

async function load(reset = true) {
  if (reset) {
    loading.value = true
    page.value = 0
    total.value = 0
    hasMore.value = false
  }
  try {
    await Promise.all([fetchPage(0, false), fetchProjects()])
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loading.value = false
    await nextTick()
    bindLoadMore()
  }
}

async function loadMore() {
  if (!isMobile.value || loading.value || loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    await fetchPage(page.value + 1, true)
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载更多失败')
  } finally {
    loadingMore.value = false
  }
}

async function onPcPageChange(current: number) {
  if (isMobile.value) return
  loading.value = true
  try {
    await fetchPage(Math.max(current - 1, 0), false)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '翻页失败')
  } finally {
    loading.value = false
  }
}

function bindLoadMore() {
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
  if (!isMobile.value) return
  const el = loadMoreSentinel.value
  if (!el) return
  loadMoreObserver = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) void loadMore()
    },
    { root: null, rootMargin: '240px 0px', threshold: 0 },
  )
  loadMoreObserver.observe(el)
}

watch(statusTab, () => {
  if (syncingQuery) return
  syncListQuery()
  void load(true)
})

watch(roleTab, () => {
  if (syncingQuery) return
  if (statusTab.value !== 'ALL') {
    statusTab.value = 'ALL'
    return
  }
  syncListQuery()
  void load(true)
})

watch(
  () => [route.query.roleType, route.query.status] as const,
  () => {
    if (syncingQuery) return
    const nextRole = initialRoleTab()
    const nextStatus = initialStatusTab()
    if (roleTab.value === nextRole && statusTab.value === nextStatus) return
    syncingQuery = true
    roleTab.value = nextRole
    statusTab.value = nextStatus
    syncingQuery = false
    void load(true)
  },
)

onMounted(() => {
  syncListQuery()
  void load(true)
})

onUnmounted(() => {
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
})

async function act(id: number, action: 'publish' | 'close') {
  try {
    await api(`/api/v1/publisher/tasks/${id}/${action}`, { method: 'POST' })
    await load(true)
    Message.success(action === 'publish' ? '已发布' : '已下架')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '操作失败')
  }
}

function canDelete(task: Task) {
  return task.status === 'DRAFT' || task.status === 'CLOSED'
}

function removeTask(task: Task) {
  Modal.confirm({
    title: '删除任务',
    content: `确定删除「${task.title}」吗？历史申请与已结束协作会一并清理；若仍有进行中项目则不能删除。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    onOk: async () => {
      try {
        await api(`/api/v1/publisher/tasks/${task.id}`, { method: 'DELETE' })
        Message.success('已删除')
        await load(true)
      } catch (cause) {
        Message.error(cause instanceof Error ? cause.message : '删除失败')
        throw cause
      }
    },
  })
}

function openInbox(taskId?: number) {
  if (taskId) router.push({ path: '/publisher/inbox', query: { taskId: String(taskId) } })
  else router.push('/publisher/inbox')
}
</script>

<template>
  <section :class="{ 'publisher-mobile': isMobile }">
    <header class="page-head">
      <div v-if="!isMobile">
        <a-typography-title :heading="4" style="margin: 0">我的任务</a-typography-title>
        <p class="subtitle">查看待审申请与已入项学生；草稿与已下架可删除。</p>
      </div>
      <a-space wrap>
        <a-button size="small" @click="openInbox()">全部申请</a-button>
        <a-button
          type="primary"
          size="small"
          @click="router.push({ path: '/publisher/tasks/new', query: listQuery() })"
        >
          {{ isMobile ? '新建任务' : '发布任务' }}
        </a-button>
      </a-space>
    </header>

    <nav class="role-tabs" :class="{ compact: isMobile }" aria-label="任务类型">
      <button
        v-for="item in TASK_ROLE_TYPES"
        :key="item.value"
        type="button"
        class="role-tab"
        :class="{ active: roleTab === item.value }"
        @click="roleTab = item.value"
      >
        <span class="role-name">{{ item.label }}</span>
        <span class="role-count">{{ roleCounts[item.value] }}</span>
      </button>
    </nav>

    <a-tabs
      v-model:active-key="statusTab"
      type="rounded"
      :size="isMobile ? 'medium' : 'large'"
      class="status-tabs"
    >
      <a-tab-pane key="ALL" title="全部" />
      <a-tab-pane key="OPEN" title="已发布" />
      <a-tab-pane key="DRAFT" title="草稿" />
      <a-tab-pane key="CLOSED" title="已下架" />
    </a-tabs>

    <a-spin :loading="loading" style="width: 100%">
      <a-empty v-if="!loading && tasks.length === 0" :description="emptyDescription" />
      <div v-else class="stack">
        <a-card v-for="task in tasks" :key="task.id" class="task-card">
          <div class="task-body">
            <a-space wrap style="margin-bottom: 8px">
              <a-tag size="small">{{ task.publicCode }}</a-tag>
              <a-tag color="arcoblue" size="small">{{ TASK_ROLE_LABEL[task.roleType] || task.roleType }}</a-tag>
              <a-tag v-if="task.roleType !== 'TA'" size="small">{{ task.techField }}</a-tag>
              <a-tag v-if="task.roleType === 'INTERN'" size="small">{{ task.difficulty }}</a-tag>
              <a-tag :color="statusColor[task.status] || 'gray'" size="small">
                {{ statusLabel[task.status] || task.status }}
              </a-tag>
              <a-tag v-if="(task.pendingApplications || 0) > 0" color="orange" size="small">
                待审 {{ task.pendingApplications }}
              </a-tag>
            </a-space>
            <a-typography-title :heading="6" style="margin: 0 0 4px">{{ task.title }}</a-typography-title>
            <a-typography-text type="secondary">
              已入项 {{ task.filled }}/{{ task.quota }}
              <template v-if="(task.pendingApplications || 0) > 0"> · 待审申请 {{ task.pendingApplications }}</template>
              <template v-if="task.roleType === 'INTERN'"> · {{ task.programmingLanguages }} · {{ task.durationWeeks }} 周</template>
              <template v-if="task.stipendText"> · {{ task.stipendText }}</template>
            </a-typography-text>

            <div v-if="(projectsByTask.get(task.id) || []).length" class="enrolled">
              <div class="enrolled-title">已接收学生</div>
              <div
                v-for="item in projectsByTask.get(task.id)"
                :key="item.id"
                class="enrolled-row"
              >
                <div>
                  <strong>{{ item.studentName }}</strong>
                  <span class="muted"> · {{ projectStatusLabel[item.status] || item.status }}</span>
                </div>
                <a-button
                  size="mini"
                  type="text"
                  @click="router.push(`/publisher/projects/${item.id}/detail`)"
                >
                  进入协作
                </a-button>
              </div>
            </div>
            <div v-else-if="task.status === 'OPEN'" class="enrolled empty-hint">
              暂无学生入项；有人申请后会出现在「审阅」，通过后显示在这里。
            </div>
          </div>

          <div class="task-footer">
            <div class="actions-manage">
              <a-button
                size="small"
                @click="router.push({ path: `/publisher/tasks/${task.id}`, query: listQuery() })"
              >
                编辑
              </a-button>
              <a-button
                v-if="task.status === 'OPEN'"
                size="small"
                @click="act(task.id, 'close')"
              >
                下架
              </a-button>
              <a-button
                v-if="canDelete(task)"
                size="small"
                status="danger"
                @click="removeTask(task)"
              >
                删除
              </a-button>
            </div>
            <div class="actions-primary">
              <a-button
                v-if="(task.pendingApplications || 0) > 0"
                type="primary"
                size="small"
                @click="openInbox(task.id)"
              >
                审阅申请 · {{ task.pendingApplications }}
              </a-button>
              <a-button
                v-else-if="task.filled > 0"
                type="outline"
                size="small"
                @click="openInbox(task.id)"
              >
                查看申请
              </a-button>
              <a-button
                v-if="task.status !== 'OPEN'"
                type="primary"
                size="small"
                @click="act(task.id, 'publish')"
              >
                {{ task.status === 'DRAFT' ? '发布' : '重新发布' }}
              </a-button>
            </div>
          </div>
        </a-card>
        <div
          v-if="isMobile && (tasks.length || hasMore)"
          ref="loadMoreSentinel"
          class="load-more-sentinel"
          aria-hidden="true"
        />
        <div v-if="isMobile && loadingMore" class="load-more-status">加载更多…</div>
        <div v-else-if="isMobile && tasks.length && !hasMore" class="load-more-status muted">已经到底了</div>
        <div v-if="showPcPager" class="pager">
          <a-pagination
            :total="total"
            :current="page + 1"
            :page-size="PAGE_SIZE"
            show-total
            @change="onPcPageChange"
          />
        </div>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.role-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0 0 12px;
}
.role-tab {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  background: #fff;
  padding: 12px 14px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}
.role-tab:hover {
  border-color: #bfdbfe;
}
.role-tab.active {
  border-color: #2563eb;
  background: linear-gradient(180deg, #eff6ff 0%, #fff 70%);
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.1);
}
.role-name {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}
.role-tab.active .role-name {
  color: #1d4ed8;
}
.role-count {
  font-size: 12px;
  color: #6b7280;
  font-weight: 600;
}
.role-tabs.compact {
  gap: 8px;
}
.role-tabs.compact .role-tab {
  padding: 10px 12px;
  border-radius: 10px;
}
.role-tabs.compact .role-name {
  font-size: 13px;
}
.status-tabs {
  margin: 4px 0 14px;
}
.status-tabs :deep(.arco-tabs-nav-tab) {
  flex: 1;
  justify-content: space-between;
}
.status-tabs :deep(.arco-tabs-nav-tab-list) {
  display: flex;
  width: 100%;
}
.status-tabs :deep(.arco-tabs-tab) {
  flex: 1;
  justify-content: center;
  margin: 0;
  padding: 0 8px;
}
.task-card {
  border: 1px solid #eef2f7;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
.task-card :deep(.arco-card-body) {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.task-body {
  min-width: 0;
  width: 100%;
}
.task-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  padding-top: 12px;
  border-top: 1px solid #eef2f7;
}
.actions-primary {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  margin-left: auto;
}
.actions-manage {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}
.enrolled {
  margin-top: 14px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  width: 100%;
  box-sizing: border-box;
}
.enrolled-title {
  font-size: 12px;
  font-weight: 650;
  color: #374151;
  margin-bottom: 8px;
}
.enrolled-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
  font-size: 13px;
}
.enrolled-row + .enrolled-row { border-top: 1px solid #eef2f7; }
.muted { color: #6b7280; font-weight: 400; }
.empty-hint {
  color: #8b95a5;
  font-size: 12px;
  line-height: 1.6;
}
.publisher-mobile .page-head {
  align-items: center;
  margin-bottom: 14px;
}
.publisher-mobile,
.publisher-mobile .stack,
.publisher-mobile .task-card {
  width: 100%;
}
.publisher-mobile .enrolled {
  margin-top: 12px;
  width: 100%;
}
.load-more-sentinel {
  height: 1px;
  width: 100%;
}
.load-more-status {
  text-align: center;
  padding: 12px 0 4px;
  font-size: 13px;
  color: #6b7280;
}
.load-more-status.muted {
  color: #9ca3af;
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 8px 0 4px;
}
@media (max-width: 700px) {
  .task-footer {
    flex-direction: column;
    align-items: stretch;
  }
  .actions-primary {
    margin-left: 0;
    width: 100%;
  }
  .actions-primary :deep(.arco-btn) {
    flex: 1;
  }
}
</style>
