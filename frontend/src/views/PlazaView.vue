<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { auth } from '../auth'
import { loadDicts, dictOptions, scheduleLabelMap } from '../composables/useDicts'
import { TASK_ROLE_TYPES, type Task } from '../types'

type FilterKey =
  | 'techFields'
  | 'supportLanguages'
  | 'programmingLanguages'
  | 'durations'
  | 'scheduleTypes'
  | 'weeklyHours'
  | 'educationLevels'

interface SelectedChip {
  key: FilterKey | 'keyword'
  value: string
  label: string
  group: string
}

const router = useRouter()
const sourceTasks = ref<Task[]>([])
const loading = ref(false)
const activeModule = ref<'TA' | 'INTERN' | 'RA'>('INTERN')
const tutorialDocUrl = ref('')
const tutorialBannerText = ref('系统使用教程请参考平台文档，点击查看完整说明。')
const filters = reactive({
  techFields: [] as string[],
  supportLanguages: [] as string[],
  programmingLanguages: [] as string[],
  durations: [] as string[],
  scheduleTypes: [] as string[],
  weeklyHours: [] as string[],
  educationLevels: [] as string[],
  keyword: '',
})

const modules = TASK_ROLE_TYPES.map((item) => ({
  value: item.value,
  label: item.label,
  blurb:
    item.value === 'TA'
      ? '助教任务：课程支持、答疑与教学协作'
      : item.value === 'RA'
        ? '科研任务：课题研究、实验与论文相关协作'
        : '开发任务：产品功能、工程实现与开源贡献',
}))

const techOptions = dictOptions('TECH_FIELD')
const supportOptions = dictOptions('SUPPORT_LANGUAGE')
const durationOptions = dictOptions('DURATION_RANGE')
const taScheduleOptions = dictOptions('TA_SCHEDULE')
const raScheduleOptions = dictOptions('RA_SCHEDULE')
const weeklyOptions = dictOptions('WEEKLY_HOURS_RANGE')
const educationOptions = dictOptions('STUDENT_IDENTITY')
const languageOptions = dictOptions('PROGRAMMING_LANGUAGE')
const scheduleLabels = scheduleLabelMap()

const activeModuleMeta = computed(() => modules.find((item) => item.value === activeModule.value) || modules[1])
const isIntern = computed(() => activeModule.value === 'INTERN')
const isTa = computed(() => activeModule.value === 'TA')
const isRa = computed(() => activeModule.value === 'RA')

function inRange(value: number, range: string) {
  const [min, max] = range.split('-').map(Number)
  return value >= min && value <= max
}

function matchesAnyRange(value: number, ranges: string[]) {
  if (!ranges.length) return true
  return ranges.some((range) => inRange(value, range))
}

const tasks = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return sourceTasks.value.filter((task) => {
    if (filters.supportLanguages.length && !filters.supportLanguages.includes(task.supportLanguages)) {
      return false
    }
    if (isIntern.value) {
      if (filters.techFields.length && !filters.techFields.includes(task.techField)) return false
      if (filters.durations.length && !matchesAnyRange(task.durationWeeks, filters.durations)) return false
      if (
        filters.programmingLanguages.length &&
        !filters.programmingLanguages.some((lang) =>
          task.programmingLanguages
            .split(/[,，、/|]+/)
            .map((part) => part.trim())
            .includes(lang),
        )
      ) {
        return false
      }
    } else if (isTa.value) {
      if (filters.scheduleTypes.length && !filters.scheduleTypes.includes(task.scheduleType || '')) return false
      if (filters.weeklyHours.length && !matchesAnyRange(task.estimatedHours, filters.weeklyHours)) return false
      if (
        filters.educationLevels.length &&
        !filters.educationLevels.some((level) =>
          (task.educationLevels || '')
            .split(',')
            .map((part) => part.trim())
            .includes(level),
        )
      ) {
        return false
      }
    } else {
      if (filters.scheduleTypes.length && !filters.scheduleTypes.includes(task.scheduleType || '')) return false
      if (filters.techFields.length && !filters.techFields.includes(task.techField)) return false
    }
    if (!keyword) return true
    return `${task.title} ${task.techStack} ${task.programmingLanguages} ${task.publisherName} ${task.techField} ${task.stipendText || ''}`
      .toLowerCase()
      .includes(keyword)
  })
})

