<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { auth } from '../auth'
import { PROPOSAL_TEMPLATE, type ChatMessage, type Task } from '../types'
import { routeId } from '../format'

const route = useRoute()
const router = useRouter()
const task = ref<Task | null>(null)
const messages = ref<ChatMessage[]>([])
const proposal = ref(PROPOSAL_TEMPLATE)
const draft = ref('')
const studentId = ref('')
const error = ref('')
const info = ref('')
const loading = ref(false)
const id = computed(() => routeId(route.params.id))
const isStudent = computed(() => auth.user?.role === 'STUDENT')
const isOwner = computed(() => auth.user?.role === 'PUBLISHER' && task.value?.publisherId === auth.user.id)

async function load() {
  if (!id.value) return
  error.value = ''
  const queryStudent = route.query.student
  if (typeof queryStudent === 'string') studentId.value = queryStudent
  task.value = await api<Task>(`/api/v1/tasks/${id.value}`)
  if (auth.token && (isStudent.value || (isOwner.value && studentId.value))) {
    const query = isOwner.value ? `?studentId=${studentId.value}` : ''
    messages.value = await api<ChatMessage[]>(`/api/v1/tasks/${id.value}/messages${query}`)
  }
}

watch(() => route.fullPath, () => load().catch((cause) => { error.value = cause instanceof Error ? cause.message : '加载失败' }), { immediate: true })

async function apply() {
  if (!auth.token) return router.push('/auth/login')
  loading.value = true
  error.value = ''
  try {
    await api(`/api/v1/tasks/${id.value}/applications`, { method: 'POST', body: JSON.stringify({ proposalMd: proposal.value }) })
    info.value = '申请书已提交'
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '提交失败'
  } finally {
    loading.value = false
  }
}

async function send() {
  loading.value = true
  error.value = ''
  try {
    await api(`/api/v1/tasks/${id.value}/messages`, {
      method: 'POST',
      body: JSON.stringify({ body: draft.value, studentId: isOwner.value ? Number(studentId.value) : null }),
    })
    draft.value = ''
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '发送失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section v-if="task" class="split">
    <article class="card stack">
      <div class="pills">
        <span class="pill accent">{{ task.roleType }}</span>
        <span class="pill">{{ task.difficulty }}</span>
        <span class="pill">名额 {{ task.filled }}/{{ task.quota }}</span>
        <span class="pill">{{ task.status }}</span>
      </div>
      <h1>{{ task.title }}</h1>
      <p class="muted">发布方 {{ task.publisherName }} · {{ task.durationWeeks }} 周 · 每周约 {{ task.estimatedHours }} 小时</p>
      <p class="note">{{ task.stipendText }}</p>
      <dl class="defs">
        <dt>背景与目标</dt><dd>{{ task.background }}</dd>
        <dt>技术栈</dt><dd>{{ task.techStack }}</dd>
        <dt>产出要求</dt><dd>{{ task.outputRequirement }}</dd>
        <dt>技术要求</dt><dd>{{ task.techRequirement }}</dd>
        <dt>仓库</dt><dd>{{ task.repoUrl || '未提供' }}</dd>
        <dt>学习资料</dt><dd>{{ task.learningMaterials || '未提供' }}</dd>
      </dl>
      <h2>PRD</h2>
      <pre class="md">{{ task.prdMd }}</pre>
      <h2>技术文档</h2>
      <pre class="md">{{ task.techDocMd || '发布方尚未补充技术文档。' }}</pre>
    </article>
    <div class="stack">
      <p v-if="error" class="banner">{{ error }}</p>
      <p v-if="info" class="note">{{ info }}</p>
      <form v-if="isStudent && !task.myApplicationId" class="card form" @submit.prevent="apply">
        <h2>申请书</h2>
        <p class="muted">先沟通再投递更好。每个任务只能投一单。</p>
        <textarea v-model="proposal" rows="12" required />
        <button class="btn primary" :disabled="loading">提交申请书</button>
      </form>
      <article v-else-if="isStudent && task.myApplicationStatus" class="card stack">
        <h2>我的申请</h2>
        <span class="pill accent">{{ task.myApplicationStatus }}</span>
        <router-link v-if="task.projectId" :to="`/me/projects/${task.projectId}`">进入工作台</router-link>
      </article>
      <article v-if="auth.token && (isStudent || isOwner)" class="card stack">
        <h2>任务私信</h2>
        <label v-if="isOwner">学生 ID<input v-model="studentId" @change="load" /></label>
        <p v-for="item in messages" :key="item.id"><strong>{{ item.senderName }}</strong> {{ item.body }}</p>
        <p v-if="messages.length === 0" class="muted">还没有消息。</p>
        <textarea v-model="draft" rows="3" placeholder="写一条消息" />
        <button class="btn primary" type="button" :disabled="loading || !draft.trim()" @click="send">发送</button>
      </article>
      <p v-else class="card muted">登录后可以私信发布方并提交申请书。</p>
    </div>
  </section>
</template>
