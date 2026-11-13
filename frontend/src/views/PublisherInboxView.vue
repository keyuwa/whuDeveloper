<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import ProposalFile from '../components/ProposalFile.vue'
import { useIsMobile } from '../composables/useDevice'
import { renderMarkdown } from '../markdown'
import { TASK_ROLE_TYPES, type Application } from '../types'
import TaskFilterBar from '../components/TaskFilterBar.vue'

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
const tab = ref('pending')
const roleTab = ref<typeof TASK_ROLE_TYPES[number]['value'] | ''>('')
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
  if (roleTab.value) list = list.filter((item) => item.taskRoleType === roleTab.value)
  if (taskIdFilter.value) list = list.filter((item) => item.taskId === taskIdFilter.value)
  if (tab.value === 'pending') list = list.filter((item) => item.status === 'SUBMITTED' || item.status === 'INTERVIEW')
  else if (tab.value !== 'all') list = list.filter((item) => item.status === tab.value)
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
  if (isMobile.value) return candidates.value
  return selected.value ? [selected.value] : []
})
const taskOptions = computed(() => {
  const seen = new Map<number, string>()
  for (const item of items.value) {
    if (roleTab.value && item.taskRoleType !== roleTab.value) continue
    if (!seen.has(item.taskId)) seen.set(item.taskId, item.taskTitle)
  }
  return [...seen.entries()].map(([id, title]) => ({ value: String(id), label: title }))
})
const taskPick = computed({
  get: () => (taskIdFilter.value ? String(taskIdFilter.value) : undefined),
  set: (value: string | undefined) => {
    if (!value) router.push('/publisher/inbox')
    else router.push({ path: '/publisher/inbox', query: { taskId: value } })
  },
})
const emptyDescription = computed(() => {
  if (keyword.value.trim() || taskIdFilter.value || roleTab.value) return '没有匹配的申请'
  return tab.value === 'pending' ? '暂无待审申请' : '还没有人申请该任务'
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
}, { immediate: true })

