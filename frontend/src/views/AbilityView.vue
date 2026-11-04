<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { api } from '../api'
import { auth } from '../auth'

const router = useRouter()

interface LeaderboardEntry {
  rank: number
  userId: number
  displayName: string
  abilityLabel: string
  abilityLevel: string
  abilityApprentice: boolean
  abilityMgmt: boolean
  points: number
}

interface AbilityApplication {
  id: number
  applicantId: number
  applicantName: string
  applicantAbilityLabel: string
  targetLevel: string
  wantApprentice: boolean
  wantMgmt: boolean
  targetLabel: string
  selfAssessment: string
  evidence: string | null
  status: string
  reviewerName: string | null
  resultLabel: string | null
  decideComment: string | null
  createdAt: string
  decidedAt: string | null
}

interface MyAbility {
  abilityLevel: string
  abilityApprentice: boolean
  abilityMgmt: boolean
  abilityLabel: string
  points: number
  myRank: number | null
  canJudge: boolean
  recentPoints: { id: number; delta: number; balanceAfter: number; reason: string; createdAt: string }[]
  applications: AbilityApplication[]
}

const loading = ref(false)
const board = ref<LeaderboardEntry[]>([])
const mine = ref<MyAbility | null>(null)
const queue = ref<AbilityApplication[]>([])
const form = reactive({
  targetLevel: 'LV2',
  wantApprentice: false,
  wantMgmt: false,
  selfAssessment: '',
  evidence: '',
})

const statusLabel: Record<string, string> = {
  SUBMITTED: '待认领',
  IN_REVIEW: '审阅中',
  NEED_INFO: '需补材料',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}

const isStudent = computed(() => auth.user?.role === 'STUDENT')
const canApply = computed(() => isStudent.value && Boolean(auth.token))

async function load() {
  loading.value = true
  try {
    board.value = await api<LeaderboardEntry[]>('/api/v1/ability/leaderboard')
    if (auth.token) {
      mine.value = await api<MyAbility>('/api/v1/ability/me')
      if (mine.value.canJudge) {
        queue.value = await api<AbilityApplication[]>('/api/v1/ability/applications/queue')
      } else {
        queue.value = []
      }
    }
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function submitApply() {
  if (form.selfAssessment.trim().length < 100) {
    Message.warning('对照细则自评请至少 100 字')
    return
  }
  try {
    await api('/api/v1/ability/applications', {
      method: 'POST',
      body: JSON.stringify({
        targetLevel: form.targetLevel,
        wantApprentice: form.wantApprentice,
        wantMgmt: form.wantMgmt,
        selfAssessment: form.selfAssessment.trim(),
        evidence: form.evidence.trim() || null,
      }),
    })
    Message.success('申请已提交')
    form.selfAssessment = ''
    form.evidence = ''
    form.wantApprentice = false
    form.wantMgmt = false
    await load()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '提交失败')
  }
}

async function claim(id: number) {
  try {
    await api(`/api/v1/ability/applications/${id}/claim`, { method: 'POST', body: '{}' })
    Message.success('已认领')
    await load()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '认领失败')
  }
}

