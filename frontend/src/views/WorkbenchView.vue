<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { useIsMobile } from '../composables/useDevice'
import { TASK_ROLE_LABEL, TASK_ROLE_TYPES, type Application, type Project, type Task } from '../types'

const ROLE_STORAGE_KEY = 'student.workbench.roleType'

const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const projects = ref<Project[]>([])
const applications = ref<Application[]>([])
const shortlists = ref<Task[]>([])
const loading = ref(false)

function parseRoleTab(value: unknown): 'TA' | 'INTERN' | 'RA' | 'TEAM' | null {
  const normalized = String(value || '').toUpperCase()
  return normalized === 'TA' || normalized === 'INTERN' || normalized === 'RA' || normalized === 'TEAM'
    ? normalized
    : null
}

const roleTab = ref<'TA' | 'INTERN' | 'RA' | 'TEAM'>(
  parseRoleTab(route.query.roleType) ||
    parseRoleTab(sessionStorage.getItem(ROLE_STORAGE_KEY)) ||
    'INTERN',
)

const tab = computed(() => {
  if (route.path.includes('/applications')) return 'applications'
  if (route.path.includes('/candidates')) return 'candidates'
  return 'enrolled'
})

const projectStatusLabel: Record<string, string> = {
  ACTIVE: '进行中',
  COMPLETING: '结项待验收',
  COMPLETED: '已结项',
  TERMINATED: '已终止',
}

const projectStatusColor: Record<string, string> = {
  ACTIVE: 'arcoblue',
  COMPLETING: 'orange',
  COMPLETED: 'gray',
  TERMINATED: 'red',
}

const applicationStatusLabel: Record<string, string> = {
  SUBMITTED: '待审阅',
  INTERVIEW: '面试中',
  ACCEPTED: '已通过',
  REJECTED: '未通过',
  WITHDRAWN: '已撤回',
}

const applicationStatusColor: Record<string, string> = {
  SUBMITTED: 'arcoblue',
  INTERVIEW: 'orange',
  ACCEPTED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'gray',
}

function projectRole(item: Project) {
  return (item.taskRoleType || '').toUpperCase()
}

function applicationRole(item: Application) {
  return (item.taskRoleType || '').toUpperCase()
}

const activeProjects = computed(() =>
  projects.value.filter((item) => item.status !== 'TERMINATED'),
)

const pendingApplications = computed(() =>
  applications.value.filter((item) => item.status !== 'ACCEPTED'),
)

const roleCounts = computed(() => {
  const counts = { TA: 0, INTERN: 0, RA: 0, TEAM: 0 }
  if (tab.value === 'enrolled') {
    for (const item of activeProjects.value) {
      const role = projectRole(item)
      if (role in counts) counts[role as keyof typeof counts] += 1
    }
  } else if (tab.value === 'applications') {
    for (const item of pendingApplications.value) {
      const role = applicationRole(item)
      if (role in counts) counts[role as keyof typeof counts] += 1
    }
  } else {
    for (const item of shortlists.value) {
      const role = (item.roleType || '').toUpperCase()
      if (role in counts) counts[role as keyof typeof counts] += 1
    }
  }
  return counts
})

const orderedProjects = computed(() =>
  [...activeProjects.value]
    .filter((item) => projectRole(item) === roleTab.value)
    .sort((a, b) => Number(b.status === 'ACTIVE') - Number(a.status === 'ACTIVE')),
)

const filteredApplications = computed(() =>
  pendingApplications.value.filter((item) => applicationRole(item) === roleTab.value),
)

const filteredShortlists = computed(() =>
  shortlists.value.filter((item) => (item.roleType || '').toUpperCase() === roleTab.value),
)

const roleLabel = computed(() => TASK_ROLE_LABEL[roleTab.value] || roleTab.value)

const emptyDescription = computed(() => {
  if (tab.value === 'enrolled') return `还没有已接的${roleLabel.value}，申请通过入项后会出现在这里`
  if (tab.value === 'applications') return `还没有待处理的${roleLabel.value}申请，通过后会进入「已接项目」`
  return `暂无备选的${roleLabel.value}，在任务详情页可点「加入备选」`
})

function rememberRoleTab() {
  sessionStorage.setItem(ROLE_STORAGE_KEY, roleTab.value)
}

function syncRoleQuery() {
  rememberRoleTab()
  const nextQuery = { ...route.query, roleType: roleTab.value }
  void router.replace({ path: route.path, query: nextQuery })
}

watch(roleTab, syncRoleQuery)

