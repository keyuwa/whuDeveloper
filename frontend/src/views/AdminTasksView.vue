<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { Task } from '../types'

const tasks = ref<Task[]>([])
const error = ref('')
async function load() { tasks.value = await api<Task[]>('/api/v1/admin/tasks') }
onMounted(() => load().catch((cause) => { error.value = cause instanceof Error ? cause.message : '加载失败' }))
async function setStatus(task: Task, status: string) {
  await api(`/api/v1/admin/tasks/${task.id}`, { method: 'PATCH', body: JSON.stringify({ status }) })
  await load()
}
</script>

<template>
  <section>
    <header class="page-head"><div><p class="kicker">Admin</p><h1>任务</h1></div></header>
    <p v-if="error" class="banner">{{ error }}</p>
    <table class="table">
      <thead><tr><th>名称</th><th>类型</th><th>发布方</th><th>名额</th><th>状态</th></tr></thead>
      <tbody>
        <tr v-for="task in tasks" :key="task.id">
          <td>{{ task.title }}</td>
          <td>{{ task.roleType }} {{ task.difficulty }}</td>
          <td>{{ task.publisherName }}</td>
          <td>{{ task.filled }}/{{ task.quota }}</td>
          <td>
            <select :value="task.status" @change="setStatus(task, ($event.target as HTMLSelectElement).value)">
              <option>DRAFT</option><option>OPEN</option><option>CLOSED</option>
            </select>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
