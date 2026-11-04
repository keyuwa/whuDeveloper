<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import type { Task } from '../types'

const tasks = ref<Task[]>([])
const loading = ref(false)

async function load() {
  tasks.value = await api<Task[]>('/api/v1/admin/tasks')
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

async function setStatus(task: Task, status: string) {
  try {
    await api(`/api/v1/admin/tasks/${task.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    })
    await load()
    Message.success('状态已更新')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '更新失败')
  }
}
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <a-typography-title :heading="4" style="margin: 0">任务</a-typography-title>
        <p class="subtitle">管理发布与下架。</p>
      </div>
    </header>
    <a-spin :loading="loading" style="width: 100%">
      <a-table :data="tasks" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column title="名称" data-index="title" />
          <a-table-column title="领域 / 难度">
            <template #cell="{ record }">
              {{ record.techField }}<template v-if="record.roleType === 'INTERN'"> · {{ record.difficulty }}</template>
            </template>
          </a-table-column>
          <a-table-column title="发布方" data-index="publisherName" />
          <a-table-column title="名额">
            <template #cell="{ record }">{{ record.filled }}/{{ record.quota }}</template>
          </a-table-column>
          <a-table-column title="状态">
            <template #cell="{ record }">
              <a-select :model-value="record.status" size="small" style="width: 120px" @change="(value) => setStatus(record, String(value))">
                <a-option value="DRAFT">DRAFT</a-option>
                <a-option value="OPEN">OPEN</a-option>
                <a-option value="CLOSED">CLOSED</a-option>
              </a-select>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-spin>
  </section>
</template>
