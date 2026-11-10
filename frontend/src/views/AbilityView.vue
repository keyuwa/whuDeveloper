<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { auth } from '../auth'

const router = useRouter()

interface LeaderboardEntry {
  rank: number
  userId: number
  displayName: string
  points: number
}

interface MyAbility {
  points: number
  myRank: number | null
  recentPoints: { id: number; delta: number; balanceAfter: number; reason: string; createdAt: string }[]
}

const loading = ref(false)
const board = ref<LeaderboardEntry[]>([])
const mine = ref<MyAbility | null>(null)

async function load() {
  loading.value = true
  try {
    board.value = await api<LeaderboardEntry[]>('/api/v1/ability/leaderboard')
    if (auth.token) {
      mine.value = await api<MyAbility>('/api/v1/ability/me')
    }
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="ability-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">Top Dev</h1>
        <p class="subtitle">一期先展示协作积分与排行。平台已规划 LV 等级评定，二期开放申请与裁定。</p>
      </div>
    </header>

    <a-spin :loading="loading" style="width: 100%">
      <div v-if="mine" class="me-strip">
        <div>
          <span class="me-label">我的积分</span>
          <strong>{{ mine.points }}</strong>
        </div>
        <div v-if="mine.myRank">
          <span class="me-label">排名</span>
          <strong>#{{ mine.myRank }}</strong>
        </div>
      </div>
      <div v-else-if="!auth.token" class="me-strip guest">
        <p>登录后可查看个人积分与排名。</p>
      </div>

      <section class="block">
        <h2 class="block-title">积分排行榜</h2>
        <p class="block-desc">按协作积分排序。完成任务、交付与审阅等行为会累积积分。</p>
        <div class="board">
          <div v-if="!board.length" class="empty">暂无上榜数据</div>
          <article v-for="item in board" :key="item.userId" class="board-row" :class="{ top: item.rank <= 3 }">
            <span class="rank">{{ item.rank }}</span>
            <div class="who">
              <strong>{{ item.displayName }}</strong>
            </div>
            <span class="pts">{{ item.points }} 分</span>
          </article>
        </div>
      </section>

      <section v-if="mine?.recentPoints?.length" class="block">
        <h2 class="block-title">最近积分变动</h2>
        <div class="ledger">
          <article v-for="item in mine.recentPoints" :key="item.id" class="ledger-row">
            <div>
              <strong :class="item.delta >= 0 ? 'plus' : 'minus'">
                {{ item.delta >= 0 ? '+' : '' }}{{ item.delta }}
              </strong>
              <span class="reason">{{ item.reason }}</span>
            </div>
            <span class="muted">{{ item.createdAt.replace('T', ' ').slice(0, 16) }} · 余额 {{ item.balanceAfter }}</span>
          </article>
        </div>
      </section>

      <section class="block roadmap">
        <h2 class="block-title">等级评定（二期）</h2>
        <p class="block-desc">
          Top Dev 将提供 LV0–LV5 能力等级（含见习 / 管理轨）。等级不由积分自动决定，需对照细则申请，由管理员或资深同学裁定。一期仅预告标准，申请入口二期开放。
        </p>
        <a-button type="outline" @click="router.push('/ability/rubric')">预览评定细则</a-button>
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
.page-head { margin-bottom: 22px; }
.page-title { margin: 0; font-size: 28px; font-weight: 800; letter-spacing: -0.02em; color: #111827; }
.subtitle { margin: 8px 0 0; color: #6b7280; font-size: 14px; line-height: 1.5; max-width: 40em; }
.me-strip {
  display: flex; flex-wrap: wrap; gap: 20px 28px;
  padding: 14px 16px; margin-bottom: 22px;
  border: 1px solid #eef2f7; border-radius: 14px; background: #fff;
}
.me-strip.guest p { margin: 0; color: #6b7280; font-size: 14px; }
.me-label { display: block; font-size: 12px; color: #9ca3af; margin-bottom: 2px; }
.me-strip strong { font-size: 18px; color: #111827; }
.block { margin-bottom: 28px; }
.block-title { margin: 0 0 6px; font-size: 18px; font-weight: 750; color: #111827; }
.block-desc { margin: 0 0 14px; color: #6b7280; font-size: 13px; line-height: 1.55; }
.board { display: flex; flex-direction: column; gap: 8px; }
.board-row {
  display: grid; grid-template-columns: 40px 1fr auto; gap: 12px; align-items: center;
  padding: 12px 14px; border-radius: 12px; background: #fff; border: 1px solid #eef2f7;
}
.board-row.top { background: linear-gradient(90deg, #eff6ff, #fff 55%); border-color: #dbeafe; }
.rank { font-weight: 800; color: #2563eb; }
.who strong { color: #111827; }
.pts { font-weight: 700; color: #111827; }
.empty { padding: 24px; color: #9ca3af; text-align: center; }
.ledger { display: flex; flex-direction: column; gap: 8px; }
.ledger-row {
  display: flex; flex-direction: column; gap: 4px;
  padding: 12px 14px; border-radius: 12px; background: #fff; border: 1px solid #eef2f7;
}
.ledger-row strong { margin-right: 8px; }
.plus { color: #059669; }
.minus { color: #dc2626; }
.reason { color: #374151; font-size: 13px; }
.muted { color: #9ca3af; font-size: 12px; }
.roadmap {
  padding: 16px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}
</style>
