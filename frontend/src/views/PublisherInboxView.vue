<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import ProposalFile from '../components/ProposalFile.vue'
import { useIsMobile } from '../composables/useDevice'
import { renderMarkdown } from '../markdown'
import type { Application } from '../types'

type AiDetail = {
  completeness?: number
  fit?: number
  feasibility?: number
  evidence?: number
  feasibilityLabel?: string
  risks?: string
}

const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const items = ref<Application[]>([])
const notes = ref<Record<number, string>>({})
const loading = ref(false)
const tab = ref<'pending' | 'all'>('pending')
let pollTimer: ReturnType<typeof setInterval> | null = null

const statusLabel: Record<string, string> = {
  SUBMITTED: '待审阅',
  INTERVIEW: '面试中',
  ACCEPTED: '已通过',
  REJECTED: '未通过',
  WITHDRAWN: '已撤回',
}

function isChallenge(item: Application) {
  return item.postingMode === 'CHALLENGE' || item.taskRoleType === 'RA'
}

function parseAiDetail(item: Application): AiDetail | null {
  if (!item.aiDetailJson) return null
  try {
    return JSON.parse(item.aiDetailJson) as AiDetail
  } catch {
    return null
  }
}

function aiLabel(item: Application) {
  if (!isChallenge(item)) return ''
  if (item.aiStatus === 'DONE' && item.aiScore != null) return `AI ${item.aiScore}`
  if (item.aiStatus === 'PENDING') return 'AI 评分中'
  if (item.aiStatus === 'FAILED') return 'AI 暂不可用'
  if (item.aiStatus === 'SKIPPED') return 'AI 未评分'
  return ''
}

function canRescoreAi(item: Application) {
  return isChallenge(item) && (item.aiStatus === 'SKIPPED' || item.aiStatus === 'FAILED' || item.aiStatus === 'DONE')
}

function feasibilityTagColor(label?: string) {
  if (label === '初步可行') return 'green'
  if (label === '条件可行') return 'orange'
  if (label === '可行性存疑') return 'red'
  return 'purple'
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

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function syncPoll() {
  const pending = items.value.some((item) => item.aiStatus === 'PENDING')
  if (pending && !pollTimer) {
    pollTimer = setInterval(() => {
      load().catch(() => undefined)
    }, 4000)
  } else if (!pending) {
    stopPoll()
  }
}

async function load() {
  items.value = await api<Application[]>('/api/v1/publisher/applications')
  syncPoll()
}

onMounted(() => {
  load().catch((cause) => Message.error(cause instanceof Error ? cause.message : '加载失败'))
})

onUnmounted(() => {
  stopPoll()
})

watch(taskIdFilter, (id) => {
  if (id) tab.value = 'all'
})

async function review(id: number, decision: 'ACCEPT' | 'REJECT') {
  loading.value = true
  try {
    const challenge = items.value.some((item) => item.id === id && isChallenge(item))
    await api(`/api/v1/applications/${id}/review`, {
      method: 'POST',
      body: JSON.stringify({ decision, note: notes.value[id] || '' }),
    })
    await load()
    if (decision === 'ACCEPT') Message.success('已通过，学生已入项')
    else Message.success(challenge ? '已标记不采纳' : '已拒绝')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '操作失败')
  } finally {
    loading.value = false
  }
}

async function award(id: number) {
  loading.value = true
  try {
    await api(`/api/v1/applications/${id}/award`, {
      method: 'POST',
      body: JSON.stringify({ note: notes.value[id] || '' }),
    })
    await load()
    Message.success('已通过；名额未满时可继续通过其他方案')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '操作失败')
  } finally {
    loading.value = false
  }
}

async function rescoreAi(id: number) {
  loading.value = true
  try {
    await api(`/api/v1/applications/${id}/ai-rescore`, { method: 'POST' })
    await load()
    Message.success('已重新发起 AI 初判')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '重评失败')
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
          {{ item.taskTitle }}
          <template v-if="item.positionTitle"> · {{ item.positionTitle }}</template>
          <template v-if="isChallenge(item)"> · 科研方案</template>
        </a-typography-paragraph>
        <div v-if="aiLabel(item)" class="ai-box">
          <div class="resume-title">
            AI 可行性初判
            <a-tag size="small" color="purple" style="margin-left: 8px">{{ aiLabel(item) }}</a-tag>
            <a-tag
              v-if="parseAiDetail(item)?.feasibilityLabel"
              size="small"
              :color="feasibilityTagColor(parseAiDetail(item)?.feasibilityLabel)"
              style="margin-left: 6px"
            >
              {{ parseAiDetail(item)?.feasibilityLabel }}
            </a-tag>
          </div>
          <p v-if="item.aiSummary">{{ item.aiSummary }}</p>
          <div v-if="parseAiDetail(item) && item.aiStatus === 'DONE'" class="ai-dims">
            <span>完整 {{ parseAiDetail(item)?.completeness ?? '—' }}</span>
            <span>匹配 {{ parseAiDetail(item)?.fit ?? '—' }}</span>
            <span>可行 {{ parseAiDetail(item)?.feasibility ?? '—' }}</span>
            <span>依据 {{ parseAiDetail(item)?.evidence ?? '—' }}</span>
          </div>
          <p v-if="parseAiDetail(item)?.risks" class="ai-risk">风险：{{ parseAiDetail(item)?.risks }}</p>
          <p v-if="item.aiStatus === 'SKIPPED' && item.aiSummary" class="ai-risk">{{ item.aiSummary }}</p>
          <p class="ai-hint">仅供参考，揭榜仍由你人工决定。</p>
          <div v-if="canRescoreAi(item)" style="margin-top: 8px">
            <a-button size="mini" :loading="loading" @click="rescoreAi(item.id)">重新 AI 初判</a-button>
          </div>
        </div>
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
        <div class="resume-title" style="margin-top: 12px">{{ isChallenge(item) ? '方案内容' : '申请内容' }}</div>
        <article v-if="item.proposalMd" class="md-body proposal-view" v-html="renderMarkdown(item.proposalMd)" />
        <ProposalFile v-if="item.proposalFileName" :application-id="item.id" :file-name="item.proposalFileName" />
        <template v-if="item.status === 'SUBMITTED'">
          <a-input v-model="notes[item.id]" placeholder="评语（可选）" style="margin: 12px 0" />
          <div class="action-row">
            <a-space wrap>
              <a-button status="danger" :loading="loading" @click="review(item.id, 'REJECT')">
                {{ isChallenge(item) ? '不采纳' : '拒绝' }}
              </a-button>
              <a-button
                type="text"
                @click="router.push({ path: '/messages', query: { taskId: String(item.taskId), studentId: String(item.studentId) } })"
              >
                打开对话
              </a-button>
            </a-space>
            <a-button
              v-if="isChallenge(item)"
              type="primary"
              :loading="loading"
              @click="award(item.id)"
            >
              通过
            </a-button>
            <a-button
              v-else
              type="primary"
              :loading="loading"
              @click="review(item.id, 'ACCEPT')"
            >
              通过
            </a-button>
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
.ai-box {
  margin: 10px 0 6px;
  padding: 12px 14px;
  background: #f5f3ff;
  border: 1px dashed #c4b5fd;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: #4c1d95;
}
.ai-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #7c3aed;
  opacity: 0.85;
}
.ai-dims {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  margin-top: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #5b21b6;
}
.ai-risk {
  margin: 8px 0 0;
  font-size: 12px;
  color: #6d28d9;
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
