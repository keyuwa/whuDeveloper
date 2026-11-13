<script setup lang="ts">
import { computed, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import type { RequestOption } from '@arco-design/web-vue/es/upload/interfaces'
import { uploadPdf } from '../../api'
import { useProjectWorkspace } from '../../composables/useProjectWorkspace'

const { project, saving, isStudent, isPublisher, run } = useProjectWorkspace()
const reportMd = ref('')
const reportFileUrl = ref('')
const reportFileName = ref('')
const prUrl = ref('')
const uploading = ref(false)

const completionStatusLabel: Record<string, string> = {
  SUBMITTED: '待验收',
  APPROVED: '已通过',
  REJECTED: '需补充',
}

const completionStatusColor: Record<string, string> = {
  SUBMITTED: 'orangered',
  APPROVED: 'green',
  REJECTED: 'red',
}

const canSubmit = computed(
  () => Boolean(reportMd.value.trim()) || Boolean(reportFileUrl.value.trim()),
)

function customUpload(option: RequestOption) {
  void doUpload(option)
  return {}
}

async function doUpload(option: RequestOption) {
  const file = option.fileItem.file
  if (!file) {
    option.onError?.(new Error('未选择文件'))
    Message.error('请先选择 PDF 文件')
    return
  }
  uploading.value = true
  try {
    const stored = await uploadPdf(file)
    reportFileUrl.value = stored.url
    reportFileName.value = stored.fileName
    option.onSuccess?.(stored)
    Message.success('结项报告已上传')
  } catch (cause) {
    option.onError?.(cause)
    Message.error(cause instanceof Error ? cause.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

function clearReport() {
  reportFileUrl.value = ''
  reportFileName.value = ''
}

async function submit() {
  if (!project.value) return
  if (!canSubmit.value) {
    Message.warning('请填写结项文字材料或上传 PDF')
    return
  }
  const prUrls = prUrl.value.split(/\s+/).filter(Boolean)
  if (prUrls.some((url) => !/^https?:\/\//i.test(url))) {
    Message.warning('代码仓库 / PR 链接须为 http(s) 地址')
    return
  }
  await run(`/api/v1/projects/${project.value.id}/completion`, {
    reportMd: reportMd.value.trim() || null,
    reportFileUrl: reportFileUrl.value.trim() || null,
    reportFileName: reportFileName.value || null,
    prUrls,
  })
  reportMd.value = ''
  reportFileUrl.value = ''
  reportFileName.value = ''
  prUrl.value = ''
}
</script>

<template>
  <section v-if="project" class="stack">
    <a-alert class="soft-alert" type="info" :show-icon="true">
      按申请约定完成协作后提交结项：可交文字说明或 PDF，代码仓库 / PR 链接可选。
    </a-alert>

    <a-card class="panel-card" title="结项验收" :bordered="false">
      <template v-if="project.completion">
        <div class="result-head">
          <a-tag :color="completionStatusColor[project.completion.status] || 'gray'" size="large">
            {{ completionStatusLabel[project.completion.status] || project.completion.status }}
          </a-tag>
        </div>

        <div v-if="project.completion.reportMd" class="text-block">
          <div class="section-label">文字材料</div>
          <pre class="md report-preview">{{ project.completion.reportMd }}</pre>
        </div>

        <div v-if="project.completion.reportFileUrl" class="doc-file">
          <div class="section-label">结果报告 PDF</div>
          <a :href="project.completion.reportFileUrl" target="_blank" rel="noopener">
            {{ project.completion.reportFileName || '打开 PDF' }}
          </a>
        </div>

        <div v-if="project.completion.prUrls.length" class="pr-list">
          <div class="section-label">代码仓库 / PR</div>
          <a
            v-for="url in project.completion.prUrls"
            :key="url"
            class="pr-link"
            :href="url"
            target="_blank"
            rel="noopener"
          >
            {{ url }}
          </a>
        </div>
        <p v-if="project.completion.reviewNote" class="review-note">审阅意见：{{ project.completion.reviewNote }}</p>
        <a-space
          v-if="isPublisher && project.status === 'COMPLETING' && project.completion.status === 'SUBMITTED'"
          style="margin-top: 16px"
        >
          <a-button
            type="primary"
            :loading="saving"
            @click="run(`/api/v1/projects/${project.id}/completion/review`, { decision: 'APPROVE', note: '通过' })"
          >
            通过结项
          </a-button>
          <a-button
            :loading="saving"
            @click="run(`/api/v1/projects/${project.id}/completion/review`, { decision: 'REJECT', note: '请补充材料' })"
          >
            驳回
          </a-button>
        </a-space>
      </template>

      <div v-if="isStudent && project.status === 'ACTIVE' && (!project.completion || project.completion.status === 'REJECTED')" class="submit-box">
        <div class="section-label" style="margin-bottom: 12px">
          {{ project.completion ? '重新提交结项材料' : '提交结项材料' }}
        </div>

        <a-alert class="check-tip" type="warning" :show-icon="true">
          文字材料与 PDF <strong>至少提交一种</strong>；代码仓库 / PR 链接为可选项。
        </a-alert>

        <div class="stack">
          <div>
            <div class="field-label">文字材料（可选）</div>
            <a-textarea
              v-model="reportMd"
              :auto-size="{ minRows: 5, maxRows: 14 }"
              placeholder="说明完成内容、成果要点、协作总结等"
            />
          </div>

          <div>
            <div class="field-label">结果报告 PDF（可选）</div>
            <a-upload
              class="doc-upload"
              :custom-request="customUpload"
              :show-file-list="false"
              accept="application/pdf,.pdf"
              :disabled="uploading || saving"
            >
              <template #upload-button>
                <div class="drop" :class="{ filled: reportFileUrl }">
                  <span class="drop-mark">PDF</span>
                  <span class="drop-title">{{ reportFileName || '点击上传结项结果报告' }}</span>
                  <span class="drop-hint">{{ reportFileUrl ? '点击可更换文件' : '可选 · .pdf · 20MB 以内' }}</span>
                </div>
              </template>
            </a-upload>
            <div v-if="reportFileUrl" class="doc-links">
              <a :href="reportFileUrl" target="_blank" rel="noopener">预览</a>
              <button type="button" @click="clearReport">移除</button>
            </div>
          </div>

          <div>
            <div class="field-label">代码仓库 / PR 链接（可选）</div>
            <a-input
              v-model="prUrl"
              placeholder="仓库或 PR 的 http(s) 链接，多个用空格分隔"
              allow-clear
            />
          </div>

          <div>
            <a-button type="primary" :loading="saving || uploading" :disabled="!canSubmit" @click="submit">
              提交结项
            </a-button>
          </div>
        </div>
      </div>

      <a-empty
        v-if="!project.completion && (!isStudent || project.status !== 'ACTIVE')"
        description="尚未提交结项材料"
      />
    </a-card>
  </section>
</template>

<style scoped>
.panel-card {
  background: var(--color-bg-2);
  border-radius: 10px;
}
.result-head {
  margin-bottom: 14px;
}
.text-block {
  margin-bottom: 14px;
}
.report-preview {
  margin: 8px 0 0;
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
}
.section-label {
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--color-text-3);
  text-transform: uppercase;
}
.field-label {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--color-text-2);
}
.pr-list {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pr-link {
  overflow-wrap: anywhere;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
}
.doc-file {
  margin-bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.doc-file a {
  overflow-wrap: anywhere;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
}
.review-note {
  margin-top: 12px;
  color: var(--color-text-2);
}
.submit-box {
  margin-top: 8px;
  padding: 16px 0 0;
  border-top: 1px solid #eef2f7;
}
.check-tip {
  margin-bottom: 14px;
}
.doc-upload {
  width: 100%;
}
.doc-upload :deep(.arco-upload) {
  width: 100%;
}
.drop {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  padding: 18px 16px;
  border: 1px dashed var(--color-border-3);
  border-radius: 10px;
  background: var(--color-bg-2);
  cursor: pointer;
}
.drop.filled {
  border-color: rgb(var(--primary-6));
}
.drop-mark {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--color-text-3);
}
.drop-title {
  font-size: 14px;
  color: var(--color-text-1);
}
.drop-hint {
  font-size: 12px;
  color: var(--color-text-3);
}
.doc-links {
  margin-top: 8px;
  display: flex;
  gap: 14px;
  font-size: 13px;
}
.doc-links button {
  border: 0;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  padding: 0;
}
</style>
