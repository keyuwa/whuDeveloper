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

const keyword = ref('')
const selectedId = ref<number | null>(null)
const candidates = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return filtered.value
  return filtered.value.filter((item) =>
    [item.realName, item.studentName, item.taskTitle, item.positionTitle, item.school]
      .some((field) => field?.toLowerCase().includes(query)),
  )
})
const selected = computed(() =>
  candidates.value.find((item) => item.id === selectedId.value) ?? candidates.value[0],
)
const displayed = computed(() => {
  if (isMobile.value) return filtered.value
  return selected.value ? [selected.value] : []
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
  if (decision === 'REJECT' && !notes.value[id]?.trim()) {
    Message.warning('请填写不通过的具体原因')
    return
  }
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
    <div v-else class="review-workspace" :class="{ 'review-desktop': !isMobile }">
      <aside v-if="!isMobile" class="candidate-panel" aria-label="候选人">
        <div class="candidate-search">
          <a-input v-model="keyword" aria-label="搜索候选人" placeholder="搜索姓名、学校或项目" allow-clear />
        </div>
        <a-empty v-if="!candidates.length" description="没有匹配的申请" />
        <div v-else class="candidate-list">
          <button
            v-for="candidate in candidates"
            :key="candidate.id"
            type="button"
            class="candidate-item"
            :aria-pressed="selected?.id === candidate.id"
            @click="selectedId = candidate.id"
          >
            <span class="candidate-line">
              <strong>{{ candidate.realName || candidate.studentName }}</strong>
              <a-tag size="small" :color="statusColor[candidate.status] || 'gray'">
                {{ statusLabel[candidate.status] || candidate.status }}
              </a-tag>
            </span>
            <span class="candidate-task">{{ candidate.taskTitle }}<template v-if="candidate.positionTitle"> · {{ candidate.positionTitle }}</template></span>
            <time :datetime="candidate.createdAt">{{ candidate.createdAt.slice(0, 10) }}</time>
          </button>
        </div>
      </aside>
      <div class="stack review-detail">
      <a-card v-for="item in displayed" :key="item.id">
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
        <div class="review-layout">
          <div class="review-material">
            <div class="resume-title">{{ isChallenge(item) ? '方案内容' : '申请内容' }}</div>
            <article v-if="item.proposalMd" class="md-body proposal-view" v-html="renderMarkdown(item.proposalMd)" />
            <ProposalFile v-if="item.proposalFileName" :application-id="item.id" :file-name="item.proposalFileName" preview :inline="!isMobile" :compact="isMobile" />
            <details v-if="aiLabel(item)" class="ai-box" :open="!isMobile">
              <summary class="resume-title review-summary">
                AI 可行性初判
                <a-tag size="small" color="arcoblue" style="margin-left: 8px">{{ aiLabel(item) }}</a-tag>
                <a-tag
                  v-if="parseAiDetail(item)?.feasibilityLabel"
                  size="small"
                  :color="feasibilityTagColor(parseAiDetail(item)?.feasibilityLabel)"
                  style="margin-left: 6px"
                >
                  {{ parseAiDetail(item)?.feasibilityLabel }}
                </a-tag>
              </summary>
              <p v-if="item.aiSummary">{{ item.aiSummary }}</p>
              <div v-if="parseAiDetail(item) && item.aiStatus === 'DONE'" class="ai-dims">
                <span>完整 {{ parseAiDetail(item)?.completeness ?? '—' }}</span>
                <span>匹配 {{ parseAiDetail(item)?.fit ?? '—' }}</span>
                <span>可行 {{ parseAiDetail(item)?.feasibility ?? '—' }}</span>
                <span>依据 {{ parseAiDetail(item)?.evidence ?? '—' }}</span>
              </div>
              <p v-if="parseAiDetail(item)?.risks" class="ai-risk">风险：{{ parseAiDetail(item)?.risks }}</p>
              <p class="ai-hint">仅供参考，揭榜仍由你人工决定。</p>
              <div v-if="canRescoreAi(item)" style="margin-top: 8px">
                <a-button size="mini" :loading="loading" @click="rescoreAi(item.id)">重新 AI 初判</a-button>
              </div>
            </details>
            <details v-if="item.school || item.college || item.major || item.bio || item.resumeFileUrl" class="resume-box" :open="!isMobile">
              <summary class="resume-title">学生简历</summary>
              <p v-if="item.school || item.college || item.major">
                {{ [item.school, item.college, item.major].filter(Boolean).join(' · ') }}
              </p>
              <pre v-if="item.bio" class="md">{{ item.bio }}</pre>
              <a v-if="item.resumeFileUrl" :href="item.resumeFileUrl" target="_blank" rel="noopener">
                {{ item.resumeFileName || '查看简历 PDF' }}
              </a>
            </details>
          </div>
          <div class="review-controls">
            <a-button
              @click="router.push({ path: '/messages', query: { taskId: String(item.taskId), studentId: String(item.studentId) } })"
            >
              与学生沟通
            </a-button>
            <template v-if="item.status === 'SUBMITTED'">
              <a-input v-model="notes[item.id]" placeholder="审阅意见（拒绝时必填）" style="margin: 12px 0" />
              <div class="action-row">
                <a-space wrap>
                  <a-button status="danger" :loading="loading" @click="review(item.id, 'REJECT')">
                    {{ isChallenge(item) ? '不采纳' : '拒绝' }}
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
          </div>
        </div>
      </a-card>
      </div>
    </div>
  </section>
</template>

<style scoped>
.review-desktop { display: grid; grid-template-columns: minmax(220px, 28%) minmax(0, 1fr); align-items: start; gap: 16px; }
.review-detail { min-width: 0; }
.candidate-panel { position: sticky; top: var(--desktop-sticky-top); min-width: 0; border: 1px solid var(--desktop-line); border-radius: var(--radius-lg); background: var(--bg-surface); overflow: hidden; }
.candidate-search { padding: 12px; border-bottom: 1px solid #e8edf2; }
.candidate-list { max-height: min(70vh, 780px); overflow-y: auto; }
.candidate-item { display: grid; gap: 6px; width: 100%; padding: 14px 12px; border: 0; border-left: 3px solid transparent; border-bottom: 1px solid #edf0f4; background: #fff; text-align: left; color: inherit; font: inherit; cursor: pointer; }
.candidate-item:hover { background: var(--bg-subtle); }
.candidate-item[aria-pressed="true"] { border-left-color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); }
.candidate-item:focus-visible { outline: 2px solid rgb(var(--primary-6)); outline-offset: -2px; }
.candidate-line { display: flex; justify-content: space-between; gap: 8px; align-items: start; }
.candidate-line strong, .candidate-task { overflow-wrap: anywhere; }
.candidate-task { color: #475569; font-size: 13px; }
.candidate-item time { color: #667585; font-size: 12px; }

@media (min-width: 1000px) {
  .review-layout { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr); gap: 24px; align-items: start; }
  .review-controls { position: sticky; top: var(--desktop-sticky-top); }
}

.resume-box {
  margin: 10px 0 6px;
  padding: 12px 14px;
  background: var(--bg-subtle);
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #4c596c;
}
.ai-box {
  margin: 10px 0 6px;
  padding: 12px 14px;
  background: var(--bg-subtle);
  border: 1px solid var(--surface-line);
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-body);
}
.ai-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--text-body);
  opacity: 0.85;
}
.ai-dims {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  margin-top: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-strong);
}
.ai-risk {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--text-body);
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
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.inbox-mobile .action-row :deep(.arco-btn) { width: 100%; min-height: 44px; }
.inbox-mobile .action-row :deep(.arco-space-item) { flex: 1; }
.inbox-mobile .review-controls { margin-top: 14px; padding-top: 12px; border-top: 1px solid #e8edf5; }
.inbox-mobile :is(.ai-box, .resume-box) {
  padding: 10px 12px;
  border: 1px solid #e8edf5;
  background: #f8fafc;
  color: #334155;
}
.inbox-mobile details:not([open]) .resume-title { margin-bottom: 0; }
.review-summary { overflow-wrap: anywhere; }
.review-material summary { cursor: pointer; }
.inbox-mobile .accepted-box {
  flex-direction: column;
  align-items: stretch;
}
</style>
