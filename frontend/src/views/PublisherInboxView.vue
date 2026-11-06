<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import ProposalFile from '../components/ProposalFile.vue'
import { useIsMobile } from '../composables/useDevice'
import { renderMarkdown } from '../markdown'
import type { Application } from '../types'

const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const items = ref<Application[]>([])
const notes = ref<Record<number, string>>({})
const loading = ref(false)
const tab = ref<'pending' | 'all'>('pending')

const statusLabel: Record<string, string> = {
  SUBMITTED: '待审阅',
  INTERVIEW: '面试中',
  ACCEPTED: '已通过',
  REJECTED: '未通过',
  WITHDRAWN: '已撤回',
}

const statusColor: Record<string, string> = {
  SUBMITTED: 'arcoblue',
  INTERVIEW: 'orange',
  ACCEPTED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'gray',
}

const taskIdFilter = computed(() => {
  const raw = route.query.taskId
  const value = Array.isArray(raw) ? raw[0] : raw
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
})

const filtered = computed(() => {
  let list = items.value
  if (taskIdFilter.value) list = list.filter((item) => item.taskId === taskIdFilter.value)
  if (tab.value === 'pending') list = list.filter((item) => item.status === 'SUBMITTED' || item.status === 'INTERVIEW')
  return list
})

const filterTitle = computed(() => {
  if (!taskIdFilter.value) return null
  return items.value.find((item) => item.taskId === taskIdFilter.value)?.taskTitle || `任务 #${taskIdFilter.value}`
})

const pendingCount = computed(() =>
  items.value.filter((item) => {
    if (taskIdFilter.value && item.taskId !== taskIdFilter.value) return false
    return item.status === 'SUBMITTED' || item.status === 'INTERVIEW'
  }).length,
)

async function load() {
  items.value = await api<Application[]>('/api/v1/publisher/applications')
}

onMounted(() => {
  load().catch((cause) => Message.error(cause instanceof Error ? cause.message : '加载失败'))
})

watch(taskIdFilter, (id) => {
  if (id) tab.value = 'all'
})

async function review(id: number, decision: 'ACCEPT' | 'REJECT') {
  loading.value = true
  try {
    await api(`/api/v1/applications/${id}/review`, {
      method: 'POST',
      body: JSON.stringify({ decision, note: notes.value[id] || '' }),
    })
    await load()
    Message.success(decision === 'ACCEPT' ? '已通过，学生已入项' : '已拒绝')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '操作失败')
  } finally {
    loading.value = false
  }
}

function clearTaskFilter() {
  router.push('/publisher/inbox')
}
</script>

<template>
  <section :class="{ 'inbox-mobile': isMobile }">
    <header class="page-head">
      <div>
        <a-typography-title v-if="!isMobile" :heading="4" style="margin: 0">申请审阅</a-typography-title>
        <p class="subtitle">
          <template v-if="filterTitle">正在查看「{{ filterTitle }}」的申请</template>
          <template v-else-if="!isMobile">学生申请后会出现在这里；通过后可进入协作项目。</template>
          <template v-else>待审 {{ pendingCount }} 份</template>
        </p>
      </div>
      <a-button v-if="taskIdFilter" size="small" @click="clearTaskFilter">全部任务</a-button>
    </header>

    <a-tabs v-model:active-key="tab" type="rounded" :size="isMobile ? 'medium' : 'large'" style="margin-bottom: 12px">
      <a-tab-pane key="pending" :title="`待审阅 · ${pendingCount}`" />
      <a-tab-pane key="all" :title="`全部 · ${taskIdFilter ? items.filter(i => i.taskId === taskIdFilter).length : items.length}`" />
    </a-tabs>

    <a-empty
      v-if="filtered.length === 0"
      :description="tab === 'pending' ? '暂无待审申请' : '还没有人申请该任务'"
    />
    <div v-else class="stack">
      <a-card v-for="item in filtered" :key="item.id">
        <div style="display: flex; justify-content: space-between; gap: 12px; align-items: center">
          <a-typography-title :heading="6" style="margin: 0">
            {{ item.realName || item.studentName }}
            <template v-if="item.studentIdentity"> · {{ item.studentIdentity }}</template>
          </a-typography-title>
          <a-tag :color="statusColor[item.status] || 'gray'">
            {{ statusLabel[item.status] || item.status }}
          </a-tag>
        </div>
        <a-typography-paragraph type="secondary">
          {{ item.taskTitle }} · 催办 {{ item.remindCount }} 次
        </a-typography-paragraph>
        <div v-if="item.school || item.college || item.major || item.bio" class="resume-box">
          <div class="resume-title">学生简历</div>
          <p v-if="item.school || item.college || item.major">
            {{ [item.school, item.college, item.major].filter(Boolean).join(' · ') }}
          </p>
          <pre v-if="item.bio" class="md">{{ item.bio }}</pre>
          <a v-if="item.resumeFileUrl" :href="item.resumeFileUrl" target="_blank" rel="noopener">
            {{ item.resumeFileName || '查看简历 PDF' }}
          </a>
        </div>
        <div class="resume-title" style="margin-top: 12px">申请内容</div>
        <article v-if="item.proposalMd" class="md-body proposal-view" v-html="renderMarkdown(item.proposalMd)" />
        <ProposalFile v-if="item.proposalFileName" :application-id="item.id" :file-name="item.proposalFileName" />
        <template v-if="item.status === 'SUBMITTED'">
          <a-input v-model="notes[item.id]" placeholder="评语（可选）" style="margin: 12px 0" />
          <div class="action-row">
            <a-space wrap>
              <a-button status="danger" :loading="loading" @click="review(item.id, 'REJECT')">拒绝</a-button>
              <a-button
                type="text"
                @click="router.push({ path: '/messages', query: { taskId: String(item.taskId), studentId: String(item.studentId) } })"
              >
                打开对话
              </a-button>
            </a-space>
            <a-button type="primary" :loading="loading" @click="review(item.id, 'ACCEPT')">通过并接收</a-button>
          </div>
        </template>
        <div v-else-if="item.status === 'ACCEPTED'" class="accepted-box">
          <span>已接收该学生入项</span>
          <a-button
            v-if="item.projectId"
            type="primary"
            size="small"
            @click="router.push(`/publisher/projects/${item.projectId}/detail`)"
          >
            打开项目
          </a-button>
        </div>
        <p v-if="item.reviewNote" class="review-note">审阅意见：{{ item.reviewNote }}</p>
      </a-card>
    </div>
  </section>
</template>

<style scoped>
.resume-box {
  margin: 10px 0 6px;
  padding: 12px 14px;
  background: #f6f8fb;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #4c596c;
}
.resume-title {
  font-size: 13px;
  font-weight: 600;
  color: #243751;
  margin-bottom: 6px;
}
.md { white-space: pre-wrap; overflow-wrap: anywhere; }
.action-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.accepted-box {
  margin-top: 12px;
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  color: #166534;
  font-size: 13px;
}
.review-note {
  margin-top: 10px;
  color: #6b7280;
  font-size: 13px;
}
.inbox-mobile .page-head {
  align-items: flex-start;
  margin-bottom: 12px;
}
.inbox-mobile .action-row {
  flex-direction: column;
  align-items: stretch;
}
.inbox-mobile .accepted-box {
  flex-direction: column;
  align-items: stretch;
}
</style>
