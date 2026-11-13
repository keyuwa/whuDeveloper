<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
const props = defineProps<{ applicationId: number; fileName: string; preview?: boolean; inline?: boolean; compact?: boolean }>()
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
  <div class="proposal-file" :class="{ compact }">
    <span class="pdf-mark">PDF</span><span class="file-name">{{ fileName }}</span>
    <div v-if="compact" class="file-actions">
      <a-button v-if="preview" type="primary" :loading="loading" @click="open(false)">预览申请书</a-button>
      <a-button :loading="loading" aria-label="下载申请书" @click="open(true)">下载</a-button>
    </div>
    <template v-else>
      <a-button v-if="preview" :loading="loading" @click="open(false)">预览申请书</a-button>
      <a-button :loading="loading" @click="open(true)">下载申请书</a-button>
    </template>
    <iframe v-if="inline && visible && url" :src="url" title="申请书 PDF" class="pdf-frame" />
  </div>
  <a-modal v-if="!inline && visible" v-model:visible="visible" fullscreen :title="fileName" :footer="false">
    <a :href="url" :download="fileName">无法预览时下载 PDF</a>
    <iframe :src="url" title="申请书 PDF" class="pdf-frame full-preview" />
  </a-modal>
</template>
<style scoped>
.proposal-file { display: flex; align-items: center; gap: 12px; padding: 16px; background: var(--bg-subtle); border: 1px solid var(--surface-line); border-radius: var(--field-radius); flex-wrap: wrap; }
.pdf-mark { color: #b04b3d; font-size: 11px; font-weight: 700; }
.file-name { flex: 1; min-width: 100px; overflow-wrap: anywhere; font-size: 13px; }
.proposal-file.compact { padding: 12px; gap: 8px; }
.compact .file-name { min-width: 0; }
.file-actions { flex-basis: 100%; display: flex; gap: 8px; }
.file-actions :deep(.arco-btn) { min-height: 44px; }
.file-actions :deep(.arco-btn-primary) { flex: 1; }
.pdf-frame { width: 100%; min-width: 0; height: 32rem; border: 0; }
.full-preview { height: calc(100dvh - 115px); }
</style>
