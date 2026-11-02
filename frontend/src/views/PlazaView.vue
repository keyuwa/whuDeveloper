<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { Task } from '../types'

const tasks = ref<Task[]>([])
const error = ref('')
const typeLabel: Record<string, string> = { TA: '助教', INTERN: '实习', RA: '研究助理' }

onMounted(async () => {
  try {
    tasks.value = await api<Task[]>('/api/v1/tasks')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  }
})
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <p class="kicker">Open tasks</p>
        <h1>任务广场</h1>
        <p class="muted">TA / Intern / RA。补助标准只展示，不在平台支付。</p>
      </div>
    </header>
    <p v-if="error" class="banner">{{ error }}</p>
    <div class="grid">
      <router-link v-for="task in tasks" :key="task.id" class="card flat-link" :to="`/tasks/${task.id}`">
        <div class="pills">
          <span class="pill accent">{{ typeLabel[task.roleType] }}</span>
          <span class="pill">{{ task.difficulty }}</span>
          <span class="pill">{{ task.filled }}/{{ task.quota }}</span>
        </div>
        <h2>{{ task.title }}</h2>
        <p class="muted">{{ task.techStack }}</p>
        <p>{{ task.stipendText }}</p>
        <p class="muted">{{ task.publisherName }} · {{ task.durationWeeks }} 周</p>
      </router-link>
    </div>
  </section>
</template>
