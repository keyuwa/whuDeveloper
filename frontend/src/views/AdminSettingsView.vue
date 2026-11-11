<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { renderMarkdown } from '../markdown'

type SettingsSection = 'ark' | 'rubric' | 'tutorial' | 'plaza' | 'sla'

type SettingsPayload = {
  reviewSlaDays: number
  escalateSlaDays: number
  tutorialDocUrl: string
  tutorialBannerText: string
  plazaBlurbTa: string
  plazaBlurbIntern: string
  plazaBlurbRa: string
  plazaBlurbTeam: string
  abilityRubricMd: string
  arkEnabled: boolean
  arkApiKeyMasked: string
  arkApiKeyConfigured: boolean
  arkBaseUrl: string
  arkModel: string
  raChallengeAiPrompt: string
}

const SECTIONS: { key: SettingsSection; label: string; desc: string }[] = [
  { key: 'ark', label: '科研 RA · AI', desc: '揭榜方案可行性初判接入与 Prompt' },
  { key: 'rubric', label: 'WHU Dev 细则', desc: '能力裁定 Markdown 细则' },
  { key: 'tutorial', label: '教程文档', desc: '任务广场教程 Banner' },
  { key: 'plaza', label: '频道说明', desc: '广场助教 / 开发 / 科研 / 项目简介' },
  { key: 'sla', label: 'SLA', desc: '催办与升级天数' },
]

const DEFAULT_PLAZA_BLURBS = {
  plazaBlurbTa: '助教任务：课程支持、答疑与教学协作',
  plazaBlurbIntern: '开发任务：产品功能、工程实现与开源贡献',
  plazaBlurbRa: '科研任务：以揭榜形式开放方向，学生提交方案竞标',
  plazaBlurbTeam: '项目：运营、标注等协作需求，按岗位报名，一次可招多人',
}

const route = useRoute()
const router = useRouter()
const loadingPage = ref(true)
const preview = ref(false)
const clearArkApiKey = ref(false)
const saving = ref(false)
const testing = ref(false)
const testResult = ref<{ ok: boolean; message: string; latencyMs?: number | null; sample?: string | null } | null>(null)

const form = reactive({
  reviewSlaDays: 7,
  escalateSlaDays: 14,
  tutorialDocUrl: '',
  tutorialBannerText: '查看使用教程',
  plazaBlurbTa: DEFAULT_PLAZA_BLURBS.plazaBlurbTa,
  plazaBlurbIntern: DEFAULT_PLAZA_BLURBS.plazaBlurbIntern,
  plazaBlurbRa: DEFAULT_PLAZA_BLURBS.plazaBlurbRa,
  plazaBlurbTeam: DEFAULT_PLAZA_BLURBS.plazaBlurbTeam,
  abilityRubricMd: '',
  arkEnabled: false,
  arkApiKey: '',
  arkApiKeyMasked: '',
  arkApiKeyConfigured: false,
  arkBaseUrl: '',
  arkModel: '',
  raChallengeAiPrompt: '',
})

function isSettingsSection(value: string): value is SettingsSection {
  return value === 'ark' || value === 'rubric' || value === 'tutorial' || value === 'plaza' || value === 'sla'
}

const section = computed<SettingsSection>(() => {
  const raw = String(route.params.section || '').toLowerCase()
  if (isSettingsSection(raw)) return raw
  return 'ark'
})

const currentMeta = computed(() => SECTIONS.find((item) => item.key === section.value) || SECTIONS[0])
const rubricHtml = computed(() => renderMarkdown(form.abilityRubricMd))

const promptPlaceholder = `留空则使用内置默认模板。可用占位符：
{{title}} {{techField}} {{background}} {{outputRequirement}} {{techRequirement}} {{proposal}}

请让模型输出 JSON：score、completeness、fit、feasibility、evidence、summary、risks。`

function applySettings(settings: SettingsPayload) {
  form.reviewSlaDays = settings.reviewSlaDays
  form.escalateSlaDays = settings.escalateSlaDays
  form.tutorialDocUrl = settings.tutorialDocUrl || ''
  form.tutorialBannerText = settings.tutorialBannerText || form.tutorialBannerText
  form.plazaBlurbTa = settings.plazaBlurbTa || DEFAULT_PLAZA_BLURBS.plazaBlurbTa
  form.plazaBlurbIntern = settings.plazaBlurbIntern || DEFAULT_PLAZA_BLURBS.plazaBlurbIntern
  form.plazaBlurbRa = settings.plazaBlurbRa || DEFAULT_PLAZA_BLURBS.plazaBlurbRa
  form.plazaBlurbTeam = settings.plazaBlurbTeam || DEFAULT_PLAZA_BLURBS.plazaBlurbTeam
  form.abilityRubricMd = settings.abilityRubricMd || ''
  form.arkEnabled = Boolean(settings.arkEnabled)
  form.arkApiKeyMasked = settings.arkApiKeyMasked || ''
  form.arkApiKeyConfigured = Boolean(settings.arkApiKeyConfigured)
  form.arkBaseUrl = settings.arkBaseUrl || ''
  form.arkModel = settings.arkModel || ''
  form.raChallengeAiPrompt = settings.raChallengeAiPrompt || ''
}

