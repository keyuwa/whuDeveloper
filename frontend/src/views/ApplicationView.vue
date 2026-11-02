<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { auth } from '../auth'
import PathSteps from '../components/PathSteps.vue'
import StatusPill from '../components/StatusPill.vue'
import { formatTime, routeId } from '../format'
import { applicationStatusLabel, taskTypeLabel } from '../labels'
import type { Application, Project, Task, TaskType } from '../types'

const route = useRoute()
const router = useRouter()
const task = ref<Task | null>(null)
const applications = ref<Application[]>([])
const proposal = ref('')
const error = ref('')
const formError = ref('')
const loading = ref(false)
const saving = ref(false)

const id = computed(() => routeId(route.params.id))
const isStudent = computed(() => auth.user?.role === 'STUDENT')
const mine = computed(() => task.value?.myApplication ?? null)

const step = computed(() => {
  if (mine.value?.projectId || applications.value.some((item) => item.projectId)) return 6
  if (mine.value?.status === 'ACCEPTED' || applications.value.some((item) => item.status === 'ACCEPTED')) return 5
  if (mine.value || applications.value.length > 0) return 4
  return 3
})

function preset(type: TaskType) {
  if (type === 'TA') {
    return '我修过数据结构，熟悉线性表、树和图。可以每周固定时段答疑，并按时完成作业批改。'
  }
  return '我熟悉 Vue 3 和 TypeScript，希望参与门户的一个小功能。会按周同步进展，并在结项时附上 Pull Request。'
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    if (!id.value) {
      error.value = '任务不存在'
      return
    }
    task.value = await api<Task>(`/api/tasks/${id.value}`)
    if (!isStudent.value) {
      applications.value = await api<Application[]>(`/api/tasks/${id.value}/applications`)
    } else if (!task.value.myApplication && proposal.value.length === 0) {
      proposal.value = preset(task.value.type)
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => route.fullPath, load, { immediate: true })

async function submitProposal() {
  if (!id.value) return
  formError.value = ''
  saving.value = true
  try {
    await api(`/api/tasks/${id.value}/applications`, {
      method: 'POST',
      body: JSON.stringify({ proposal: proposal.value }),
    })
    await load()
  } catch (cause) {
    formError.value = cause instanceof Error ? cause.message : '提交失败'
  } finally {
    saving.value = false
  }
}

async function accept(applicationId: number) {
  saving.value = true
  formError.value = ''
  try {
    await api(`/api/applications/${applicationId}/accept`, { method: 'POST' })
    await load()
  } catch (cause) {
    formError.value = cause instanceof Error ? cause.message : '录取失败'
  } finally {
    saving.value = false
  }
}

async function enroll(applicationId: number) {
  saving.value = true
  formError.value = ''
  try {
    const project = await api<Project>(`/api/applications/${applicationId}/enroll`, { method: 'POST' })
    await router.push(`/projects/${project.id}`)
  } catch (cause) {
    formError.value = cause instanceof Error ? cause.message : '入学失败'
    saving.value = false
  }
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">申请</p>
        <h1>{{ isStudent ? '提交申请' : '审核申请' }}</h1>
      </div>
      <PathSteps :current="step" />
    </header>

    <p v-if="error" class="banner">{{ error }}</p>
    <p v-else-if="loading && !task" class="muted">加载中…</p>

    <div v-else-if="task" class="split">
      <article class="card">
        <StatusPill :kind="task.type" :label="taskTypeLabel[task.type]" />
        <h2>{{ task.title }}</h2>
        <p class="meta">发布者 {{ task.publisherName }}</p>
        <p class="prose">{{ task.description }}</p>
        <router-link class="text-link" :to="`/tasks/${task.id}`">返回任务详情</router-link>
      </article>

      <div class="stack">
        <p v-if="formError" class="banner">{{ formError }}</p>

        <form v-if="isStudent && !mine" class="card" @submit.prevent="submitProposal">
          <h2>申请说明</h2>
          <p class="muted">说明你为什么适合这个任务。演示文案已填好，可以直接提交。</p>
          <label>
            说明
            <textarea v-model="proposal" rows="7" maxlength="2000" required />
          </label>
          <button class="btn" type="submit" :disabled="saving">{{ saving ? '提交中…' : '提交申请' }}</button>
        </form>

        <article v-else-if="isStudent && mine" class="card">
          <div class="card-top">
            <h2>我的申请</h2>
            <StatusPill :kind="mine.status" :label="applicationStatusLabel[mine.status]" />
          </div>
          <p class="prose">{{ mine.proposal }}</p>
          <p v-if="mine.status === 'SUBMITTED'" class="muted">
            已提交。请退出后使用发布者账号 publisher / publisher123 登录并录取。
          </p>
          <button
            v-else-if="!mine.projectId"
            type="button"
            class="btn"
            :disabled="saving"
            @click="enroll(mine.id)"
          >
            {{ saving ? '入学中…' : '确认入学' }}
          </button>
          <router-link v-else class="btn" :to="`/projects/${mine.projectId}`">进入项目</router-link>
        </article>

        <article v-else class="card">
          <h2>申请列表</h2>
          <p v-if="applications.length === 0" class="muted">还没有学生申请。请用学生账号提交一份申请后再回来。</p>
          <ul v-else class="people">
            <li v-for="item in applications" :key="item.id">
              <div class="card-top">
                <strong>{{ item.studentName }}</strong>
                <StatusPill :kind="item.status" :label="applicationStatusLabel[item.status]" />
              </div>
              <p class="prose">{{ item.proposal }}</p>
              <p class="meta">{{ formatTime(item.createdAt) }}</p>
              <button
                v-if="item.status === 'SUBMITTED'"
                type="button"
                class="btn"
                :disabled="saving"
                @click="accept(item.id)"
              >
                录取
              </button>
              <p v-else-if="!item.projectId" class="muted">已录取，等待学生确认入学。</p>
              <router-link v-else class="btn secondary" :to="`/projects/${item.projectId}`">查看项目</router-link>
            </li>
          </ul>
        </article>
      </div>
    </div>
  </section>
</template>