watch(roleTab, () => {
  selectedId.value = null
  if (taskIdFilter.value) taskPick.value = undefined
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

</script>

<template>
  <section class="inbox-board" :class="{ 'inbox-mobile': isMobile }">
    <section class="review-management-panel" aria-label="申请审阅与筛选">
    <header class="management-heading">
      <div>
        <a-typography-title v-if="!isMobile" :heading="4" style="margin: 0">申请审阅</a-typography-title>
        <p class="management-description">
          <template v-if="filterTitle">正在查看「{{ filterTitle }}」的申请</template>
          <template v-else-if="!isMobile">查看申请书、审阅学生申请，跟进录取结果</template>
          <template v-else>待审 {{ pendingCount }} 份</template>
        </p>
      </div>
    </header>

    <TaskFilterBar v-model="roleTab" class="management-filters" show-all>
      <a-input v-model="keyword" allow-clear size="medium" aria-label="搜索申请" placeholder="搜索姓名、学校或任务">
        <template #prefix><icon-search aria-hidden="true" /></template>
      </a-input>
      <a-select v-model="tab" size="medium" aria-label="审阅状态">
        <a-option value="all">全部状态</a-option>
        <a-option value="pending">待审阅</a-option>
        <a-option value="INTERVIEW">面试中</a-option>
        <a-option value="ACCEPTED">已通过</a-option>
        <a-option value="REJECTED">未通过</a-option>
        <a-option value="WITHDRAWN">已撤回</a-option>
      </a-select>
      <a-select v-model="taskPick" size="medium" allow-clear allow-search
        aria-label="所属任务" placeholder="全部任务" :options="taskOptions" />
      <a-button v-if="keyword || taskIdFilter || tab !== 'pending' || roleTab" type="text"
        @click="keyword = ''; taskPick = undefined; tab = 'pending'; roleTab = ''">清空</a-button>
    </TaskFilterBar>
    </section>

    <a-empty
      v-if="candidates.length === 0"
      :description="emptyDescription"
    />
    <div v-else class="review-workspace" :class="{ 'review-desktop': !isMobile }">
      <aside v-if="!isMobile" class="candidate-panel" aria-label="候选人">
        <div class="candidate-list">
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
          <a-typography-title class="applicant-heading" :heading="6" style="margin: 0">
            {{ item.realName || item.studentName }}
            <span v-if="item.studentIdentity" class="applicant-identity"> · {{ item.studentIdentity }}</span>
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
.review-management-panel {
  flex: none;
  margin-bottom: 16px;
  padding: 0;
  border: 1px solid var(--surface-line);
  border-radius: var(--surface-radius);
  background: var(--bg-surface);
}
.management-heading {
  padding: 18px 20px 16px;
  background: var(--heading-surface);
  border-radius: var(--surface-radius) var(--surface-radius) 0 0;
}
.management-heading :deep(.arco-typography) { font-size: 22px; font-weight: 700; line-height: 1.4; color: var(--text-strong); }
.applicant-heading.arco-typography { font-size: 20px; font-weight: 700; line-height: 1.45; color: var(--text-strong); overflow-wrap: anywhere; }
.applicant-identity { font-size: 14px; font-weight: 400; color: var(--text-body); }
.inbox-mobile .applicant-heading { font-size: 17px; }
.management-description { margin: 6px 0 0; color: var(--text-body); font-size: 13px; line-height: 1.5; overflow-wrap: anywhere; }
.review-management-panel .management-filters {
  margin: 0;
  padding: 0 0 12px;
  border: 0;
  border-top: 1px solid var(--surface-line);
  border-radius: 0 0 var(--surface-radius) var(--surface-radius);
}
.review-management-panel .management-filters :deep(.filter-controls) {
  padding: 12px 20px 0;
}
@media (max-width: 767px) {
  .management-heading { padding: 12px; }
  .review-management-panel .management-filters { --filter-outer-inset: 12px; padding: 10px 12px 12px; }
  .management-description { margin-top: 0; }
}
.inbox-board:not(.inbox-mobile) {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - var(--desktop-header-height, 58px) - 70px);
  min-height: 0;
  overflow: hidden;
}
.review-desktop { display: grid; grid-template-columns: minmax(220px, 28%) minmax(0, 1fr); align-items: start; gap: 16px; }
.inbox-board:not(.inbox-mobile) .review-desktop {
  flex: 1;
  align-items: stretch;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  grid-template-rows: minmax(0, 1fr);
}
.review-detail { min-width: 0; }
.inbox-board:not(.inbox-mobile) .review-detail {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.inbox-board:not(.inbox-mobile) .review-detail :deep(.arco-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.inbox-board:not(.inbox-mobile) .review-detail :deep(.arco-card-body) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.candidate-panel { position: sticky; top: var(--desktop-sticky-top); min-width: 0; border: 1px solid var(--desktop-line); border-radius: var(--radius-lg); background: var(--bg-surface); overflow: hidden; }
.inbox-board:not(.inbox-mobile) .candidate-panel {
  position: static;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.candidate-list { max-height: min(70vh, 780px); overflow-y: auto; }
.board-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 12px;
}
.board-filter-label {
  flex: none;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}
.board-search { width: min(280px, 100%); }
.board-task { width: 220px; }
.board-search,
.board-task :deep(.arco-select-view) {
  height: 36px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}
.board-search :deep(.arco-input),
.board-task :deep(.arco-select-view-value),
.board-task :deep(.arco-select-view-input) {
  color: #111827;
  font-size: 13px;
}
.board-search :deep(.arco-input::placeholder),
.board-task :deep(.arco-select-view-input::placeholder) {
  color: #64748b;
}
.board-search:hover,
.board-search.arco-input-focus,
.board-task :deep(.arco-select-view:hover),
.board-task :deep(.arco-select-view.arco-select-view-focus) {
  background: #fff;
  border-color: #2563eb;
}
@media (max-width: 767px) {
  .board-filters { flex-wrap: wrap; }
  .board-search, .board-task { width: 100%; }
}
.inbox-board:not(.inbox-mobile) .candidate-list {
  flex: 1;
  max-height: none;
  min-height: 0;
}
.candidate-item { display: grid; gap: 6px; width: 100%; padding: 14px 12px; border: 0; border-left: 3px solid transparent; border-bottom: 1px solid #edf0f4; background: #fff; text-align: left; color: inherit; font: inherit; cursor: pointer; }
.candidate-item:hover { background: var(--bg-subtle); }
.candidate-item[aria-pressed="true"] { border-left-color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); }
.candidate-item:focus-visible { outline: 2px solid rgb(var(--primary-6)); outline-offset: -2px; }
.candidate-line { display: flex; justify-content: space-between; gap: 8px; align-items: start; }
.candidate-line strong, .candidate-task { overflow-wrap: anywhere; }
.candidate-line strong { font-size: 15px; font-weight: 700; line-height: 1.5; }
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
  font-size: 14px;
  font-weight: 650;
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
