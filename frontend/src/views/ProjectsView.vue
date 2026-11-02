<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { auth } from '../auth'
import type { Project } from '../types'

const items = ref<Project[]>([])
const error = ref('')
onMounted(async () => {
  try { items.value = await api<Project[]>('/api/v1/projects') }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '加载失败' }
})
function href(item: Project) {
  return auth.user?.role === 'PUBLISHER' ? `/publisher/projects/${item.id}` : `/me/projects/${item.id}`
}
</script>

<template>
  <section>
    <header class="page-head"><div><p class="kicker">Projects</p><h1>项目</h1></div></header>
    <p v-if="error" class="banner">{{ error }}</p>
    <div class="grid">
      <router-link v-for="item in items" :key="item.id" class="card flat-link" :to="href(item)">
        <div class="pills"><span class="pill accent">{{ item.taskType }}</span><span class="pill">{{ item.status }}</span></div>
        <h2>{{ item.taskTitle }}</h2>
        <p class="muted">{{ item.studentName }} · {{ item.publisherName }}</p>
      </router-link>
    </div>
    <p v-if="items.length === 0" class="muted">还没有项目。申请被录取后会出现在这里。</p>
  </section>
</template>
