<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'

const reviewSlaDays = ref(7)
const escalateSlaDays = ref(14)
const info = ref('')
const error = ref('')

onMounted(async () => {
  try {
    const settings = await api<{ reviewSlaDays: number; escalateSlaDays: number }>('/api/v1/admin/settings')
    reviewSlaDays.value = settings.reviewSlaDays
    escalateSlaDays.value = settings.escalateSlaDays
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  }
})

async function save() {
  error.value = ''
  try {
    await api('/api/v1/admin/settings', {
      method: 'PUT',
      body: JSON.stringify({ reviewSlaDays: reviewSlaDays.value, escalateSlaDays: escalateSlaDays.value }),
    })
    info.value = '已保存。把审核天数设为 0 后，到催办队列点「立即扫描」即可验证。'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败'
  }
}
</script>

<template>
  <form class="card form login-wrap" @submit.prevent="save">
    <div><p class="kicker">Settings</p><h1>SLA</h1></div>
    <label>申请未审催办（天）<input v-model.number="reviewSlaDays" type="number" min="0" max="60" /></label>
    <label>升级到管理队列（天）<input v-model.number="escalateSlaDays" type="number" min="0" max="120" /></label>
    <p class="muted">定时任务每分钟扫描一次。0 天表示刚提交就算逾期，方便本地验证。</p>
    <p v-if="info" class="note">{{ info }}</p>
    <p v-if="error" class="banner">{{ error }}</p>
    <button class="btn primary">保存</button>
  </form>
</template>