const resultCount = computed(() => tasks.value.length)

const selectedChips = computed<SelectedChip[]>(() => {
  const chips: SelectedChip[] = []
  const pushMany = (key: FilterKey, values: string[], options: { label: string; value: string }[], group: string) => {
    for (const value of values) {
      const hit = options.find((item) => item.value === value)
      chips.push({ key, value, label: hit?.label || value, group })
    }
  }
  if (isIntern.value) {
    pushMany('durations', filters.durations, durationOptions.value, '开发周期')
    pushMany('techFields', filters.techFields, techOptions.value, '技术领域')
    pushMany('programmingLanguages', filters.programmingLanguages, languageOptions.value, '编程语言')
  } else if (isTa.value) {
    pushMany('scheduleTypes', filters.scheduleTypes, taScheduleOptions.value, '学期')
    pushMany('weeklyHours', filters.weeklyHours, weeklyOptions.value, '每周投入')
    pushMany('educationLevels', filters.educationLevels, educationOptions.value, '在读身份')
  } else {
    pushMany('scheduleTypes', filters.scheduleTypes, raScheduleOptions.value, '科研周期')
    pushMany('techFields', filters.techFields, techOptions.value, '研究方向')
  }
  pushMany('supportLanguages', filters.supportLanguages, supportOptions.value, '支持语言')
  if (filters.keyword.trim()) {
    chips.push({
      key: 'keyword',
      value: filters.keyword.trim(),
      label: `关键词：${filters.keyword.trim()}`,
      group: '关键词',
    })
  }
  return chips
})

function isSelected(key: FilterKey, value: string) {
  return filters[key].includes(value)
}

function toggleFilter(key: FilterKey, value: string) {
  const list = filters[key]
  const index = list.indexOf(value)
  if (index >= 0) list.splice(index, 1)
  else list.push(value)
}

function removeChip(chip: SelectedChip) {
  if (chip.key === 'keyword') {
    filters.keyword = ''
    return
  }
  const list = filters[chip.key]
  const index = list.indexOf(chip.value)
  if (index >= 0) list.splice(index, 1)
}

function taskCode(task: Task) {
  return task.publicCode || '—'
}

function durationLabel(weeks: number) {
  return `${weeks} 周`
}

function scheduleLabel(task: Task) {
  if (!task.scheduleType) return '—'
  return scheduleLabels.value[task.scheduleType] || task.scheduleType
}

function seatLabel(task: Task) {
  if (task.filled <= 0) return '—'
  return `${task.filled}/${task.quota}`
}

async function loadModuleList() {
  loading.value = true
  try {
    const list = await api<Task[]>(`/api/v1/tasks?roleType=${activeModule.value}`)
    sourceTasks.value = list
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
    sourceTasks.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.techFields = []
  filters.supportLanguages = []
  filters.programmingLanguages = []
  filters.durations = []
  filters.scheduleTypes = []
  filters.weeklyHours = []
  filters.educationLevels = []
  filters.keyword = ''
}

async function switchModule(value: 'TA' | 'INTERN' | 'RA') {
  if (activeModule.value === value) return
  activeModule.value = value
  resetFilters()
  await loadModuleList()
}

function openTask(task: Task) {
  void router.push(`/tasks/${task.id}`)
}

async function openPublisherChat(task: Task, event: Event) {
  event.stopPropagation()
  if (!auth.token) {
    void router.push('/auth/login')
    return
  }
  if (auth.user?.role !== 'STUDENT') {
    void router.push(`/tasks/${task.id}`)
    return
  }
  try {
    await api(`/api/v1/tasks/${task.id}/messages/open`, { method: 'POST' })
    void router.push({ path: '/messages', query: { taskId: String(task.id) } })
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '无法开始沟通')
  }
}

