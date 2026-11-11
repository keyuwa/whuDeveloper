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
  bidDeadline: '' as string,
})
const bidLongTerm = ref(true)
const positions = ref<{ id?: number; title: string; direction: string; quota: number; requirement: string }[]>([
  { title: '研发', direction: '研发', quota: 3, requirement: '' },
  { title: '运营', direction: '运营', quota: 20, requirement: '' },
])

const isIntern = computed(() => form.roleType === 'INTERN')
const isTa = computed(() => form.roleType === 'TA')
const isRa = computed(() => form.roleType === 'RA')
const isTeam = computed(() => form.roleType === 'TEAM')
const techFieldDictOptions = dictOptions('TECH_FIELD')
const researchDirectionOptions = dictOptions('RESEARCH_DIRECTION')
const teamPositionOptions = dictOptions('TEAM_POSITION')
const fieldOptions = computed(() => (isRa.value ? researchDirectionOptions.value : techFieldDictOptions.value))
const difficultyOptions = dictOptions('DIFFICULTY')
const educationOptions = dictItems('STUDENT_IDENTITY')
const programmingLanguageOptions = dictOptions('PROGRAMMING_LANGUAGE')
const taScheduleOptions = dictOptions('TA_SCHEDULE')
const raScheduleOptions = dictOptions('RA_SCHEDULE')
const scheduleOptions = computed(() => (isTa.value ? taScheduleOptions.value : raScheduleOptions.value))
const teamQuotaTotal = computed(() => positions.value.reduce((sum, item) => sum + (Number(item.quota) || 0), 0))

function syncFieldForRole() {
  if (isTa.value || isTeam.value) return
  const options = fieldOptions.value
  if (!options.length) return
  if (!options.some((item) => item.value === form.techField)) {
    form.techField = options[0].value
  }
}

function addPosition() {
  const first = teamPositionOptions.value[0]?.value || '其它'
  positions.value.push({ title: first, direction: first, quota: 5, requirement: '' })
}

function onPositionDirectionChange(index: number, value: string) {
  const row = positions.value[index]
  if (!row) return
  row.direction = value
  row.title = value
}

function removePosition(index: number) {
  if (positions.value.length <= 1) {
    Message.warning('至少保留一个岗位')
    return
  }
  positions.value.splice(index, 1)
}

function tasksListQuery(status?: string) {
  const roleFromForm =
    form.roleType === 'TA' || form.roleType === 'INTERN' || form.roleType === 'RA' || form.roleType === 'TEAM'
      ? form.roleType
      : null
  const roleFromQuery = String(route.query.roleType || '').toUpperCase()
  const roleType =
    roleFromForm ||
    (roleFromQuery === 'TA' || roleFromQuery === 'INTERN' || roleFromQuery === 'RA' || roleFromQuery === 'TEAM'
      ? roleFromQuery
      : 'INTERN')
  const statusFromQuery = String(route.query.status || '').toUpperCase()
  const nextStatus =
    status ||
    (statusFromQuery === 'OPEN' || statusFromQuery === 'DRAFT' || statusFromQuery === 'CLOSED' || statusFromQuery === 'ALL'
      ? statusFromQuery
      : 'ALL')
  sessionStorage.setItem('publisher.tasks.roleType', roleType)
  sessionStorage.setItem('publisher.tasks.status', nextStatus)
  return { roleType, status: nextStatus }
}

function goTasksList(status?: string) {
  return router.push({ path: '/publisher/tasks', query: tasksListQuery(status) })
}

