<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import type { Task } from '../types'

const tasks = ref<Task[]>([])
const loading = ref(false)

const keyword = ref('')
const sentinel = ref<HTMLElement | null>(null)
const hasMore = ref(false)
let requestId = 0
let nextPage = 0
let appliedKeyword = ''

async function load(append = false) {
  if (append && (loading.value || !hasMore.value)) return
  if (!append) {
    appliedKeyword = keyword.value.trim()
    nextPage = 0
    hasMore.value = false
    tasks.value = []
  }
  const id = ++requestId
  loading.value = true
  try {
    const query = new URLSearchParams({ keyword: appliedKeyword, page: String(nextPage) })
    const result = await api<{ items: Task[]; hasMore: boolean }>(`/api/v1/admin/tasks?${query}`)
    if (id !== requestId) return
    tasks.value = append ? [...tasks.value, ...result.items] : result.items
    hasMore.value = result.hasMore
    nextPage++
  } catch (cause) {
    if (id === requestId) Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    if (id === requestId) loading.value = false
  }
}

function loadOnScroll() {
  const bounds = sentinel.value?.getBoundingClientRect()
  if (bounds && bounds.top < window.innerHeight && bounds.bottom > 0) {
    void load(true)
  }
}

onMounted(() => {
  void load()
  window.addEventListener('scroll', loadOnScroll, { capture: true, passive: true })
})
onUnmounted(() => window.removeEventListener('scroll', loadOnScroll, true))

function clearFilter() {
  keyword.value = ''
  void load()
}

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
        <p class="subtitle">管理项目发布与撤下发布。</p>
      </div>
    </header>
    <form class="task-filter" role="search" @submit.prevent="load()">
      <a-input v-model="keyword" aria-label="搜索任务名称或编号" placeholder="任务名称或编号" allow-clear @clear="clearFilter" />
      <a-button type="primary" html-type="submit" :loading="loading">筛选</a-button>
    </form>
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
      <div v-if="hasMore" ref="sentinel" class="load-more">
        <a-button type="text" :loading="loading" @click="load(true)">加载更多</a-button>
      </div>
    </a-spin>
  </section>
</template>


<style scoped>
.task-filter {
  display: flex;
  gap: 8px;
  max-width: 420px;
  margin-bottom: 16px;
}
.load-more { text-align: center; padding: 12px; }
.task-filter :deep(.arco-input-wrapper) { min-width: 0; }
</style>
