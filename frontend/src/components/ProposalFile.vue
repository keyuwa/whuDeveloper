<script setup lang="ts">
import { ref } from 'vue'
import { Message } from '@arco-design/web-vue'
const props = defineProps<{ applicationId: number; fileName: string }>()
const loading = ref(false)
async function download() {
  loading.value = true
  try {
    const response = await fetch(`/api/v1/applications/${props.applicationId}/proposal`, {
      headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
    })
    if (!response.ok) throw new Error(response.status === 403 ? '无权查看该申请书' : '申请书下载失败，请重新登录或稍后重试')
    const url = URL.createObjectURL(await response.blob())
    const link = document.createElement('a')
    link.href = url
    link.download = props.fileName
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 10000)
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '下载失败')
  } finally {
    loading.value = false
  }
}
</script>
<template>
  <div class="proposal-file"><span class="pdf-mark">PDF</span><span class="file-name">{{ fileName }}</span><a-button :loading="loading" @click="download">下载申请书</a-button></div>
</template>
<style scoped>
.proposal-file { display: flex; align-items: center; gap: 12px; padding: 16px; background: #f5f8fc; border: 1px solid #e2e8f0; border-radius: 6px; flex-wrap: wrap; }
.pdf-mark { color: #b04b3d; font-size: 11px; font-weight: 700; }
.file-name { flex: 1; min-width: 100px; overflow-wrap: anywhere; font-size: 13px; }
</style>