onMounted(async () => {
  await loadDicts()
  if (!route.params.id) {
    const queryRole = String(route.query.roleType || '').toUpperCase()
    if (queryRole === 'TA' || queryRole === 'INTERN' || queryRole === 'RA' || queryRole === 'TEAM') {
      form.roleType = queryRole
      if (queryRole !== 'INTERN' && selectedLanguages.value.includes('TypeScript')) {
        selectedLanguages.value = ['不限']
      }
    }
    syncFieldForRole()
    return
  }
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
      bidDeadline: task.bidDeadline ? task.bidDeadline.slice(0, 19) : '',
    })
    bidLongTerm.value = task.roleType === 'RA' ? !task.bidDeadline : true
    if (task.roleType === 'TEAM' && task.positions?.length) {
      positions.value = task.positions.map((item) => ({
        id: item.id,
        title: item.title,
        direction: item.direction,
        quota: item.quota,
        requirement: item.requirement || '',
      }))
    }
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
        await goTasksList()
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
  if (isTa.value && !form.scheduleType) {
    Message.error('请选择助教学期')
    return
  }
  if (isRa.value && !bidLongTerm.value && !form.bidDeadline) {
    Message.error('请设置揭榜截止时间，或勾选长期开放')
    return
  }
  if (isTeam.value) {
    if (!positions.value.length) {
      Message.error('请至少添加一个岗位')
      return
    }
    for (const item of positions.value) {
      if (!item.direction) {
        Message.error('请选择岗位')
        return
      }
      if (!item.quota || item.quota < 1) {
        Message.error('岗位名额至少为 1')
        return
      }
    }
  }
  if (isTa.value && selectedEducations.value.length === 0) {
    Message.error('请选择在读身份')
    return
  }
  if (!isRa.value && !isTeam.value && !form.techRequirement.trim()) {
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
    const path = route.params.id
      ? `/api/v1/publisher/tasks/${route.params.id}?publish=${publish}`
      : `/api/v1/publisher/tasks?publish=${publish}`
    const method = route.params.id ? 'PUT' : 'POST'
    const payload = {
      ...form,
      techField: isTa.value ? '教学助教' : isTeam.value ? positions.value[0]?.direction || '其它' : form.techField,
      difficulty: isIntern.value ? form.difficulty : 'L3',
      repoUrl: isIntern.value ? form.repoUrl || null : null,
      scheduleType: isIntern.value || isRa.value || isTeam.value ? null : form.scheduleType || null,
      educationLevels: isTa.value ? form.educationLevels : null,
      durationWeeks: isIntern.value || isTeam.value ? form.durationWeeks : null,
      estimatedHours: isRa.value ? null : form.estimatedHours,
      supportLanguages: null,
      stipendText: form.stipendText.trim(),
      prdFileUrl: isIntern.value ? form.prdFileUrl || null : null,
      prdFileName: isIntern.value ? form.prdFileName || null : null,
      techDocFileUrl: isIntern.value ? form.techDocFileUrl || null : null,
      techDocFileName: isIntern.value ? form.techDocFileName || null : null,
      quota: isTeam.value ? teamQuotaTotal.value : form.quota,
      bidDeadline: isRa.value && !bidLongTerm.value && form.bidDeadline ? form.bidDeadline : null,
      positions: isTeam.value
        ? positions.value.map((item) => ({
            id: item.id || null,
            title: (item.title || item.direction).trim(),
            direction: item.direction,
            quota: item.quota,
            requirement: item.requirement.trim() || null,
          }))
        : null,
    }
    await api<Task>(path, { method, body: JSON.stringify(payload) })
    Message.success(publish ? '已发布' : '已保存草稿')
    await goTasksList(publish ? 'ALL' : 'DRAFT')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <a-card :title="route.params.id ? '编辑项目' : (isRa ? '发布科研 RA' : '发布项目')">
    <a-alert
      v-if="isRa"
      type="info"
      style="margin-bottom: 16px"
      title="科研 RA · 揭榜形式"
    >
      科研 RA 采用揭榜：发布开放方向，学生提交方案；可同时揭榜多人，截止时间可设也可长期开放。
    </a-alert>
    <a-alert
      v-if="isTeam"
      type="info"
      style="margin-bottom: 16px"
      title="项目 · 一任务多岗位"
    >
      助教 / 开发 / 科研以外的需求放这里。可挂多个岗位（字典维护），各岗位独立名额，适合数据集、运营等场景。
    </a-alert>
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
          @change="() => { form.scheduleType = ''; syncFieldForRole() }"
        >
          <a-radio v-for="item in TASK_ROLE_TYPES" :key="item.value" :value="item.value">{{ item.label }}</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-row v-if="!isTa && !isTeam" :gutter="12">
        <a-col :span="isIntern ? 16 : 24">
          <a-form-item :label="isRa ? '研究方向' : '技术领域'" required>
            <a-select v-model="form.techField">
              <a-option v-for="item in fieldOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
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
      </a-row>
      <a-form-item v-if="isTa" label="在读身份" required>
        <a-checkbox-group :model-value="selectedEducations" @change="(value) => syncEducations((value as string[]) || [])">
          <a-checkbox v-for="item in educationOptions" :key="item.value" :value="item.value">{{ item.label }}</a-checkbox>
        </a-checkbox-group>
      </a-form-item>
      <div v-if="isTeam" class="positions-block">
        <div class="positions-head">
          <strong>岗位设置</strong>
          <span class="positions-total">总名额 {{ teamQuotaTotal }}</span>
          <a-button size="mini" type="outline" @click="addPosition">添加岗位</a-button>
        </div>
        <div v-for="(item, index) in positions" :key="index" class="position-row">
          <a-select
            :model-value="item.direction"
            placeholder="选择岗位"
            @change="(value) => onPositionDirectionChange(index, String(value || ''))"
          >
            <a-option v-for="opt in teamPositionOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-option>
          </a-select>
          <a-input-number v-model="item.quota" :min="1" :max="200" placeholder="名额" />
          <a-input v-model="item.requirement" placeholder="岗位要求（可选）" />
          <a-button size="mini" status="danger" type="text" @click="removePosition(index)">删除</a-button>
        </div>
      </div>
      <a-row :gutter="[16, 8]" class="meta-row">
        <a-col v-if="!isTeam" :xs="24" :sm="12" :lg="8">
          <a-form-item :label="isRa ? '揭榜名额' : '名额'">
            <a-input-number v-model="form.quota" :min="1" :max="isRa ? 50 : 50" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col v-if="isRa" :xs="24" :sm="12" :lg="16">
          <a-form-item label="揭榜截止">
            <div class="deadline-row">
              <a-checkbox
                :model-value="bidLongTerm"
                @change="(checked) => { bidLongTerm = Boolean(checked); if (bidLongTerm) form.bidDeadline = '' }"
              >
                长期开放
              </a-checkbox>
              <a-date-picker
                v-if="!bidLongTerm"
                v-model="form.bidDeadline"
                show-time
                value-format="YYYY-MM-DDTHH:mm:ss"
                format="YYYY-MM-DD HH:mm"
                style="flex: 1; min-width: 200px"
                placeholder="方案提交截止时间"
              />
            </div>
          </a-form-item>
        </a-col>
        <a-col v-if="isIntern || isTeam" :xs="24" :sm="12" :lg="8">
          <a-form-item :label="isTeam ? '周期' : '开发周期'">
            <a-input-number v-model="form.durationWeeks" :min="1" style="width: 100%">
              <template #suffix>周</template>
            </a-input-number>
          </a-form-item>
        </a-col>
        <a-col v-if="isTa" :xs="24" :sm="12" :lg="8">
          <a-form-item label="学期" required>
            <a-select v-model="form.scheduleType" placeholder="请选择">
              <a-option v-for="item in scheduleOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col v-if="!isRa" :xs="24" :sm="12" :lg="8">
          <a-form-item label="每周投入">
            <a-input-number v-model="form.estimatedHours" :min="1" style="width: 100%">
              <template #suffix>小时</template>
            </a-input-number>
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
      <a-form-item :label="isRa ? '背景与开放方向' : '背景与目标'" required>
        <a-textarea
          v-model="form.background"
          :auto-size="{ minRows: 3 }"
          :placeholder="isRa ? '说明研究背景、希望攻关的开放问题' : ''"
        />
      </a-form-item>
      <a-form-item :label="isRa ? '期望产出' : '产出要求'" :required="!isRa">
        <a-textarea
          v-model="form.outputRequirement"
          :placeholder="isRa ? '可选：希望方案落地后产出什么（论文、原型、数据集等）' : ''"
        />
      </a-form-item>
      <a-form-item
        field="techRequirement"
        :label="isRa ? '能力与约束' : '能力要求'"
        :required="!isRa"
        :rules="isRa ? [] : [{ required: true, message: '请填写能力要求' }]"
      >
        <a-textarea
          v-model="form.techRequirement"
          :auto-size="{ minRows: 3 }"
          :placeholder="isRa ? '可选：前置知识、数据/算力约束、协作方式等' : '例如：熟悉 Java / Spring Boot，能独立完成模块开发与周报'"
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
.deadline-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.positions-block {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  background: #fafbfc;
}
.positions-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.positions-total {
  margin-right: auto;
  font-size: 13px;
  color: #6b7280;
}
.position-row {
  display: grid;
  grid-template-columns: minmax(120px, 1.2fr) 100px minmax(0, 1.6fr) auto;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
@media (max-width: 800px) {
  .position-row {
    grid-template-columns: 1fr;
  }
}
.meta-row :deep(.arco-form-item-label) {
  white-space: nowrap;
  min-height: 22px;
}
.meta-row :deep(.arco-input-suffix) {
  color: var(--color-text-3);
  font-size: 13px;
  padding: 0 8px;
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
