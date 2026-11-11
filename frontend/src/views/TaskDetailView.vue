<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { auth, setSession } from '../auth'
import { useIsMobile } from '../composables/useDevice'
import { loadDicts, scheduleLabelMap } from '../composables/useDicts'
import { renderMarkdown } from '../markdown'
import {
  CHALLENGE_PROPOSAL_TEMPLATE,
  CHALLENGE_STATUS_LABEL,
  PROPOSAL_TEMPLATE,
  TASK_ROLE_LABEL,
  type Application,
  type Task,
  type User,
} from '../types'

const scheduleLabels = scheduleLabelMap()
import ProposalFile from '../components/ProposalFile.vue'
import { routeId } from '../format'

const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const task = ref<Task | null>(null)
const proposalText = ref('')
const proposalFile = ref<File | null>(null)
const myApplication = ref<Application | null>(null)
const applicationLabels: Record<string, string> = {
  SUBMITTED: '待审阅',
  ACCEPTED: '已通过',
  REJECTED: '未通过',
}
const isChallenge = computed(() => task.value?.postingMode === 'CHALLENGE' || task.value?.roleType === 'RA')
const isTeam = computed(() => task.value?.roleType === 'TEAM')
const selectedPositionId = ref<number | null>(null)
const challengeOpen = computed(() => {
  if (!task.value || !isChallenge.value) return task.value?.status === 'OPEN'
  if (task.value.status !== 'OPEN') return false
  if (task.value.challengeStatus && task.value.challengeStatus !== 'OPEN') return false
  if (task.value.bidDeadline && new Date(task.value.bidDeadline).getTime() < Date.now()) return false
  return true
})
const openPositions = computed(() =>
  (task.value?.positions || []).filter((item) => item.filled < item.quota),
)
const proposalGuideHtml = computed(() =>
  renderMarkdown(isChallenge.value ? CHALLENGE_PROPOSAL_TEMPLATE : PROPOSAL_TEMPLATE),
)
const myProposalHtml = computed(() => renderMarkdown(myApplication.value?.proposalMd || ''))
const canSubmitApplication = computed(
  () =>
    Boolean(proposalText.value.trim() || proposalFile.value) &&
    challengeOpen.value &&
    (!isTeam.value || selectedPositionId.value != null),
)
function selectProposal(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  proposalFile.value = null
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.pdf') || file.size > 5 * 1024 * 1024 || file.size === 0) {
    Message.error('请选择不超过 5 MB 的 PDF 附件')
    input.value = ''
    return
  }
  proposalFile.value = file
}
function clearProposalFile() {
  proposalFile.value = null
}
const loading = ref(false)
const id = computed(() => routeId(route.params.id))
const isStudent = computed(() => auth.user?.role === 'STUDENT')
const resumeReady = computed(() => Boolean(auth.user?.resumeReady))

const techFieldTags = computed(() =>
  (task.value?.techStack || '')
    .split(/[,，、/|]+/)
    .map((item) => item.trim())
    .filter(Boolean),
)

const programmingTags = computed(() =>
  (task.value?.programmingLanguages || '')
    .split(/[,，、/|]+/)
    .map((item) => item.trim())
    .filter(Boolean),
)

async function load() {
  if (!id.value) return
  await loadDicts()
  proposalFile.value = null
  myApplication.value = null
  selectedPositionId.value = null
  task.value = await api<Task>(`/api/v1/tasks/${id.value}`)
  if (task.value.roleType === 'TEAM') {
    const firstOpen = (task.value.positions || []).find((item) => item.filled < item.quota)
    selectedPositionId.value = firstOpen?.id ?? task.value.positions?.[0]?.id ?? null
  }
  if (isStudent.value) {
    const me = await api<User>('/api/v1/me/resume')
    if (auth.token && auth.user) setSession(auth.token, { ...auth.user, ...me })
  }
  if (isStudent.value && task.value.myApplicationId) {
    const applications = await api<Application[]>('/api/v1/me/applications')
    myApplication.value = applications.find(item => item.id === task.value?.myApplicationId) || null
  }
}

