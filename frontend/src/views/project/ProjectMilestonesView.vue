<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import ProposalFile from '../../components/ProposalFile.vue'
import { useProjectWorkspace } from '../../composables/useProjectWorkspace'
import { renderMarkdown } from '../../markdown'

const { project, saving, isStudent, isPublisher, run } = useProjectWorkspace()

const content = ref('')
const blockers = ref('')
const plan = ref('')
const supportNeeded = ref('')

const feedbackDraft = reactive<Record<number, { score: number | undefined; comment: string }>>({})

const reportStatusLabel: Record<string, string> = {
  SUBMITTED: '待反馈',
  SCORED: '已反馈',
}

const placeholders = computed(() => {
  const role = project.value?.taskRoleType || 'INTERN'
  if (role === 'TA') {
    return {
      content: '例：答疑 X 次 / 批改作业 / 更新课件',
      blockers: '例：无；或课堂平台权限 / 学生材料缺失',
      plan: '例：下周习题课准备 / 批改截止',
      support: '例：需要发布方确认评分标准',
    }
  }
  if (role === 'RA') {
    return {
      content: '例：跑完实验 A / 读文献 / 整理数据',
      blockers: '例：无；或数据权限 / 算力 / 方法不确定',
      plan: '例：对比实验 B / 写方法小节',
      support: '例：需要确认实验设计',
    }
  }
  return {
    content: '例：完成 XX 接口 / 提 PR #n / 联调',
    blockers: '例：无；或需求边界不清 / 本地环境',
    plan: '例：补测试 / 合入主分支',
    support: '例：需要确认接口字段',
  }
})

const sortedReports = computed(() => {
  const list = project.value?.reports ?? []
  return [...list].sort((a, b) => b.weekIndex - a.weekIndex)
})

const lastPlan = computed(() => {
  const list = project.value?.reports ?? []
  if (!list.length) return null
  const latest = [...list].sort((a, b) => b.weekIndex - a.weekIndex)[0]
  return latest?.plan?.trim() || null
})

function draftFor(reportId: number) {
  if (!feedbackDraft[reportId]) {
    feedbackDraft[reportId] = { score: undefined, comment: '' }
  }
  return feedbackDraft[reportId]
}

function formatDate(value: string | null | undefined) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value.slice(0, 10)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function hasBlocker(text: string | null | undefined) {
  const v = (text || '').trim()
  return v.length > 0 && v !== '无'
}

const canSubmit = computed(() => !!content.value.trim())

async function submitReport() {
  if (!project.value || !canSubmit.value) return
  await run(`/api/v1/projects/${project.value.id}/reports`, {
    content: content.value.trim(),
    blockers: blockers.value.trim(),
    plan: plan.value.trim(),
    supportNeeded: supportNeeded.value.trim() || null,
  })
  content.value = ''
  blockers.value = ''
  plan.value = ''
  supportNeeded.value = ''
}

async function feedbackReport(reportId: number) {
  if (!project.value) return
  const draft = draftFor(reportId)
  await run(`/api/v1/projects/${project.value.id}/reports/${reportId}/score`, {
    score: draft.score ?? null,
    comment: draft.comment.trim() || null,
  })
}
</script>

