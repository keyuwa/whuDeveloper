<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { renderMarkdown } from '../markdown'

const router = useRouter()
const loading = ref(false)
const markdown = ref('')
const html = computed(() => renderMarkdown(markdown.value))

onMounted(async () => {
  loading.value = true
  try {
    const res = await api<{ markdown: string }>('/api/v1/ability/rubric')
    markdown.value = res.markdown
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '无法加载细则')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="rubric-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">能力裁定细则</h1>
        <p class="subtitle">WHU TOP Developer 统一能力标准与申请流程说明。</p>
      </div>
      <a-button type="outline" @click="router.push('/ability')">返回 Top Dev</a-button>
    </header>
    <a-spin :loading="loading" style="width: 100%">
      <article class="rubric-card md-body" v-html="html" />
    </a-spin>
  </section>
</template>

<style scoped>
.rubric-page {
  width: 100%;
  max-width: 920px;
  margin: 0 auto;
}
.page-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
  margin-bottom: 22px;
}
.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #111827;
}
.subtitle {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.5;
}
.rubric-card {
  padding: 28px 32px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #eef2f7;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  min-height: 200px;
}
</style>

<style>
.md-body {
  color: #374151;
  font-size: 15px;
  line-height: 1.75;
}
.md-body h1,
.md-body h2,
.md-body h3,
.md-body h4 {
  color: #111827;
  font-weight: 750;
  letter-spacing: -0.02em;
  margin: 1.4em 0 0.55em;
  line-height: 1.3;
}
.md-body h1 { font-size: 1.7em; margin-top: 0; }
.md-body h2 { font-size: 1.35em; padding-bottom: 0.35em; border-bottom: 1px solid #eef2f7; }
.md-body h3 { font-size: 1.15em; }
.md-body p { margin: 0.65em 0; }
.md-body ul,
.md-body ol { padding-left: 1.4em; margin: 0.55em 0; }
.md-body li { margin: 0.25em 0; }
.md-body table {
  width: 100%;
  border-collapse: collapse;
  margin: 1em 0;
  font-size: 14px;
}
.md-body th,
.md-body td {
  border: 1px solid #e5e7eb;
  padding: 8px 10px;
  text-align: left;
  vertical-align: top;
}
.md-body th { background: #f9fafb; font-weight: 650; }
.md-body code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.9em;
  background: #f3f4f6;
  padding: 0.1em 0.35em;
  border-radius: 4px;
}
.md-body pre {
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  padding: 12px 14px;
  overflow-x: auto;
}
.md-body pre code { background: transparent; padding: 0; }
.md-body blockquote {
  margin: 0.8em 0;
  padding: 0.2em 0 0.2em 14px;
  border-left: 3px solid #2563eb;
  color: #4b5563;
}
.md-body hr {
  border: 0;
  border-top: 1px solid #eef2f7;
  margin: 1.6em 0;
}
.md-body a { color: #2563eb; }
.md-body strong { color: #111827; font-weight: 700; }
</style>