function decide(item: AbilityApplication, decision: 'APPROVE' | 'REJECT') {
  Modal.confirm({
    title: decision === 'APPROVE' ? '通过裁定' : '驳回申请',
    content: decision === 'APPROVE'
      ? `确认将「${item.applicantName}」定为「${item.targetLabel}」？（可在下步改判）`
      : `确认驳回「${item.applicantName}」的申请？`,
    onOk: async () => {
      try {
        await api(`/api/v1/ability/applications/${item.id}/decide`, {
          method: 'POST',
          body: JSON.stringify({
            decision,
            resultLevel: item.targetLevel,
            resultApprentice: item.wantApprentice,
            resultMgmt: item.wantMgmt,
            comment: decision === 'APPROVE' ? '对照细则通过' : '材料尚不足以支撑目标档',
          }),
        })
        Message.success('已处理')
        await load()
      } catch (cause) {
        Message.error(cause instanceof Error ? cause.message : '处理失败')
        throw cause
      }
    },
  })
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="ability-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">TOP 评级</h1>
        <p class="subtitle">积分榜激发协作；能力 LV 需对照细则申请，由管理员或 LV4/LV5 裁定。</p>
      </div>
      <a-button type="outline" @click="router.push('/ability/rubric')">查看裁定细则</a-button>
    </header>

    <a-spin :loading="loading" style="width: 100%">
      <div v-if="mine" class="me-strip">
        <div>
          <span class="me-label">我的能力</span>
          <strong>{{ mine.abilityLabel }}</strong>
        </div>
        <div>
          <span class="me-label">积分</span>
          <strong>{{ mine.points }}</strong>
        </div>
        <div v-if="mine.myRank">
          <span class="me-label">排名</span>
          <strong>#{{ mine.myRank }}</strong>
        </div>
      </div>

      <section class="block">
        <h2 class="block-title">积分排行榜</h2>
        <p class="block-desc">按协作积分排序，展示昵称与能力标签。等级不由积分自动决定。</p>
        <div class="board">
          <div v-if="!board.length" class="empty">暂无上榜数据</div>
          <article v-for="item in board" :key="item.userId" class="board-row" :class="{ top: item.rank <= 3 }">
            <span class="rank">{{ item.rank }}</span>
            <div class="who">
              <strong>{{ item.displayName }}</strong>
              <span class="tag">{{ item.abilityLabel }}</span>
            </div>
            <span class="pts">{{ item.points }} 分</span>
          </article>
        </div>
      </section>

      <section v-if="canApply" id="apply" class="block">
        <h2 class="block-title">申请能力裁定</h2>
        <p class="block-desc">对照细则自评后提交；通过后更新公开能力标签。驳回后相同目标有 14 日冷却。</p>
        <div class="apply-form">
          <div class="field">
            <label>目标等级</label>
            <a-select v-model="form.targetLevel" style="width: 160px">
              <a-option v-for="lv in ['LV0', 'LV1', 'LV2', 'LV3', 'LV4', 'LV5']" :key="lv" :value="lv">{{ lv }}</a-option>
            </a-select>
          </div>
          <div class="field checks">
            <a-checkbox v-model="form.wantApprentice">见习</a-checkbox>
            <a-checkbox v-model="form.wantMgmt">管理轨 M</a-checkbox>
          </div>
          <div class="field">
            <label>对照细则自评（至少 100 字）</label>
            <a-textarea v-model="form.selfAssessment" :auto-size="{ minRows: 5, maxRows: 12 }" placeholder="逐条说明你为何达到目标档，尤其是查错/协作/交付证据" />
          </div>
          <div class="field">
            <label>证据链接与说明（可选）</label>
            <a-textarea v-model="form.evidence" :auto-size="{ minRows: 3, maxRows: 8 }" placeholder="仓库 / PR / 周报项目 / 其它可核验材料" />
          </div>
          <a-button type="primary" @click="submitApply">提交申请</a-button>
        </div>

        <div v-if="mine?.applications?.length" class="my-apps">
          <h3 class="sub-title">我的申请</h3>
          <article v-for="item in mine.applications" :key="item.id" class="app-card">
            <div class="app-head">
              <strong>{{ item.targetLabel }}</strong>
              <a-tag size="small">{{ statusLabel[item.status] || item.status }}</a-tag>
            </div>
            <p class="muted">{{ item.createdAt.replace('T', ' ').slice(0, 16) }}</p>
            <p v-if="item.resultLabel" class="muted">结果：{{ item.resultLabel }}</p>
            <p v-if="item.decideComment" class="muted">意见：{{ item.decideComment }}</p>
          </article>
        </div>
      </section>

      <section v-else-if="!auth.token" class="block">
        <p class="block-desc">登录学生账号后可申请能力裁定。</p>
      </section>

      <section v-if="mine?.canJudge" class="block">
        <h2 class="block-title">裁定队列</h2>
        <p class="block-desc">管理员与 LV4/LV5 可认领并终裁。</p>
        <div v-if="!queue.length" class="empty">暂无待处理申请</div>
        <article v-for="item in queue" :key="item.id" class="app-card review">
          <div class="app-head">
            <strong>{{ item.applicantName }}</strong>
            <span class="tag">现 {{ item.applicantAbilityLabel }} → 申 {{ item.targetLabel }}</span>
            <a-tag size="small">{{ statusLabel[item.status] || item.status }}</a-tag>
          </div>
          <p class="body">{{ item.selfAssessment }}</p>
          <p v-if="item.evidence" class="muted evidence">证据：{{ item.evidence }}</p>
          <a-space style="margin-top: 10px">
            <a-button size="small" @click="claim(item.id)">认领</a-button>
            <a-button size="small" type="primary" @click="decide(item, 'APPROVE')">通过</a-button>
            <a-button size="small" status="danger" @click="decide(item, 'REJECT')">驳回</a-button>
          </a-space>
        </article>
      </section>
    </a-spin>

  </section>
</template>

<style scoped>
.ability-page {
  width: 100%;
  max-width: 920px;
  margin: 0 auto;
}
.page-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-end; margin-bottom: 22px; }
.page-title { margin: 0; font-size: 28px; font-weight: 800; letter-spacing: -0.02em; color: #111827; }
.subtitle { margin: 8px 0 0; color: #6b7280; font-size: 14px; line-height: 1.5; max-width: 36em; }
.me-strip {
  display: flex; flex-wrap: wrap; gap: 20px 28px;
  padding: 14px 16px; margin-bottom: 22px;
  border: 1px solid #eef2f7; border-radius: 14px; background: #fff;
}
.me-label { display: block; font-size: 12px; color: #9ca3af; margin-bottom: 2px; }
.me-strip strong { font-size: 18px; color: #111827; }
.block { margin-bottom: 28px; }
.block-title { margin: 0 0 6px; font-size: 18px; font-weight: 750; color: #111827; }
.block-desc { margin: 0 0 14px; color: #6b7280; font-size: 13px; }
.board { display: flex; flex-direction: column; gap: 8px; }
.board-row {
  display: grid; grid-template-columns: 40px 1fr auto; gap: 12px; align-items: center;
  padding: 12px 14px; border-radius: 12px; background: #fff; border: 1px solid #eef2f7;
}
.board-row.top { background: linear-gradient(90deg, #eff6ff, #fff 55%); border-color: #dbeafe; }
.rank { font-weight: 800; color: #2563eb; }
.who { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.who strong { color: #111827; }
.tag { font-size: 12px; color: #1d4ed8; background: #dbeafe; border-radius: 999px; padding: 2px 8px; }
.pts { font-weight: 700; color: #111827; }
.empty { padding: 24px; color: #9ca3af; text-align: center; }
.apply-form {
  display: flex; flex-direction: column; gap: 14px;
  padding: 16px; border-radius: 14px; background: #fff; border: 1px solid #eef2f7;
}
.field label { display: block; margin-bottom: 6px; font-size: 13px; color: #4b5563; }
.checks { display: flex; gap: 16px; }
.my-apps { margin-top: 18px; display: flex; flex-direction: column; gap: 10px; }
.sub-title { margin: 0 0 4px; font-size: 14px; font-weight: 700; }
.app-card { padding: 14px; border-radius: 12px; border: 1px solid #eef2f7; background: #fff; }
.app-card.review { background: #fafafa; }
.app-head { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 6px; }
.body { margin: 8px 0; white-space: pre-wrap; line-height: 1.6; color: #374151; font-size: 13px; }
.muted { margin: 4px 0 0; color: #6b7280; font-size: 12px; }
.evidence { white-space: pre-wrap; }
</style>
