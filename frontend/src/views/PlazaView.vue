<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { auth } from '../auth'
import { useIsMobile } from '../composables/useDevice'
import { loadDicts, dictOptions, scheduleLabelMap } from '../composables/useDicts'
import { readCachedSiteConfig, writeCachedSiteConfig } from '../siteConfigCache'
import { TASK_ROLE_LABEL, TASK_ROLE_TYPES, type Task, type TaskPage } from '../types'

const isMobile = useIsMobile()
const PAGE_SIZE = 20

type FilterKey =
  | 'techFields'
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
const loadingMore = ref(false)
const page = ref(0)
const hasMore = ref(true)
const totalCount = ref(0)
const showPcPager = computed(() => !isMobile.value && totalCount.value > PAGE_SIZE)
const loadError = ref(false)
const activeModule = ref<'TA' | 'INTERN' | 'RA' | 'TEAM'>('INTERN')
const filterDrawerVisible = ref(false)
const loadMoreSentinel = ref<HTMLElement | null>(null)
let loadMoreObserver: IntersectionObserver | null = null
const cachedSite = readCachedSiteConfig()
const tutorialDocUrlStudent = ref(cachedSite?.tutorialDocUrlStudent || '')
const tutorialDocUrlPublisher = ref(cachedSite?.tutorialDocUrlPublisher || '')
const tutorialDocUrl = computed(() => {
  if (auth.user?.role === 'PUBLISHER') return tutorialDocUrlPublisher.value
  return tutorialDocUrlStudent.value
})
const HERO_DESC = '从课堂到开源，从想法到实践。发现适合你的项目，与志同道合的人一起完成。'
const DEFAULT_MODULE_BLURBS: Record<string, string> = {
  TA: '助教任务：课程支持、答疑与教学协作',
  INTERN: '开发任务：产品功能、工程实现与开源贡献',
  RA: '科研任务：以揭榜形式开放方向，学生提交方案竞标',
  TEAM: '项目：运营、标注等协作需求，按岗位报名，一次可招多人',
}
const moduleBlurbs = ref<Record<string, string>>({ ...DEFAULT_MODULE_BLURBS })
const filters = reactive({
  techFields: [] as string[],
  programmingLanguages: [] as string[],
  durations: [] as string[],
  scheduleTypes: [] as string[],
  weeklyHours: [] as string[],
  educationLevels: [] as string[],
  keyword: '',
})

const modules = computed(() =>
  TASK_ROLE_TYPES.map((item) => ({
    value: item.value,
    label: item.label,
    shortLabel: item.shortLabel,
    blurb: moduleBlurbs.value[item.value] || DEFAULT_MODULE_BLURBS[item.value] || '',
  })),
)

const techOptions = dictOptions('TECH_FIELD')
const researchOptions = dictOptions('RESEARCH_DIRECTION')
const teamPositionOptions = dictOptions('TEAM_POSITION')
const durationOptions = dictOptions('DURATION_RANGE')
const taScheduleOptions = dictOptions('TA_SCHEDULE')
const weeklyOptions = dictOptions('WEEKLY_HOURS_RANGE')
const educationOptions = dictOptions('STUDENT_IDENTITY')
const languageOptions = dictOptions('PROGRAMMING_LANGUAGE')
const scheduleLabels = scheduleLabelMap()

const activeModuleMeta = computed(
  () => modules.value.find((item) => item.value === activeModule.value) || modules.value[1],
)
const isIntern = computed(() => activeModule.value === 'INTERN')
const isTa = computed(() => activeModule.value === 'TA')
const isRa = computed(() => activeModule.value === 'RA')
const isTeam = computed(() => activeModule.value === 'TEAM')

interface FilterSection {
  key: FilterKey
  label: string
  options: { label: string; value: string }[]
}

