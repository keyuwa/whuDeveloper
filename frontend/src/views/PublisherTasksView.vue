<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { api } from '../api'
import { TASK_ROLE_LABEL, type Task } from '../types'

const router = useRouter()
const tasks = ref<Task[]>([])
const loading = ref(false)

const statusLabel: Record<string, string> = {
  DRAFT: '草稿',
  OPEN: '已发布',
  CLOSED: '已下架',
}

const statusColor: Record<string, string> = {
  DRAFT: 'gray',
  OPEN: 'green',
  CLOSED: 'orangered',
}

async function load() {
  tasks.value = await api<Task[]>('/api/v1/publisher/tasks')
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

async function act(id: number, action: 'publish' | 'close') {
  try {
    await api(`/api/v1/publisher/tasks/${id}/${action}`, { method: 'POST' })
    await load()
    Message.success(action === 'publish' ? '已发布' : '已下架')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '操作失败')
  }
}

function canDelete(task: Task) {
  return task.status === 'DRAFT' || task.status === 'CLOSED'
}

function removeTask(task: Task) {
  Modal.confirm({
    title: '删除任务',
    content: `确定删除「${task.title}」吗？历史申请与已结束协作会一并清理；若仍有进行中项目则不能删除。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    onOk: async () => {
      try {
        await api(`/api/v1/publisher/tasks/${task.id}`, { method: 'DELETE' })
        Message.success('已删除')
        await load()
      } catch (cause) {
        Message.error(cause instanceof Error ? cause.message : '删除失败')
        throw cause
      }
    },
  })
}
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <a-typography-title :heading="4" style="margin: 0">我的任务</a-typography-title>
        <p class="subtitle">草稿与已下架任务可删除；已发布需先下架。</p>
      </div>
      <a-button type="primary" @click="router.push('/publisher/tasks/new')">发布任务</a-button>
    </header>
    <a-spin :loading="loading" style="width: 100%">
      <a-empty v-if="!loading && tasks.length === 0" description="还没有任务" />
      <div v-else class="stack">
        <a-card v-for="task in tasks" :key="task.id">
          <div style="display: flex; justify-content: space-between; gap: 16px; align-items: center; flex-wrap: wrap">
            <div>
              <a-space wrap style="margin-bottom: 8px">
                <a-tag size="small">{{ task.publicCode }}</a-tag>
                <a-tag color="arcoblue" size="small">{{ TASK_ROLE_LABEL[task.roleType] || task.roleType }}</a-tag>
                <a-tag v-if="task.roleType !== 'TA'" size="small">{{ task.techField }}</a-tag>
                <a-tag v-if="task.roleType === 'INTERN'" size="small">{{ task.difficulty }}</a-tag>
                <a-tag :color="statusColor[task.status] || 'gray'" size="small">
                  {{ statusLabel[task.status] || task.status }}
                </a-tag>
              </a-space>
              <a-typography-title :heading="6" style="margin: 0 0 4px">{{ task.title }}</a-typography-title>
              <a-typography-text type="secondary">
                {{ task.filled }}/{{ task.quota }} · {{ task.programmingLanguages }} · {{ task.durationWeeks }} 周
                <template v-if="task.stipendText"> · {{ task.stipendText }}</template>
              </a-typography-text>
            </div>
            <a-space>
              <a-button @click="router.push(`/publisher/tasks/${task.id}`)">编辑</a-button>
              <a-button v-if="task.status !== 'OPEN'" type="primary" @click="act(task.id, 'publish')">发布</a-button>
              <a-button v-else @click="act(task.id, 'close')">下架</a-button>
              <a-button v-if="canDelete(task)" type="outline" status="danger" @click="removeTask(task)">
                删除
              </a-button>
            </a-space>
          </div>
        </a-card>
      </div>
    </a-spin>
  </section>
</template>
