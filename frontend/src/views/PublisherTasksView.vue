<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { api } from '../api'
import TaskFilterBar from '../components/TaskFilterBar.vue'
import { useIsMobile } from '../composables/useDevice'
import { TASK_ROLE_LABEL, type Project, type PublisherTaskPage, type Task } from '../types'

const PAGE_SIZE = 20
const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const tasks = ref<Task[]>([])
const selectedTaskId = ref<number | null>(null)
const activeTask = computed(() => tasks.value.find(task => task.id === selectedTaskId.value) ?? tasks.value[0])
const displayedTasks = computed(() => isMobile.value ? tasks.value : activeTask.value ? [activeTask.value] : [])

const projects = ref<Project[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const page = ref(0)
const hasMore = ref(false)

const ROLE_STORAGE_KEY = 'publisher.tasks.roleType'
const STATUS_STORAGE_KEY = 'publisher.tasks.status'

function parseRoleTab(value: unknown): 'TA' | 'INTERN' | 'RA' | 'TEAM' | null {
  const normalized = String(value || '').toUpperCase()
  return normalized === 'TA' || normalized === 'INTERN' || normalized === 'RA' || normalized === 'TEAM'
    ? normalized
    : null
}

function parseStatusTab(value: unknown): 'ALL' | 'OPEN' | 'DRAFT' | 'CLOSED' | null {
  const normalized = String(value || '').toUpperCase()
  return normalized === 'OPEN' || normalized === 'DRAFT' || normalized === 'CLOSED' || normalized === 'ALL'
    ? normalized
    : null
}

function initialRoleTab(): 'TA' | 'INTERN' | 'RA' | 'TEAM' {
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

const roleTab = ref<'TA' | 'INTERN' | 'RA' | 'TEAM'>(initialRoleTab())
const statusTab = ref<'ALL' | 'OPEN' | 'DRAFT' | 'CLOSED'>(initialStatusTab())
const keyword = ref('')
const loadMoreSentinel = ref<HTMLElement | null>(null)
let loadMoreObserver: IntersectionObserver | null = null
let syncingQuery = false
let keywordTimer: ReturnType<typeof setTimeout> | null = null

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
  if (keyword.value.trim()) return '没有匹配的任务，换个关键词试试'
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
  const query = keyword.value.trim()
  if (query) qs.set('keyword', query)
  const result = await api<PublisherTaskPage>(`/api/v1/publisher/tasks?${qs}`)
  tasks.value = append ? [...tasks.value, ...result.items] : result.items
  page.value = result.page
  hasMore.value = result.hasMore

}

async function load(reset = true) {
  if (reset) {
    loading.value = true
    page.value = 0
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
  if (loading.value || loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    await fetchPage(page.value + 1, true)
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载更多失败')
  } finally {
    loadingMore.value = false
    await nextTick()
    bindLoadMore()
  }
}

function bindLoadMore() {
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
  const el = loadMoreSentinel.value
  if (!el || !hasMore.value) return
  const root = el.parentElement?.classList.contains('desktop-list') ? el.parentElement : null
  loadMoreObserver = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return
      if (loading.value || loadingMore.value || !hasMore.value) return
      loadMoreObserver?.disconnect()
      loadMoreObserver = null
      void loadMore()
    },
    { root, rootMargin: '160px 0px', threshold: 0 },
  )
  loadMoreObserver.observe(el)
}

watch(statusTab, () => {
  if (syncingQuery) return
  syncListQuery()
  void load(true)
})