const filterSections = computed<FilterSection[]>(() => {
  if (isIntern.value) {
    return [
      { key: 'durations', label: '开发周期', options: durationOptions.value },
      { key: 'techFields', label: '技术领域', options: techOptions.value },
      { key: 'programmingLanguages', label: '编程语言', options: languageOptions.value },
    ]
  }
  if (isTa.value) {
    return [
      { key: 'scheduleTypes', label: '学期', options: taScheduleOptions.value },
      { key: 'weeklyHours', label: '每周投入', options: weeklyOptions.value },
      { key: 'educationLevels', label: '在读身份', options: educationOptions.value },
    ]
  }
  if (isTeam.value) {
    return [{ key: 'techFields', label: '岗位', options: teamPositionOptions.value }]
  }
  return [
    { key: 'techFields', label: '研究方向', options: researchOptions.value },
  ]
})

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
    } else if (isTeam.value) {
      if (filters.techFields.length) {
        const directions = (task.positions || []).map((item) => item.direction || item.title)
        const hit = directions.some((dir) => filters.techFields.includes(dir)) || filters.techFields.includes(task.techField)
        if (!hit) return false
      }
    } else {
      if (filters.scheduleTypes.length && !filters.scheduleTypes.includes(task.scheduleType || '')) return false
      if (filters.techFields.length && !filters.techFields.includes(task.techField)) return false
    }
    if (!keyword) return true
    const positionText = (task.positions || []).map((item) => `${item.title} ${item.direction}`).join(' ')
    return `${task.title} ${task.techStack} ${task.programmingLanguages} ${task.publisherName} ${task.techField} ${task.stipendText || ''} ${positionText}`
      .toLowerCase()
      .includes(keyword)
  })
})

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
  } else if (isTeam.value) {
    pushMany('techFields', filters.techFields, teamPositionOptions.value, '岗位')
  } else {
    pushMany('techFields', filters.techFields, researchOptions.value, '研究方向')
  }
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

const selectedFilterCount = computed(
  () => selectedChips.value.filter((chip) => chip.key !== 'keyword').length,
)

/** 无本地筛选时展示服务端总数；有筛选时展示当前已加载中的匹配数 */
const resultCount = computed(() =>
  selectedFilterCount.value > 0 || filters.keyword.trim()
    ? tasks.value.length
    : totalCount.value,
)

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

async function requestTaskPage(nextPage: number) {
  return api<TaskPage>(`/api/v1/tasks?roleType=${activeModule.value}&page=${nextPage}&size=${PAGE_SIZE}`)
}

async function fetchPage(nextPage: number, append: boolean) {
  if (append) {
    if (loadingMore.value || loading.value || !hasMore.value) return
    loadingMore.value = true
  } else {
    loading.value = true
    loadError.value = false
  }
  try {
    let data: TaskPage
    try {
      data = await requestTaskPage(nextPage)
    } catch (firstError) {
      // Cold-start / first proxy hop often fails once; retry before showing empty.
      if (append) throw firstError
      await new Promise((resolve) => setTimeout(resolve, 800))
      data = await requestTaskPage(nextPage)
    }
    sourceTasks.value = append
      ? [...sourceTasks.value, ...data.items.filter((item) => !sourceTasks.value.some((row) => row.id === item.id))]
      : data.items
    page.value = data.page
    hasMore.value = data.hasMore
    totalCount.value = data.total
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
    if (!append) {
      sourceTasks.value = []
      totalCount.value = 0
      hasMore.value = false
      loadError.value = true
    }
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadModuleList() {
  page.value = 0
  hasMore.value = true
  loading.value = true
  loadError.value = false
  await fetchPage(0, false)
  await nextTick()
  observeLoadMore()
}

async function loadMore() {
  if (!isMobile.value || !hasMore.value || loading.value || loadingMore.value) return
  await fetchPage(page.value + 1, true)
}

async function onPcPageChange(current: number) {
  if (isMobile.value) return
  await fetchPage(Math.max(current - 1, 0), false)
  const anchor = document.getElementById('opportunities')
  if (anchor) anchor.scrollIntoView({ behavior: 'smooth', block: 'start' })
  else window.scrollTo({ top: 0, behavior: 'smooth' })
}

function observeLoadMore() {
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
  if (!isMobile.value) return
  const el = loadMoreSentinel.value
  if (!el) return
  loadMoreObserver = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) void loadMore()
    },
    { root: null, rootMargin: '240px 0px', threshold: 0 },
  )
  loadMoreObserver.observe(el)
}

