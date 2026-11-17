<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import WorkbenchProjectPanel from '../components/WorkbenchProjectPanel.vue'
import { useIsMobile } from '../composables/useDevice'
import { TASK_ROLE_LABEL, TASK_ROLE_TYPES, type Application, type Project, type Task } from '../types'

const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const projects = ref<Project[]>([])
const applications = ref<Application[]>([])
const shortlists = ref<Task[]>([])
const loading = ref(false)
const error = ref('')
const selected = ref<Project | null>(null)
const panelTab = ref('detail')
const roleTab = computed(() => String(route.query.roleType || ''))
const search = computed(() => String(route.query.q || ''))
const tab = computed(() => route.path.includes('/applications') ? 'applications' : route.path.includes('/candidates') ? 'candidates' : route.query.view === 'ended' ? 'ended' : 'enrolled')
function filterQuery(key: string, value: string) {
  void router.replace({ query: { ...route.query, [key]: value || undefined } })
}
function matches(title: string, role?: string) {
  return (!roleTab.value || role === roleTab.value) && title.toLowerCase().includes(search.value.trim().toLowerCase())
}
const activeProjects = computed(() => projects.value.filter(item => ['ACTIVE', 'COMPLETING'].includes(item.status)))
const endedProjects = computed(() => projects.value.filter(item => ['COMPLETED', 'TERMINATED'].includes(item.status)))
const orderedProjects = computed(() => (tab.value === 'ended' ? endedProjects.value : activeProjects.value).filter(item => matches(item.taskTitle, item.taskRoleType)))
const filteredApplications = computed(() => applications.value.filter(item => matches(item.taskTitle, item.taskRoleType)))
const shortlistStatusLabel: Record<string, string> = {
  OPEN: '已发布',
  CLOSED: '已撤下',
  DRAFT: '草稿',
}
const filteredShortlists = computed(() => shortlists.value.filter(item => matches(item.title, item.roleType)))
const resultCount = computed(() => tab.value === 'applications' ? filteredApplications.value.length : tab.value === 'candidates' ? filteredShortlists.value.length : orderedProjects.value.length)
function openProject(item: Project, section = 'detail') {
  if (isMobile.value) void router.push(`/me/projects/${item.id}/${section}`)
  else { selected.value = item; panelTab.value = section }
}
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

