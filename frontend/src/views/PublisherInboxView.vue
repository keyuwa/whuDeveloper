<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import ProposalFile from '../components/ProposalFile.vue'
import { useIsMobile } from '../composables/useDevice'
import { renderMarkdown } from '../markdown'
import { TASK_ROLE_TYPES, type Application, type Project } from '../types'
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

interface ProjectReviewItem {
  id: number
  projectId: number
  taskTitle: string
  studentName: string
  type: 'REPORT' | 'COMPLETION'
  weekIndex: number | null
  status: string
  submittedAt: string
}

const reviewType = ref<'APPLICATION' | ProjectReviewItem['type']>('APPLICATION')
const reviewFilter = ref<'pending' | 'done'>('pending')
const projectReviews = ref<ProjectReviewItem[]>([])
const loadingReviews = ref(false)
const reviewError = ref('')
const reviewTypes = [
  { value: 'APPLICATION', label: '申请' },
  { value: 'REPORT', label: '周报' },
  { value: 'COMPLETION', label: '结项' },
] as const
const projectStatusLabel: Record<string, string> = {
  SCORED: '已审阅', APPROVED: '已通过', REJECTED: '已退回',
}
const reviewKeyword = ref('')
const visibleReviews = computed(() => projectReviews.value.filter((item) =>
  item.type === reviewType.value && (item.status === 'SUBMITTED') === (reviewFilter.value === 'pending')
    && [item.studentName, item.taskTitle].some((value) => value.toLowerCase().includes(reviewKeyword.value.trim().toLowerCase())),
))

const selectedReviewId = ref<number | null>(null)
const selectedReview = computed(() =>
  visibleReviews.value.find((item) => item.id === selectedReviewId.value) ?? visibleReviews.value[0],
)
const reviewProject = ref<Project | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
const selectedReport = computed(() =>
  reviewProject.value?.reports.find((report) => report.id === selectedReview.value?.id),
)
const selectedCompletion = computed(() => reviewProject.value?.completion)

watch([() => selectedReview.value?.projectId, isMobile], async ([projectId, mobile], _previous, onCleanup) => {
  reviewProject.value = null
  previewError.value = ''
  previewLoading.value = false
  if (!projectId || mobile) return
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  previewLoading.value = true
  try {
    reviewProject.value = await api<Project>(`/api/v1/projects/${projectId}`, { signal: controller.signal })
  } catch (cause) {
    if (!controller.signal.aborted) previewError.value = cause instanceof Error ? cause.message : '材料加载失败'
  } finally {
    if (!controller.signal.aborted) previewLoading.value = false
  }
}, { immediate: true })

async function loadProjectReviews() {
  loadingReviews.value = true
  reviewError.value = ''
  try {
    projectReviews.value = await api<ProjectReviewItem[]>('/api/v1/projects/review-queue')
  } catch (cause) {
    reviewError.value = cause instanceof Error ? cause.message : '加载审阅待办失败'
  } finally {
    loadingReviews.value = false
  }
}

watch(() => route.query.review, (value) => {
  reviewType.value = value === 'report' ? 'REPORT' : value === 'completion' ? 'COMPLETION' : 'APPLICATION'
  selectedReviewId.value = null
  reviewFilter.value = 'pending'
  void loadProjectReviews()
}, { immediate: true })

function goReview(item: ProjectReviewItem) {
  const base = `/publisher/projects/${item.projectId}`
  return item.type === 'REPORT' ? `${base}/progress#report-${item.id}` : `${base}/completion`
}

const reviewHeading = computed(() => ({
  APPLICATION: '申请审阅',
  REPORT: '周报审阅',
  COMPLETION: '结项验收',
})[reviewType.value])
const reviewDescription = computed(() => ({
  APPLICATION: '查看申请书、审阅学生申请，跟进录取结果',
  REPORT: '查看学生周报，反馈项目进展',
  COMPLETION: '查看结项材料，确认项目交付结果',
})[reviewType.value])

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
const pendingByType = computed(() => ({
  APPLICATION: pendingCount.value,
  REPORT: projectReviews.value.filter((item) => item.type === 'REPORT' && item.status === 'SUBMITTED').length,
  COMPLETION: projectReviews.value.filter((item) => item.type === 'COMPLETION' && item.status === 'SUBMITTED').length,
}))


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

