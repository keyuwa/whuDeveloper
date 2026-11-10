<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { api } from '../../api'
import ProposalFile from '../../components/ProposalFile.vue'
import { useProjectWorkspace } from '../../composables/useProjectWorkspace'
import { renderMarkdown } from '../../markdown'
import type { Application } from '../../types'

type AiDetail = {
  completeness?: number
  fit?: number
  feasibility?: number
  evidence?: number
  feasibilityLabel?: string
  risks?: string
}

const { project, loading, isPublisher, load } = useProjectWorkspace()
const saving = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

const isRa = computed(
  () => project.value?.taskRoleType === 'RA' || project.value?.postingMode === 'CHALLENGE',
)

const showAi = computed(() => isPublisher.value && isRa.value)

const aiDetail = computed<AiDetail | null>(() => {
  if (!project.value?.aiDetailJson) return null
  try {
    return JSON.parse(project.value.aiDetailJson) as AiDetail
  } catch {
    return null
  }
})

const aiLabel = computed(() => {
  const item = project.value
  if (!item || !showAi.value) return ''
  if (item.aiStatus === 'DONE' && item.aiScore != null) return `AI ${item.aiScore}`
  if (item.aiStatus === 'PENDING') return 'AI 评分中'
  if (item.aiStatus === 'FAILED') return 'AI 暂不可用'
  if (item.aiStatus === 'SKIPPED') return 'AI 未评分'
  return ''
})

const canRescore = computed(
  () =>
    showAi.value &&
    Boolean(project.value?.applicationId) &&
    (project.value?.aiStatus === 'SKIPPED' ||
      project.value?.aiStatus === 'FAILED' ||
      project.value?.aiStatus === 'DONE'),
)

function feasibilityTagColor(label?: string) {
  if (label === '初步可行') return 'green'
  if (label === '条件可行') return 'orange'
  if (label === '可行性存疑') return 'red'
  return 'purple'
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPoll() {
  stopPoll()
  pollTimer = setInterval(() => {
    void load(true).then(() => {
      if (project.value?.aiStatus && project.value.aiStatus !== 'PENDING') stopPoll()
    })
  }, 2500)
}

async function rescoreAi() {
  if (!project.value?.applicationId) return
  saving.value = true
  try {
    await api<Application>(`/api/v1/applications/${project.value.applicationId}/ai-rescore`, {
      method: 'POST',
    })
    Message.success('已重新发起 AI 初判')
    await load(true)
    if (project.value?.aiStatus === 'PENDING') startPoll()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '重新初判失败')
  } finally {
    saving.value = false
  }
}

watch(
  () => (showAi.value ? project.value?.aiStatus : null),
  (status) => {
    if (status === 'PENDING') startPoll()
    else stopPoll()
  },
  { immediate: true },
)

onUnmounted(stopPoll)
</script>

<template>
  <a-spin :loading="loading && !project" style="width: 100%">
    <template v-if="project">
      <div class="stack">
        <a-card class="panel-card" title="申请内容" :bordered="false">
          <article
            v-if="project.proposalMd"
            class="md-body proposal-view"
            v-html="renderMarkdown(project.proposalMd)"
          />
          <a-empty v-else description="无文字申请内容" />
          <ProposalFile
            v-if="project.proposalFileName"
            :application-id="project.applicationId"
            :file-name="project.proposalFileName"
          />
        </a-card>

        <a-card v-if="showAi" class="ai-card" title="AI 初判结果" :bordered="false">
          <a-space wrap style="margin-bottom: 12px">
            <a-tag v-if="aiLabel" color="purple">{{ aiLabel }}</a-tag>
            <a-tag
              v-if="aiDetail?.feasibilityLabel"
              :color="feasibilityTagColor(aiDetail.feasibilityLabel)"
            >
              {{ aiDetail.feasibilityLabel }}
            </a-tag>
            <a-typography-text v-if="project.aiScoredAt" type="secondary" style="font-size: 12px">
              {{ project.aiScoredAt.replace('T', ' ').slice(0, 16) }}
            </a-typography-text>
          </a-space>

          <p v-if="project.aiSummary" class="ai-summary">{{ project.aiSummary }}</p>
          <div v-if="aiDetail && project.aiStatus === 'DONE'" class="ai-dims">
            <span>完整 {{ aiDetail.completeness ?? '—' }}</span>
            <span>匹配 {{ aiDetail.fit ?? '—' }}</span>
            <span>可行 {{ aiDetail.feasibility ?? '—' }}</span>
            <span>依据 {{ aiDetail.evidence ?? '—' }}</span>
          </div>
          <p v-if="aiDetail?.risks" class="ai-risk">风险：{{ aiDetail.risks }}</p>
          <p v-if="project.aiStatus === 'SKIPPED' && project.aiSummary" class="ai-risk">{{ project.aiSummary }}</p>
          <p v-if="!project.aiSummary && project.aiStatus === 'PENDING'" class="ai-hint">正在评分，请稍候…</p>
          <p v-if="!project.aiSummary && project.aiStatus !== 'PENDING'" class="ai-hint">暂无初判结果</p>
          <p class="ai-hint">仅供参考，入项与协作判断仍由你决定。</p>

          <div v-if="canRescore" style="margin-top: 12px">
            <a-button size="small" :loading="saving || project.aiStatus === 'PENDING'" @click="rescoreAi">
              重新 AI 初判
            </a-button>
          </div>
        </a-card>
      </div>
    </template>
    <a-empty v-else-if="!loading" description="暂无申请内容" />
  </a-spin>
</template>

<style scoped>
.ai-card {
  border: 1px dashed #c4b5fd;
  background: #faf8ff;
}
.ai-summary {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #4c1d95;
}
.ai-dims {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  margin-top: 10px;
  font-size: 12px;
  font-weight: 600;
  color: #5b21b6;
}
.ai-risk {
  margin: 10px 0 0;
  font-size: 13px;
  color: #6d28d9;
}
.ai-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: #7c3aed;
  opacity: 0.85;
}
.proposal-view {
  font-size: 14px;
  line-height: 1.8;
}
.md-body :deep(p) {
  margin: 0 0 0.75em;
}
</style>
