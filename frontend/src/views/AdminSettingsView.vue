<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { renderMarkdown } from '../markdown'

const loading = ref(false)
const preview = ref(false)
const form = reactive({
  reviewSlaDays: 7,
  escalateSlaDays: 14,
  tutorialDocUrl: '',
  tutorialBannerText: '系统使用教程请参考平台文档，点击查看完整说明。',
  abilityRubricMd: '',
})

const rubricHtml = computed(() => renderMarkdown(form.abilityRubricMd))

onMounted(async () => {
  try {
    const settings = await api<{
      reviewSlaDays: number
      escalateSlaDays: number
      tutorialDocUrl: string
      tutorialBannerText: string
      abilityRubricMd: string
    }>('/api/v1/admin/settings')
    form.reviewSlaDays = settings.reviewSlaDays
    form.escalateSlaDays = settings.escalateSlaDays
    form.tutorialDocUrl = settings.tutorialDocUrl || ''
    form.tutorialBannerText = settings.tutorialBannerText || form.tutorialBannerText
    form.abilityRubricMd = settings.abilityRubricMd || ''
    if (!form.abilityRubricMd.trim()) {
      const rubric = await api<{ markdown: string }>('/api/v1/ability/rubric')
      form.abilityRubricMd = rubric.markdown || ''
    }
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  }
})

async function save() {
  loading.value = true
  try {
    await api('/api/v1/admin/settings', {
      method: 'PUT',
      body: JSON.stringify(form),
    })
    Message.success('已保存')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="settings-page">
    <a-card title="Top Dev 裁定细则" style="width: 100%; margin-bottom: 16px">
      <a-typography-paragraph type="secondary">
        支持 Markdown。保存后在「Top Dev → 查看裁定细则」以排版模式展示；留空则回退内置默认文案。
      </a-typography-paragraph>
      <div class="rubric-toolbar">
        <a-radio-group v-model="preview" type="button" size="small">
          <a-radio :value="false">编辑</a-radio>
          <a-radio :value="true">预览</a-radio>
        </a-radio-group>
        <a-link href="/ability/rubric" target="_blank">打开公开页</a-link>
      </div>
      <a-textarea
        v-if="!preview"
        v-model="form.abilityRubricMd"
        :auto-size="{ minRows: 18, maxRows: 36 }"
        placeholder="粘贴或编写 Markdown 细则…"
      />
      <article v-else class="preview-card md-body" v-html="rubricHtml" />
    </a-card>

    <a-card title="教程文档" style="width: min(640px, 100%); margin-bottom: 16px">
      <a-typography-paragraph type="secondary">
        配置后会在任务广场流程条下方显示教程 Banner；链接为空则不展示。
      </a-typography-paragraph>
      <a-form :model="form" layout="vertical">
        <a-form-item label="教程文档链接">
          <a-input v-model="form.tutorialDocUrl" placeholder="例如飞书 Wiki 链接" allow-clear />
        </a-form-item>
        <a-form-item label="Banner 文案">
          <a-input v-model="form.tutorialBannerText" placeholder="提示文案" allow-clear />
        </a-form-item>
      </a-form>
    </a-card>

    <a-card title="SLA" style="width: min(640px, 100%)">
      <a-typography-paragraph type="secondary">
        定时任务每分钟扫描一次。0 天表示刚提交就算逾期，方便本地验证。
      </a-typography-paragraph>
      <a-form :model="form" layout="vertical">
        <a-form-item label="申请未审催办（天）">
          <a-input-number v-model="form.reviewSlaDays" :min="0" :max="60" style="width: 100%" />
        </a-form-item>
        <a-form-item label="升级到管理队列（天）">
          <a-input-number v-model="form.escalateSlaDays" :min="0" :max="120" style="width: 100%" />
        </a-form-item>
        <a-button type="primary" :loading="loading" @click="save">保存全部设置</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.settings-page { max-width: 920px; }
.rubric-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.preview-card {
  min-height: 320px;
  padding: 18px 20px;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  background: #fafafa;
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