async function load() {
  loading.value = true
  try {
    const [projectList, applicationList, shortlist] = await Promise.all([
      api<Project[]>('/api/v1/projects'),
      api<Application[]>('/api/v1/me/applications'),
      api<Task[]>('/api/v1/me/shortlists'),
    ])
    projects.value = projectList
    applications.value = applicationList
    shortlists.value = shortlist
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function switchTab(key: string | number) {
  const query = { roleType: roleTab.value }
  if (key === 'applications') router.push({ path: '/me/workbench/applications', query })
  else if (key === 'candidates') router.push({ path: '/me/workbench/candidates', query })
  else router.push({ path: '/me/workbench', query })
}

async function removeShortlist(taskId: number) {
  loading.value = true
  try {
    await api(`/api/v1/tasks/${taskId}/shortlist`, { method: 'DELETE' })
    shortlists.value = shortlists.value.filter((item) => item.id !== taskId)
    Message.success('已取消备选')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '取消失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  rememberRoleTab()
  load()
})
</script>

<template>
  <section class="workbench" :class="{ 'workbench-mobile': isMobile }">
    <header v-if="!isMobile" class="page-head">
      <div>
        <a-typography-title :heading="4" style="margin: 0">工作台</a-typography-title>
        <p class="subtitle">已接项目、已投申请与备选收藏，按助教 / 开发 / 科研 / 项目分栏查看。</p>
      </div>
      <router-link to="/" class="discover-link">探索新项目 ↗</router-link>
    </header>
    <div v-else class="mobile-wb-head">
      <router-link to="/" class="discover-link">探索新项目 ↗</router-link>
    </div>

    <nav class="role-tabs" :class="{ compact: isMobile }" aria-label="任务类型">
      <button
        v-for="item in TASK_ROLE_TYPES"
        :key="item.value"
        type="button"
        class="role-tab"
        :class="{ active: roleTab === item.value }"
        @click="roleTab = item.value"
      >
        <span class="role-name">{{ isMobile ? item.shortLabel : item.label }}</span>
        <span class="role-count">{{ roleCounts[item.value] }}</span>
      </button>
    </nav>

    <a-tabs class="workspace-tabs" :active-key="tab" type="rounded" :size="isMobile ? 'medium' : 'large'" @change="switchTab">
      <a-tab-pane key="enrolled" :title="`已接项目 · ${orderedProjects.length}`" />
      <a-tab-pane key="applications" :title="`已申请 · ${filteredApplications.length}`" />
      <a-tab-pane key="candidates" :title="`备选项目 · ${filteredShortlists.length}`" />
    </a-tabs>

    <a-spin :loading="loading" style="width: 100%; margin-top: 8px">
      <template v-if="tab === 'enrolled'">
        <div v-if="!loading && orderedProjects.length === 0" class="empty-state">
          <a-empty :description="emptyDescription" />
        </div>
        <div v-else class="task-grid">
          <router-link
            v-for="item in orderedProjects"
            :key="item.id"
            class="task-card-link"
            :to="`/me/projects/${item.id}/${item.status === 'ACTIVE' ? 'progress' : 'completion'}`"
          >
            <a-card class="task-card work-card" hoverable :bordered="true">
              <div class="card-top">
                <a-space wrap :size="6">
                  <a-tag color="arcoblue" size="small">{{ TASK_ROLE_LABEL[item.taskRoleType || ''] || item.taskRoleType }}</a-tag>
                  <a-tag v-if="item.taskRoleType !== 'TA' && item.taskType" size="small">{{ item.taskType }}</a-tag>
                  <a-tag :color="projectStatusColor[item.status] || 'gray'" size="small">
                    {{ projectStatusLabel[item.status] || item.status }}
                  </a-tag>
                </a-space>
              </div>
              <a-typography-title :heading="6" style="margin: 14px 0 10px">{{ item.taskTitle }}</a-typography-title>
              <div class="card-meta">
                <span>发布方 {{ item.publisherName }}</span>
              </div>
              <div class="card-foot">
                <span>{{ item.status === 'ACTIVE' ? '进入项目' : '查看项目' }} →</span>
              </div>
            </a-card>
          </router-link>
        </div>
      </template>

      <template v-else-if="tab === 'applications'">
        <div v-if="!loading && filteredApplications.length === 0" class="empty-state">
          <a-empty :description="emptyDescription" />
        </div>
        <div v-else class="task-grid">
          <router-link
            v-for="item in filteredApplications"
            :key="item.id"
            class="task-card-link"
            :to="`/tasks/${item.taskId}`"
          >
            <a-card class="task-card work-card" hoverable>
              <div class="card-top">
                <a-space wrap :size="6">
                  <a-tag color="arcoblue" size="small">{{ TASK_ROLE_LABEL[item.taskRoleType || ''] || item.taskRoleType }}</a-tag>
                  <a-tag v-if="item.taskRoleType !== 'TA' && item.taskType" size="small">{{ item.taskType }}</a-tag>
                  <a-tag :color="applicationStatusColor[item.status] || 'gray'" size="small">
                    {{ applicationStatusLabel[item.status] || item.status }}
                  </a-tag>
                </a-space>
              </div>
              <a-typography-title :heading="6" style="margin: 14px 0 10px">{{ item.taskTitle }}</a-typography-title>
              <div class="card-meta">
                <span v-if="item.positionTitle">岗位 {{ item.positionTitle }}</span>
                <span>投递于 {{ item.createdAt.replace('T', ' ').slice(0, 16) }}</span>
                <span v-if="item.reviewNote">审阅：{{ item.reviewNote }}</span>
              </div>
              <div class="card-foot">
                <span>查看任务 →</span>
              </div>
            </a-card>
          </router-link>
        </div>
      </template>

      <template v-else>
        <div v-if="!loading && filteredShortlists.length === 0" class="empty-state">
          <a-empty :description="emptyDescription" />
        </div>
        <div v-else class="task-grid">
          <div
            v-for="item in filteredShortlists"
            :key="item.id"
            class="task-card-link"
          >
            <a-card class="task-card work-card" hoverable>
              <div class="card-top">
                <a-space wrap :size="6">
                  <a-tag color="arcoblue" size="small">{{ TASK_ROLE_LABEL[item.roleType] || item.roleType }}</a-tag>
                  <a-tag v-if="item.roleType !== 'TA'" size="small">{{ item.techField }}</a-tag>
                  <a-tag v-if="item.roleType === 'INTERN'" size="small">{{ item.difficulty }}</a-tag>
                  <a-tag size="small">备选中</a-tag>
                </a-space>
              </div>
              <a-typography-title :heading="6" style="margin: 14px 0 10px">
                <router-link class="title-link" :to="`/tasks/${item.id}`">{{ item.title }}</router-link>
              </a-typography-title>
              <div class="card-meta">
                <span>发布方 {{ item.publisherName }}</span>
                <span>{{ item.durationWeeks }} 周</span>
              </div>
              <div class="card-foot candidate-actions">
                <a-button size="mini" status="danger" type="outline" :loading="loading" @click="removeShortlist(item.id)">
                  取消备选
                </a-button>
                <router-link class="detail-link" :to="`/tasks/${item.id}`">查看详情 →</router-link>
              </div>
            </a-card>
          </div>
        </div>
      </template>
    </a-spin>
  </section>
</template>

<style scoped>
.workbench { max-width: 100%; margin: 0 auto; padding: 8px 0 40px; }
.page-head { align-items: center; margin-bottom: 22px; }
.discover-link { color: #2563eb; font-size: 13px; font-weight: 600; white-space: nowrap; }
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
.role-tab:hover { border-color: #bfdbfe; }
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
.role-tab.active .role-name { color: #1d4ed8; }
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
.role-tabs.compact .role-name { font-size: 13px; white-space: nowrap; }
.role-tabs.compact .role-count {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.task-grid { display: flex; flex-direction: column; gap: 12px; background: transparent; border: 0; border-radius: 0; overflow: visible; }
.task-card-link + .task-card-link { border-top: 0; }
.work-card {
  border: 1px solid #eef2f7 !important;
  border-radius: 16px !important;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  background: #fff;
}
.work-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  background: #fff;
  border-color: #bfdbfe !important;
}
.work-card :deep(.arco-card-body) { padding: 22px 26px; display: grid; grid-template-columns: minmax(0,1fr) auto; column-gap: 24px; }
.card-top { grid-column: 1; }
.work-card :deep(h6) { grid-column: 1; font-size: 17px; line-height: 1.5; font-weight: 700; }
.card-meta { grid-column: 1; display: flex; flex-wrap: wrap; gap: 12px; font-size: 13px; color: #6b7280; line-height: 1.7; }
.card-foot { grid-column: 2; grid-row: 1 / 4; display: flex; align-items: center; font-size: 13px; color: #2563eb; font-weight: 600; }
.candidate-actions { flex-direction: column; justify-content: center; gap: 10px; align-items: flex-end; }
.title-link { color: inherit; text-decoration: none; }
.title-link:hover { color: #2563eb; }
.detail-link { color: #2563eb; font-weight: 600; text-decoration: none; white-space: nowrap; }
.mobile-wb-head {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.workbench-mobile .work-card :deep(.arco-card-body) {
  padding: 16px;
  display: block;
}
.workbench-mobile .card-foot {
  margin-top: 14px;
  justify-content: flex-start;
}
.workbench-mobile .candidate-actions {
  align-items: stretch;
  flex-direction: row;
  flex-wrap: wrap;
}
.workbench-mobile :deep(.arco-tabs-nav-tab) {
  font-size: 13px;
}
@media (max-width: 600px) { .work-card :deep(.arco-card-body) { padding: 18px; display: block; }.card-foot { margin-top: 16px; }.candidate-actions { align-items: flex-start; }.page-head { align-items: flex-start; }.subtitle { max-width: 220px; } }
</style>
