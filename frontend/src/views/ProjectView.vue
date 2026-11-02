<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { auth } from '../auth'
import type { Project } from '../types'
import { routeId } from '../format'

const route = useRoute()
const project = ref<Project | null>(null)
const error = ref('')
const note = ref('本阶段已对齐目标。')
const deliverable = ref('')
const weekIndex = ref(1)
const content = ref('本周完成了约定工作，并记录了问题。')
const plan = ref('下周继续推进。')
const score = ref(4)
const comment = ref('进展清楚。')
const reportMd = ref('## 结项报告\n按任务要求完成交付。')
const prUrl = ref('https://github.com/keyuwa/whuDeveloper/pull/1')
const loading = ref(false)
const id = computed(() => routeId(route.params.id))
const isStudent = computed(() => auth.user?.role === 'STUDENT' && project.value?.studentId === auth.user.id)
const isPublisher = computed(() => auth.user?.role === 'PUBLISHER' && project.value?.publisherId === auth.user.id)
const currentMilestone = computed(() => project.value?.milestones.find((item) => item.status === 'IN_PROGRESS' || item.status === 'REJECTED'))

async function load() {
  if (!id.value) return
  project.value = await api<Project>(`/api/v1/projects/${id.value}`)
  const maxWeek = project.value.reports.reduce((max, item) => Math.max(max, item.weekIndex), 0)
  weekIndex.value = maxWeek + 1
}

watch(() => route.fullPath, () => { error.value = ''; load().catch((cause) => { error.value = cause instanceof Error ? cause.message : '加载失败' }) }, { immediate: true })

async function run(path: string, body: unknown) {
  loading.value = true
  error.value = ''
  try {
    project.value = await api<Project>(path, { method: 'POST', body: JSON.stringify(body) })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '操作失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section v-if="project" class="stack">
    <header class="page-head">
      <div>
        <p class="kicker">Workspace</p>
        <h1>{{ project.taskTitle }}</h1>
        <p class="muted">{{ project.studentName }} {{ project.abilityLevel }} · {{ project.publisherName }}</p>
      </div>
      <span class="pill accent">{{ project.status }}</span>
    </header>
    <p v-if="error" class="banner">{{ error }}</p>
    <article class="card stack">
      <h2>节点</h2>
      <div v-for="item in project.milestones" :key="item.id" class="row">
        <div>
          <strong>{{ item.seq }}. {{ item.title }}</strong>
          <p class="muted">{{ item.status }} <span v-if="item.note">· {{ item.note }}</span></p>
        </div>
        <div class="actions" v-if="isPublisher && item.status === 'SUBMITTED'">
          <button class="btn primary" type="button" :disabled="loading" @click="run(`/api/v1/projects/${project.id}/milestones/${item.id}/review`, { decision: 'APPROVE' })">通过</button>
          <button class="btn" type="button" :disabled="loading" @click="run(`/api/v1/projects/${project.id}/milestones/${item.id}/review`, { decision: 'REJECT', note: '请补充' })">驳回</button>
        </div>
      </div>
      <form v-if="isStudent && currentMilestone" class="form" @submit.prevent="run(`/api/v1/projects/${project.id}/milestones/${currentMilestone.id}/submit`, { note, deliverableUrl: deliverable })">
        <label>节点说明<textarea v-model="note" required /></label>
        <label>交付链接<input v-model="deliverable" /></label>
        <button class="btn primary" :disabled="loading">提交当前节点</button>
      </form>
    </article>
    <article class="card stack">
      <h2>周报</h2>
      <div v-for="item in project.reports" :key="item.id">
        <div class="row">
          <strong>第 {{ item.weekIndex }} 周</strong>
          <span class="pill">{{ item.status }}<span v-if="item.score"> · {{ item.score }}/5</span></span>
        </div>
        <p>{{ item.content }}</p>
        <form v-if="isPublisher && item.status === 'SUBMITTED'" class="actions" @submit.prevent="run(`/api/v1/projects/${project.id}/reports/${item.id}/score`, { score, comment })">
          <input v-model.number="score" type="number" min="1" max="5" style="width:80px" />
          <input v-model="comment" placeholder="评语" />
          <button class="btn primary" :disabled="loading">评分</button>
        </form>
      </div>
      <form v-if="isStudent && project.status !== 'COMPLETED'" class="form" @submit.prevent="run(`/api/v1/projects/${project.id}/reports`, { weekIndex, content, plan })">
        <label>周次<input v-model.number="weekIndex" type="number" min="1" max="30" /></label>
        <label>本周工作<textarea v-model="content" required /></label>
        <label>下周计划<textarea v-model="plan" /></label>
        <button class="btn primary" :disabled="loading">提交周报</button>
      </form>
    </article>
    <article class="card stack">
      <h2>结项</h2>
      <template v-if="project.completion">
        <span class="pill accent">{{ project.completion.status }}</span>
        <pre class="md">{{ project.completion.reportMd }}</pre>
        <a v-for="url in project.completion.prUrls" :key="url" :href="url" target="_blank" rel="noopener">{{ url }}</a>
        <p v-if="project.completion.reviewNote">{{ project.completion.reviewNote }}</p>
        <div v-if="isPublisher && project.completion.status === 'SUBMITTED'" class="actions">
          <button class="btn primary" type="button" :disabled="loading" @click="run(`/api/v1/projects/${project.id}/completion/review`, { decision: 'APPROVE', note: '通过' })">通过结项</button>
          <button class="btn" type="button" :disabled="loading" @click="run(`/api/v1/projects/${project.id}/completion/review`, { decision: 'REJECT', note: '请补充材料' })">驳回</button>
        </div>
      </template>
      <form v-if="isStudent && project.completion?.status !== 'APPROVED'" class="form" @submit.prevent="run(`/api/v1/projects/${project.id}/completion`, { reportMd, prUrls: prUrl.split(/\s+/).filter(Boolean) })">
        <label>结项报告<textarea v-model="reportMd" required /></label>
        <label>PR 链接，多个用空格分隔<input v-model="prUrl" required /></label>
        <button class="btn primary" :disabled="loading || project.reports.length === 0">提交结项</button>
      </form>
    </article>
  </section>
</template>
