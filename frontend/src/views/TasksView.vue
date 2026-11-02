<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { auth } from '../auth'
import PathSteps from '../components/PathSteps.vue'
import StatusPill from '../components/StatusPill.vue'
import { formatTime, routeId } from '../format'
import { applicationStatusLabel, taskTypeLabel } from '../labels'
import type { Task } from '../types'

const route = useRoute()
const router = useRouter()
const tasks = ref<Task[]>([])
const task = ref<Task | null>(null)
const error = ref('')
const loading = ref(false)

const id = computed(() => routeId(route.params.id))
const isStudent = computed(() => auth.user?.role === 'STUDENT')

const actionLabel = computed(() => {
  if (!task.value) return ''
  if (!isStudent.value) return '审核申请'
  return task.value.myApplication ? '查看申请' : '填写申请'
})

async function load() {
  error.value = ''
  loading.value = true
  try {
    if (route.params.id && !id.value) {
      error.value = '任务不存在'
      return
    }
    if (id.value) {
      task.value = await api<Task>(`/api/tasks/${id.value}`)
    } else {
      tasks.value = await api<Task[]>('/api/tasks')
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => route.fullPath, load, { immediate: true })

function openApplication() {
  if (!task.value) return
  router.push(`/tasks/${task.value.id}/application`)
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">任务</p>
        <h1>{{ id ? '任务详情' : '开放任务' }}</h1>
      </div>
      <PathSteps :current="2" />
    </header>

    <p v-if="error" class="banner">{{ error }}</p>
    <p v-if="loading && (id ? task?.id !== id : tasks.length === 0)" class="muted">加载中…</p>

    <div v-if="!id && !(loading && tasks.length === 0)" class="task-grid">
      <router-link v-for="item in tasks" :key="item.id" class="task-card" :to="`/tasks/${item.id}`">
        <div class="card-top">
          <StatusPill :kind="item.type" :label="taskTypeLabel[item.type]" />
          <StatusPill
            v-if="item.myApplication"
            :kind="item.myApplication.status"
            :label="applicationStatusLabel[item.myApplication.status]"
          />
        </div>
        <h2>{{ item.title }}</h2>
        <p class="clamp">{{ item.description }}</p>
        <p class="meta">发布者 {{ item.publisherName }}</p>
      </router-link>
      <p v-if="tasks.length === 0" class="muted">还没有任务。</p>
    </div>

    <article v-else-if="task && task.id === id" class="card detail">
      <div class="card-top">
        <StatusPill :kind="task.type" :label="taskTypeLabel[task.type]" />
        <span class="meta">{{ formatTime(task.createdAt) }}</span>
      </div>
      <h2>{{ task.title }}</h2>
      <p class="meta">发布者 {{ task.publisherName }}</p>
      <p class="prose">{{ task.description }}</p>
      <div class="actions">
        <router-link class="btn secondary" to="/tasks">返回列表</router-link>
        <button type="button" class="btn" @click="openApplication">{{ actionLabel }}</button>
      </div>
    </article>
  </section>
</template>