async function loadSiteConfig() {
  try {
    const config = await api<{ tutorialDocUrl: string; tutorialBannerText: string }>('/api/v1/site-config')
    tutorialDocUrl.value = config.tutorialDocUrl || ''
    if (config.tutorialBannerText) tutorialBannerText.value = config.tutorialBannerText
  } catch {
    /* banner optional */
  }
}

onMounted(async () => {
  await Promise.all([loadDicts(), loadSiteConfig()])
  await loadModuleList()
})
</script>

<template>
  <section class="plaza">
    <div class="hero">
      <div class="hero-main">
        <div class="hero-copy">
          <p class="eyebrow">WHU TOP Developer</p>
          <h1>Welcome to <span>Campus Projects</span></h1>
          <p class="hero-description">
            让想法落地，让能力被看见。在<strong>助教</strong>、<strong>开发</strong>、<strong>科研</strong>
            三个频道里找到适合你的任务。
          </p>
          <a href="#opportunities" class="explore">探索开放任务 →</a>
        </div>
        <div class="hero-cards" aria-hidden="true">
          <article class="hero-card">
            <div class="hero-card-icon ta">&lt;/&gt;</div>
            <div>
              <h3>助教 · TA</h3>
              <p>课程支持与教学协作</p>
            </div>
          </article>
          <article class="hero-card">
            <div class="hero-card-icon intern">{}</div>
            <div>
              <h3>开发 · Intern</h3>
              <p>产品功能与工程实现</p>
            </div>
          </article>
          <article class="hero-card">
            <div class="hero-card-icon ra">◆</div>
            <div>
              <h3>科研 · RA</h3>
              <p>课题研究与实验协作</p>
            </div>
          </article>
        </div>
      </div>
      <a
        v-if="tutorialDocUrl"
        class="tutorial-banner"
        :href="tutorialDocUrl"
        target="_blank"
        rel="noopener"
      >
        <span class="tutorial-kicker">使用教程</span>
        <span class="tutorial-text">{{ tutorialBannerText }}</span>
        <span class="tutorial-link">查看文档 ↗</span>
      </a>
    </div>
    <div class="path-strip">
      <span><b>01</b> 筛选感兴趣的项目</span>
      <span><b>02</b> 沟通并提交申请</span>
      <span><b>03</b> 入项协作并完成</span>
    </div>

    <header id="opportunities" class="page-head">
      <div>
        <a-typography-title :heading="4" style="margin: 0">任务广场</a-typography-title>
        <p class="subtitle">先选频道；每个频道使用各自适配的筛选条件。</p>
      </div>
      <a-typography-text type="secondary">本频道 {{ resultCount }} 个项目</a-typography-text>
    </header>

    <nav class="module-tabs" aria-label="任务频道">
      <button
        v-for="item in modules"
        :key="item.value"
        type="button"
        class="module-tab"
        :class="{ active: activeModule === item.value }"
        @click="switchModule(item.value)"
      >
        <span class="module-name">{{ item.label }}</span>
        <span class="module-blurb">{{ item.blurb }}</span>
      </button>
    </nav>
    <p class="module-hint">{{ activeModuleMeta.blurb }}</p>

    <section class="filter-panel">
      <template v-if="isIntern">
        <div class="filter-row">
          <span class="filter-label">开发周期</span>
          <div class="chip-group">
            <button
              v-for="item in durationOptions"
              :key="`du-${item.value}`"
              type="button"
              class="chip"
              :class="{ active: isSelected('durations', item.value) }"
              @click="toggleFilter('durations', item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <span class="filter-label">技术领域</span>
          <div class="chip-group wrap">
            <button
              v-for="item in techOptions"
              :key="`t-${item.value}`"
              type="button"
              class="chip"
              :class="{ active: isSelected('techFields', item.value) }"
              @click="toggleFilter('techFields', item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <span class="filter-label">编程语言</span>
          <div class="chip-group wrap">
            <button
              v-for="item in languageOptions"
              :key="`pl-${item.value}`"
              type="button"
              class="chip"
              :class="{ active: isSelected('programmingLanguages', item.value) }"
              @click="toggleFilter('programmingLanguages', item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
      </template>

      <template v-else-if="isTa">
        <div class="filter-row">
          <span class="filter-label">学期</span>
          <div class="chip-group">
            <button
              v-for="item in taScheduleOptions"
              :key="`ta-${item.value}`"
              type="button"
              class="chip"
              :class="{ active: isSelected('scheduleTypes', item.value) }"
              @click="toggleFilter('scheduleTypes', item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <span class="filter-label">每周投入</span>
          <div class="chip-group">
            <button
              v-for="item in weeklyOptions"
              :key="`wh-${item.value}`"
              type="button"
              class="chip"
              :class="{ active: isSelected('weeklyHours', item.value) }"
              @click="toggleFilter('weeklyHours', item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <span class="filter-label">在读身份</span>
          <div class="chip-group">
            <button
              v-for="item in educationOptions"
              :key="`edu-${item.value}`"
              type="button"
              class="chip"
              :class="{ active: isSelected('educationLevels', item.value) }"
              @click="toggleFilter('educationLevels', item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="filter-row">
          <span class="filter-label">科研周期</span>
          <div class="chip-group">
            <button
              v-for="item in raScheduleOptions"
              :key="`ra-${item.value}`"
              type="button"
              class="chip"
              :class="{ active: isSelected('scheduleTypes', item.value) }"
              @click="toggleFilter('scheduleTypes', item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <span class="filter-label">研究方向</span>
          <div class="chip-group wrap">
            <button
              v-for="item in techOptions"
              :key="`rt-${item.value}`"
              type="button"
              class="chip"
              :class="{ active: isSelected('techFields', item.value) }"
              @click="toggleFilter('techFields', item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
      </template>

      <div class="filter-row">
        <span class="filter-label">支持语言</span>
        <div class="chip-group">
          <button
            v-for="item in supportOptions"
            :key="`s-${item.value}`"
            type="button"
            class="chip"
            :class="{ active: isSelected('supportLanguages', item.value) }"
            @click="toggleFilter('supportLanguages', item.value)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div class="filter-row tools">
        <span class="filter-label">关键词</span>
        <div class="tool-group">
          <a-input
            v-model="filters.keyword"
            allow-clear
            placeholder="搜索标题 / 发布方"
            style="width: min(280px, 100%)"
          />
          <a-button @click="resetFilters">清空已选</a-button>
        </div>
      </div>

      <div class="selected-bar">
        <span class="selected-label">已选</span>
        <div v-if="selectedChips.length" class="selected-chips">
          <button
            v-for="chip in selectedChips"
            :key="`${chip.key}-${chip.value}`"
            type="button"
            class="selected-chip"
            @click="removeChip(chip)"
          >
            <span>{{ chip.group }} · {{ chip.label }}</span>
            <span class="selected-x">×</span>
          </button>
        </div>
        <span v-else class="selected-empty">暂无筛选条件，点击上方选项可多选</span>
      </div>
    </section>

    <a-spin :loading="loading" class="list-spin">
      <a-empty v-if="!loading && tasks.length === 0" description="没有匹配的任务，请调整筛选条件" />
      <div v-else-if="tasks.length" class="table-wrap">
        <table class="project-table">
          <thead>
            <tr>
              <th class="col-code">项目编号</th>
              <th class="col-title">项目名称</th>
              <th>发布方</th>
              <th v-if="isIntern">开发周期</th>
              <th v-else-if="isTa">学期</th>
              <th v-else>科研周期</th>
              <th v-if="isTa">每周投入</th>
              <th v-else-if="isRa">方向</th>
              <th>支持语言</th>
              <th>名额</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="task in tasks"
              :key="task.id"
              class="project-row"
              @click="openTask(task)"
            >
              <td class="col-code mono">{{ taskCode(task) }}</td>
              <td class="col-title">
                <div class="title-cell">
                  <span class="title-text">{{ task.title }}</span>
                  <span v-if="!isTa" class="title-meta">
                    <template v-if="isIntern">{{ task.techField }} · {{ task.programmingLanguages }}</template>
                    <template v-else>{{ task.techField }}</template>
                  </span>
                </div>
              </td>
              <td>
                <button type="button" class="publisher-cell" @click="openPublisherChat(task, $event)">
                  {{ task.publisherName }}
                </button>
              </td>
              <td v-if="isIntern">{{ durationLabel(task.durationWeeks) }}</td>
              <td v-else>{{ scheduleLabel(task) }}</td>
              <td v-if="isTa">{{ task.estimatedHours }} 小时/周</td>
              <td v-else-if="isRa">{{ task.techField }}</td>
              <td>{{ task.supportLanguages }}</td>
              <td>{{ seatLabel(task) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.plaza {
  --accent: #2563eb;
  --line: #eef2f7;
  --ink: #111827;
  --muted: #6b7280;
}
.page-head { display: flex; justify-content: space-between; align-items: end; gap: 16px; margin-bottom: 14px; }
.module-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 10px;
}
.module-tab {
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  padding: 16px 18px;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}
.module-tab:hover {
  border-color: #bfdbfe;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}
.module-tab.active {
  border-color: #2563eb;
  background: linear-gradient(180deg, #eff6ff 0%, #fff 70%);
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.12);
}
.module-name {
  display: block;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink);
}
.module-tab.active .module-name { color: #1d4ed8; }
.module-blurb {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--muted);
}
.module-tab.active .module-blurb { color: #4b5563; }
.module-hint {
  margin: 0 0 14px;
  color: var(--muted);
  font-size: 13px;
}
.hero {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px 24px;
  border-radius: 16px;
  color: var(--ink);
  background:
    radial-gradient(ellipse at 90% 10%, rgba(79, 70, 229, 0.07), transparent 45%),
    radial-gradient(ellipse at 0% 100%, rgba(37, 99, 235, 0.06), transparent 40%),
    #fff;
  border: 1px solid #eef2f7;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 8px 24px rgba(15, 23, 42, 0.04);
}
.hero-main {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 20px;
  align-items: center;
}
.hero-copy { position: relative; z-index: 1; min-width: 0; }
.eyebrow {
  margin: 0;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #6b7280;
}
.hero h1 {
  font-family: inherit;
  font-size: clamp(22px, 3vw, 28px);
  font-weight: 800;
  line-height: 1.2;
  margin: 8px 0 8px;
  color: #111827;
  letter-spacing: -0.02em;
}
.hero h1 span {
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero-description {
  line-height: 1.65;
  color: #6b7280;
  font-size: 13px;
  max-width: 36em;
  margin: 0;
}
.hero-description strong { color: #111827; font-weight: 700; }
.explore {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 14px;
  border-radius: 8px;
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  color: #fff;
  font-weight: 600;
  font-size: 13px;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.22);
}
.explore:hover { filter: brightness(1.05); color: #fff; }
.hero-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hero-card {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f9fafb;
  border: 1px solid #eef2f7;
  display: flex;
  align-items: center;
  gap: 10px;
}
.hero-card-icon {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
}
.hero-card-icon.ta { background: #2563eb; }
.hero-card-icon.intern { background: #4f46e5; }
.hero-card-icon.ra { background: #0ea5e9; }
.hero-card h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: #111827;
}
.hero-card p {
  margin: 2px 0 0;
  font-size: 11px;
  color: #6b7280;
}
.path-strip {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 8px 16px;
  color: #6b7280;
  font-size: 13px;
}
.path-strip b {
  font-size: 12px;
  font-weight: 800;
  color: #2563eb;
  margin-right: 10px;
}
.tutorial-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: linear-gradient(90deg, #eff6ff 0%, #f8fafc 70%);
  color: inherit;
  text-decoration: none;
}
.tutorial-banner:hover { border-color: #93c5fd; }
.tutorial-kicker {
  flex: none;
  font-size: 11px;
  font-weight: 600;
  color: #1d4ed8;
  background: #dbeafe;
  border-radius: 5px;
  padding: 2px 7px;
}
.tutorial-text {
  flex: 1;
  font-size: 12px;
  color: #4b5563;
  line-height: 1.4;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tutorial-link { flex: none; font-size: 12px; color: #2563eb; white-space: nowrap; font-weight: 600; }

.filter-panel {
  margin-bottom: 18px;
  padding: 18px 20px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.filter-row {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 12px;
  align-items: start;
}
.filter-label {
  padding-top: 6px;
  color: var(--muted);
  font-size: 13px;
  white-space: nowrap;
}
.chip-group {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}
.chip-group.wrap {
  flex-wrap: wrap;
  overflow: visible;
}
.chip {
  border: 1px solid var(--line);
  background: #f7f9fc;
  color: #334155;
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}
.chip:hover { border-color: #93c5fd; }
.chip.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.tool-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.selected-bar {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 4px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
}
.selected-label {
  flex: 0 0 auto;
  margin-top: 4px;
  font-size: 13px;
  color: var(--muted);
}
.selected-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.selected-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
}
.selected-chip:hover {
  border-color: #2563eb;
}
.selected-x {
  font-size: 14px;
  line-height: 1;
  color: #5b7c90;
}
.selected-empty {
  color: #94a3b8;
  font-size: 13px;
  padding-top: 4px;
}
.list-spin {
  width: 100%;
  min-height: 120px;
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.project-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 920px;
  font-size: 14px;
  color: var(--ink);
}
.project-table th,
.project-table td {
  padding: 14px 16px;
  border-bottom: 1px solid #eef1f5;
  text-align: left;
  vertical-align: middle;
}
.project-table thead th {
  background: #f6f8fb;
  color: #5b6b7c;
  font-weight: 600;
  font-size: 13px;
  white-space: nowrap;
}
.project-row {
  cursor: pointer;
  transition: background 0.15s ease;
}
.project-row:hover {
  background: #f8fafc;
}
.project-row:last-child td {
  border-bottom: none;
}
.col-code {
  width: 108px;
  white-space: nowrap;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: #64748b;
  font-size: 13px;
}
.col-title {
  min-width: 280px;
}
.title-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.title-text {
  color: #1d4ed8;
  font-weight: 600;
}
.project-row:hover .title-text {
  text-decoration: underline;
}
.title-meta {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.4;
}
.publisher-cell {
  border: 0;
  padding: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font: inherit;
}
.publisher-cell:hover {
  text-decoration: underline;
}
.stipend-cell {
  max-width: 180px;
  color: var(--muted);
  font-size: 13px;
  overflow-wrap: anywhere;
}
.apply-link {
  color: #94a3b8;
}
.apply-link.linked {
  color: #2563eb;
  font-weight: 600;
}

@media (max-width: 900px) {
  .module-tabs { grid-template-columns: 1fr; }
  .filter-row { grid-template-columns: 1fr; gap: 8px; }
  .filter-label { padding-top: 0; }
  .hero-main { grid-template-columns: 1fr; }
}
@media (max-width: 700px) {
  .hero { padding: 16px; }
  .hero-cards { display: none; }
  .tutorial-text { white-space: normal; }
  .path-strip { flex-wrap: wrap; gap: 12px; }
  .page-head { align-items: start; flex-direction: column; }
}
</style>
