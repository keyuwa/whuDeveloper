<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { AuditEvent, Reminder } from '../types'

const items = ref<Reminder[]>([])
const audit = ref<AuditEvent[]>([])
const info = ref('')
const error = ref('')

async function load() {
  items.value = await api<Reminder[]>('/api/v1/admin/reminders')
  audit.value = await api<AuditEvent[]>('/api/v1/admin/audit')
}
onMounted(() => load().catch((cause) => { error.value = cause instanceof Error ? cause.message : '加载失败' }))

async function scan() {
  const result = await api<{ created: number }>('/api/v1/admin/reminders/scan', { method: 'POST' })
  info.value = `本次新增 ${result.created} 条催办`
  await load()
}
async function act(id: number, action: 'escalate' | 'resolve') {
  await api(`/api/v1/admin/reminders/${id}/${action}`, { method: 'POST' })
  await load()
}
</script>

<template>
  <section class="stack">
    <header class="page-head">
      <div><p class="kicker">SLA queue</p><h1>催办队列</h1></div>
      <button class="btn primary" type="button" @click="scan">立即扫描</button>
    </header>
    <p v-if="info" class="note">{{ info }}</p>
    <p v-if="error" class="banner">{{ error }}</p>
    <table class="table">
      <thead><tr><th>类型</th><th>对象</th><th>状态</th><th>键</th><th></th></tr></thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td>{{ item.type }}</td>
          <td>{{ item.targetType }} #{{ item.targetId }}</td>
          <td>{{ item.status }}</td>
          <td>{{ item.dedupeKey }}</td>
          <td class="actions">
            <button class="btn" type="button" @click="act(item.id, 'escalate')">升级</button>
            <button class="btn" type="button" @click="act(item.id, 'resolve')">关闭</button>
          </td>
        </tr>
      </tbody>
    </table>
    <article class="card">
      <h2>最近审计</h2>
      <p v-for="event in audit.slice(0, 12)" :key="event.id" class="muted">{{ event.action }} · {{ event.entityType }} #{{ event.entityId }} · {{ event.detail || '' }}</p>
    </article>
  </section>
</template>