async function load() {
  loading.value = true
  error.value = ''
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
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function switchTab(key: string | number) {
  selected.value = null
  const path = key === 'applications' ? '/me/workbench/applications' : key === 'candidates' ? '/me/workbench/candidates' : '/me/workbench'
  void router.push({ path, query: { ...route.query, view: key === 'ended' ? 'ended' : undefined } })
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

onMounted(load)
watch(isMobile, () => { selected.value = null })
</script>

<template>
  <section class="workbench">
    <header class="wb-heading">
      <div><h1>我的工作台</h1><p>项目进展与申请，一处处理。</p></div>
      <router-link to="/plaza">探索项目</router-link>
    </header>
    <div class="wb-surface">
      <div class="wb-navigation">
        <a-tabs :active-key="tab === 'candidates' ? '' : tab" @change="switchTab">
          <a-tab-pane key="enrolled" :title="`已接项目 ${activeProjects.length}`" />
          <a-tab-pane key="applications" :title="`我的申请 ${applications.length}`" />
          <a-tab-pane key="ended" :title="`已结束 ${endedProjects.length}`" />
        </a-tabs>
        <button class="saved-link" :class="{ selected: tab === 'candidates' }" @click="switchTab('candidates')">备选 {{ shortlists.length }}</button>
      </div>
      <div class="wb-toolbar">
        <a-input :model-value="search" placeholder="搜索项目名称" allow-clear aria-label="搜索项目名称" @update:model-value="filterQuery('q', $event)" />
        <a-select :model-value="roleTab" aria-label="频道筛选" @update:model-value="filterQuery('roleType', String($event))">
          <a-option value="">全部</a-option>
          <a-option v-for="role in TASK_ROLE_TYPES" :key="role.value" :value="role.value">{{ role.label }}</a-option>
        </a-select>
        <span v-if="!isMobile" class="result-count">{{ resultCount }} 个结果</span>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
      <a-alert v-if="error" type="error">{{ error }}，请点击刷新重试。</a-alert>
      <a-spin :loading="loading" style="width:100%">
        <div v-if="!isMobile" class="wb-columns"><span>项目 / 频道</span><span>发布方</span><span>状态</span><span>操作</span></div>
        <div v-if="!error && !loading && !resultCount" class="wb-empty">
          <a-empty :description="search || roleTab ? '没有符合筛选条件的项目' : tab === 'ended' ? '还没有已结束的项目' : tab === 'applications' ? '还没有申请记录' : tab === 'candidates' ? '在项目详情页加入备选，方便稍后申请' : '申请通过入项后，项目会出现在这里'" />
          <a-button v-if="search || roleTab" @click="router.replace({ query: { view: route.query.view } })">清除筛选</a-button>
          <router-link v-else to="/plaza">去探索项目</router-link>
        </div>
        <template v-else-if="tab === 'enrolled' || tab === 'ended'">

          <article v-for="item in orderedProjects" :key="item.id" class="wb-row">
            <div class="project-name"><button @click="openProject(item)">{{ item.taskTitle }}</button><span>{{ TASK_ROLE_LABEL[item.taskRoleType || ''] || '项目' }}</span></div>
            <div class="publisher"><span v-if="isMobile">发布方 </span>{{ item.publisherName }}</div>
            <div class="status"><a-tag :color="projectStatusColor[item.status]">{{ projectStatusLabel[item.status] || item.status }}</a-tag></div>
            <div class="row-actions"><a-button v-if="item.status === 'ACTIVE'" type="primary" @click="openProject(item, 'progress')">提交周报</a-button><a-button v-else type="primary" @click="openProject(item, 'completion')">查看结项</a-button><a-button type="text" @click="openProject(item)">详情</a-button></div>
          </article>
        </template>
        <template v-else-if="tab === 'applications'">
          <article v-for="item in filteredApplications" :key="item.id" class="wb-row">
            <div class="project-name"><router-link :to="{path: `/tasks/${item.taskId}`, query: {from: 'workbench', tab: 'applications'}}">{{ item.taskTitle }}</router-link><span>{{ TASK_ROLE_LABEL[item.taskRoleType || ''] }}<template v-if="item.positionTitle"> · {{ item.positionTitle }}</template></span><p v-if="item.reviewNote" class="review-note">{{ item.reviewNote }}</p></div>
            <div class="publisher"><span v-if="isMobile">发布方 </span>{{ item.publisherName || '—' }}</div>
            <div class="status"><a-tag :color="applicationStatusColor[item.status]">{{ applicationStatusLabel[item.status] || item.status }}</a-tag></div>
            <div class="row-actions"><router-link :to="{path: `/tasks/${item.taskId}`, query: {from: 'workbench', tab: 'applications'}}">查看申请</router-link></div>
          </article>
        </template>
        <template v-else>
          <article v-for="item in filteredShortlists" :key="item.id" class="wb-row">
            <div class="project-name"><router-link :to="`/tasks/${item.id}`">{{ item.title }}</router-link><span>{{ TASK_ROLE_LABEL[item.roleType] || item.roleType }}</span></div>
            <div class="publisher"><span v-if="isMobile">发布方 </span>{{ item.publisherName || '—' }}</div>
            <div class="status"><a-tag :color="item.status === 'OPEN' ? 'green' : 'gray'">{{ shortlistStatusLabel[item.status] || item.status }}</a-tag></div>
            <div class="row-actions"><router-link :to="{path: `/tasks/${item.id}`, query: {from: 'workbench', tab: 'candidates'}}">查看项目</router-link><a-button type="text" :disabled="loading" @click="removeShortlist(item.id)">取消备选</a-button></div>
          </article>
        </template>
      </a-spin>
    </div>
    <a-drawer :visible="!!selected && !isMobile" :width="'min(860px, 85vw)'" :footer="false" unmount-on-close @cancel="selected = null">
      <template #title>
        <div class="project-drawer-heading">
          <span>{{ selected?.taskTitle }}</span>
          <router-link v-if="selected" :to="`/me/projects/${selected.id}/${panelTab}`">打开完整项目</router-link>
        </div>
      </template>
      <WorkbenchProjectPanel v-if="selected" :key="selected.id" :project-id="selected.id" :initial-tab="panelTab" @tab-change="panelTab = $event" @updated="load" />
    </a-drawer>
  </section>
</template>

<style scoped>
.project-drawer-heading { display: flex; align-items: center; justify-content: space-between; gap: 24px; width: 100%; padding-right: 16px; box-sizing: border-box; }
.project-drawer-heading > span { min-width: 0; overflow-wrap: anywhere; }
.project-drawer-heading > a { flex-shrink: 0; color: #2563eb; font-size: 13px; font-weight: 500; }
.workbench { --wb-line: #e5eaf1; --wb-muted: #64748b; padding: 8px 0 32px; }
.wb-heading { display:flex; justify-content:space-between; align-items:center; margin-bottom:24px; gap:16px; }
.wb-heading h1 { font-size:24px; margin:0 0 6px; color:#172033; }
.wb-heading p { color:var(--wb-muted); margin:0; font-size:14px; }
.workbench a { color:#2563eb; }
.wb-surface { background:white; border:1px solid var(--wb-line); border-radius:12px; overflow:hidden; }
.wb-navigation { display:flex; align-items:center; padding:0 24px; border-bottom:1px solid var(--wb-line); gap:16px; }
.wb-navigation :deep(.arco-tabs) { flex:1; min-width:0; }
.wb-navigation :deep(.arco-tabs-nav::before) { display:none; }
.wb-navigation :deep(.arco-tabs-content) { display:none; }
.wb-navigation :deep(.arco-tabs-tab) { padding:20px 0; }
.saved-link { border:0; background:none; font:inherit; color:var(--wb-muted); cursor:pointer; padding:12px 0; white-space:nowrap; }
.saved-link.selected { color:#2563eb; font-weight:600; }
.wb-toolbar { display:flex; align-items:center; gap:12px; padding:20px 24px; }
.wb-toolbar :deep(.arco-input-wrapper) { max-width:300px; }
.wb-toolbar :deep(.arco-select) { width:150px; flex-shrink:0; }
.result-count { color:var(--wb-muted); font-size:13px; margin-left:auto; white-space:nowrap; }
.wb-row,.wb-columns { display:grid; grid-template-columns:minmax(0,1fr) 140px 130px 190px; gap:20px; align-items:center; padding:22px 24px; }
.wb-columns { background:#f8fafc; padding-top:12px; padding-bottom:12px; font-size:12px; color:var(--wb-muted); }
.wb-row + .wb-row { border-top:1px solid var(--wb-line); }
.wb-row:hover { background:#fbfcfe; }
.project-name { min-width:0; display:flex; flex-direction:column; gap:8px; }
.project-name button,.project-name > a { font:inherit; font-weight:600; font-size:15px; text-align:left; color:#172033; border:0; background:none; padding:0; cursor:pointer; line-height:1.6; overflow-wrap:anywhere; }
.project-name button:hover,.project-name > a:hover { color:#2563eb; }
.project-name > span,.publisher,.review-note { font-size:13px; color:var(--wb-muted); line-height:1.6; }
.review-note { margin:0; }
.row-actions { display:flex; align-items:center; gap:8px; justify-content:flex-end; }
@media (min-width:769px) {
  .wb-columns > :last-child { text-align:center; }
  .wb-row .row-actions { justify-content:center; }
}
.wb-empty { padding:56px 16px; text-align:center; display:grid; gap:20px; justify-items:center; }
button:focus-visible,a:focus-visible { outline:2px solid #2563eb; outline-offset:4px; }
@media (min-width:769px) and (max-width:1100px) { .wb-row,.wb-columns { grid-template-columns:minmax(0,1fr) 90px 100px 160px; gap:12px; padding-left:16px; padding-right:16px; }}
@media (max-width:768px) {
  .wb-heading { margin-bottom:18px; }.wb-heading h1 { font-size:20px; }.wb-heading p { font-size:12px; }.wb-heading > a { font-size:13px; white-space:nowrap; }
  .wb-surface { background:transparent; border:0; overflow:visible; }
  .wb-navigation { padding:0; gap:10px; }.wb-navigation :deep(.arco-tabs-tab) { margin:0 14px 0 0; padding:14px 0; font-size:13px; }.saved-link { font-size:12px; }
  .wb-toolbar { padding:16px 0; gap:8px; flex-wrap:wrap; }.wb-toolbar :deep(.arco-input-wrapper) { flex:1; max-width:none; min-width:100px; }.wb-toolbar :deep(.arco-select) { width:108px; }.wb-toolbar > :deep(.arco-btn) { padding:0 10px; }
  .wb-row { position:relative; display:grid; grid-template-columns:minmax(0,1fr) auto; gap:10px; padding:18px 16px; background:white; border:1px solid var(--wb-line); border-radius:10px; margin-bottom:12px; }
  .project-name { grid-column:1 / -1; }.publisher { grid-column:1; }.status { grid-column:2; grid-row:2; }.row-actions { grid-column:1 / -1; border-top:1px solid #f0f3f7; padding-top:12px; margin-top:2px; }.row-actions :deep(.arco-btn) { min-height:40px; }.row-actions > a { padding:10px 0; }.project-name > span { font-size:12px; }.application-row .status { grid-column:1 / -1; justify-self:start; }
}
</style>
