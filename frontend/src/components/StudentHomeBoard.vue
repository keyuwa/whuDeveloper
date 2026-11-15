<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import { auth } from '../auth'
import { majorScore } from '../majorMatch'
import { TASK_ROLE_LABEL, type Project, type Task, type TaskPage } from '../types'

const projects = ref<Project[]>([])
const tasks = ref<Task[]>([])
const loading = ref(false)

const major = computed(() => (auth.user?.role === 'STUDENT' ? auth.user.major || '' : '').trim())

const projectStatus: Record<string, string> = {
  ACTIVE: '进行中',
  COMPLETING: '结项待验收',
  COMPLETED: '已结项',
}

const milestoneStatus: Record<string, string> = {
  PENDING: '未开始',
  IN_PROGRESS: '进行中',
  SUBMITTED: '待确认',
  APPROVED: '已完成',
  REJECTED: '需修改',
}

const progress = computed(() =>
  projects.value.filter((item) => item.status === 'ACTIVE' || item.status === 'COMPLETING'),
)

const recommended = computed(() => {
  const ranked = tasks.value
    .map((task) => ({ task, score: major.value ? majorScore(major.value, task) : 0 }))
    .filter((item) => item.score > 0)
    .sort((a, b) => b.score - a.score)
  return ranked.slice(0, 4).map((item) => item.task)
})

function progressLine(item: Project) {
  const reports = [...(item.reports || [])].sort((a, b) => b.weekIndex - a.weekIndex)
  const latest = reports[0]
  if (latest) {
    return `第 ${latest.weekIndex} 周周报 · ${latest.status === 'SCORED' ? '已反馈' : '待反馈'}`
  }
  const current = (item.milestones || []).find((milestone) => milestone.status !== 'APPROVED')
  if (current) {
    return `${current.title} · ${milestoneStatus[current.status] || current.status}`
  }
  return '尚未提交周报'
}

onMounted(async () => {
  if (!auth.token || auth.user?.role !== 'STUDENT') return
  loading.value = true
  try {
    const [mine, page] = await Promise.all([
      api<Project[]>('/api/v1/projects'),
      api<TaskPage>('/api/v1/tasks?page=0&size=50'),
    ])
    projects.value = mine
    tasks.value = page.items
  } catch {
    projects.value = []
    tasks.value = []
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="student-board" aria-label="学生主页">
    <div>
      <h2>目前进展</h2>
      <p v-if="loading" class="muted">正在读取协作进度</p>
      <ul v-else-if="progress.length" class="rows">
        <li v-for="item in progress" :key="item.id">
          <router-link :to="`/me/projects/${item.id}/progress`">
            <strong>{{ item.taskTitle }}</strong>
            <span>{{ TASK_ROLE_LABEL[item.taskRoleType || ''] || '协作' }} · {{ projectStatus[item.status] || item.status }}</span>
            <span>{{ progressLine(item) }}</span>
          </router-link>
        </li>
      </ul>
      <p v-else class="muted">还没有进行中的协作。去任务广场看看合适的任务。</p>
    </div>
    <div>
      <h2>为你推荐</h2>
      <p v-if="!major" class="muted">简历里还没有专业，补上之后推荐会更准</p>
      <ul v-if="recommended.length" class="rows">
        <li v-for="task in recommended" :key="task.id">
          <router-link :to="`/tasks/${task.id}`">
            <strong>{{ task.title }}</strong>
            <span>{{ TASK_ROLE_LABEL[task.roleType] || task.roleType }} · {{ task.techField }}</span>
            <span>{{ task.stipendText }} · {{ task.publisherName }}</span>
          </router-link>
        </li>
      </ul>
      <p v-else-if="!loading" class="muted">当前开放任务里还没有和这个专业直接对应的内容。</p>
    </div>
  </section>
</template>

<style scoped>
.student-board {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin: 8px 0 22px;
}
.student-board h2 {
  margin: 0 0 8px;
  font-size: 18px;
  color: #111827;
}
.muted {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}
.rows {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
}
.rows a {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 14px;
  color: inherit;
  border-bottom: 1px solid #f1f5f9;
}
.rows li:last-child a { border-bottom: 0; }
.rows a:hover { background: #f8fbff; }
.rows strong {
  font-size: 14px;
  color: #111827;
  overflow-wrap: anywhere;
}
.rows span {
  font-size: 12px;
  color: #64748b;
}
@media (max-width: 800px) {
  .student-board { grid-template-columns: 1fr; }
}
</style>