function resetFilters() {
  filters.techFields = []
  filters.programmingLanguages = []
  filters.durations = []
  filters.scheduleTypes = []
  filters.weeklyHours = []
  filters.educationLevels = []
  filters.keyword = ''
}

async function switchModule(value: 'TA' | 'INTERN' | 'RA' | 'TEAM') {
  if (activeModule.value === value) return
  activeModule.value = value
  resetFilters()
  filterDrawerVisible.value = false
  await loadModuleList()
}

function openFilterDrawer() {
  filterDrawerVisible.value = true
}

function applyFilterDrawer() {
  filterDrawerVisible.value = false
}

function resetFiltersInDrawer() {
  resetFilters()
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
    const config = await api<{
      tutorialDocUrlStudent?: string
      tutorialDocUrlPublisher?: string
      /** @deprecated legacy single link; used when role-specific fields are absent */
      tutorialDocUrl?: string
      tutorialBannerText: string
      plazaBlurbTa?: string
      plazaBlurbIntern?: string
      plazaBlurbRa?: string
      plazaBlurbTeam?: string
    }>('/api/v1/site-config')
    const studentUrl = config.tutorialDocUrlStudent || config.tutorialDocUrl || ''
    const publisherUrl = config.tutorialDocUrlPublisher || ''
    tutorialDocUrlStudent.value = studentUrl
    tutorialDocUrlPublisher.value = publisherUrl
    writeCachedSiteConfig({
      tutorialDocUrlStudent: studentUrl,
      tutorialDocUrlPublisher: publisherUrl,
      tutorialBannerText: config.tutorialBannerText?.trim() || '查看使用教程',
    })
    moduleBlurbs.value = {
      TA: config.plazaBlurbTa || DEFAULT_MODULE_BLURBS.TA,
      INTERN: config.plazaBlurbIntern || DEFAULT_MODULE_BLURBS.INTERN,
      RA: config.plazaBlurbRa || DEFAULT_MODULE_BLURBS.RA,
      TEAM: config.plazaBlurbTeam || DEFAULT_MODULE_BLURBS.TEAM,
    }
  } catch {
    /* optional */
  }
}

onMounted(() => {
  // Show spinner immediately; do not wait on dicts/config or first paint looks empty.
  loading.value = true
  void loadModuleList()
  void loadDicts()
  void loadSiteConfig()
})

onUnmounted(() => {
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
})
</script>

