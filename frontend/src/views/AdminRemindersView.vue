<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import type { AuditEvent, Reminder } from '../types'

const items = ref<Reminder[]>([])
const audit = ref<AuditEvent[]>([])
const loading = ref(false)

async function load() {
  items.value = await api<Reminder[]>('/api/v1/admin/reminders')
  audit.value = await api<AuditEvent[]>('/api/v1/admin/audit')
}

onMounted(async () => {
  loading.value = true
  try {
    await load()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loading.value = false
  }
})

async function scan() {
  try {
    const result = await api<{ created: number }>('/api/v1/admin/reminders/scan', { method: 'POST' })
    Message.success(`本次新增 ${result.created} 条催办`)
    await load()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '扫描失败')
  }
}

async function act(id: number, action: 'escalate' | 'resolve') {
  try {
    await api(`/api/v1/admin/reminders/${id}/${action}`, { method: 'POST' })
    await load()
    Message.success(action === 'escalate' ? '已升级' : '已关闭')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '操作失败')
  }
}
</script>

<template>
  <section class="stack">
    <header class="page-head">
      <div>
        <a-typography-title :heading="4" style="margin: 0">催办队列</a-typography-title>
        <p class="subtitle">SLA 扫描与升级处理。</p>
      </div>
      <a-button type="primary" @click="scan">立即扫描</a-button>
    </header>
    <a-spin :loading="loading" style="width: 100%">
      <a-table :data="items" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column title="类型" data-index="type" />
          <a-table-column title="对象">
            <template #cell="{ record }">{{ record.targetType }} #{{ record.targetId }}</template>
          </a-table-column>
          <a-table-column title="状态" data-index="status" />
          <a-table-column title="键" data-index="dedupeKey" />
          <a-table-column title="操作">
            <template #cell="{ record }">
              <a-space>
                <a-button size="mini" @click="act(record.id, 'escalate')">升级</a-button>
                <a-button size="mini" @click="act(record.id, 'resolve')">关闭</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-spin>
    <a-card title="最近审计">
      <a-empty v-if="audit.length === 0" description="暂无审计记录" />
      <a-list v-else :bordered="false" size="small">
        <a-list-item v-for="event in audit.slice(0, 12)" :key="event.id">
          <a-typography-text type="secondary">
            {{ event.action }} · {{ event.entityType }} #{{ event.entityId }} · {{ event.detail || '' }}
          </a-typography-text>
        </a-list-item>
      </a-list>
    </a-card>
  </section>
</template>
