<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
const props = defineProps<{ applicationId: number; fileName: string; preview?: boolean; inline?: boolean }>()
const loading = ref(false)
const url = ref('')
const visible = ref(false)
let request: AbortController | undefined

function reset() {
  request?.abort()
  request = undefined
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
  loading.value = false
  visible.value = false
}
watch(() => props.applicationId, reset)
onBeforeUnmount(reset)

async function open(download: boolean) {
  if (loading.value) return
  const controller = new AbortController()
  request = controller
  loading.value = true
  try {
    if (!url.value) {
      const response = await fetch(`/api/v1/applications/${props.applicationId}/proposal`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
        signal: controller.signal,
      })
      if (!response.ok) throw new Error(response.status === 403 ? '无权查看该申请书' : '无法读取申请书，请重试')
      const blob = await response.blob()
      if (controller.signal.aborted) return
      url.value = URL.createObjectURL(blob)
    }
    if (download) {
      const link = document.createElement('a')
      link.href = url.value
      link.download = props.fileName
      link.click()
    } else {
      visible.value = true
    }
  } catch (error) {
    if (!controller.signal.aborted) Message.error(error instanceof Error ? error.message : '无法读取申请书')
  } finally {
    if (request === controller) loading.value = false
  }
}
</script>
<template>
  <div class="proposal-file">
    <span class="pdf-mark">PDF</span><span class="file-name">{{ fileName }}</span>
    <a-button v-if="preview" :loading="loading" @click="open(false)">预览申请书</a-button>
    <a-button :loading="loading" @click="open(true)">下载申请书</a-button>
    <iframe v-if="inline && visible && url" :src="url" title="申请书 PDF" class="pdf-frame" />
  </div>
  <a-modal v-if="!inline && visible" v-model:visible="visible" fullscreen :title="fileName" :footer="false">
    <a :href="url" :download="fileName">无法预览时下载 PDF</a>
    <iframe :src="url" title="申请书 PDF" class="pdf-frame full-preview" />
  </a-modal>
</template>
<style scoped>
.proposal-file { display: flex; align-items: center; gap: 12px; padding: 16px; background: #f5f8fc; border: 1px solid #e2e8f0; border-radius: 6px; flex-wrap: wrap; }
.pdf-mark { color: #b04b3d; font-size: 11px; font-weight: 700; }
.file-name { flex: 1; min-width: 100px; overflow-wrap: anywhere; font-size: 13px; }
.pdf-frame { width: 100%; min-width: 0; height: 32rem; border: 0; }
.full-preview { height: calc(100dvh - 115px); }
</style>