<template>
  <section class="plaza" :class="{ 'plaza-mobile': isMobile }">
    <section v-if="!isMobile" class="hero" aria-label="平台介绍">
      <div class="hero-glow" aria-hidden="true" />
      <div class="hero-grid" aria-hidden="true" />
      <div class="hero-inner">
        <p class="hero-eyebrow"><span></span> WHU DEVELOPER · 校园协作社区</p>
        <h1 class="hero-title">把热爱写进项目，<br />让成长<span>真实发生。</span></h1>
        <p class="hero-desc">{{ HERO_DESC }}</p>
        <div class="hero-actions">
          <a href="#opportunities" class="hero-cta primary">探索开放任务 <span aria-hidden="true">↗</span></a>
          <a
            v-if="tutorialDocUrl"
            class="hero-cta secondary"
            :href="tutorialDocUrl"
            target="_blank"
            rel="noopener"
          >
            使用教程
          </a>
        </div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <div class="art-orbit"></div>
        <div class="art-center"><span>WHU</span><small>IDEAS INTO IMPACT</small></div>
        <div class="art-card art-teach"><span class="art-symbol">Aa</span><div><strong>助教</strong><small>SHARE KNOWLEDGE</small></div></div>
        <div class="art-card art-code"><span class="art-symbol">&lt;/&gt;</span><div><strong>开发</strong><small>BUILD SOMETHING</small></div></div>
        <div class="art-card art-research"><span class="art-symbol">✳</span><div><strong>科研</strong><small>EXPLORE THE UNKNOWN</small></div></div>
        <div class="art-card art-project"><span class="art-symbol">◇</span><div><strong>项目</strong><small>JOIN THE WORK</small></div></div>
        <div class="art-caption">四个频道 · 一起创造</div>
      </div>
    </section>
    <header id="opportunities" class="page-head" :class="{ 'page-head-mobile': isMobile }">
      <div v-if="!isMobile">
        <a-typography-title :heading="4" style="margin: 0">任务广场</a-typography-title>
        <p class="subtitle">先选频道；每个频道使用各自适配的筛选条件。</p>
      </div>
      <p v-else class="mobile-count">{{ resultCount }} 个开放职位</p>
      <a-typography-text v-if="!isMobile" type="secondary">本频道 {{ resultCount }} 个项目</a-typography-text>
    </header>

    <nav class="module-tabs" :class="{ compact: isMobile }" aria-label="任务频道">
      <button
        v-for="item in modules"
        :key="item.value"
        type="button"
        class="module-tab"
        :class="{ active: activeModule === item.value }"
        @click="switchModule(item.value)"
      >
        <span class="module-name">{{ isMobile ? item.shortLabel : item.label }}</span>
        <span v-if="!isMobile" class="module-blurb">{{ item.blurb }}</span>
      </button>
    </nav>
    <p v-if="!isMobile" class="module-hint">{{ activeModuleMeta.blurb }}</p>

    <!-- Desktop filters -->
    <section v-if="!isMobile" class="filter-panel">
      <div class="filter-body">
        <div v-for="section in filterSections" :key="section.key" class="filter-row">
          <span class="filter-label">{{ section.label }}</span>
          <div class="chip-group wrap">
            <button
              v-for="item in section.options"
              :key="`${section.key}-${item.value}`"
              type="button"
              class="chip"
              :class="{ active: isSelected(section.key, item.value) }"
              @click="toggleFilter(section.key, item.value)"
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
      </div>
    </section>

    <!-- Mobile filter entry + bottom drawer -->
    <section v-else class="mobile-filter-entry">
      <div class="mobile-filter-bar">
        <button type="button" class="filter-toggle" :class="{ active: selectedFilterCount > 0 }" @click="openFilterDrawer">
          <span>筛选</span>
          <span v-if="selectedFilterCount" class="filter-count">{{ selectedFilterCount }}</span>
        </button>
        <a-input
          v-model="filters.keyword"
          allow-clear
          size="small"
          placeholder="搜索标题 / 发布方"
          class="mobile-filter-search"
        />
      </div>
      <div v-if="selectedFilterCount" class="mobile-selected-row">
        <button
          v-for="chip in selectedChips.filter((item) => item.key !== 'keyword')"
          :key="`${chip.key}-${chip.value}`"
          type="button"
          class="selected-chip"
          @click="removeChip(chip)"
        >
          <span>{{ chip.label }}</span>
          <span class="selected-x">×</span>
        </button>
      </div>

      <a-drawer
        v-model:visible="filterDrawerVisible"
        placement="bottom"
        height="78%"
        :footer="false"
        :header="false"
        unmount-on-close
        class="filter-drawer"
      >
        <div class="filter-drawer-inner">
          <div class="drawer-head">
            <strong>筛选条件</strong>
            <button type="button" class="drawer-close" @click="filterDrawerVisible = false">关闭</button>
          </div>
          <div class="drawer-body">
            <section v-for="section in filterSections" :key="`m-${section.key}`" class="drawer-section">
              <h3>{{ section.label }}</h3>
              <div class="chip-group wrap">
                <button
                  v-for="item in section.options"
                  :key="`m-${section.key}-${item.value}`"
                  type="button"
                  class="chip"
                  :class="{ active: isSelected(section.key, item.value) }"
                  @click="toggleFilter(section.key, item.value)"
                >
                  {{ item.label }}
                </button>
              </div>
            </section>
          </div>
          <div class="drawer-foot">
            <a-button size="large" @click="resetFiltersInDrawer">重置</a-button>
            <a-button type="primary" size="large" long @click="applyFilterDrawer">
              查看结果（{{ resultCount }}）
            </a-button>
          </div>
        </div>
      </a-drawer>
    </section>

    <a-spin :loading="loading" class="list-spin">
      <a-empty v-if="!loading && tasks.length === 0" description="没有匹配的任务，请调整筛选条件" />
      <div v-else-if="tasks.length && isMobile" class="job-cards">
        <article
          v-for="task in tasks"
          :key="task.id"
          class="job-card"
          role="button"
          tabindex="0"
          @click="openTask(task)"
          @keydown.enter="openTask(task)"
        >
          <div class="job-card-top">
            <a-tag size="small" color="arcoblue">{{ TASK_ROLE_LABEL[task.roleType] || task.roleType }}</a-tag>
            <span class="job-code mono">{{ taskCode(task) }}</span>
          </div>
          <h3 class="job-title">{{ task.title }}</h3>
          <p class="job-meta">
            <template v-if="isIntern">{{ task.techField }} · {{ durationLabel(task.durationWeeks) }}</template>
            <template v-else-if="isTa">{{ scheduleLabel(task) }} · {{ task.estimatedHours }}h/周</template>
            <template v-else-if="isTeam">
              {{ (task.positions || []).map((item) => item.title).join(' / ') || task.techField }} · 名额 {{ seatLabel(task) }}
            </template>
            <template v-else>{{ task.techField }} · {{ scheduleLabel(task) }}</template>
          </p>
          <div class="job-foot">
            <button type="button" class="publisher-chip" @click="openPublisherChat(task, $event)">
              {{ task.publisherName }}
            </button>
            <span class="seat">名额 {{ seatLabel(task) }}</span>
          </div>
        </article>
      </div>
      <div v-else-if="tasks.length" class="table-wrap">
        <table class="project-table">
          <thead>
            <tr>
              <th class="col-code">项目编号</th>
              <th class="col-title">项目名称</th>
              <th>发布方</th>
              <th v-if="isIntern">开发周期</th>
              <th v-else-if="isTa">学期</th>
              <th v-else-if="isRa">截止</th>
              <th v-else-if="isTeam">岗位</th>
              <th v-if="isTa">每周投入</th>
              <th v-else-if="isRa">方向</th>
              <th v-else-if="isTeam">周期</th>
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
                    <template v-else-if="isTeam">
                      {{ (task.positions || []).map((item) => `${item.title} ${item.filled}/${item.quota}`).join(' · ') || task.techField }}
                    </template>
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
              <td v-else-if="isTa">{{ scheduleLabel(task) }}</td>
              <td v-else-if="isRa">
                {{ task.bidDeadline ? task.bidDeadline.replace('T', ' ').slice(0, 16) : '长期' }}
              </td>
              <td v-else-if="isTeam">
                {{ (task.positions || []).map((item) => item.title).join(' / ') || task.techField }}
              </td>
              <td v-if="isTa">{{ task.estimatedHours }} 小时/周</td>
              <td v-else-if="isRa">{{ task.techField }}</td>
              <td v-else-if="isTeam">{{ durationLabel(task.durationWeeks) }}</td>
              <td>{{ seatLabel(task) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div
        v-if="isMobile && (tasks.length || hasMore)"
        ref="loadMoreSentinel"
        class="load-more-sentinel"
        aria-hidden="true"
      />
      <div v-if="isMobile && loadingMore" class="load-more-status">加载更多…</div>
      <div v-else-if="isMobile && tasks.length && !hasMore" class="load-more-status muted">已经到底了</div>
      <div v-else-if="loadError && !tasks.length" class="load-more-status">
        <a-button type="text" @click="loadModuleList">重新加载</a-button>
      </div>
      <div v-if="showPcPager" class="pager">
        <a-pagination
          :total="totalCount"
          :current="page + 1"
          :page-size="PAGE_SIZE"
          show-total
          @change="onPcPageChange"
        />
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
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 16px;
  margin: 28px 0 14px;
  scroll-margin-top: 24px;
}
.page-head .subtitle {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}
.module-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
  --hero-ink: #111827;
  --hero-muted: #6b7280;
  --hero-line: #eef2f7;
  --hero-blue: #2563eb;
  --hero-blue-deep: #1d4ed8;
  position: relative;
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  align-items: center;
  overflow: hidden;
  margin-bottom: 14px;
  padding: 34px 42px;
  border: 1px solid var(--hero-line);
  border-radius: 16px;
  background:
    radial-gradient(ellipse at 88% 28%, rgba(37, 99, 235, 0.14), transparent 52%),
    radial-gradient(ellipse at 8% 92%, rgba(79, 70, 229, 0.08), transparent 46%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 48%, #eff6ff 100%);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 8px 24px rgba(15, 23, 42, 0.04);
  animation: hero-in .55s ease-out both;
}
.hero-glow {
  position: absolute;
  width: 330px;
  height: 330px;
  right: -130px;
  top: -180px;
  border: 50px solid rgba(255, 255, 255, 0.55);
  border-radius: 50%;
  pointer-events: none;
}
.hero-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(37, 99, 235, 0.12) 1px, transparent 1px);
  background-size: 19px 19px;
  mask-image: linear-gradient(90deg, transparent 35%, black);
  pointer-events: none;
}
.hero-inner { position: relative; z-index: 1; }
.hero-eyebrow {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 10px;
  letter-spacing: 1.8px;
  margin-bottom: 18px;
}
.hero-eyebrow span {
  width: 6px;
  height: 6px;
  background: var(--hero-blue);
  border-radius: 50%;
}
.hero-title {
  font-family: "Sora", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  color: var(--hero-ink);
  font-size: clamp(27px, 3.2vw, 40px);
  line-height: 1.45;
  font-weight: 700;
  letter-spacing: -0.035em;
  margin: 0;
  animation: hero-rise .55s ease-out both;
}
.hero-title > span { color: var(--hero-blue-deep); }
.hero-desc {
  max-width: 34em;
  margin-top: 15px;
  color: var(--hero-muted);
  font-size: 13px;
  line-height: 1.9;
}
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 23px;
}
.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 11px 18px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  transition: background .2s, box-shadow .2s, border-color .2s, color .2s;
}
.hero-cta.primary {
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  color: #fff;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.22);
}
.hero-cta.primary:hover {
  filter: brightness(1.04);
  color: #fff;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.28);
}
.hero-cta.secondary {
  border: 1px solid #bfdbfe;
  color: var(--hero-blue-deep);
  background: rgba(255, 255, 255, 0.85);
}
.hero-cta.secondary:hover {
  background: #fff;
  border-color: #93c5fd;
  color: #1e40af;
}
.hero-cta:focus-visible {
  outline: 3px solid rgba(37, 99, 235, 0.35);
  outline-offset: 3px;
}
.hero-art {
  position: relative;
  height: 265px;
  margin-left: 24px;
}
.art-orbit {
  position: absolute;
  width: 245px;
  height: 245px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: 50%;
  left: 50%;
  top: 47%;
  transform: translate(-50%, -50%);
}
.art-orbit::after {
  content: '';
  position: absolute;
  inset: 26px;
  border: 1px dashed rgba(79, 70, 229, 0.22);
  border-radius: 50%;
}
.art-center {
  position: absolute;
  top: 99px;
  left: 50%;
  transform: translateX(-50%) rotate(-8deg);
  width: 130px;
  height: 102px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #2563eb, #4f46e5);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 16px;
  box-shadow: 0 14px 24px rgba(37, 99, 235, 0.22);
}
.art-center > span {
  font: 700 28px "Sora", sans-serif;
  letter-spacing: 2px;
}
.art-center small {
  font: 7px ui-monospace, SFMono-Regular, Menlo, monospace;
  letter-spacing: 1px;
  margin-top: 10px;
  color: #dbeafe;
}
.art-card {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 17px;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}
.art-card strong {
  display: block;
  color: #111827;
  font-size: 13px;
  font-weight: 650;
}
.art-card small {
  display: block;
  font: 7px ui-monospace, SFMono-Regular, Menlo, monospace;
  color: #94a3b8;
  margin-top: 5px;
  letter-spacing: .6px;
}
.art-symbol {
  font: 700 18px "Sora", sans-serif;
  color: var(--hero-blue);
}
.art-teach { top: 8px; left: 0; transform: rotate(-7deg); }
.art-code { top: 18px; right: -4px; transform: rotate(6deg); }
.art-research { left: -8px; bottom: 28px; transform: rotate(-4deg); }
.art-project { right: -8px; bottom: 22px; transform: rotate(5deg); }
.art-caption {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  color: #64748b;
  font-size: 10px;
  letter-spacing: 3px;
  white-space: nowrap;
}
@media (max-width: 1000px) { .hero { padding: 30px; grid-template-columns: 1.2fr .8fr; }.hero-art { transform: scale(.85); transform-origin: center; margin-left: 0; } }
@media (max-width: 760px) { .hero { grid-template-columns: 1fr; }.hero-art { display: none; } }
@keyframes hero-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes hero-rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
.page-head-mobile {
  margin: 8px 0 10px;
  align-items: center;
}

