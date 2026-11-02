<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import type { Task } from '../types'

const route = useRoute()
const router = useRouter()
const error = ref('')
const loading = ref(false)
const form = reactive({
  title: '',
  roleType: 'INTERN',
  difficulty: 'L3',
  background: '',
  techStack: '',
  outputRequirement: '',
  techRequirement: '',
  durationWeeks: 8,
  estimatedHours: 10,
  repoUrl: '',
  learningMaterials: '',
  stipendText: '津贴标准由实验室公布，本页只展示，不在线发放。',
  prdMd: '## 背景\n\n## 目标\n\n## 验收\n',
  techDocMd: '',
  quota: 1,
})

onMounted(async () => {
  if (!route.params.id) return
  const task = await api<Task>(`/api/v1/tasks/${route.params.id}`)
  Object.assign(form, task)
})

async function save(publish: boolean) {
  loading.value = true
  error.value = ''
  try {
    const path = route.params.id ? `/api/v1/publisher/tasks/${route.params.id}` : `/api/v1/publisher/tasks?publish=${publish}`
    const method = route.params.id ? 'PUT' : 'POST'
    const task = await api<Task>(path, { method, body: JSON.stringify(form) })
    if (route.params.id && publish) await api(`/api/v1/publisher/tasks/${task.id}/publish`, { method: 'POST' })
    await router.push('/publisher/tasks')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="card form" @submit.prevent="save(true)">
    <div><p class="kicker">Task</p><h1>{{ route.params.id ? '编辑任务' : '新任务' }}</h1></div>
    <label>名称<input v-model="form.title" required /></label>
    <div class="row">
      <label>类型<select v-model="form.roleType"><option>TA</option><option>INTERN</option><option>RA</option></select></label>
      <label>难度<select v-model="form.difficulty"><option>L1</option><option>L2</option><option>L3</option><option>L4</option><option>L5</option></select></label>
      <label>名额<input v-model.number="form.quota" type="number" min="1" /></label>
      <label>周期（周）<input v-model.number="form.durationWeeks" type="number" min="1" /></label>
      <label>每周工时<input v-model.number="form.estimatedHours" type="number" min="1" /></label>
    </div>
    <label>背景与目标<textarea v-model="form.background" required /></label>
    <label>技术栈<input v-model="form.techStack" required /></label>
    <label>产出要求<textarea v-model="form.outputRequirement" required /></label>
    <label>技术要求<textarea v-model="form.techRequirement" required /></label>
    <label>仓库<input v-model="form.repoUrl" /></label>
    <label>学习资料<textarea v-model="form.learningMaterials" /></label>
    <label>补助说明<input v-model="form.stipendText" required /></label>
    <label>PRD Markdown<textarea v-model="form.prdMd" rows="8" required /></label>
    <label>技术文档 Markdown<textarea v-model="form.techDocMd" rows="6" /></label>
    <p v-if="error" class="banner">{{ error }}</p>
    <div class="actions">
      <button class="btn" type="button" :disabled="loading" @click="save(false)">保存草稿</button>
      <button class="btn primary" :disabled="loading">上架</button>
    </div>
  </form>
</template>
