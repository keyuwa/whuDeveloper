<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { Task } from '../types'

const tasks = ref<Task[]>([])
const error = ref('')
async function load() {
  tasks.value = await api<Task[]>('/api/v1/publisher/tasks')
}
onMounted(() => load().catch((cause) => { error.value = cause instanceof Error ? cause.message : '加载失败' }))
async function act(id: number, action: 'publish' | 'close') {
  await api(`/api/v1/publisher/tasks/${id}/${action}`, { method: 'POST' })
  await load()
}
</script>

<template>
  <section>
    <header class="page-head">
      <div><p class="kicker">Publisher</p><h1>我的任务</h1></div>
      <router-link class="btn primary" to="/publisher/tasks/new">发布任务</router-link>
    </header>
    <p v-if="error" class="banner">{{ error }}</p>
    <div class="stack">
      <article v-for="task in tasks" :key="task.id" class="card row">
        <div>
          <div class="pills"><span class="pill accent">{{ task.roleType }}</span><span class="pill">{{ task.status }}</span></div>
          <h2>{{ task.title }}</h2>
          <p class="muted">{{ task.filled }}/{{ task.quota }} · {{ task.difficulty }}</p>
        </div>
        <div class="actions">
          <router-link class="btn" :to="`/publisher/tasks/${task.id}`">编辑</router-link>
          <button v-if="task.status !== 'OPEN'" class="btn primary" type="button" @click="act(task.id, 'publish')">上架</button>
          <button v-else class="btn" type="button" @click="act(task.id, 'close')">下架</button>
        </div>
      </article>
    </div>
  </section>
</template>
