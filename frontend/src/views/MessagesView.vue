<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { ThreadSummary } from '../types'

const items = ref<ThreadSummary[]>([])
const error = ref('')
onMounted(async () => {
  try { items.value = await api<ThreadSummary[]>('/api/v1/messages') }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '加载失败' }
})
</script>

<template>
  <section>
    <header class="page-head"><div><p class="kicker">Messages</p><h1>任务私信</h1></div></header>
    <p v-if="error" class="banner">{{ error }}</p>
    <div class="stack">
      <router-link v-for="item in items" :key="`${item.taskId}-${item.studentId}`" class="card flat-link" :to="`/tasks/${item.taskId}`">
        <strong>{{ item.taskTitle }}</strong>
        <span class="muted">{{ item.studentName }}</span>
        <p>{{ item.lastBody }}</p>
      </router-link>
      <p v-if="items.length === 0" class="muted">还没有私信。可以在任务详情里先和发布方沟通。</p>
    </div>
  </section>
</template>
