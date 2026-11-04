<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import ProposalFile from '../components/ProposalFile.vue'
import { renderMarkdown } from '../markdown'
import type { Application } from '../types'

const router = useRouter()
const items = ref<Application[]>([])
const note = ref('')
const loading = ref(false)

async function load() {
  items.value = await api<Application[]>('/api/v1/publisher/applications')
}

onMounted(() => {
  load().catch((cause) => Message.error(cause instanceof Error ? cause.message : '加载失败'))
})

async function review(id: number, decision: 'ACCEPT' | 'REJECT') {
  loading.value = true
  try {
    await api(`/api/v1/applications/${id}/review`, {
      method: 'POST',
      body: JSON.stringify({ decision, note: note.value }),
    })
    await load()
    Message.success(decision === 'ACCEPT' ? '已通过' : '已拒绝')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <a-typography-title :heading="4" style="margin: 0">申请审阅</a-typography-title>
        <p class="subtitle">查看简历与申请，通过或拒绝；需要沟通可打开对话。</p>
      </div>
    </header>
    <a-empty v-if="items.length === 0" description="暂无待审申请" />
    <div v-else class="stack">
      <a-card v-for="item in items" :key="item.id">
        <div style="display: flex; justify-content: space-between; gap: 12px; align-items: center">
          <a-typography-title :heading="6" style="margin: 0">
            {{ item.realName || item.studentName }}
            <template v-if="item.studentIdentity"> · {{ item.studentIdentity }}</template>
          </a-typography-title>
          <a-tag color="arcoblue">{{ item.status }}</a-tag>
        </div>
        <a-typography-paragraph type="secondary">{{ item.taskTitle }} · 催办 {{ item.remindCount }} 次</a-typography-paragraph>
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
          <a-input v-model="note" placeholder="评语" style="margin: 12px 0" />
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
            <a-button type="primary" :loading="loading" @click="review(item.id, 'ACCEPT')">通过</a-button>
          </div>
        </template>
        <a-button v-if="item.projectId" type="outline" style="margin-top: 12px" @click="router.push(`/publisher/projects/${item.projectId}/detail`)">
          打开项目
        </a-button>
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
</style>