watch(
  () => route.fullPath,
  () => load().catch((cause) => Message.error(cause instanceof Error ? cause.message : '加载失败')),
  { immediate: true },
)

function scrollToApply() {
  const el = document.getElementById('apply')
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function apply() {
  if (!auth.token) return router.push('/auth/login')
  if (!resumeReady.value) {
    Message.warning('请先完善简历后再投递')
    return router.push('/me/resume')
  }
  const text = proposalText.value.trim()
  if (!challengeOpen.value) return Message.error(isChallenge.value ? '揭榜已截止或已结束' : '项目未开放申请')
  if (isTeam.value && selectedPositionId.value == null) {
    scrollToApply()
    return Message.error('请选择要申请的岗位')
  }
  if (!text && !proposalFile.value) {
    scrollToApply()
    return Message.error(isChallenge.value ? '请填写方案内容，或上传 PDF 附件' : '请填写申请内容，或上传 PDF 附件')
  }
  if (text.length > 10000) return Message.error('申请文字不超过 10000 字')
  loading.value = true
  try {
    const body = new FormData()
    body.append('proposalText', text)
    if (proposalFile.value) body.append('file', proposalFile.value)
    if (isTeam.value && selectedPositionId.value != null) {
      body.append('positionId', String(selectedPositionId.value))
    }
    await api(`/api/v1/tasks/${id.value}/applications`, {
      method: 'POST',
      body,
    })
    Message.success(isChallenge.value ? '方案已提交' : '申请已提交')
    proposalText.value = ''
    proposalFile.value = null
    await load()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '提交失败')
  } finally {
    loading.value = false
  }
}

function onMobileApplyClick() {
  if (task.value?.myApplicationId) return
  if (!proposalText.value.trim() && !proposalFile.value) {
    scrollToApply()
    Message.info(isChallenge.value ? '请先填写或上传科研方案' : '请先填写或上传申请内容')
    return
  }
  return apply()
}

async function toggleShortlist() {
  if (!auth.token) return router.push('/auth/login')
  if (!isStudent.value || !task.value) return
  loading.value = true
  try {
    if (task.value.shortlisted) {
      await api(`/api/v1/tasks/${task.value.id}/shortlist`, { method: 'DELETE' })
      Message.success('已移出备选')
    } else {
      await api(`/api/v1/tasks/${task.value.id}/shortlist`, { method: 'POST' })
      Message.success('已加入备选')
    }
    await load()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '操作失败')
  } finally {
    loading.value = false
  }
}

