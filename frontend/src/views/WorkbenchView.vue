<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import type { Application, Project, Task } from '../types'

const route = useRoute()
const router = useRouter()
const projects = ref<Project[]>([])
const applications = ref<Application[]>([])
const shortlists = ref<Task[]>([])
const loading = ref(false)

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

const activeProjects = computed(() =>
  projects.value.filter((item) => item.status !== 'TERMINATED'),
)

const orderedProjects = computed(() =>
  [...activeProjects.value].sort((a, b) => Number(b.status === 'ACTIVE') - Number(a.status === 'ACTIVE')),
)

/** 已通过的会出现在「已接项目」，此处只保留待审 / 未通过等 */
const pendingApplications = computed(() =>
  applications.value.filter((item) => item.status !== 'ACCEPTED'),
)

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
  if (key === 'applications') router.push('/me/workbench/applications')
  else if (key === 'candidates') router.push('/me/workbench/candidates')
  else router.push('/me/workbench')
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
</script>

<template>
  <section class="workbench">
    <header class="page-head">
      <div>
        <a-typography-title :heading="4" style="margin: 0">工作台</a-typography-title>
        <p class="subtitle">已接项目、已投申请与备选收藏，分栏查看进度。</p>
      </div>
      <router-link to="/" class="discover-link">探索新项目 ↗</router-link>
    </header>

    <a-tabs class="workspace-tabs" :active-key="tab" type="line" size="large" @change="switchTab">
      <a-tab-pane key="enrolled" :title="`已接项目 · ${activeProjects.length}`" />
      <a-tab-pane key="applications" :title="`已申请 · ${pendingApplications.length}`" />
      <a-tab-pane key="candidates" :title="`备选项目 · ${shortlists.length}`" />
    </a-tabs>

    <a-spin :loading="loading" style="width: 100%; margin-top: 8px">
      <template v-if="tab === 'enrolled'">
        <div v-if="!loading && orderedProjects.length === 0" class="empty-state">
          <a-empty description="还没有已接项目，申请通过入项后会出现在这里" />
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
                  <a-tag color="arcoblue" size="small">{{ item.taskType }}</a-tag>
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
        <div v-if="!loading && pendingApplications.length === 0" class="empty-state">
          <a-empty description="还没有待处理的申请，通过后会进入「已接项目」" />
        </div>
        <div v-else class="task-grid">
          <router-link
            v-for="item in pendingApplications"
            :key="item.id"
            class="task-card-link"
            :to="`/tasks/${item.taskId}`"
          >
            <a-card class="task-card work-card" hoverable>
              <div class="card-top">
                <a-space wrap :size="6">
                  <a-tag color="arcoblue" size="small">{{ item.taskType }}</a-tag>
                  <a-tag :color="applicationStatusColor[item.status] || 'gray'" size="small">
                    {{ applicationStatusLabel[item.status] || item.status }}
                  </a-tag>
                </a-space>
              </div>
              <a-typography-title :heading="6" style="margin: 14px 0 10px">{{ item.taskTitle }}</a-typography-title>
              <div class="card-meta">
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
        <div v-if="!loading && shortlists.length === 0" class="empty-state">
          <a-empty description="暂无备选项目，在任务详情页可点「加入备选」" />
        </div>
        <div v-else class="task-grid">
          <div
            v-for="item in shortlists"
            :key="item.id"
            class="task-card-link"
          >
            <a-card class="task-card work-card" hoverable>
              <div class="card-top">
                <a-space wrap :size="6">
                  <a-tag color="arcoblue" size="small">{{ item.techField }}</a-tag>
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
@media (max-width: 600px) { .work-card :deep(.arco-card-body) { padding: 18px; display: block; }.card-foot { margin-top: 16px; }.candidate-actions { align-items: flex-start; }.page-head { align-items: flex-start; }.subtitle { max-width: 220px; } }
</style>
