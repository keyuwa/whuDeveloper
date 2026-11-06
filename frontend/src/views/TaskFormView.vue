<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, Modal, type RequestOption } from '@arco-design/web-vue'
import { api, uploadPdf } from '../api'
import { useIsMobile } from '../composables/useDevice'
import { dictItems, dictOptions, loadDicts } from '../composables/useDicts'
import { TASK_ROLE_TYPES, type Task } from '../types'

const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const formRef = ref()
const loading = ref(false)
const taskStatus = ref<string | null>(null)
const selectedLanguages = ref<string[]>(['TypeScript'])
const selectedEducations = ref<string[]>([])
const form = reactive({
  title: '',
  roleType: 'INTERN',
  techField: '全栈应用',
  difficulty: 'L3',
  background: '',
  techStack: '',
  programmingLanguages: 'TypeScript',
  supportLanguages: '中文',
  outputRequirement: '',
  techRequirement: '',
  durationWeeks: 8,
  scheduleType: '',
  educationLevels: '',
  estimatedHours: 10,
  repoUrl: '',
  learningMaterials: '',
  stipendText: '',
  prdFileUrl: '',
  prdFileName: '',
  techDocFileUrl: '',
  techDocFileName: '',
  quota: 1,
})

const isIntern = computed(() => form.roleType === 'INTERN')
const isTa = computed(() => form.roleType === 'TA')
const isRa = computed(() => form.roleType === 'RA')
const techFieldOptions = dictOptions('TECH_FIELD')
const difficultyOptions = dictOptions('DIFFICULTY')
const supportLanguageOptions = dictOptions('SUPPORT_LANGUAGE')
const educationOptions = dictItems('STUDENT_IDENTITY')
const programmingLanguageOptions = dictOptions('PROGRAMMING_LANGUAGE')
const taScheduleOptions = dictOptions('TA_SCHEDULE')
const raScheduleOptions = dictOptions('RA_SCHEDULE')
const scheduleOptions = computed(() => (isTa.value ? taScheduleOptions.value : raScheduleOptions.value))

onMounted(async () => {
  await loadDicts()
  if (!route.params.id) return
  try {
    const task = await api<Task>(`/api/v1/tasks/${route.params.id}`)
    taskStatus.value = task.status
    Object.assign(form, {
      title: task.title,
      roleType: task.roleType || 'INTERN',
      techField: task.techField,
      difficulty: task.difficulty,
      background: task.background,
      techStack: task.techStack,
      programmingLanguages: task.programmingLanguages,
      supportLanguages: task.supportLanguages,
      outputRequirement: task.outputRequirement,
      techRequirement: task.techRequirement,
      durationWeeks: task.durationWeeks,
      scheduleType: task.scheduleType || '',
      educationLevels: task.educationLevels || '',
      estimatedHours: task.estimatedHours,
      repoUrl: task.repoUrl || '',
      learningMaterials: task.learningMaterials || '',
      stipendText: task.stipendText || '',
      prdFileUrl: task.prdFileUrl || '',
      prdFileName: task.prdFileName || '',
      techDocFileUrl: task.techDocFileUrl || '',
      techDocFileName: task.techDocFileName || '',
      quota: task.quota,
    })
    selectedLanguages.value = task.programmingLanguages
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
    if (!isIntern.value && selectedLanguages.value.length === 0) {
      selectedLanguages.value = ['不限']
    }
    selectedEducations.value = (task.educationLevels || '')
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  }
})

function syncLanguages(value: string[]) {
  selectedLanguages.value = value
  form.programmingLanguages = value.join(',')
}

function syncEducations(value: string[]) {
  selectedEducations.value = value
  form.educationLevels = value.join(',')
}

function makeUploader(kind: 'prd' | 'tech') {
  return (option: RequestOption) => {
    void upload(option)
    return {}
  }
  async function upload(option: RequestOption) {
    const file = option.fileItem.file
    if (!file) {
      option.onError?.(new Error("未选择文件"))
      Message.error("请先选择 PDF 文件")
      return
    }
    try {
      const stored = await uploadPdf(file)
      if (kind === 'prd') {
        form.prdFileUrl = stored.url
        form.prdFileName = stored.fileName
      } else {
        form.techDocFileUrl = stored.url
        form.techDocFileName = stored.fileName
      }
      option.onSuccess?.(stored)
      Message.success('PDF 已上传')
    } catch (cause) {
      option.onError?.(cause)
      Message.error(cause instanceof Error ? cause.message : '上传失败')
    }
  }
}