<template>
  <section v-if="project" class="stack">
    <a-collapse v-if="project.proposalMd || project.proposalFileName" :bordered="false" class="legacy-proposal">
      <a-collapse-item key="proposal" header="查看申请内容">
        <article v-if="project.proposalMd" class="md-body proposal-view" v-html="renderMarkdown(project.proposalMd)" />
        <div v-if="project.proposalFileName" class="proposal-reference">
          <span>PDF 附件</span>
          <ProposalFile :application-id="project.applicationId" :file-name="project.proposalFileName" />
        </div>
      </a-collapse-item>
    </a-collapse>

    <a-card class="panel-card" title="周报" :bordered="false">
      <div v-if="isStudent && project.status === 'ACTIVE'" class="submit-box">
        <div class="focus-label" style="margin-bottom: 10px">提交本周周报</div>
        <div v-if="lastPlan" class="last-plan">
          <div class="focus-label">对照上周计划</div>
          <p class="last-plan-body">{{ lastPlan }}</p>
        </div>
        <div class="stack">
          <p class="muted">卡点请写进「风险与阻塞」；需要发布方拍板的写进「需要支持」。临时沟通仍可用私信。</p>
          <div>
            <div class="field-label">本周完成</div>
            <a-textarea v-model="content" :placeholder="placeholders.content" :auto-size="{ minRows: 3 }" />
          </div>
          <div>
            <div class="field-label">风险与阻塞（可选）</div>
            <a-textarea v-model="blockers" :placeholder="placeholders.blockers" :auto-size="{ minRows: 2 }" />
          </div>
          <div>
            <div class="field-label">下周计划（可选）</div>
            <a-textarea v-model="plan" :placeholder="placeholders.plan" :auto-size="{ minRows: 2 }" />
          </div>
          <div>
            <div class="field-label">需要发布方支持（可选）</div>
            <a-textarea v-model="supportNeeded" :placeholder="placeholders.support" :auto-size="{ minRows: 2 }" />
          </div>
          <div>
            <a-button type="primary" :loading="saving" :disabled="!canSubmit" @click="submitReport">提交周报</a-button>
          </div>
        </div>
      </div>

      <div v-if="sortedReports.length" class="report-list" :class="{ 'with-submit': isStudent && project.status === 'ACTIVE' }">
        <article
          v-for="item in sortedReports"
          :key="item.id"
          class="report-item"
          :class="{ pending: item.status === 'SUBMITTED' }"
        >
          <div class="report-head">
            <strong>第 {{ item.weekIndex }} 周</strong>
            <span v-if="item.createdAt" class="muted date">{{ formatDate(item.createdAt) }}</span>
            <a-tag size="small" :color="item.status === 'SUBMITTED' ? 'orangered' : 'green'">
              {{ reportStatusLabel[item.status] || item.status }}
              <span v-if="item.score"> · {{ item.score }}/5</span>
            </a-tag>
          </div>

          <div class="report-section">
            <div class="section-label">本周完成</div>
            <p class="report-body">{{ item.content }}</p>
          </div>
          <div class="report-section" :class="{ alert: hasBlocker(item.blockers) && item.status === 'SUBMITTED' }">
            <div class="section-label">风险与阻塞</div>
            <p class="report-body">{{ item.blockers || '—' }}</p>
          </div>
          <div class="report-section">
            <div class="section-label">下周计划</div>
            <p class="report-body">{{ item.plan || '—' }}</p>
          </div>
          <div v-if="item.supportNeeded" class="report-section" :class="{ alert: item.status === 'SUBMITTED' }">
            <div class="section-label">需要支持</div>
            <p class="report-body">{{ item.supportNeeded }}</p>
          </div>
          <p v-if="item.scoreComment" class="muted feedback-note">反馈：{{ item.scoreComment }}</p>

          <a-space v-if="isPublisher && item.status === 'SUBMITTED'" style="margin-top: 10px" wrap>
            <a-input-number
              v-model="draftFor(item.id).score"
              :min="1"
              :max="5"
              placeholder="评分"
              allow-clear
              style="width: 100px"
            />
            <a-input v-model="draftFor(item.id).comment" placeholder="评语（可选）" style="width: 220px" />
            <a-button type="primary" size="small" :loading="saving" @click="feedbackReport(item.id)">
              确认已阅
            </a-button>
          </a-space>
        </article>
      </div>
      <a-empty v-else description="暂无周报" />
    </a-card>
  </section>
</template>

<style scoped>
.panel-card {
  background: var(--color-bg-2);
  border-radius: 10px;
}
.focus-label {
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--color-text-3);
  text-transform: uppercase;
}
.field-label {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--color-text-2);
}
.submit-box {
  margin-bottom: 8px;
  padding: 0 0 20px;
  border-bottom: 1px solid #e7ebf0;
}
.last-plan {
  margin-bottom: 14px;
  padding: 12px 14px;
  background: #f7f9fb;
  border: 1px solid #e7ebf0;
  border-radius: 6px;
}
.last-plan-body {
  margin: 8px 0 0;
  line-height: 1.7;
  color: var(--color-text-1);
  white-space: pre-wrap;
}
.report-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.report-list.with-submit {
  margin-top: 8px;
}
.report-item {
  padding: 16px 0 20px;
  border-bottom: 1px solid #edf0f4;
}
.report-item.pending {
  background: linear-gradient(90deg, #fff8f3 0%, transparent 48px);
  padding-left: 10px;
  margin-left: -10px;
}
.report-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}
.report-head .date {
  font-size: 12px;
}
.report-section {
  margin-bottom: 8px;
}
.report-section.alert .section-label,
.report-section.alert .report-body {
  color: #c73e0a;
}
.section-label {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 2px;
}
.report-body {
  margin: 0;
  line-height: 1.7;
  color: var(--color-text-1);
  white-space: pre-wrap;
}
.feedback-note {
  margin: 8px 0 0;
}
.panel-card {
  border-radius: 4px;
  border: 1px solid #e7ebf0;
}
.proposal-reference {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: #687789;
  font-size: 13px;
}
.proposal-reference :deep(.proposal-file) {
  padding: 8px 10px;
  background: transparent;
  border: 0;
}
.proposal-reference :deep(.file-name) {
  max-width: 190px;
}
.legacy-proposal {
  background: #fff;
}
@media (max-width: 700px) {
  .proposal-reference {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
