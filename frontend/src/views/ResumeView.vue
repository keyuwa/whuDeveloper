<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Message, type RequestOption } from '@arco-design/web-vue'
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
        <a-collapse v-if="isMobile" :default-active-key="['base', 'bio', 'file']" :bordered="false" class="resume-collapse">
          <a-collapse-item key="base" header="基本信息">
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
          </a-collapse-item>
          <a-collapse-item key="bio" header="个人简介">
            <a-textarea
              v-model="form.bio"
              :auto-size="{ minRows: 5, maxRows: 12 }"
              placeholder="介绍学习经历、项目经验、可投入时间等，至少 20 字"
            />
          </a-collapse-item>
          <a-collapse-item key="file" header="简历附件（可选）">
            <div class="upload-row">
              <a-upload :custom-request="onUpload" :show-file-list="false" accept=".pdf,application/pdf">
                <a-button>上传 PDF</a-button>
              </a-upload>
              <div v-if="form.resumeFileUrl" class="file-row">
                <a :href="form.resumeFileUrl" target="_blank" rel="noopener">{{ form.resumeFileName || '查看附件' }}</a>
                <button type="button" class="linkish" @click="clearFile">移除</button>
              </div>
            </div>
          </a-collapse-item>
        </a-collapse>

        <template v-else>
        <div class="form-section">
          <h2 class="section-title">基本信息</h2>
          <a-row :gutter="20">
            <a-col :xs="24" :sm="12">
              <a-form-item label="真实姓名" required>
                <a-input v-model="form.realName" placeholder="与学籍一致" size="large" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="在读身份" required>
                <a-select v-model="form.studentIdentity" size="large">
                  <a-option v-for="item in identityOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="学校" required>
                <a-input v-model="form.school" placeholder="例如 武汉大学" size="large" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="学院" required>
                <a-input v-model="form.college" placeholder="例如 计算机学院" size="large" />
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="专业" required>
                <a-input v-model="form.major" placeholder="例如 软件工程" size="large" />
              </a-form-item>
            </a-col>
          </a-row>
        </div>

        <div class="form-section">
          <h2 class="section-title">个人简介</h2>
          <a-form-item hide-label required>
            <a-textarea
              v-model="form.bio"
              :auto-size="{ minRows: 6, maxRows: 14 }"
              placeholder="介绍学习经历、项目经验、可投入时间等，至少 20 字"
            />
          </a-form-item>
        </div>

        <div class="form-section">
          <h2 class="section-title">简历附件 <span class="optional">可选</span></h2>
          <a-form-item hide-label>
            <div class="upload-row">
              <a-upload :custom-request="onUpload" :show-file-list="false" accept=".pdf,application/pdf">
                <a-button>上传 PDF</a-button>
              </a-upload>
              <div v-if="form.resumeFileUrl" class="file-row">
                <a :href="form.resumeFileUrl" target="_blank" rel="noopener">{{ form.resumeFileName || '查看附件' }}</a>
                <button type="button" class="linkish" @click="clearFile">移除</button>
              </div>
            </div>
          </a-form-item>
        </div>

        </template>

        <div class="form-actions" :class="{ sticky: isMobile }">
          <a-button type="primary" size="large" long :loading="saving" @click="save">保存简历</a-button>
        </div>
      </a-form>
    </a-spin>
  </section>
</template>

<style scoped>
.resume-page {
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 28px;
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
.form-section {
  padding: 8px 0 20px;
  border-bottom: 1px solid #eef2f7;
  margin-bottom: 8px;
}
.form-section:last-of-type {
  border-bottom: 0;
}
.section-title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}
.optional {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #9ca3af;
}
.resume-form :deep(.arco-form-item-label-col > label),
.resume-form :deep(.arco-form-item-label) {
  color: #4b5563;
  font-weight: 500;
}
.upload-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
}
.file-row {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 13px;
}
.file-row a {
  color: #2563eb;
  font-weight: 500;
}
.linkish {
  border: 0;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  padding: 0;
}
.linkish:hover {
  color: #111827;
}
.form-actions {
  padding-top: 12px;
}
.resume-collapse {
  background: transparent;
  margin-bottom: 12px;
}
.resume-collapse :deep(.arco-collapse-item-content) {
  padding: 4px 0 12px;
}
.resume-mobile .page-title {
  font-size: 20px;
}
.resume-mobile .page-head {
  margin-bottom: 16px;
  align-items: center;
}
.form-actions.sticky {
  position: sticky;
  bottom: 72px;
  z-index: 20;
  padding: 12px 0 calc(12px + var(--bottom-inset, 0px));
  background: linear-gradient(180deg, transparent, #f9fafb 30%);
}
</style>