function clearFile(kind: 'prd' | 'tech') {
  if (kind === 'prd') {
    form.prdFileUrl = ''
    form.prdFileName = ''
  } else {
    form.techDocFileUrl = ''
    form.techDocFileName = ''
  }
}

async function publish() {
  const errors = await formRef.value?.validate().catch((cause: unknown) => cause)
  if (errors) return
  await save(true)
}

function canDeleteTask() {
  return Boolean(route.params.id) && (taskStatus.value === 'DRAFT' || taskStatus.value === 'CLOSED')
}

function removeTask() {
  if (!canDeleteTask()) return
  Modal.confirm({
    title: '删除任务',
    content: `确定删除「${form.title || '未命名'}」吗？删除后不可恢复。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    onOk: async () => {
      loading.value = true
      try {
        await api(`/api/v1/publisher/tasks/${route.params.id}`, { method: 'DELETE' })
        Message.success('已删除')
        await router.push('/publisher/tasks')
      } catch (cause) {
        Message.error(cause instanceof Error ? cause.message : '删除失败')
        throw cause
      } finally {
        loading.value = false
      }
    },
  })
}

async function save(publish: boolean) {
  if (!form.roleType) {
    Message.error('请选择任务类型')
    return
  }
  if ((isTa.value || isRa.value) && !form.scheduleType) {
    Message.error(isTa.value ? '请选择助教学期' : '请选择科研周期')
    return
  }
  if (isTa.value && selectedEducations.value.length === 0) {
    Message.error('请选择在读身份')
    return
  }
  if (!form.techRequirement.trim()) {
    Message.error('请填写能力要求')
    return
  }
  if (!form.stipendText.trim()) {
    Message.error('请填写补助水平')
    return
  }
  if (isIntern.value && !form.difficulty) {
    Message.error('请选择项目难度')
    return
  }
  if (isIntern.value && !form.prdFileUrl) {
    Message.error('请上传需求文档 PDF')
    return
  }
  loading.value = true
  try {
    form.programmingLanguages = selectedLanguages.value.join(',') || (isIntern.value ? '' : '不限')
    form.educationLevels = isTa.value ? selectedEducations.value.join(',') : ''
    const path = route.params.id ? `/api/v1/publisher/tasks/${route.params.id}` : `/api/v1/publisher/tasks?publish=${publish}`
    const method = route.params.id ? 'PUT' : 'POST'
    const payload = {
      ...form,
      techField: isTa.value ? '教学助教' : form.techField,
      difficulty: isIntern.value ? form.difficulty : 'L3',
      repoUrl: isIntern.value ? form.repoUrl || null : null,
      scheduleType: isIntern.value ? null : form.scheduleType || null,
      educationLevels: isTa.value ? form.educationLevels : null,
      durationWeeks: isIntern.value ? form.durationWeeks : null,
      stipendText: form.stipendText.trim(),
      prdFileUrl: isIntern.value ? form.prdFileUrl || null : null,
      prdFileName: isIntern.value ? form.prdFileName || null : null,
      techDocFileUrl: isIntern.value ? form.techDocFileUrl || null : null,
      techDocFileName: isIntern.value ? form.techDocFileName || null : null,
    }
    const task = await api<Task>(path, { method, body: JSON.stringify(payload) })
    if (route.params.id && publish) await api(`/api/v1/publisher/tasks/${task.id}/publish`, { method: 'POST' })
    Message.success(publish ? '已发布' : '已保存')
    await router.push('/publisher/tasks')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <a-card :title="route.params.id ? '编辑项目' : '发布项目'">
    <a-alert
      v-if="isMobile"
      type="info"
      style="margin-bottom: 16px"
      title="建议使用电脑填写"
    >
      手机端可查看与轻量编辑；完整发岗字段较多，推荐在电脑浏览器完成。
    </a-alert>
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item field="title" label="项目名称" :rules="[{ required: true, message: '请填写名称' }]">
        <a-input v-model="form.title" />
      </a-form-item>
      <a-form-item field="roleType" label="任务类型" required :rules="[{ required: true, message: '请选择任务类型' }]">
        <a-radio-group
          v-model="form.roleType"
          type="button"
          @change="() => { form.scheduleType = '' }"
        >
          <a-radio v-for="item in TASK_ROLE_TYPES" :key="item.value" :value="item.value">{{ item.label }}</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-row :gutter="12">
        <a-col v-if="!isTa" :span="isIntern ? 8 : 12">
          <a-form-item :label="isRa ? '研究方向' : '技术领域'" required>
            <a-select v-model="form.techField">
              <a-option v-for="item in techFieldOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col v-if="isIntern" :span="8">
          <a-form-item label="项目难度" required>
            <a-select v-model="form.difficulty">
              <a-option v-for="item in difficultyOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :span="isTa ? 24 : isIntern ? 8 : 12">
          <a-form-item label="支持语言" required>
            <a-select v-model="form.supportLanguages">
              <a-option v-for="item in supportLanguageOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
            </a-select>
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item v-if="isTa" label="在读身份" required>
        <a-checkbox-group :model-value="selectedEducations" @change="(value) => syncEducations((value as string[]) || [])">
          <a-checkbox v-for="item in educationOptions" :key="item.value" :value="item.value">{{ item.label }}</a-checkbox>
        </a-checkbox-group>
      </a-form-item>
      <a-row :gutter="12">
        <a-col :span="8">
          <a-form-item label="名额"><a-input-number v-model="form.quota" :min="1" style="width: 100%" /></a-form-item>
        </a-col>
        <a-col v-if="isIntern" :span="8">
          <a-form-item label="开发周期（周）"><a-input-number v-model="form.durationWeeks" :min="1" style="width: 100%" /></a-form-item>
        </a-col>
        <a-col v-else :span="8">
          <a-form-item :label="isTa ? '学期' : '科研周期'" required>
            <a-select v-model="form.scheduleType" placeholder="请选择">
              <a-option v-for="item in scheduleOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item :label="isTa ? '每周投入（小时）' : '每周工时'">
            <a-input-number v-model="form.estimatedHours" :min="1" style="width: 100%" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item v-if="isIntern" label="编程语言" required>
        <a-select
          :model-value="selectedLanguages"
          multiple
          allow-create
          allow-clear
          placeholder="选择或输入编程语言"
          @change="(value) => syncLanguages((value as string[]) || [])"
        >
          <a-option v-for="item in programmingLanguageOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="isIntern" label="技术栈说明" required>
        <a-input v-model="form.techStack" placeholder="例如 Vue 3, Spring Boot, MySQL" />
      </a-form-item>
      <a-form-item v-if="isIntern" label="仓库">
        <a-input v-model="form.repoUrl" placeholder="可选：代码仓库链接" />
      </a-form-item>
      <a-form-item label="背景与目标" required><a-textarea v-model="form.background" :auto-size="{ minRows: 3 }" /></a-form-item>
      <a-form-item label="产出要求" required><a-textarea v-model="form.outputRequirement" /></a-form-item>
      <a-form-item
        field="techRequirement"
        label="能力要求"
        required
        :rules="[{ required: true, message: '请填写能力要求' }]"
      >
        <a-textarea
          v-model="form.techRequirement"
          :auto-size="{ minRows: 3 }"
          placeholder="例如：熟悉 Java / Spring Boot，能独立完成模块开发与周报"
        />
      </a-form-item>
      <a-form-item
        field="stipendText"
        label="补助水平"
        required
        :rules="[{ required: true, message: '请填写补助水平' }]"
      >
        <a-textarea
          v-model="form.stipendText"
          :auto-size="{ minRows: 2 }"
          placeholder="例如：按周/月发放，具体标准与考核方式"
        />
      </a-form-item>
      <a-form-item label="学习资料"><a-textarea v-model="form.learningMaterials" placeholder="可选：链接、书单等" /></a-form-item>

      <section v-if="isIntern" class="doc-section">
        <div class="doc-head">
          <div>
            <h3>项目文档</h3>
            <p>上传 PDF，单个不超过 20MB。需求文档必填，技术文档可选。</p>
          </div>
        </div>
        <div class="doc-grid">
          <article class="doc-card" :class="{ filled: form.prdFileUrl }">
            <div class="doc-card-top">
              <span class="doc-kicker">需求文档</span>
              <span class="doc-badge required">必填</span>
            </div>
            <p class="doc-desc">目标、范围与验收标准</p>
            <a-upload
              class="doc-upload"
              :custom-request="makeUploader('prd')"
              :show-file-list="false"
              accept="application/pdf,.pdf"
            >
              <template #upload-button>
                <div class="drop">
                  <span class="drop-mark">PDF</span>
                  <span class="drop-title">{{ form.prdFileName || '点击上传需求文档' }}</span>
                  <span class="drop-hint">{{ form.prdFileUrl ? '点击可更换文件' : '.pdf · 20MB 以内' }}</span>
                </div>
              </template>
            </a-upload>
            <div v-if="form.prdFileUrl" class="doc-links">
              <a :href="form.prdFileUrl" target="_blank" rel="noopener">预览</a>
              <button type="button" @click="clearFile('prd')">移除</button>
            </div>
          </article>

          <article class="doc-card" :class="{ filled: form.techDocFileUrl }">
            <div class="doc-card-top">
              <span class="doc-kicker">技术文档</span>
              <span class="doc-badge">可选</span>
            </div>
            <p class="doc-desc">架构、接口与运行环境</p>
            <a-upload
              class="doc-upload"
              :custom-request="makeUploader('tech')"
              :show-file-list="false"
              accept="application/pdf,.pdf"
            >
              <template #upload-button>
                <div class="drop">
                  <span class="drop-mark">PDF</span>
                  <span class="drop-title">{{ form.techDocFileName || '点击上传技术文档' }}</span>
                  <span class="drop-hint">{{ form.techDocFileUrl ? '点击可更换文件' : '.pdf · 20MB 以内' }}</span>
                </div>
              </template>
            </a-upload>
            <div v-if="form.techDocFileUrl" class="doc-links">
              <a :href="form.techDocFileUrl" target="_blank" rel="noopener">预览</a>
              <button type="button" @click="clearFile('tech')">移除</button>
            </div>
          </article>
        </div>
      </section>
    </a-form>

    <footer class="form-actions">
      <p>草稿仅自己可见。发布后学生可申请；已发布需先下架，草稿/已下架可在此删除。</p>
      <div class="action-buttons">
        <a-button
          v-if="canDeleteTask()"
          size="large"
          type="outline"
          status="danger"
          :loading="loading"
          @click="removeTask"
        >
          删除任务
        </a-button>
        <a-button size="large" :loading="loading" @click="save(false)">保存草稿</a-button>
        <a-button size="large" type="primary" :loading="loading" @click="publish">发布</a-button>
      </div>
    </footer>
  </a-card>
</template>

<style scoped>
.doc-section {
  margin-top: 8px;
  padding: 18px;
  border-radius: 12px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
}
.doc-head h3 {
  margin: 0;
  font-size: 16px;
}
.doc-head p {
  margin-top: 6px;
  color: var(--color-text-3);
  font-size: 13px;
}
.doc-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 16px;
}
.doc-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border-radius: 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
}
.doc-card.filled {
  border-color: rgb(var(--primary-3));
  box-shadow: inset 0 0 0 1px rgb(var(--primary-2));
}
.doc-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.doc-kicker {
  font-size: 15px;
  font-weight: 600;
}
.doc-badge {
  font-size: 12px;
  line-height: 1;
  padding: 4px 8px;
  border-radius: 999px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
}
.doc-badge.required {
  color: rgb(var(--primary-6));
  background: rgb(var(--primary-1));
}
.doc-desc {
  color: var(--color-text-3);
  font-size: 13px;
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
  gap: 4px;
  width: 100%;
  min-height: 112px;
  padding: 16px;
  border-radius: 10px;
  border: 1px dashed var(--color-border-3);
  background: var(--color-fill-1);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}
.drop:hover {
  border-color: rgb(var(--primary-6));
  background: rgb(var(--primary-1));
}
.drop-mark {
  font: 11px/1 ui-monospace, SFMono-Regular, Menlo, monospace;
  letter-spacing: 0.08em;
  color: rgb(var(--primary-6));
}
.drop-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
}
.drop-hint {
  font-size: 12px;
  color: var(--color-text-3);
}
.doc-links {
  display: flex;
  gap: 14px;
  font-size: 13px;
}
.doc-links button {
  padding: 0;
  border: 0;
  background: transparent;
  color: rgb(var(--danger-6));
  cursor: pointer;
}
.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
  padding: 16px 18px;
  border-radius: 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
}
.form-actions p {
  margin: 0;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.6;
}
.action-buttons {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}
@media (max-width: 800px) {
  .doc-grid,
  .form-actions {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }
  .action-buttons {
    width: 100%;
  }
  .action-buttons :deep(.arco-btn) {
    flex: 1;
  }
}
</style>