function nextCandidateId(id: number) {
  const index = candidates.value.findIndex((item) => item.id === id)
  return candidates.value[index + 1]?.id ?? candidates.value[index - 1]?.id ?? null
}

async function review(id: number, decision: 'ACCEPT' | 'REJECT') {
  if (decision === 'REJECT' && !notes.value[id]?.trim()) {
    Message.warning('请填写不通过的具体原因')
    return
  }
  const nextId = nextCandidateId(id)
  loading.value = true
  try {
    const challenge = items.value.some((item) => item.id === id && isChallenge(item))
    await api(`/api/v1/applications/${id}/review`, {
      method: 'POST',
      body: JSON.stringify({ decision, note: notes.value[id] || '' }),
    })
    await load()
    if (!candidates.value.some((item) => item.id === id)) selectedId.value = nextId
    if (decision === 'ACCEPT') Message.success('已通过，学生已入项')
    else Message.success(challenge ? '已标记不采纳' : '已拒绝')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '操作失败')
  } finally {
    loading.value = false
  }
}

async function award(id: number) {
  const nextId = nextCandidateId(id)
  loading.value = true
  try {
    await api(`/api/v1/applications/${id}/award`, {
      method: 'POST',
      body: JSON.stringify({ note: notes.value[id] || '' }),
    })
    await load()
    if (!candidates.value.some((item) => item.id === id)) selectedId.value = nextId
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
    <section class="review-management-panel" aria-label="审阅与筛选">
      <header class="management-heading">
        <div class="review-heading-row">
          <a-typography-title :heading="4" style="margin: 0">{{ reviewHeading }}</a-typography-title>
          <a-tag color="arcoblue" round>
            {{ reviewType !== 'APPLICATION' && (loadingReviews || reviewError) ? '待审 — 份' : `待审 ${pendingByType[reviewType]} 份` }}
          </a-tag>
        </div>
        <p class="management-description">
          {{ reviewType === 'APPLICATION' && filterTitle ? `正在查看「${filterTitle}」的申请` : reviewDescription }}
        </p>
        <a-space wrap class="review-type-tabs" aria-label="审阅类型">
          <a-button
            v-for="type in reviewTypes"
            :key="type.value"
            size="small"
            :type="reviewType === type.value ? 'primary' : 'outline'"
            :aria-pressed="reviewType === type.value"
            @click="router.replace({ query: { review: type.value === 'APPLICATION' ? undefined : type.value.toLowerCase() } })"
          >{{ type.label }} · {{ type.value !== 'APPLICATION' && (loadingReviews || reviewError) ? '—' : pendingByType[type.value] }}</a-button>
        </a-space>
      </header>
    <TaskFilterBar v-if="reviewType === 'APPLICATION'" v-model="roleTab" class="management-filters" show-all>
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
      <div v-else class="project-review-filters">
        <a-input v-model="reviewKeyword" allow-clear aria-label="搜索提交记录" placeholder="搜索学生或项目">
          <template #prefix><icon-search aria-hidden="true" /></template>
        </a-input>
        <a-select v-model="reviewFilter" aria-label="审阅状态">
          <a-option value="pending">待审阅</a-option>
          <a-option value="done">已处理</a-option>
        </a-select>
        <a-button :loading="loadingReviews" @click="loadProjectReviews">刷新</a-button>
      </div>
    </section>
    <a-spin v-if="reviewType !== 'APPLICATION'" :loading="loadingReviews" class="project-review-list">
      <a-alert v-if="reviewError" type="error">{{ reviewError }}</a-alert>
      <div v-else-if="visibleReviews.length && !isMobile && selectedReview" class="desktop-project-review">
        <aside class="candidate-panel" aria-label="提交记录">
          <div class="project-pane-heading">提交记录 <span>{{ visibleReviews.length }} 份</span></div>
          <div class="candidate-list">
            <button
              v-for="item in visibleReviews"
              :key="item.id"
              type="button"
              class="candidate-item"
              :aria-pressed="selectedReview.id === item.id"
              @click="selectedReviewId = item.id"
            >
              <span class="candidate-line">
                <strong>{{ item.studentName }}</strong>
                <a-tag size="small" :color="item.status === 'SUBMITTED' ? 'arcoblue' : 'gray'">
                  {{ item.status === 'SUBMITTED' ? '待审阅' : projectStatusLabel[item.status] }}
                </a-tag>
              </span>
              <span class="project-row-meta">{{ item.taskTitle }}<template v-if="item.weekIndex"> · 第 {{ item.weekIndex }} 周</template></span>
            </button>
          </div>
        </aside>
        <section class="project-preview" aria-label="材料预览">
          <header class="project-preview-header">
            <div>
              <h3>{{ selectedReview.taskTitle }}</h3>
              <p>{{ selectedReview.studentName }}<template v-if="selectedReview.weekIndex"> · 第 {{ selectedReview.weekIndex }} 周</template> · {{ new Date(selectedReview.submittedAt).toLocaleString('zh-CN') }}</p>
            </div>
            <a-button type="primary" @click="router.push(goReview(selectedReview))">
              {{ selectedReview.status === 'SUBMITTED' ? '进入审阅' : '打开记录' }}
            </a-button>
          </header>
          <a-spin :key="selectedReview.id" :loading="previewLoading" class="project-preview-body">
            <a-alert v-if="previewError" type="error">{{ previewError }}</a-alert>
            <template v-else-if="reviewType === 'REPORT' && selectedReport">
              <dl class="project-review-material">
                <dt>本周完成</dt><dd>{{ selectedReport.content }}</dd>
                <dt>风险与阻塞</dt><dd>{{ selectedReport.blockers || '—' }}</dd>
                <dt>下周计划</dt><dd>{{ selectedReport.plan || '—' }}</dd>
                <template v-if="selectedReport.supportNeeded"><dt>需要支持</dt><dd>{{ selectedReport.supportNeeded }}</dd></template>
                <template v-if="selectedReport.scoreComment"><dt>审阅意见</dt><dd>{{ selectedReport.scoreComment }}</dd></template>
              </dl>
            </template>
            <template v-else-if="reviewType === 'COMPLETION' && selectedCompletion">
              <dl class="project-review-material">
                <template v-if="selectedCompletion.reportMd"><dt>结项说明</dt><dd>{{ selectedCompletion.reportMd }}</dd></template>
                <template v-if="selectedCompletion.reportFileUrl">
                  <dt>结项附件</dt><dd><a :href="selectedCompletion.reportFileUrl" target="_blank" rel="noopener">{{ selectedCompletion.reportFileName || '打开 PDF' }}</a></dd>
                </template>
                <template v-if="selectedCompletion.prUrls.length">
                  <dt>成果链接</dt><dd><ul><li v-for="url in selectedCompletion.prUrls" :key="url"><a :href="url" target="_blank" rel="noopener">{{ url }}</a></li></ul></dd>
                </template>
                <template v-if="selectedCompletion.reviewNote"><dt>验收意见</dt><dd>{{ selectedCompletion.reviewNote }}</dd></template>
              </dl>
            </template>
          </a-spin>
        </section>
      </div>
      <ul v-else-if="visibleReviews.length" class="mobile-review-records">
        <li v-for="item in visibleReviews" :key="item.id">
          <router-link :to="goReview(item)" class="mobile-review-link">
            <strong class="mobile-review-title">{{ item.taskTitle }}</strong>
            <span class="mobile-review-meta">
              {{ item.studentName }}<template v-if="item.weekIndex"> · 第 {{ item.weekIndex }} 周</template>
            </span>
            <a-tag class="mobile-review-status" size="small" :color="item.status === 'SUBMITTED' ? 'arcoblue' : item.status === 'REJECTED' ? 'red' : 'green'">
              {{ item.status === 'SUBMITTED' ? '待审阅' : projectStatusLabel[item.status] || item.status }}
            </a-tag>
            <span aria-hidden="true" class="mobile-review-chevron"></span>
          </router-link>
        </li>
      </ul>
      <a-empty v-else-if="!loadingReviews" :description="reviewKeyword.trim() ? '没有匹配的提交记录' : reviewFilter === 'pending' ? (reviewType === 'REPORT' ? '暂无待审周报' : '暂无待审结项') : '暂无已处理记录'" />
    </a-spin>
    <template v-else>

    <a-empty
      v-if="candidates.length === 0"
      :description="emptyDescription"
    />
    <div v-else class="review-workspace" :class="{ 'review-desktop': !isMobile }">
      <aside v-if="!isMobile" class="candidate-panel" aria-label="候选人">
        <div class="project-pane-heading">申请记录 <span>{{ candidates.length }} 份</span></div>
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
      <a-card v-for="item in displayed" :key="item.id" class="application-card">
        <button v-if="isMobile" type="button" class="mobile-application-toggle" :aria-expanded="selectedId === item.id" @click="selectedId = selectedId === item.id ? null : item.id">
          <span><strong>{{ item.realName || item.studentName }}</strong><span class="candidate-task">{{ item.taskTitle }}</span></span>
          <span class="mobile-toggle-end"><a-tag :color="statusColor[item.status] || 'gray'">{{ statusLabel[item.status] || item.status }}</a-tag>{{ selectedId === item.id ? '收起' : '查看' }}</span>
        </button>
        <template v-if="!isMobile || selectedId === item.id">
        <div class="application-heading">
        <div class="application-heading-main">
        <div class="applicant-title-row">
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
        </div>
            <a-button
              @click="router.push({ path: '/messages', query: { taskId: String(item.taskId), studentId: String(item.studentId) } })"
            >
              与学生沟通
            </a-button>
        </div>
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
            <template v-if="item.status === 'SUBMITTED'">
              <a-textarea v-model="notes[item.id]" aria-label="审阅意见" placeholder="填写审阅意见，拒绝时必填" :auto-size="{ minRows: 1, maxRows: 3 }" class="review-opinion" />
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
        </template>
      </a-card>
      </div>
    </div>
    </template>
  </section>
</template>

<style scoped>
.mobile-review-records {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--surface-line);
  border-radius: var(--surface-radius);
  background: var(--bg-surface);
}
.mobile-review-records li + li { border-top: 1px solid var(--surface-line); }
.mobile-review-link {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 7px;
  grid-template-areas: "title status arrow" "meta status arrow";
  align-items: center;
  gap: 4px 10px;
  padding: 14px;
  color: var(--text-strong);
  text-decoration: none;
}
.mobile-review-link:active { background: var(--bg-subtle); }
.mobile-review-link:focus-visible { outline: 2px solid rgb(var(--primary-6)); outline-offset: -3px; }
.mobile-review-title { grid-area: title; font-size: 15px; overflow-wrap: anywhere; }
.mobile-review-meta { grid-area: meta; color: var(--text-body); font-size: 13px; overflow-wrap: anywhere; }
.mobile-review-status { grid-area: status; }
.mobile-review-chevron {
  grid-area: arrow;
  width: 6px;
  height: 6px;
  border-top: 1px solid var(--text-body);
  border-right: 1px solid var(--text-body);
  transform: rotate(45deg);
}

.desktop-project-review {
  display: grid;
  grid-template-columns: clamp(240px, 25%, 320px) minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
}
.inbox-board:not(.inbox-mobile) .project-review-list { flex: 1; overflow: hidden; }
.inbox-board:not(.inbox-mobile) .project-review-list :deep(> .arco-spin-children),
.inbox-board:not(.inbox-mobile) .desktop-project-review { height: 100%; }
.desktop-project-review { grid-template-rows: minmax(0, 1fr); }
.desktop-project-review .project-preview { display: flex; flex-direction: column; min-height: 0; }
.desktop-project-review .project-pane-heading,
.desktop-project-review .project-preview-header { flex: none; }
.desktop-project-review .project-preview-body { flex: 1; min-height: 0; overflow-y: auto; }
.project-pane-heading,
.project-preview-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--surface-line);
  background: var(--bg-surface);
}
.project-pane-heading { display: flex; justify-content: space-between; font-weight: 700; }
.project-pane-heading span, .project-row-meta, .project-preview-header p { color: var(--text-body); font-size: 13px; }
.project-row-meta { display: block; overflow-wrap: anywhere; }
.project-preview { min-width: 0; border: 1px solid var(--surface-line); border-radius: var(--radius-lg); background: var(--bg-surface); overflow: hidden; }
.project-preview-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.project-preview-header h3 { margin: 0; overflow-wrap: anywhere; }
.project-preview-header p { margin: 4px 0 0; }
.project-preview-header :deep(.arco-btn) { flex: none; }
.project-preview-body { display: block; padding: 16px; }
.project-review-material { margin: 0; }
.project-review-material dt { font-weight: 700; margin-bottom: 4px; }
.project-review-material dd { margin: 0 0 16px; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.65; }
.project-review-material dd:last-child { margin-bottom: 0; }