.filter-panel {
  margin-bottom: 22px;
  padding: 22px 24px 20px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  display: flex;
  flex-direction: column;
  gap: 0;
}
.filter-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.filter-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 16px 20px;
  align-items: start;
}
.filter-label {
  padding-top: 8px;
  color: #6b7280;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
}
.chip-group {
  display: flex;
  flex-wrap: nowrap;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 2px;
}
.chip-group.wrap {
  flex-wrap: wrap;
  gap: 10px 12px;
  overflow: visible;
}
.chip {
  border: 1px solid #e5eaf0;
  background: #f8fafc;
  color: #334155;
  border-radius: 999px;
  padding: 8px 16px;
  font-size: 13px;
  line-height: 1.3;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}
.chip:hover { border-color: #93c5fd; background: #eff6ff; }
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
  padding-top: 16px;
  border-top: 1px dashed var(--line);
}
.filter-row.tools {
  padding-top: 4px;
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
.load-more-sentinel {
  width: 100%;
  height: 1px;
}
.load-more-status {
  text-align: center;
  padding: 14px 8px 8px;
  font-size: 13px;
  color: #64748b;
}
.load-more-status.muted {
  color: #94a3b8;
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0 4px;
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
  .module-tabs { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-row { grid-template-columns: 1fr; gap: 8px; }
  .filter-label { padding-top: 0; }
}
@media (prefers-reduced-motion: reduce) {
  .hero,
  .hero-title,
  .hero-desc,
  .hero-actions,
  .hero-glow {
    animation: none !important;
  }
}
.module-tabs.compact {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin-bottom: 12px;
}
.module-tabs.compact .module-tab {
  padding: 8px 4px;
  border-radius: 10px;
  text-align: center;
}
.module-tabs.compact .module-name { font-size: 13px; white-space: nowrap; }
.mobile-count { margin: 0; color: #6b7280; font-size: 13px; }
.mobile-filter-entry {
  margin-bottom: 12px;
}
.mobile-filter-bar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 14px;
}
.filter-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 999px;
  padding: 7px 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}
.filter-toggle.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.filter-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}
.filter-toggle:not(.active) .filter-count {
  background: #2563eb;
  color: #fff;
}
.mobile-filter-search { width: 100%; }
.mobile-selected-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  padding: 8px 2px 0;
}
.mobile-selected-row .selected-chip {
  flex: none;
}
.filter-drawer-inner {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 60vh;
}
.drawer-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0 14px;
  border-bottom: 1px solid #eef2f7;
  flex: none;
}
.drawer-head strong {
  font-size: 16px;
}
.drawer-close {
  border: 0;
  background: transparent;
  color: #2563eb;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  padding: 0;
}
.drawer-body {
  flex: 1;
  padding: 4px 0 12px;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.drawer-section {
  padding: 14px 0;
  border-bottom: 1px solid #f1f5f9;
}
.drawer-section:last-child {
  border-bottom: 0;
}
.drawer-section h3 {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}
.drawer-section .chip-group.wrap {
  gap: 8px;
}
.drawer-foot {
  flex: none;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  padding: 12px 0 calc(4px + var(--bottom-inset, 0px));
  background: #fff;
  border-top: 1px solid #eef2f7;
}
:deep(.filter-drawer .arco-drawer-body) {
  padding: 12px 16px 8px;
  height: 100%;
}
.job-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.job-card {
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  cursor: pointer;
}
.job-card:active { background: #f8fafc; }
.job-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.job-code { font-size: 12px; }
.job-title {
  margin: 0 0 8px;
  font-size: 16px;
  line-height: 1.45;
  color: #111827;
}
.job-meta {
  margin: 0 0 12px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}
.job-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}
.publisher-chip {
  border: 0;
  background: #eff6ff;
  color: #2563eb;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}
.seat { font-size: 12px; color: #9ca3af; }
</style>