async function startChat() {
  if (!auth.token) return router.push('/auth/login')
  if (!isStudent.value || !id.value) return
  loading.value = true
  try {
    await api(`/api/v1/tasks/${id.value}/messages/open`, { method: 'POST' })
    await router.push({ path: '/messages', query: { taskId: String(id.value) } })
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '无法开始沟通')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section v-if="task" class="detail" :class="{ 'detail-mobile': isMobile, 'has-mobile-cta': isMobile && isStudent }">
    <router-link to="/" class="back-link">← 返回项目广场</router-link>
    <a-card class="hero-card" :bordered="false">
      <a-typography-title :heading="3" style="margin: 0 0 12px">{{ task.title }}</a-typography-title>
      <a-space wrap>
        <a-tag size="large" class="mono">{{ task.publicCode }}</a-tag>
        <a-tag color="arcoblue" size="large">{{ TASK_ROLE_LABEL[task.roleType] || task.roleType }}</a-tag>
        <a-tag v-if="task.roleType === 'INTERN'" color="orangered" size="large">{{ task.difficulty }}</a-tag>
        <a-tag v-if="isChallenge && task.challengeStatus" color="purple" size="large">
          {{ CHALLENGE_STATUS_LABEL[task.challengeStatus] || task.challengeStatus }}
        </a-tag>
        <a-tag size="large">名额 {{ task.filled }}/{{ task.quota }}</a-tag>
        <a-tag v-if="isChallenge" size="large">
          {{ task.bidDeadline ? `截止 ${task.bidDeadline.replace('T', ' ').slice(0, 16)}` : '长期开放' }}
        </a-tag>
        <a-tag v-if="isChallenge" size="large">待审方案 {{ task.pendingApplications || 0 }}</a-tag>
      </a-space>
      <div v-if="isStudent && !isMobile" class="hero-actions">
        <a-button type="primary" :loading="loading" @click="startChat">立即沟通</a-button>
        <a-button
          v-if="task.status === 'OPEN' && !task.projectId"
          :type="task.shortlisted ? 'outline' : 'secondary'"
          :loading="loading"
          @click="toggleShortlist"
        >
          {{ task.shortlisted ? '取消备选' : '加入备选' }}
        </a-button>
        <a href="#apply" class="apply-jump">
          {{ task.myApplicationId ? (isChallenge ? '查看我的方案 ↓' : '查看我的申请 ↓') : (isChallenge ? '提交科研方案 ↓' : '准备申请书 ↓') }}
        </a>
      </div>
    </a-card>

    <div class="detail-grid" :class="{ stacked: isMobile }">
      <div class="main-col stack">
        <a-card title="项目介绍">
          <a-typography-paragraph>{{ task.background }}</a-typography-paragraph>
          <a-divider v-if="task.prdFileUrl || task.prdMd" />
          <div v-if="task.prdFileUrl" class="doc-file">
            <strong>需求文档</strong>
            <a :href="task.prdFileUrl" target="_blank" rel="noopener">{{ task.prdFileName || '打开 PDF' }}</a>
          </div>
          <div v-if="task.prdMd" class="project-document">
            <template v-for="(line, index) in task.prdMd.replace(/\\n/g, '\n').split('\n')" :key="index">
              <h3 v-if="/^#{1,6}\s/.test(line)">{{ line.replace(/^#{1,6}\s+/, '') }}</h3>
              <p v-else-if="line.trim()">{{ line }}</p>
            </template>
          </div>
        </a-card>

        <a-card :title="isChallenge ? '期望产出' : '项目产出要求'">
          <pre class="md">{{ task.outputRequirement }}</pre>
        </a-card>

        <a-card :title="isChallenge ? '能力与约束' : '能力要求'">
          <pre class="md">{{ task.techRequirement }}</pre>
        </a-card>

        <a-card title="补助水平">
          <pre class="md">{{ task.stipendText || '未填写' }}</pre>
        </a-card>

        <a-card v-if="task.learningMaterials" title="学习资料">
          <pre class="md">{{ task.learningMaterials }}</pre>
        </a-card>

        <a-card v-if="task.techDocFileUrl || task.techDocMd" title="技术文档">
          <div v-if="task.techDocFileUrl" class="doc-file">
            <a :href="task.techDocFileUrl" target="_blank" rel="noopener">{{ task.techDocFileName || '打开技术文档 PDF' }}</a>
          </div>
          <pre v-if="task.techDocMd" class="md">{{ task.techDocMd }}</pre>
        </a-card>
        <a-card v-if="isStudent" id="apply" class="application-panel" :title="isChallenge ? '科研方案' : '项目申请'">
          <template v-if="!task.myApplicationId">
            <a-alert
              v-if="!resumeReady"
              type="warning"
              style="margin-bottom: 14px"
              title="投递前需先完善简历"
            >
              姓名、学校、学院、专业、在读身份与个人简介填写完整后方可申请。
              <a-button type="text" @click="router.push('/me/resume')">去完善简历</a-button>
            </a-alert>
            <p class="application-intro">
              {{ isChallenge
                ? '本科研任务采用揭榜：提交研究方案（文字或 PDF 至少一种）。多人可并行竞标，发布方比选后揭榜入项。'
                : isTeam
                  ? '请先选择岗位，再填写申请内容或上传 PDF；每个项目仅可申请一次。'
                  : '可直接填写文字申请，也可另附 PDF；至少提供其中一种。支持 Markdown。' }}
            </p>
            <div v-if="isTeam" class="position-pick" style="margin-bottom: 14px">
              <strong style="display: block; margin-bottom: 8px">选择岗位</strong>
              <a-radio-group v-model="selectedPositionId" direction="vertical">
                <a-radio
                  v-for="item in task.positions || []"
                  :key="item.id"
                  :value="item.id"
                  :disabled="item.filled >= item.quota"
                >
                  {{ item.title }} · 名额 {{ item.filled }}/{{ item.quota }}
                  <span v-if="item.requirement" style="color: #6b7280; margin-left: 8px">{{ item.requirement }}</span>
                  <a-tag v-if="item.filled >= item.quota" size="small" color="gray" style="margin-left: 8px">已满</a-tag>
                </a-radio>
              </a-radio-group>
              <p v-if="!openPositions.length" style="color: #c0362c; margin: 8px 0 0">当前岗位均已满额</p>
            </div>
            <div class="application-guide">
              <strong>结构提示（填写时可参考，不会自动写入正文）</strong>
              <article class="md-body proposal-guide" v-html="proposalGuideHtml" />
            </div>
            <a-textarea
              v-model="proposalText"
              :auto-size="{ minRows: 8, maxRows: 18 }"
              :disabled="loading || !challengeOpen || !resumeReady"
              :placeholder="isChallenge ? '在此填写科研方案…' : '在此填写申请内容…'"
              style="margin-bottom: 12px"
            />
            <label class="upload-area">
              <strong>{{ proposalFile ? proposalFile.name : '可选：上传 PDF 附件' }}</strong>
              <span>PDF 格式，最大 5 MB · 可不传</span>
              <input type="file" accept=".pdf,application/pdf" :disabled="loading || !challengeOpen || !resumeReady" @change="selectProposal" />
            </label>
            <div v-if="proposalFile" class="application-actions" style="margin-top: 8px">
              <button type="button" class="linkish" @click="clearProposalFile">移除附件</button>
            </div>
            <div class="application-actions">
              <span>{{ isChallenge ? '每个科研任务仅可提交一次方案。' : '每个项目仅可申请一次，请核对后提交。' }}</span>
              <a-button type="primary" :loading="loading" :disabled="!canSubmitApplication || !resumeReady" @click="apply">
                {{ challengeOpen ? (isChallenge ? '提交方案' : '提交申请') : (isChallenge ? '揭榜已截止' : '项目已关闭申请') }}
              </a-button>
            </div>
          </template>
          <template v-else>
            <div class="application-actions"><a-tag color="arcoblue">{{ applicationLabels[task.myApplicationStatus || ''] || task.myApplicationStatus }}</a-tag><a-button v-if="task.projectId" type="primary" @click="router.push(`/me/projects/${task.projectId}/progress`)">进入工作台</a-button></div>
            <p class="application-intro">
              <template v-if="myApplication?.positionTitle">已申请岗位：{{ myApplication.positionTitle }} · </template>
              {{ task.myApplicationStatus === 'SUBMITTED'
                ? (isChallenge ? '方案已提交，请等待发布方比选揭榜。' : '申请已提交，请等待发布方审阅。')
                : task.myApplicationStatus === 'ACCEPTED'
                  ? (isChallenge ? '已中选入项，可在工作台推进协作。' : '申请已通过，可在工作台查看申请并提交周报。')
                  : (isChallenge ? '本次未中选，可查看反馈并探索其他科研任务。' : '本次申请未通过，可查看反馈并探索其他项目。') }}
            </p>
            <article v-if="myApplication?.proposalMd" class="md-body proposal-view" v-html="myProposalHtml" />
            <ProposalFile v-if="myApplication?.proposalFileName" :application-id="myApplication.id" :file-name="myApplication.proposalFileName" />
            <p v-if="myApplication?.reviewNote" class="application-intro">审阅意见：{{ myApplication.reviewNote }}</p>
          </template>
        </a-card>
      </div>

      <aside class="side-col stack">
        <a-card title="项目信息">
          <div class="meta-list">
            <div class="meta-row">
              <span class="meta-label">发布方</span>
              <button
                v-if="isStudent || !auth.token"
                type="button"
                class="publisher-link"
                @click="startChat"
              >
                {{ task.publisherName }}
                <span>沟通</span>
              </button>
              <span v-else>{{ task.publisherName }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">任务类型</span>
              <span>{{ TASK_ROLE_LABEL[task.roleType] || task.roleType }}</span>
            </div>
            <div v-if="task.scheduleType" class="meta-row">
              <span class="meta-label">{{ task.roleType === 'TA' ? '学期' : '科研周期' }}</span>
              <span>{{ scheduleLabels[task.scheduleType] || task.scheduleType }}</span>
            </div>
            <div v-if="task.roleType === 'TA' && task.educationLevels" class="meta-row">
              <span class="meta-label">在读身份</span>
              <span>{{ task.educationLevels }}</span>
            </div>
            <div v-if="isTeam" class="meta-row">
              <span class="meta-label">岗位</span>
              <div>
                <a-tag
                  v-for="item in task.positions || []"
                  :key="item.id"
                  color="arcoblue"
                  size="small"
                  style="margin: 0 6px 6px 0"
                >
                  {{ item.title }} {{ item.filled }}/{{ item.quota }}
                </a-tag>
              </div>
            </div>
            <div v-else-if="task.roleType !== 'TA'" class="meta-row">
              <span class="meta-label">{{ task.roleType === 'RA' ? '研究方向' : '技术领域' }}</span>
              <div>
                <a-tag color="arcoblue" size="small" style="margin-right: 6px">{{ task.techField }}</a-tag>
                <a-tag v-for="tag in techFieldTags" :key="tag" size="small" style="margin: 0 6px 6px 0">{{ tag }}</a-tag>
              </div>
            </div>
            <div v-if="task.roleType !== 'TA' && !isTeam" class="meta-row">
              <span class="meta-label">编程语言</span>
              <div>
                <a-tag v-for="tag in programmingTags" :key="tag" color="green" size="small" style="margin: 0 6px 6px 0">{{ tag }}</a-tag>
              </div>
            </div>
            <div v-if="task.roleType === 'INTERN'" class="meta-row">
              <span class="meta-label">项目难度</span>
              <span>{{ task.difficulty }}</span>
            </div>
            <div v-if="task.roleType === 'INTERN' && task.repoUrl" class="meta-row">
              <span class="meta-label">仓库</span>
              <a :href="task.repoUrl" target="_blank" rel="noopener">{{ task.repoUrl }}</a>
            </div>
            <div v-if="isChallenge" class="meta-row">
              <span class="meta-label">揭榜截止</span>
              <span>{{ task.bidDeadline ? task.bidDeadline.replace('T', ' ').slice(0, 16) : '长期开放' }}</span>
            </div>
            <div v-if="!isChallenge" class="meta-row">
              <span class="meta-label">{{ task.roleType === 'INTERN' ? '开发周期' : '周期' }}</span>
              <span>{{ task.durationWeeks }} 周</span>
            </div>
            <div v-if="!isChallenge" class="meta-row">
              <span class="meta-label">预估工时</span>
              <span>每周约 {{ task.estimatedHours }} 小时</span>
            </div>
          </div>
        </a-card>

        <a-card v-if="!auth.token">
          <a-typography-text type="secondary">登录后可与发布方沟通并提交申请。</a-typography-text>
          <a-space style="margin-top: 12px">
            <a-button type="primary" @click="router.push('/auth/login')">登录</a-button>
            <a-button @click="router.push('/auth/register')">注册</a-button>
          </a-space>
        </a-card>
      </aside>
    </div>

    <div v-if="isMobile && isStudent" class="mobile-cta-bar">
      <a-button
        v-if="task.status === 'OPEN' && !task.projectId"
        :type="task.shortlisted ? 'outline' : 'secondary'"
        :loading="loading"
        @click="toggleShortlist"
      >
        {{ task.shortlisted ? '已备选' : '备选' }}
      </a-button>
      <a-button :loading="loading" @click="startChat">沟通</a-button>
      <a-button
        type="primary"
        :loading="loading"
        :disabled="Boolean(task.myApplicationId) || !challengeOpen"
        @click="onMobileApplyClick"
      >
        {{ task.myApplicationId ? (isChallenge ? '已交方案' : '已申请') : (isChallenge ? '提交方案' : '立即申请') }}
      </a-button>
    </div>
  </section>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.hero-card {
  background: linear-gradient(135deg, #f7fbff, #ffffff);
}
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 0.9fr);
  gap: 16px;
  align-items: start;
}
.meta-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.meta-row {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 10px;
  align-items: start;
  font-size: 14px;
}
.meta-label {
  color: var(--color-text-3);
  line-height: 24px;
}
@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<style scoped>
.detail { max-width: 1080px; width: 100%; margin: 0 auto; gap: 26px; padding: 10px 0 40px; box-sizing: border-box; }
.back-link { color: #737e90; font-size: 13px; width: fit-content; }
.hero-card { background: #fff; border-radius: 4px; border-top: 4px solid #2563b5; }
.hero-card :deep(.arco-card-body) { padding: 34px 38px; }
.hero-card :deep(h3) { font-size: 28px; line-height: 1.5; }
.detail-grid { grid-template-columns: minmax(0, 1fr) 310px; gap: 28px; }
.main-col { gap: 24px; }
.main-col :deep(.arco-card) { border: 1px solid #eef2f7; border-radius: 16px; box-shadow: 0 1px 2px rgba(15,23,42,0.04); }
.main-col :deep(.arco-card-header) { margin: 0 30px; padding: 25px 0 18px; height: auto; border-bottom: 1px solid #f1f5f9; }
.main-col :deep(.arco-card-header-title) { font-size: 19px; font-weight: 700; color: #111827; border-left: 0; padding-left: 0; }
.main-col :deep(.arco-card-body) { padding: 24px 30px 32px; }
.main-col :deep(.arco-typography), .md, .project-document { font-size: 14px; line-height: 2; color: #4b5563; overflow-wrap: anywhere; white-space: pre-wrap; }
.project-document h3 { font-size: 16px; margin: 24px 0 10px; color: #111827; }
.project-document h3:first-child { margin-top: 0; }
.project-document p { margin-bottom: 10px; }
.side-col :deep(.arco-card) { border: 1px solid #eef2f7; border-radius: 16px; box-shadow: 0 1px 2px rgba(15,23,42,0.04); }
.side-col :deep(.arco-card-header) { background: #f9fafb; }
.meta-row { grid-template-columns: 70px minmax(0,1fr); line-height: 1.8; overflow-wrap: anywhere; }
.meta-list { gap: 20px; }
.doc-file { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; padding: 12px 14px; border: 1px solid #e6ebf1; border-radius: 6px; background: #f8fafc; }
@media (max-width: 900px) { .detail-grid { grid-template-columns: 1fr; }.hero-card :deep(.arco-card-body) { padding: 24px; }.main-col :deep(.arco-card-header) { margin: 0 20px; }.main-col :deep(.arco-card-body) { padding: 20px; } }
</style>

<style scoped>
.hero-card { border-top: 0; background: transparent; }
.hero-card :deep(.arco-card-body) { padding: 8px 0 18px; border-bottom: 1px solid #e1e7ee; }
.hero-card :deep(h3) { font-size: 26px; }
.hero-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; margin-top: 18px; }
.apply-jump { font-size: 13px; color: #2868b7; }
.application-panel { scroll-margin-top: 24px; scroll-margin-bottom: 96px; }
.application-intro { color: #657386; font-size: 14px; line-height: 1.8; margin: 14px 0; }
.application-guide { background: #f6f8fb; padding: 16px; margin: 16px 0; line-height: 1.9; font-size: 13px; }
.application-guide > strong { display: block; margin-bottom: 8px; color: #374151; }
.proposal-guide :deep(h2) {
  font-size: 14px;
  font-weight: 650;
  color: #111827;
  margin: 10px 0 4px;
  padding: 0;
  border: 0;
  line-height: 1.5;
}
.proposal-guide :deep(h2:first-child) { margin-top: 0; }
.proposal-guide :deep(p) { margin: 0; color: #6b7280; font-size: 12px; }
.proposal-view {
  margin: 12px 0;
  padding: 16px 18px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  color: #374151;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.proposal-view :deep(h1),
.proposal-view :deep(h2),
.proposal-view :deep(h3) {
  color: #111827;
  margin: 0.9em 0 0.4em;
  line-height: 1.4;
}
.proposal-view :deep(h2) {
  font-size: 1.05em;
  padding-bottom: 0.25em;
  border-bottom: 1px solid #e8edf3;
}
.proposal-view :deep(h2:first-child),
.proposal-view :deep(h3:first-child) { margin-top: 0; }
.proposal-view :deep(p) { margin: 0.45em 0; }
.proposal-view :deep(ul),
.proposal-view :deep(ol) { padding-left: 1.3em; margin: 0.4em 0; }
.upload-area { display: flex; flex-direction: column; gap: 12px; border: 1px dashed #a8bdd5; border-radius: 6px; padding: 24px; overflow-wrap: anywhere; }
.upload-area span { font-size: 12px; color: #778497; }
.upload-area input { max-width: 100%; }
.application-actions { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-top: 18px; }
.application-actions > span { font-size: 12px; color: #778497; }
.linkish { border: 0; background: transparent; color: #2868b7; cursor: pointer; padding: 0; font-size: 13px; }
.publisher-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  padding: 0;
  background: transparent;
  color: #2868b7;
  cursor: pointer;
  font-size: 14px;
}
.publisher-link span {
  font-size: 12px;
  color: #fff;
  background: #2563b5;
  border-radius: 4px;
  padding: 1px 6px;
}
@media (max-width: 600px) { .application-actions { align-items: flex-start; flex-direction: column; }.upload-area { padding: 16px; } }
.detail-grid { min-width: 0; width: 100%; }
.detail-grid.stacked { grid-template-columns: minmax(0, 1fr) !important; }
.main-col,
.side-col {
  min-width: 0;
  width: 100%;
  max-width: 100%;
}
.detail-mobile :deep(.arco-card) {
  max-width: 100%;
}
.doc-file a {
  display: block;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.detail-mobile.has-mobile-cta { padding-bottom: calc(72px + var(--bottom-inset, 0px)); }
.mobile-cta-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  max-width: 100vw;
  box-sizing: border-box;
  z-index: 55;
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr);
  gap: 8px;
  padding: 10px 12px calc(10px + var(--bottom-inset, 0px));
  background: rgba(255, 255, 255, 0.98);
  border-top: 1px solid #eef2f7;
  box-shadow: 0 -4px 20px rgba(15, 23, 42, 0.06);
}
.mobile-cta-bar :deep(.arco-btn) {
  min-width: 0;
}
</style>