watch(keyword, () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    void load(true)
  }, 300)
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
  if (keywordTimer) clearTimeout(keywordTimer)
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
  <section class="publisher-board" :class="{ 'publisher-mobile': isMobile }">
    <section class="task-management-panel" aria-label="任务管理与筛选">
    <header class="management-heading">
      <div v-if="!isMobile">
        <a-typography-title :heading="4" style="margin: 0">我的任务</a-typography-title>
        <p class="management-description">管理任务、审阅申请，查看已入项学生</p>
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

    <TaskFilterBar class="management-filters" :model-value="roleTab" @update:model-value="value => { if (value) roleTab = value }">
      <a-input v-model="keyword" allow-clear size="medium" aria-label="搜索任务" placeholder="搜索任务标题或编号">
        <template #prefix><icon-search aria-hidden="true" /></template>
      </a-input>
      <a-select v-model="statusTab" size="medium" aria-label="发布状态">
        <a-option value="ALL">全部状态</a-option>
        <a-option value="OPEN">已发布</a-option>
        <a-option value="DRAFT">草稿</a-option>
        <a-option value="CLOSED">已下架</a-option>
      </a-select>
      <a-button v-if="keyword || statusTab !== 'ALL'" type="text" @click="keyword = ''; statusTab = 'ALL'">清空</a-button>
    </TaskFilterBar>
    </section>

    <a-spin :loading="loading" class="board-spin">
      <a-empty v-if="!loading && tasks.length === 0" :description="emptyDescription" />
      <div v-else class="stack">
        <div :class="{ 'desktop-split': !isMobile }">
        <nav v-if="!isMobile && tasks.length" class="desktop-list" aria-label="已发布任务">
          <button v-for="task in tasks" :key="task.id" type="button" class="desktop-list-item"
            :aria-pressed="activeTask?.id === task.id" @click="selectedTaskId = task.id">
            <strong>{{ task.title }}</strong>
            <span>{{ statusLabel[task.status] || task.status }} · 已入项 {{ task.filled }}/{{ task.quota }}</span>
            <span v-if="task.pendingApplications">待审 {{ task.pendingApplications }}</span>
          </button>
          <div
            v-if="tasks.length || hasMore"
            ref="loadMoreSentinel"
            class="load-more-sentinel"
            aria-hidden="true"
          />
          <div v-if="loadingMore" class="load-more-status in-list">加载更多…</div>
          <div v-else-if="tasks.length && !hasMore" class="load-more-status in-list muted">已经到底了</div>
        </nav>
        <div class="stack desktop-detail">
        <a-card v-for="task in displayedTasks" :key="task.id" class="task-card">
          <div class="task-body">
            <div class="task-heading">
              <h2>{{ task.title }}</h2>
              <a-tag :color="statusColor[task.status] || 'gray'" size="small">
                {{ statusLabel[task.status] || task.status }}
              </a-tag>
            </div>
            <div class="task-meta">
              <p class="task-identity">
                <strong>{{ task.roleType === 'INTERN' ? '开发实习' : (TASK_ROLE_LABEL[task.roleType] || task.roleType) }}</strong>
                <span v-if="task.publicCode">{{ task.publicCode }}</span>
              </p>
              <p v-if="task.roleType === 'INTERN' || (task.roleType !== 'TA' && task.roleType !== 'TEAM' && task.techField)">
                {{ [
                  task.techField,
                  task.roleType === 'INTERN' && task.difficulty && `难度 ${task.difficulty}`,
                  task.roleType === 'INTERN' && task.programmingLanguages?.replace(/\s*[,，]\s*/g, ' / '),
                ].filter(Boolean).join(' · ') }}
              </p>
              <ul v-if="task.roleType === 'TEAM' && task.positions?.length" class="task-positions">
                <li v-for="position in task.positions" :key="position.id">{{ position.title }} {{ position.filled }}/{{ position.quota }}</li>
              </ul>
            </div>
            <div class="task-summary">
              <span>已入项 <strong>{{ task.filled }}/{{ task.quota }}</strong></span>
              <span v-if="(task.roleType === 'INTERN' || task.roleType === 'TEAM') && task.durationWeeks">
                周期 <strong>{{ task.durationWeeks }} 周</strong>
              </span>
              <span v-if="task.stipendText">补助 <strong>{{ task.stipendText }}</strong></span>
            </div>

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
            <div v-else-if="!isMobile && task.status === 'OPEN'" class="enrolled empty-hint">
              暂无学生入项
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
        </div>
        </div>
        <div
          v-if="isMobile && (tasks.length || hasMore)"
          ref="loadMoreSentinel"
          class="load-more-sentinel"
          aria-hidden="true"
        />
        <div v-if="isMobile && loadingMore" class="load-more-status">加载更多…</div>
        <div v-else-if="isMobile && tasks.length && !hasMore" class="load-more-status muted">已经到底了</div>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.task-management-panel {
  margin-bottom: 16px;
  padding: 0;
  border: 1px solid var(--surface-line);
  border-radius: var(--surface-radius);
  background: var(--bg-surface);
}
.management-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 18px 20px 16px;
  background: var(--heading-surface);
  border-radius: var(--surface-radius) var(--surface-radius) 0 0;
}
.management-heading :deep(.arco-typography) { font-size: 22px; font-weight: 700; line-height: 1.4; color: var(--text-strong); }
.desktop-list-item strong { font-size: 15px; font-weight: 700; line-height: 1.5; }
.management-description { margin: 6px 0 0; color: var(--text-body); font-size: 13px; line-height: 1.5; }
.task-management-panel .management-filters {
  margin: 0;
  padding: 0 0 12px;
  border: 0;
  border-top: 1px solid var(--surface-line);
  border-radius: 0 0 var(--surface-radius) var(--surface-radius);
}
.task-management-panel .management-filters :deep(.filter-controls) {
  padding: 12px 20px 0;
}
@media (max-width: 767px) {
  .management-heading { justify-content: flex-end; padding: 12px; }
  .task-management-panel .management-filters { --filter-outer-inset: 12px; padding: 10px 12px 12px; }
}
.role-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.role-tab.active .role-name {
  color: #1d4ed8;
}
.role-count {
  font-size: 12px;
  color: #6b7280;
  font-weight: 600;
  flex-shrink: 0;
}
.role-tabs.compact {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}
.role-tabs.compact .role-tab {
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 8px 4px;
  border-radius: 10px;
  text-align: center;
}
.role-tabs.compact .role-name {
  font-size: 13px;
  white-space: nowrap;
}
.role-tabs.compact .role-count {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.status-tabs {
  margin: 4px 0 14px;
}
.status-tabs :deep(.arco-tabs-nav),
.status-tabs :deep(.arco-tabs-nav-tab) {
  background: transparent;
}
.status-tabs :deep(.arco-tabs-nav-tab) {
  flex: 1;
  justify-content: space-between;
}
.status-tabs :deep(.arco-tabs-nav-tab-list) {
  display: flex;
  width: 100%;
  gap: 10px;
}
.status-tabs :deep(.arco-tabs-tab) {
  flex: 1;
  justify-content: center;
  height: 36px;
  margin: 0;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  color: #1e293b;
  font-weight: 650;
}
.status-tabs :deep(.arco-tabs-tab:hover) {
  border-color: #93c5fd;
  background: #fff;
  color: #1d4ed8;
}
.status-tabs :deep(.arco-tabs-tab-active),
.status-tabs :deep(.arco-tabs-tab-active:hover) {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
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
.publisher-mobile .task-card { border-radius: var(--mobile-radius); }
.publisher-mobile .task-card :deep(.arco-card-body) { padding: 14px; gap: 12px; }
.task-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.task-heading h2 { min-width: 0; font-size: 17px; font-weight: 700; line-height: 1.45; color: var(--text-strong); overflow-wrap: anywhere; }
.task-heading :deep(.arco-tag) { flex: none; }
.task-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 8px;
  color: #475569;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.task-meta {
  display: grid;
  gap: 4px;
  margin-top: 6px;
  color: #475569;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.task-identity { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 10px; }
.task-identity strong { color: rgb(var(--primary-6)); font-weight: 650; }
.task-identity span { color: #64748b; font-size: 12px; }
.task-positions { margin: 0; padding-inline-start: 18px; }
.task-summary { font-size: 13px; }
.task-summary strong { color: #1f2937; font-weight: 700; font-variant-numeric: tabular-nums; }
.publisher-mobile .task-footer { flex-direction: row; align-items: stretch; gap: 8px; padding-top: 10px; }
.publisher-mobile .actions-primary { width: auto; flex: 1 0 auto; margin-left: auto; }
.publisher-mobile :is(.actions-primary, .actions-manage) :deep(.arco-btn) { min-height: 44px; }
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
.load-more-status.in-list {
  margin-top: auto;
  padding: 14px 12px 16px;
  border-top: 1px solid #edf0f4;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
}
.board-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 14px;
}
.board-filter-label {
  flex: none;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}
.board-search {
  width: min(360px, 100%);
  height: 36px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.board-search :deep(.arco-input) {
  color: #111827;
  font-size: 13px;
}
.board-search :deep(.arco-input::placeholder) {
  color: #64748b;
}
.board-search:hover,
.board-search.arco-input-focus {
  background: #fff;
  border-color: #2563eb;
}
.publisher-board:not(.publisher-mobile) .board-filters {
  display: grid;
  grid-template-columns: 154px 126px minmax(0, 360px);
  padding: 10px 12px;
  background: var(--bg-surface);
  border: 1px solid var(--surface-line);
  border-radius: var(--surface-radius);
}
.board-select,
.publisher-board:not(.publisher-mobile) .board-search {
  width: 100%;
  min-width: 0;
}
.board-spin { width: 100%; }
.publisher-board:not(.publisher-mobile) .desktop-split { align-items: start; }
.publisher-board:not(.publisher-mobile) .desktop-list {
  max-height: max(240px, calc(100dvh - var(--desktop-header-height, 58px) - 180px));
  overflow-y: auto;
  overscroll-behavior: contain;
}
.publisher-board:not(.publisher-mobile) .desktop-list-item { padding-block: 11px; }
.publisher-board:not(.publisher-mobile) .task-heading h2 { font-size: 20px; }
.publisher-board:not(.publisher-mobile) .empty-hint {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-body);
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