.review-heading-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.review-type-tabs { margin-top: 12px; }
.project-review-filters {
  display: flex;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--surface-line);
}
.project-review-filters :deep(.arco-select-view) { width: min(180px, 100%); }
.project-review-list { display: block; min-height: 0; overflow: auto; }
@media (max-width: 767px) {
  .project-review-filters { padding-inline: 12px; }
}

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
  background: var(--bg-surface);
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
.review-desktop { display: grid; grid-template-columns: clamp(240px, 25%, 320px) minmax(0, 1fr); align-items: start; gap: 16px; }
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
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 0;
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

.application-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.application-heading-main { min-width: 0; }
.applicant-title-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.application-heading > .arco-btn { flex: none; }
.inbox-mobile .application-heading { flex-wrap: wrap; }
/* Keep decisions visible while the application itself scrolls. */
.inbox-board:not(.inbox-mobile) .application-heading { padding: 20px 24px 0; flex: none; }
.inbox-board:not(.inbox-mobile) .review-layout { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.inbox-board:not(.inbox-mobile) .review-material { flex: 1; min-height: 0; overflow-y: auto; padding: 16px 24px 24px; }
.inbox-board:not(.inbox-mobile) .review-controls { flex: none; display: grid; grid-template-columns: minmax(0, 420px) auto; justify-content: space-between; gap: 16px 24px; align-items: center; padding: 16px 24px; border-top: 1px solid var(--surface-line); background: var(--bg-surface); }

.inbox-board:not(.inbox-mobile) .review-opinion { grid-column: 1; grid-row: 1; }
.inbox-board:not(.inbox-mobile) .action-row { grid-column: 2; flex-wrap: nowrap; gap: 12px; }
.inbox-board:not(.inbox-mobile) .review-note { grid-column: 1 / -1; margin: 0; }
.project-review-filters :deep(.arco-input-wrapper) { max-width: 300px; }
.candidate-list, .review-material, .project-preview-body { overscroll-behavior: contain; scrollbar-gutter: stable; }
.candidate-item .arco-tag { flex: none; }
.project-preview-body { padding: 24px; }
.project-review-material dt { margin-bottom: 10px; font-size: 14px; }
.project-review-material dd { margin-bottom: 28px; }
.mobile-application-toggle { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; border: 0; background: transparent; padding: 0; color: var(--text-strong); font: inherit; text-align: left; cursor: pointer; }
.mobile-application-toggle > span:first-child { min-width: 0; }
.mobile-application-toggle strong { display: block; margin-bottom: 6px; }
.mobile-application-toggle .candidate-task { display: block; }
.mobile-toggle-end { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; flex: none; color: rgb(var(--primary-6)); font-size: 12px; }
.mobile-application-toggle:focus-visible { outline: 2px solid rgb(var(--primary-6)); outline-offset: 4px; }
.inbox-mobile .application-heading { margin-top: 20px; }
.inbox-mobile .review-opinion { margin: 12px 0; }
@media (max-width: 768px) {
  .project-review-filters { flex-wrap: wrap; }
  .project-review-filters :deep(.arco-input-wrapper) { flex: 1 0 100%; max-width: none; }
  .project-review-filters :deep(.arco-select-view) { flex: 1; width: auto; }
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
