<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { auth } from '../auth'
import PathSteps from '../components/PathSteps.vue'
import StatusPill from '../components/StatusPill.vue'
import { formatTime, routeId } from '../format'
import { projectStatusLabel, taskTypeLabel } from '../labels'
import type { Completion, Project, WeeklyReport } from '../types'

const route = useRoute()
const projects = ref<Project[]>([])
const project = ref<Project | null>(null)
const reports = ref<WeeklyReport[]>([])
const completion = ref<Completion | null>(null)
const error = ref('')
const loading = ref(false)
const reportError = ref('')
const reportSaving = ref(false)
const completeError = ref('')
const completeSaving = ref(false)
const weekNumber = ref(1)
const content = ref('本周完成了任务熟悉和第一次答疑 / 功能拆解，下周继续推进。')
const prUrl = ref('https://github.com/keyuwa/whuDeveloper/pull/1')
const summary = ref('按约定完成了本阶段工作，改动见 Pull Request。')

const id = computed(() => routeId(route.params.id))
const isStudentOnProject = computed(
  () => auth.user?.role === 'STUDENT' && project.value?.studentId === auth.user.id,
)
const finished = computed(() => project.value?.status === 'COMPLETED')

async function load() {
  error.value = ''
  loading.value = true
  try {
    if (route.params.id && !id.value) {
      error.value = '项目不存在'
      return
    }
    if (!id.value) {
      projects.value = await api<Project[]>('/api/projects')
      return
    }
    const projectId = id.value
    project.value = await api<Project>(`/api/projects/${projectId}`)
    reports.value = await api<WeeklyReport[]>(`/api/projects/${projectId}/reports`)
    completion.value = (await api<Completion | undefined>(`/api/projects/${projectId}/completion`)) ?? null
    weekNumber.value = reports.value.reduce((max, item) => Math.max(max, item.weekNumber), 0) + 1
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => route.fullPath, load, { immediate: true })

async function submitReport() {
  if (!id.value) return
  reportError.value = ''
  reportSaving.value = true
  try {
    await api(`/api/projects/${id.value}/reports`, {
      method: 'POST',
      body: JSON.stringify({ weekNumber: weekNumber.value, content: content.value }),
    })
    content.value = '本周继续推进约定工作，并记录了遇到的问题。'
    await load()
  } catch (cause) {
    reportError.value = cause instanceof Error ? cause.message : '提交失败'
  } finally {
    reportSaving.value = false
  }
}

async function submitCompletion() {
  if (!id.value) return
  completeError.value = ''
  completeSaving.value = true
  try {
    await api(`/api/projects/${id.value}/completion`, {
      method: 'POST',
      body: JSON.stringify({ prUrl: prUrl.value, summary: summary.value }),
    })
    await load()
  } catch (cause) {
    completeError.value = cause instanceof Error ? cause.message : '结项失败'
  } finally {
    completeSaving.value = false
  }
}

function safeHref(url: string) {
  return url.startsWith('http://') || url.startsWith('https://') ? url : undefined
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">项目</p>
        <h1>{{ id ? '周报与结项' : '我的项目' }}</h1>
      </div>
      <PathSteps :current="id ? 6 : 5" />
    </header>

    <p v-if="error" class="banner">{{ error }}</p>
    <p v-if="loading && (id ? project?.id !== id : projects.length === 0)" class="muted">加载中…</p>

    <div v-if="!id && !(loading && projects.length === 0)">
      <p v-if="projects.length === 0" class="card muted">
        还没有项目。学生在申请被录取后点击「确认入学」，项目会出现在这里。
        <router-link class="text-link" to="/tasks">去看任务</router-link>
      </p>
      <div v-else class="task-grid">
        <router-link v-for="item in projects" :key="item.id" class="task-card" :to="`/projects/${item.id}`">
          <div class="card-top">
            <StatusPill :kind="item.taskType" :label="taskTypeLabel[item.taskType]" />
            <StatusPill :kind="item.status" :label="projectStatusLabel[item.status]" />
          </div>
          <h2>{{ item.taskTitle }}</h2>
          <p class="meta">学生 {{ item.studentName }} · 发布者 {{ item.publisherName }}</p>
          <p class="meta">入学 {{ formatTime(item.enrolledAt) }}</p>
        </router-link>
      </div>
    </div>

    <div v-else-if="project && project.id === id" class="stack">
      <article class="card">
        <div class="card-top">
          <StatusPill :kind="project.taskType" :label="taskTypeLabel[project.taskType]" />
          <StatusPill :kind="project.status" :label="projectStatusLabel[project.status]" />
        </div>
        <h2>{{ project.taskTitle }}</h2>
        <p class="meta">学生 {{ project.studentName }} · 发布者 {{ project.publisherName }}</p>
        <p class="meta">入学 {{ formatTime(project.enrolledAt) }}</p>
        <router-link class="text-link" to="/projects">返回项目列表</router-link>
      </article>

      <article class="card">
        <h2>周报</h2>
        <p v-if="reports.length === 0" class="muted">还没有周报。</p>
        <ol v-else class="reports">
          <li v-for="report in reports" :key="report.id">
            <div class="card-top">
              <strong>第 {{ report.weekNumber }} 周</strong>
              <span class="meta">{{ formatTime(report.createdAt) }}</span>
            </div>
            <p class="prose">{{ report.content }}</p>
          </li>
        </ol>

        <form v-if="isStudentOnProject && !finished" class="inner-form" @submit.prevent="submitReport">
          <h3>提交周报</h3>
          <div class="row">
            <label>
              周次
              <input v-model.number="weekNumber" type="number" min="1" max="30" required />
            </label>
          </div>
          <label>
            内容
            <textarea v-model="content" rows="4" maxlength="4000" required />
          </label>
          <p v-if="reportError" class="banner">{{ reportError }}</p>
          <button class="btn" type="submit" :disabled="reportSaving">
            {{ reportSaving ? '提交中…' : '提交周报' }}
          </button>
        </form>
        <p v-else-if="!isStudentOnProject && !finished" class="muted">周报由入学学生提交，发布者可以在这里查看。</p>
      </article>

      <article class="card">
        <h2>结项</h2>
        <template v-if="completion">
          <p class="prose">{{ completion.summary }}</p>
          <p class="meta">{{ formatTime(completion.createdAt) }}</p>
          <a class="pr" :href="safeHref(completion.prUrl)" target="_blank" rel="noopener noreferrer">
            {{ completion.prUrl }}
          </a>
        </template>
        <form v-else-if="isStudentOnProject" class="inner-form" @submit.prevent="submitCompletion">
          <p class="muted">至少提交一份周报后，附上 Pull Request 链接即可结项。</p>
          <label>
            PR 链接
            <input v-model="prUrl" type="url" required />
          </label>
          <label>
            结项说明
            <textarea v-model="summary" rows="4" maxlength="1000" required />
          </label>
          <p v-if="completeError" class="banner">{{ completeError }}</p>
          <button class="btn" type="submit" :disabled="completeSaving || reports.length === 0">
            {{ completeSaving ? '提交中…' : '提交结项' }}
          </button>
        </form>
        <p v-else class="muted">学生提交结项后，PR 链接会显示在这里。</p>
      </article>
    </div>
  </section>
</template>
