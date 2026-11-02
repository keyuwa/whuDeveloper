<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { Application } from '../types'

const items = ref<Application[]>([])
const error = ref('')
onMounted(async () => {
  try { items.value = await api<Application[]>('/api/v1/me/applications') }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '加载失败' }
})
</script>

<template>
  <section>
    <header class="page-head"><div><p class="kicker">Applications</p><h1>我的申请</h1></div></header>
    <p v-if="error" class="banner">{{ error }}</p>
    <div class="stack">
      <article v-for="item in items" :key="item.id" class="card stack">
        <div class="row">
          <h2>{{ item.taskTitle }}</h2>
          <span class="pill accent">{{ item.status }}</span>
        </div>
        <p class="muted">催办次数 {{ item.remindCount }}<span v-if="item.interviewAt"> · 面试 {{ item.interviewAt.replace('T', ' ') }}</span></p>
        <p v-if="item.interviewNote">{{ item.interviewNote }}</p>
        <router-link v-if="item.projectId" :to="`/me/projects/${item.projectId}`">进入工作台</router-link>
        <router-link v-else :to="`/tasks/${item.taskId}`">查看任务</router-link>
      </article>
      <p v-if="items.length === 0" class="muted">还没有申请。</p>
    </div>
  </section>
</template>
