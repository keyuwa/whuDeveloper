<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { api } from '../api'
import { useIsMobile } from '../composables/useDevice'
import { TASK_ROLE_LABEL, type Project, type Task } from '../types'

const router = useRouter()
const isMobile = useIsMobile()
const tasks = ref<Task[]>([])
const projects = ref<Project[]>([])
const loading = ref(false)
const statusTab = ref<'ALL' | 'OPEN' | 'DRAFT' | 'CLOSED'>('ALL')

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

const statusCounts = computed(() => {
  const counts = { ALL: tasks.value.length, OPEN: 0, DRAFT: 0, CLOSED: 0 }
  for (const task of tasks.value) {
    if (task.status === 'OPEN') counts.OPEN += 1
    else if (task.status === 'DRAFT') counts.DRAFT += 1
    else if (task.status === 'CLOSED') counts.CLOSED += 1
  }
  return counts
})

const filteredTasks = computed(() => {
  if (statusTab.value === 'ALL') return tasks.value
  return tasks.value.filter((task) => task.status === statusTab.value)
})

const emptyDescription = computed(() => {
  if (statusTab.value === 'OPEN') return '暂无已发布任务'
  if (statusTab.value === 'DRAFT') return '暂无草稿'
  if (statusTab.value === 'CLOSED') return '暂无已下架任务'
  return '还没有任务'
})

async function load() {
  const [taskList, projectList] = await Promise.all([
    api<Task[]>('/api/v1/publisher/tasks'),
    api<Project[]>('/api/v1/projects'),
  ])
  tasks.value = taskList
  projects.value = projectList
}

onMounted(async () => {
  loading.value = true
  try {
    await load()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loading.value = false
  }
})

async function act(id: number, action: 'publish' | 'close') {
  try {
    await api(`/api/v1/publisher/tasks/${id}/${action}`, { method: 'POST' })
    await load()
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
        await load()
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
        <a-button type="primary" size="small" @click="router.push('/publisher/tasks/new')">
          {{ isMobile ? '新建任务' : '发布任务' }}
        </a-button>
      </a-space>
    </header>

    <a-tabs
      v-model:active-key="statusTab"
      type="rounded"
      :size="isMobile ? 'medium' : 'large'"
      class="status-tabs"
    >
      <a-tab-pane key="ALL" :title="`全部 · ${statusCounts.ALL}`" />
      <a-tab-pane key="OPEN" :title="`已发布 · ${statusCounts.OPEN}`" />
      <a-tab-pane key="DRAFT" :title="`草稿 · ${statusCounts.DRAFT}`" />
      <a-tab-pane key="CLOSED" :title="`已下架 · ${statusCounts.CLOSED}`" />
    </a-tabs>

    <a-spin :loading="loading" style="width: 100%">
      <a-empty v-if="!loading && filteredTasks.length === 0" :description="emptyDescription" />
      <div v-else class="stack">
        <a-card v-for="task in filteredTasks" :key="task.id" class="task-card">
          <div class="task-row">
            <div class="task-main">
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
                · {{ task.programmingLanguages }} · {{ task.durationWeeks }} 周
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
            <a-space wrap>
              <a-button
                v-if="(task.pendingApplications || 0) > 0 || task.filled > 0"
                type="outline"
                status="warning"
                @click="openInbox(task.id)"
              >
                {{ (task.pendingApplications || 0) > 0 ? `审阅申请 (${task.pendingApplications})` : '查看申请' }}
              </a-button>
              <a-button @click="router.push(`/publisher/tasks/${task.id}`)">编辑</a-button>
              <a-button v-if="task.status !== 'OPEN'" type="primary" @click="act(task.id, 'publish')">发布</a-button>
              <a-button v-else @click="act(task.id, 'close')">下架</a-button>
              <a-button v-if="canDelete(task)" type="outline" status="danger" @click="removeTask(task)">
                删除
              </a-button>
            </a-space>
          </div>
        </a-card>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.status-tabs {
  margin: 4px 0 14px;
}
.task-card { border: 1px solid #eef2f7; width: 100%; }
.task-card :deep(.arco-card-body) { width: 100%; }
.task-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  flex-wrap: wrap;
  width: 100%;
}
.task-main { min-width: 0; flex: 1; width: 100%; }
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
.publisher-mobile .task-row {
  flex-direction: column;
  align-items: stretch;
}
.publisher-mobile .task-main {
  width: 100%;
  max-width: 100%;
  flex: none;
}
.publisher-mobile .enrolled {
  margin-top: 12px;
  width: 100%;
}
@media (max-width: 700px) {
  .task-row {
    flex-direction: column;
    align-items: stretch;
  }
  .task-main {
    width: 100%;
    flex: none;
  }
}
</style>
