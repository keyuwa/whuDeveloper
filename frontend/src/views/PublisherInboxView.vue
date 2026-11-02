<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { Application } from '../types'

const items = ref<Application[]>([])
const error = ref('')
const note = ref('')
const interviewAt = ref('')
const interviewNote = ref('线上面试，请提前准备方案。')
const loading = ref(false)

async function load() {
  items.value = await api<Application[]>('/api/v1/publisher/applications')
}
onMounted(() => load().catch((cause) => { error.value = cause instanceof Error ? cause.message : '加载失败' }))

async function review(id: number, decision: 'ACCEPT' | 'REJECT') {
  loading.value = true
  error.value = ''
  try {
    await api(`/api/v1/applications/${id}/review`, { method: 'POST', body: JSON.stringify({ decision, note: note.value }) })
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '操作失败'
  } finally {
    loading.value = false
  }
}

async function interview(id: number) {
  loading.value = true
  error.value = ''
  try {
    await api(`/api/v1/applications/${id}/interview`, {
      method: 'POST',
      body: JSON.stringify({ interviewAt: interviewAt.value ? `${interviewAt.value}:00` : null, note: interviewNote.value }),
    })
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '操作失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section>
    <header class="page-head"><div><p class="kicker">Review</p><h1>申请审阅</h1></div></header>
    <p v-if="error" class="banner">{{ error }}</p>
    <div class="stack">
      <article v-for="item in items" :key="item.id" class="card stack">
        <div class="row">
          <h2>{{ item.studentName }} · {{ item.abilityLevel }}</h2>
          <span class="pill accent">{{ item.status }}</span>
        </div>
        <p class="muted">{{ item.taskTitle }} · 催办 {{ item.remindCount }} 次</p>
        <pre class="md">{{ item.proposalMd }}</pre>
        <p v-if="item.interviewAt" class="note">面试 {{ item.interviewAt.replace('T', ' ') }} {{ item.interviewNote }}</p>
        <div v-if="item.status === 'SUBMITTED'" class="stack">
          <label>评语<input v-model="note" /></label>
          <div class="actions">
            <button class="btn primary" type="button" :disabled="loading" @click="review(item.id, 'ACCEPT')">通过并入学</button>
            <button class="btn danger" type="button" :disabled="loading" @click="review(item.id, 'REJECT')">拒绝</button>
          </div>
          <div class="row">
            <label>面试时间<input v-model="interviewAt" type="datetime-local" /></label>
            <button class="btn" type="button" :disabled="loading" @click="interview(item.id)">约面试</button>
          </div>
          <router-link :to="`/tasks/${item.taskId}?student=${item.studentId}`">打开任务私信</router-link>
        </div>
        <router-link v-if="item.projectId" :to="`/publisher/projects/${item.projectId}`">打开项目</router-link>
      </article>
    </div>
  </section>
</template>
