<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Message, type RequestOption } from '@arco-design/web-vue'
import { IconUpload } from '@arco-design/web-vue/es/icon'
import { api, uploadPdf } from '../api'
import { auth, setSession } from '../auth'
import { useIsMobile } from '../composables/useDevice'
import { dictItems, loadDicts } from '../composables/useDicts'
import type { User } from '../types'

const isMobile = useIsMobile()

const identityOptions = dictItems('STUDENT_IDENTITY')

const loading = ref(false)
const saving = ref(false)
const form = reactive({
  realName: '',
  school: '武汉大学',
  college: '',
  major: '',
  studentIdentity: '本科生',
  bio: '',
  resumeFileUrl: '',
  resumeFileName: '',
})

async function load() {
  loading.value = true
  try {
    const user = await api<User>('/api/v1/me/resume')
    form.realName = user.realName || ''
    form.school = user.school || '武汉大学'
    form.college = user.college || ''
    form.major = user.major || ''
    form.studentIdentity = user.studentIdentity || '本科生'
    form.bio = user.bio || ''
    form.resumeFileUrl = user.resumeFileUrl || ''
    form.resumeFileName = user.resumeFileName || ''
    if (auth.token) setSession(auth.token, { ...auth.user!, ...user })
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function onUpload(option: RequestOption) {
  void (async () => {
    const file = option.fileItem.file
    if (!file) {
      option.onError?.(new Error('未选择文件'))
      return
    }
    try {
      const stored = await uploadPdf(file)
      form.resumeFileUrl = stored.url
      form.resumeFileName = stored.fileName
      option.onSuccess?.(stored)
      Message.success('简历附件已上传')
    } catch (cause) {
      option.onError?.(cause instanceof Error ? cause : new Error('上传失败'))
      Message.error(cause instanceof Error ? cause.message : '上传失败')
    }
  })()
  return {}
}

function clearFile() {
  form.resumeFileUrl = ''
  form.resumeFileName = ''
}

async function save() {
  if (!form.realName.trim() || !form.school.trim() || !form.college.trim() || !form.major.trim()) {
    Message.error('请填写姓名、学校、学院与专业')
    return
  }
  if (form.bio.trim().length < 20) {
    Message.error('个人简介至少 20 字')
    return
  }
  saving.value = true
  try {
    const user = await api<User>('/api/v1/me/resume', {
      method: 'PUT',
      body: JSON.stringify({
        realName: form.realName.trim(),
        school: form.school.trim(),
        college: form.college.trim(),
        major: form.major.trim(),
        studentIdentity: form.studentIdentity,
        bio: form.bio.trim(),
        resumeFileUrl: form.resumeFileUrl || null,
        resumeFileName: form.resumeFileName || null,
      }),
    })
    if (auth.token) setSession(auth.token, { ...auth.user!, ...user })
    Message.success(user.resumeReady ? '简历已完善，可以投递项目' : '已保存')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadDicts()
  await load()
})
</script>

<template>
  <section class="resume-page" :class="{ 'resume-mobile': isMobile }">
    <header class="page-head">
      <div>
        <h1 class="page-title">{{ isMobile ? '简历' : '我的简历' }}</h1>
        <p v-if="!isMobile" class="subtitle">完善后才可投递项目申请。PDF 附件可选。</p>
      </div>
      <a-tag v-if="auth.user?.resumeReady" color="green" size="small">已完善</a-tag>
      <a-tag v-else color="orangered" size="small">待完善</a-tag>
    </header>

    <a-spin :loading="loading" style="width: 100%">
      <a-form :model="form" layout="vertical" class="resume-form">
        <div class="resume-sections">
          <div class="form-section basic-section">
            <h2 class="section-title">基本信息</h2>
            <a-form-item label="真实姓名" required>
              <a-input v-model="form.realName" placeholder="与学籍一致" size="large" />
            </a-form-item>
            <a-form-item label="在读身份" required>
              <a-select v-model="form.studentIdentity" size="large">
                <a-option v-for="item in identityOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
              </a-select>
            </a-form-item>
            <a-form-item label="学校" required>
              <a-input v-model="form.school" placeholder="例如 武汉大学" size="large" />
            </a-form-item>
            <a-form-item label="学院" required>
              <a-input v-model="form.college" placeholder="例如 计算机学院" size="large" />
            </a-form-item>
            <a-form-item label="专业" required>
              <a-input v-model="form.major" placeholder="例如 软件工程" size="large" />
            </a-form-item>
          </div>

          <div class="form-section">
            <h2 class="section-title">个人简介 <span class="optional">至少 20 字</span></h2>
            <a-form-item hide-label required>
              <a-textarea
                v-model="form.bio"
                :auto-size="{ minRows: 6, maxRows: 14 }"
                aria-label="个人简介"
                placeholder="写下你的技能、项目经历，以及每周可投入的时间"
              />
            </a-form-item>
          </div>

          <div class="form-section">
            <h2 class="section-title">简历附件 <span class="optional">可选</span></h2>
            <a-form-item hide-label>
              <div class="upload-row">
                <a-upload :custom-request="onUpload" :show-file-list="false" accept=".pdf,application/pdf">
                  <a-button type="outline">
                    <template #icon><icon-upload /></template>
                    {{ form.resumeFileUrl ? '更换 PDF 简历' : '上传 PDF 简历' }}
                  </a-button>
                </a-upload>
                <div v-if="form.resumeFileUrl" class="file-row">
                  <a :href="form.resumeFileUrl" target="_blank" rel="noopener">{{ form.resumeFileName || '查看附件' }}</a>
                  <button type="button" class="linkish" @click="clearFile">移除</button>
                </div>
              </div>
            </a-form-item>
          </div>
        </div>

        <div class="form-actions" :class="{ sticky: isMobile }">
          <a-button type="primary" size="large" :long="isMobile" :disabled="loading" :loading="saving" @click="save">保存简历</a-button>
        </div>
      </a-form>
    </a-spin>
  </section>
</template>

<style scoped>
.resume-page { max-width: 920px; margin: 0 auto; padding: 12px 0 32px; }
.page-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 28px; }
.page-title { margin: 0; font-size: 26px; color: #172033; }
.subtitle { margin: 8px 0 0; color: #64748b; font-size: 14px; }
.resume-form { background: white; border-radius: 12px; border: 1px solid #e5eaf1; padding: 8px 36px 0; }
.form-section { min-width: 0; padding: 28px 0; }
.form-section + .form-section { border-top: 1px solid #edf0f5; }
.section-title { margin: 0 0 24px; font-size: 17px; font-weight: 600; color: #172033; }
.optional { margin-left: 8px; font-size: 12px; font-weight: 400; color: #8792a2; }
.basic-section { display: grid; grid-template-columns: 1fr 1fr; gap: 0 28px; }
.basic-section .section-title { grid-column: 1 / -1; }
.resume-form :deep(.arco-form-item-label-col > label) { color: #64748b; font-size: 13px; }
.resume-form :deep(.arco-form-item:last-child) { margin-bottom: 0; }
.resume-form :deep(.arco-input-wrapper),
.resume-form :deep(.arco-select-view),
.resume-form :deep(.arco-textarea-wrapper) { background: #f7f9fc; border: 1px solid transparent; border-radius: 8px; }
.resume-form :deep(.arco-input-focus),
.resume-form :deep(.arco-textarea-focus) { border-color: #2563eb; background: white; }
.resume-form :deep(.arco-textarea) { line-height: 1.9; padding: 14px; }
.upload-row { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; width: 100%; }
.file-row { display: flex; align-items: center; gap: 12px; min-width: 0; }
.file-row a { color: #2563eb; overflow-wrap: anywhere; font-size: 13px; }
.linkish { border: 0; background: transparent; color: #64748b; cursor: pointer; padding: 10px; flex-shrink: 0; }
.form-actions { display: flex; justify-content: flex-end; padding: 20px 0; border-top: 1px solid #edf0f5; }
.form-actions :deep(.arco-btn) { min-width: 140px; }
@media (max-width: 768px) {
  .resume-page { padding: 0 0 16px; }
  .page-head { margin: 8px 0 20px; }
  .page-title { font-size: 22px; }
  .resume-form { padding: 0 18px; border: 0; border-radius: 12px; }
  .form-section { padding: 22px 0; }
  .section-title { font-size: 16px; margin-bottom: 18px; }
  .basic-section { display: block; }
  .basic-section :deep(.arco-form-item) { display: flex; flex-direction: row; align-items: center; margin: 0; min-height: 58px; border-bottom: 1px solid #f0f2f6; }
  .basic-section :deep(.arco-form-item:last-child) { border-bottom: 0; }
  .basic-section :deep(.arco-form-item-label-col) { width: 88px; flex: 0 0 88px; padding: 0; margin: 0; }
  .basic-section :deep(.arco-form-item-wrapper-col) { flex: 1; min-width: 0; width: auto; }
  .basic-section :deep(.arco-input-wrapper),
  .basic-section :deep(.arco-select-view) { background: transparent; }
  .basic-section :deep(.arco-input) { text-align: right; }
  .basic-section :deep(.arco-select-view-value) { text-align: right; }
  .upload-row { align-items: stretch; flex-direction: column; }
  .file-row { justify-content: space-between; }
  .form-actions.sticky { position: sticky; bottom: calc(var(--mobile-nav-height, 56px) + var(--bottom-inset, 0px)); z-index: 20; background: white; padding: 14px 0; }
  .form-actions :deep(.arco-btn) { min-height: 46px; border-radius: 8px; }
}
</style>