async function loadSettings() {
  loadingPage.value = true
  try {
    const settings = await api<SettingsPayload>('/api/v1/admin/settings')
    applySettings(settings)
    if (section.value === 'rubric' && !form.abilityRubricMd.trim()) {
      const rubric = await api<{ markdown: string }>('/api/v1/ability/rubric')
      form.abilityRubricMd = rubric.markdown || ''
    }
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loadingPage.value = false
  }
}

function goSection(key: SettingsSection) {
  if (key === section.value) return
  router.push(`/admin/settings/${key}`)
}

async function saveCurrent() {
  saving.value = true
  try {
    const bodies: Record<SettingsSection, Record<string, unknown>> = {
      ark: {
        arkEnabled: form.arkEnabled,
        arkApiKey: form.arkApiKey.trim() || null,
        clearArkApiKey: clearArkApiKey.value,
        arkBaseUrl: form.arkBaseUrl,
        arkModel: form.arkModel,
        raChallengeAiPrompt: form.raChallengeAiPrompt,
      },
      rubric: { abilityRubricMd: form.abilityRubricMd },
      tutorial: {
        tutorialDocUrl: form.tutorialDocUrl,
        tutorialBannerText: form.tutorialBannerText,
      },
      plaza: {
        plazaBlurbTa: form.plazaBlurbTa,
        plazaBlurbIntern: form.plazaBlurbIntern,
        plazaBlurbRa: form.plazaBlurbRa,
        plazaBlurbTeam: form.plazaBlurbTeam,
      },
      sla: {
        reviewSlaDays: form.reviewSlaDays,
        escalateSlaDays: form.escalateSlaDays,
      },
    }
    const updated = await api<SettingsPayload>(`/api/v1/admin/settings/${section.value}`, {
      method: 'PUT',
      body: JSON.stringify(bodies[section.value]),
    })
    applySettings(updated)
    if (section.value === 'ark') {
      form.arkApiKey = ''
      clearArkApiKey.value = false
    }
    Message.success('已保存')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function testArk() {
  testing.value = true
  testResult.value = null
  try {
    const result = await api<{
      ok: boolean
      message: string
      latencyMs?: number | null
      sample?: string | null
    }>('/api/v1/admin/settings/ark/test', {
      method: 'POST',
      body: JSON.stringify({
        arkApiKey: form.arkApiKey.trim() || null,
        arkBaseUrl: form.arkBaseUrl.trim() || null,
        arkModel: form.arkModel.trim() || null,
      }),
    })
    testResult.value = result
    if (result.ok) Message.success(result.message)
    else Message.error(result.message)
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : '接口测试失败'
    testResult.value = { ok: false, message }
    Message.error(message)
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  if (!route.params.section) {
    router.replace('/admin/settings/ark')
    return
  }
  void loadSettings()
})

watch(
  () => route.params.section,
  (value) => {
    if (!value) {
      router.replace('/admin/settings/ark')
      return
    }
    const raw = String(value).toLowerCase()
    if (!isSettingsSection(raw)) {
      router.replace('/admin/settings/ark')
      return
    }
    void loadSettings()
  },
)
</script>

<template>
  <div class="settings-page">
    <header class="settings-head">
      <div>
        <a-typography-title :heading="4" style="margin: 0">系统设置</a-typography-title>
        <p class="subtitle">{{ currentMeta.desc }}</p>
      </div>
    </header>

    <nav class="section-tabs" aria-label="设置分类">
      <button
        v-for="item in SECTIONS"
        :key="item.key"
        type="button"
        class="section-tab"
        :class="{ active: section === item.key }"
        @click="goSection(item.key)"
      >
        {{ item.label }}
      </button>
    </nav>

    <a-spin :loading="loadingPage" style="width: 100%">
      <a-card :title="currentMeta.label" class="settings-card">
        <template v-if="section === 'ark'">
          <a-typography-paragraph type="secondary">
            仅对科研 RA 揭榜方案做可行性初判。接入 Agent Plan / 方舟；Key 仅管理员可改，接口脱敏展示。AI 结果仅供发布方参考，不自动揭榜。
          </a-typography-paragraph>
          <a-form :model="form" layout="vertical">
            <a-form-item label="启用 AI 初判">
              <a-switch v-model="form.arkEnabled" />
            </a-form-item>
            <a-form-item label="API Key">
              <a-input-password
                v-model="form.arkApiKey"
                :placeholder="form.arkApiKeyConfigured ? `已配置 ${form.arkApiKeyMasked}，留空表示不修改` : '粘贴 Agent Plan API Key'"
                allow-clear
              />
              <a-checkbox v-if="form.arkApiKeyConfigured" v-model="clearArkApiKey" style="margin-top: 8px">
                清除已保存的 Key
              </a-checkbox>
            </a-form-item>
            <a-form-item label="Base URL" required>
              <a-input
                v-model="form.arkBaseUrl"
                placeholder="自行填写，如 https://ark.cn-beijing.volces.com/api/plan/v1"
                allow-clear
              />
              <div class="field-hint">
                不设默认值。Agent Plan 一般为 <code>.../api/plan/v1</code>（填到 <code>/api/plan</code> 时保存会自动补 <code>/v1</code>）；平台后付费为 <code>.../api/v3</code>。
              </div>
            </a-form-item>
            <a-form-item label="模型 ID" required>
              <a-input v-model="form.arkModel" placeholder="自行填写套餐内模型 ID" allow-clear />
              <div class="field-hint">
                不设默认值。须与当前 Key 类型匹配；Agent Plan 需套餐内模型，否则可能 UnsupportedModel。
              </div>
            </a-form-item>
            <a-form-item label="接口测试">
              <div class="test-row">
                <a-button :loading="testing" @click="testArk">测试连通</a-button>
                <span class="test-hint">使用本页填写的 Key / Base URL / 模型；Key 留空则用已保存配置。</span>
              </div>
              <a-alert
                v-if="testResult"
                style="margin-top: 10px"
                :type="testResult.ok ? 'success' : 'error'"
                :title="testResult.message"
                :closable="false"
              >
                <template v-if="testResult.latencyMs != null || testResult.sample">
                  <div v-if="testResult.latencyMs != null">耗时 {{ testResult.latencyMs }} ms</div>
                  <div v-if="testResult.sample" class="test-sample">{{ testResult.sample }}</div>
                </template>
              </a-alert>
            </a-form-item>
            <a-form-item label="评审 Prompt">
              <a-textarea
                v-model="form.raChallengeAiPrompt"
                :auto-size="{ minRows: 12, maxRows: 30 }"
                :placeholder="promptPlaceholder"
              />
            </a-form-item>
          </a-form>
        </template>

        <template v-else-if="section === 'rubric'">
          <a-typography-paragraph type="secondary">
            支持 Markdown。保存后在「WHU Dev → 查看裁定细则」以排版模式展示；留空则回退内置默认文案。
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
        </template>

        <template v-else-if="section === 'tutorial'">
          <a-typography-paragraph type="secondary">
            配置后会在任务广场流程条下方显示教程 Banner；链接为空则不展示。
          </a-typography-paragraph>
          <a-form :model="form" layout="vertical" style="max-width: 640px">
            <a-form-item label="教程文档链接">
              <a-input v-model="form.tutorialDocUrl" placeholder="例如飞书 Wiki 链接" allow-clear />
            </a-form-item>
            <a-form-item label="Banner 文案">
              <a-input v-model="form.tutorialBannerText" placeholder="提示文案" allow-clear />
            </a-form-item>
          </a-form>
        </template>

        <template v-else-if="section === 'plaza'">
          <a-typography-paragraph type="secondary">
            文案显示在任务广场四个频道卡片下方；留空保存时回退为默认说明。
          </a-typography-paragraph>
          <a-form :model="form" layout="vertical" style="max-width: 720px">
            <a-form-item label="助教">
              <a-input v-model="form.plazaBlurbTa" :max-length="200" show-word-limit allow-clear />
            </a-form-item>
            <a-form-item label="开发">
              <a-input v-model="form.plazaBlurbIntern" :max-length="200" show-word-limit allow-clear />
            </a-form-item>
            <a-form-item label="科研">
              <a-input v-model="form.plazaBlurbRa" :max-length="200" show-word-limit allow-clear />
            </a-form-item>
            <a-form-item label="项目">
              <a-input v-model="form.plazaBlurbTeam" :max-length="200" show-word-limit allow-clear />
            </a-form-item>
          </a-form>
        </template>

        <template v-else>
          <a-typography-paragraph type="secondary">
            定时任务每分钟扫描一次。0 天表示刚提交就算逾期，方便本地验证。
          </a-typography-paragraph>
          <a-form :model="form" layout="vertical" style="max-width: 640px">
            <a-form-item label="申请未审催办（天）">
              <a-input-number v-model="form.reviewSlaDays" :min="0" :max="60" style="width: 100%" />
            </a-form-item>
            <a-form-item label="升级到管理队列（天）">
              <a-input-number v-model="form.escalateSlaDays" :min="0" :max="120" style="width: 100%" />
            </a-form-item>
          </a-form>
        </template>

        <div class="card-actions">
          <a-button type="primary" :loading="saving" @click="saveCurrent">保存本页</a-button>
        </div>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.settings-page {
  width: min(920px, 100%);
  margin: 0 auto;
}
.settings-head {
  margin-bottom: 14px;
  text-align: center;
}
.subtitle {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}
.section-tabs {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-bottom: 16px;
}
.section-tab {
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #374151;
  border-radius: 999px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, color 0.15s ease;
}
.section-tab:hover {
  border-color: #93c5fd;
  color: #1d4ed8;
}
.section-tab.active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
}
.settings-card { width: 100%; }
.card-actions { margin-top: 16px; }
.test-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
}
.test-hint,
.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}
.field-hint code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  background: #f3f4f6;
  padding: 1px 5px;
  border-radius: 4px;
}
.test-sample {
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
}
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
