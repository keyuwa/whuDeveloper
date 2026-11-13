<script setup lang="ts">
import { computed, nextTick, onActivated, onDeactivated, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import TaskDetailView from './TaskDetailView.vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { auth } from '../auth'
import { useIsMobile } from '../composables/useDevice'
import { loadDicts, dictOptions, scheduleLabelMap } from '../composables/useDicts'
import { readCachedSiteConfig, writeCachedSiteConfig } from '../siteConfigCache'
import { TASK_ROLE_TYPES, type Task, type TaskPage } from '../types'

defineOptions({ name: 'PlazaView' })

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
const loadError = ref(false)
const activeModule = ref<'TA' | 'INTERN' | 'RA' | 'TEAM'>('INTERN')
const filterDrawerVisible = ref(false)
const loadMoreSentinel = ref<HTMLElement | null>(null)
const desktopListRef = ref<HTMLElement | null>(null)
const desktopDetailRef = ref<HTMLElement | null>(null)
const chromeCollapsed = ref(false)
const detailAwayFromTop = ref(false)
const COLLAPSE_SCROLL_AT = 120
const EXPAND_SCROLL_AT = 12
let loadMoreObserver: IntersectionObserver | null = null
let windowScrollBound = false
let loadMoreLocked = false

function syncPlazaCompactClass() {
  const on = chromeCollapsed.value && !isMobile.value
  document.documentElement.classList.toggle('plaza-compact', on)
}

function setChromeCollapsed(next: boolean) {
  if (isMobile.value) return
  if (chromeCollapsed.value === next) return
  chromeCollapsed.value = next
  syncPlazaCompactClass()
}

function onWindowScroll() {
  if (isMobile.value) return
  const y = window.scrollY || document.documentElement.scrollTop
  if (!chromeCollapsed.value && y > COLLAPSE_SCROLL_AT) setChromeCollapsed(true)
  else if (chromeCollapsed.value && y <= EXPAND_SCROLL_AT) setChromeCollapsed(false)
}

function onDetailScroll(event: Event) {
  const el = event.currentTarget as HTMLElement | null
  if (!el) return
  detailAwayFromTop.value = el.scrollTop > 16
}

function scrollDetailToTop() {
  const pane = desktopDetailRef.value
  if (!pane) return
  pane.scrollTo({ top: 0, behavior: 'smooth' })
  detailAwayFromTop.value = false
}

watch(isMobile, () => {
  if (isMobile.value) {
    chromeCollapsed.value = false
    detailAwayFromTop.value = false
    syncPlazaCompactClass()
  }
})
const cachedSite = readCachedSiteConfig()
const tutorialDocUrlStudent = ref(cachedSite?.tutorialDocUrlStudent || '')
const tutorialDocUrlPublisher = ref(cachedSite?.tutorialDocUrlPublisher || '')
const tutorialDocUrl = computed(() => {
  if (auth.user?.role === 'PUBLISHER') return tutorialDocUrlPublisher.value
  return tutorialDocUrlStudent.value
})
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

const isIntern = computed(() => activeModule.value === 'INTERN')
const isTa = computed(() => activeModule.value === 'TA')
const isTeam = computed(() => activeModule.value === 'TEAM')
const activeModuleMeta = computed(
  () => modules.value.find((item) => item.value === activeModule.value) || modules.value[1],
)

interface FilterSection {
  key: FilterKey
  label: string
  options: { label: string; value: string }[]
}

const filterSelectTriggerProps = {
  autoFitPopupWidth: false,
  autoFitPopupMinWidth: false,
  contentClass: 'plaza-filter-dropdown',
  contentStyle: {
    minWidth: '280px',
    width: 'max-content',
    maxWidth: '420px',
  },
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
          (task.programmingLanguages || '')
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

const selectedTaskId = ref<number | null>(null)
const selectedTask = computed(() => tasks.value.find(task => task.id === selectedTaskId.value) ?? tasks.value[0])

watch(selectedTaskId, async () => {
  detailAwayFromTop.value = false
  await nextTick()
  desktopDetailRef.value?.scrollTo({ top: 0 })
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
  if (!hasMore.value || loading.value || loadingMore.value || loadMoreLocked) return
  loadMoreLocked = true
  try {
    await fetchPage(page.value + 1, true)
  } finally {
    await nextTick()
    loadMoreLocked = false
    observeLoadMore()
  }
}

function observeLoadMore() {
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
  if (isMobile.value === false && !tasks.value.length && !hasMore.value) return
  const el = loadMoreSentinel.value
  if (!el || !hasMore.value) return
  loadMoreObserver = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return
      if (!hasMore.value || loading.value || loadingMore.value || loadMoreLocked) return
      loadMoreObserver?.disconnect()
      loadMoreObserver = null
      void loadMore()
    },
    { root: null, rootMargin: '160px 0px', threshold: 0 },
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
  setChromeCollapsed(false)
  window.scrollTo({ top: 0 })
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

async function openPublisherChat(task: Task) {
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

function bindPlazaWindowScroll() {
  if (windowScrollBound) return
  window.addEventListener('scroll', onWindowScroll, { passive: true })
  windowScrollBound = true
  onWindowScroll()
}

function unbindPlazaWindowScroll() {
  if (!windowScrollBound) return
  window.removeEventListener('scroll', onWindowScroll)
  windowScrollBound = false
}

onMounted(() => {
  // Show spinner immediately; do not wait on dicts/config or first paint looks empty.
  loading.value = true
  void loadModuleList()
  void loadDicts()
  void loadSiteConfig()
  bindPlazaWindowScroll()
})

onActivated(() => {
  observeLoadMore()
  syncPlazaCompactClass()
  bindPlazaWindowScroll()
})

function disconnectLoadMore() {
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
}

function clearPlazaCompact() {
  chromeCollapsed.value = false
  detailAwayFromTop.value = false
  document.documentElement.classList.remove('plaza-compact')
}

onDeactivated(() => {
  disconnectLoadMore()
  unbindPlazaWindowScroll()
  clearPlazaCompact()
})
onUnmounted(() => {
  disconnectLoadMore()
  unbindPlazaWindowScroll()
  clearPlazaCompact()
})
</script>

<template>
  <section class="plaza" :class="{ 'plaza-mobile': isMobile, 'is-compact': chromeCollapsed && !isMobile }">
    <div v-if="!isMobile" class="browse-board">
      <header id="opportunities" class="browse-head">
        <div>
          <h1>任务广场</h1>
          <a v-if="tutorialDocUrl" :href="tutorialDocUrl" target="_blank" rel="noopener">使用教程</a>
        </div>
      </header>

      <nav class="module-tabs" aria-label="任务频道">
        <button
          v-for="item in modules"
          :key="item.value"
          type="button"
          class="module-tab"
          :class="{ active: activeModule === item.value }"
          :aria-pressed="activeModule === item.value"
          :title="item.blurb"
          @click="switchModule(item.value)"
        >
          <span class="module-name">{{ item.label }}</span>
        </button>
      </nav>
      <p class="channel-note">{{ activeModuleMeta.blurb }}</p>
    </div>

    <section v-if="isMobile" id="opportunities" class="mobile-filter-entry" aria-label="搜索与筛选任务">
      <a-input
        v-model="filters.keyword"
        allow-clear
        size="large"
        aria-label="搜索任务"
        placeholder="搜索任务或发布方"
        class="mobile-filter-search"
      >
        <template #prefix><icon-search aria-hidden="true" /></template>
      </a-input>
      <nav class="module-tabs compact" aria-label="任务频道">
        <button
          v-for="item in modules"
          :key="item.value"
          type="button"
          class="module-tab"
          :class="{ active: activeModule === item.value }"
          :aria-pressed="activeModule === item.value"
          @click="switchModule(item.value)"
        >
          <span class="module-name">{{ item.shortLabel }}</span>
        </button>
      </nav>
      <div class="mobile-filter-bar">
        <p class="mobile-count" aria-live="polite">{{ loadError ? '任务加载失败' : `${resultCount} 个开放任务` }}</p>
        <button type="button" class="filter-toggle" :class="{ active: selectedFilterCount > 0 }"
          :aria-expanded="filterDrawerVisible" aria-haspopup="dialog" @click="openFilterDrawer">
          <icon-filter aria-hidden="true" />
          <span>筛选</span>
          <span v-if="selectedFilterCount" class="filter-count">{{ selectedFilterCount }}</span>
        </button>
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

    <div class="plaza-main">
      <section v-if="!isMobile" class="browse-filters" aria-label="筛选任务">
        <div class="browse-filter-row">
          <span class="browse-filter-label">筛选</span>
          <a-input
            v-model="filters.keyword"
            allow-clear
            size="small"
            class="browse-search"
            aria-label="搜索任务"
            placeholder="搜索任务标题或发布方"
          />
          <a-select
            v-for="section in filterSections"
            :key="`${activeModule}-${section.key}`"
            v-model="filters[section.key]"
            class="browse-select"
            size="small"
            style="width: 148px"
            :data-filter-label="section.label"
            :trigger-props="filterSelectTriggerProps"
            :aria-label="section.label"
            :placeholder="section.label"
            :options="section.options"
            :max-tag-count="0"
            multiple
            allow-clear
          />
          <a-button
            v-if="selectedChips.length"
            class="browse-reset"
            type="text"
            size="small"
            @click="resetFilters"
          >
            清空
          </a-button>
        </div>
        <div v-if="selectedChips.length" class="selected-chips">
          <button
            v-for="chip in selectedChips"
            :key="`${chip.key}-${chip.value}`"
            type="button"
            class="selected-chip"
            :aria-label="`移除筛选：${chip.label}`"
            @click="removeChip(chip)"
          >
            {{ chip.label }} <span aria-hidden="true">×</span>
          </button>
        </div>
      </section>

      <a-spin :loading="loading" class="list-spin">
      <a-empty v-if="!loading && tasks.length === 0"
        :description="loadError ? '任务加载失败' : '没有匹配的任务，请调整筛选条件'" />
      <div v-else-if="tasks.length && isMobile" class="job-cards">
        <article v-for="task in tasks" :key="task.id" class="job-card">
          <router-link :to="`/tasks/${task.id}`" class="job-card-link">
            <div class="job-card-top">
              <h3 class="job-title">{{ task.title }}</h3>
              <span class="seat">名额 {{ task.filled }}/{{ task.quota }}</span>
            </div>
            <p class="job-meta">
              <template v-if="isIntern">
                <span v-if="task.techField">{{ task.techField }}</span>
                <span>{{ durationLabel(task.durationWeeks) }}</span>
              </template>
              <template v-else-if="isTa">
                <span v-if="task.scheduleType">{{ scheduleLabel(task) }}</span>
                <span>每周 {{ task.estimatedHours }} 小时</span>
              </template>
              <template v-else-if="isTeam">
                <span v-for="position in (task.positions || []).slice(0, 3)" :key="position.id">{{ position.title }}</span>
                <span v-if="!task.positions?.length && task.techField">{{ task.techField }}</span>
              </template>
              <template v-else>
                <span v-if="task.techField">{{ task.techField }}</span>
                <span v-if="task.scheduleType">{{ scheduleLabel(task) }}</span>
              </template>
            </p>
          </router-link>
          <div class="job-foot">
            <span class="job-publisher">{{ task.publisherName }}</span>
            <button v-if="auth.user?.role === 'STUDENT'" type="button" class="publisher-chip"
              :aria-label="`与${task.publisherName}沟通`" @click="openPublisherChat(task)">
              沟通
            </button>
          </div>
        </article>
      </div>
      <div
        v-else-if="selectedTask"
        class="desktop-split browse-results"
      >
        <nav
          ref="desktopListRef"
          class="desktop-list"
          aria-label="搜索结果"
        >
          <button v-for="task in tasks" :key="task.id" type="button" class="desktop-list-item"
            :aria-pressed="selectedTask.id === task.id" @click="selectedTaskId = task.id">
            <strong>{{ task.title }}</strong>
            <span class="result-tags">
              <span v-if="task.techField">{{ task.techField }}</span>
              <span>名额 {{ seatLabel(task) }}</span>
            </span>
            <span class="result-publisher">{{ task.publisherName }}</span>
          </button>
          <div
            v-if="!isMobile && (tasks.length || hasMore)"
            ref="loadMoreSentinel"
            class="load-more-sentinel"
            aria-hidden="true"
          />
          <div v-if="!isMobile && loadingMore" class="load-more-status in-list">加载更多…</div>
          <div v-else-if="!isMobile && tasks.length && !hasMore" class="load-more-status in-list muted">已经到底了</div>
        </nav>
        <div :key="selectedTask.id" class="browse-detail-wrap">
          <section
            ref="desktopDetailRef"
            class="desktop-surface browse-detail"
            aria-label="任务详情"
            tabindex="0"
            @scroll.passive="onDetailScroll"
          >
            <TaskDetailView :task-id="selectedTask.id" embedded />
          </section>
          <button
            v-if="detailAwayFromTop"
            type="button"
            class="detail-back-top"
            aria-label="回到详情顶部"
            @click="scrollDetailToTop"
          >
            回到顶部
          </button>
        </div>
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
      </a-spin>
    </div>
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
  border-radius: 14px;
  background: #fff;
  padding: 14px 14px;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
  min-width: 0;
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
.load-more-status.in-list {
  margin-top: auto;
  padding: 18px 12px 20px;
  border-top: 1px solid #edf0f4;
  background: linear-gradient(180deg, #fafbfc 0%, #f3f6fa 100%);
  font-size: 12px;
  letter-spacing: 0.02em;
}

.plaza:not(.plaza-mobile) {
  --plaza-filter-sticky: 52px;
  --plaza-detail-top: calc(var(--desktop-header-height, 58px) + 16px);
  --plaza-detail-height: calc(100dvh - var(--desktop-header-height, 58px) - 32px);
  max-width: 1140px;
  margin-inline: auto;
}
.plaza:not(.plaza-mobile).is-compact {
  --plaza-detail-top: var(--plaza-filter-sticky);
  --plaza-detail-height: calc(100dvh - var(--plaza-filter-sticky) - 16px);
}
.browse-board {
  margin-bottom: 14px;
  border: 1px solid var(--desktop-line, var(--line));
  border-radius: 14px;
  background: #fff;
  overflow: visible;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}
.plaza-main {
  position: relative;
}
.plaza:not(.plaza-mobile) .browse-board {
  margin-bottom: 0;
  border-bottom: 0;
  border-radius: 14px 14px 0 0;
}
.plaza:not(.plaza-mobile) .browse-filters {
  margin: 0 0 14px;
  padding: 10px 22px 14px;
  border: 1px solid var(--desktop-line, var(--line));
  border-top: 1px solid #f1f5f9;
  border-radius: 0 0 14px 14px;
  background: #fbfcfe;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}
.plaza.is-compact .browse-board {
  display: none;
}
.plaza.is-compact .browse-filters {
  position: sticky;
  top: 0;
  z-index: 31;
  margin-bottom: 10px;
  border-radius: 12px;
  border-top: 1px solid var(--desktop-line, var(--line));
  background: #fbfcfe;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.plaza:not(.plaza-mobile) .list-spin {
  position: relative;
  z-index: 1;
}
.browse-head {
  scroll-margin-top: 80px;
  padding: 18px 22px;
  background: var(--heading-surface);
  border-bottom: 1px solid var(--desktop-line, var(--line));
}
.browse-head > div { display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px 14px; min-width: 0; }
.browse-head h1 {
  font-family: "Sora", "Noto Sans SC", sans-serif;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--ink);
}
.browse-head a {
  font-size: 13px;
  font-weight: 500;
  color: var(--accent);
}
.browse-head a:hover { text-decoration: underline; }
.plaza:not(.plaza-mobile) .module-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin: 0;
  padding: 0 10px;
  background: #fff;
  border-bottom: 1px solid var(--desktop-line, var(--line));
}
.plaza:not(.plaza-mobile) .module-tab {
  padding: 13px 14px 11px;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  transform: none;
}
.plaza:not(.plaza-mobile) .module-tab:hover {
  border-color: transparent;
  box-shadow: none;
  background: transparent;
}
.plaza:not(.plaza-mobile) .module-tab:hover .module-name { color: #1d4ed8; }
.plaza:not(.plaza-mobile) .module-tab.active {
  border-bottom-color: var(--accent);
  background: transparent;
  box-shadow: none;
}
.plaza:not(.plaza-mobile) .module-tab .module-name {
  font-size: 14px;
  font-weight: 650;
}
.channel-note {
  margin: 0;
  padding: 10px 22px 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--muted);
}
.browse-filter-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-start;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.browse-filter-label {
  flex: none;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.browse-search {
  flex: none;
  width: 200px;
  max-width: 200px;
}
.browse-search :deep(.arco-input-wrapper),
.browse-filters :deep(.browse-search.arco-input-wrapper) {
  width: 200px;
  background: #fff;
  border-color: #dbe3ee;
  border-radius: 8px;
  box-shadow: none;
}
.browse-filters :deep(.browse-search.arco-input-wrapper:hover),
.browse-filters :deep(.browse-search.arco-input-wrapper.arco-input-focus) {
  border-color: #93c5fd;
}
/* class 落在 arco-select-view 根节点上，不要 flex 拉伸 */
.browse-filters :deep(.browse-select),
.browse-filters :deep(.browse-select.arco-select-view) {
  flex: none !important;
  flex-grow: 0 !important;
  width: 148px !important;
  min-width: 148px !important;
  max-width: 148px !important;
  background: #fff;
  border-color: #dbe3ee;
  border-radius: 8px;
}
.browse-filters :deep(.browse-select .arco-select-view-inner) {
  width: auto !important;
  max-width: 100%;
}
/* 已选值由下方 chip 展示，触发器内不显示 tag / +N */
.browse-filters :deep(.browse-select .arco-select-view-tag),
.browse-filters :deep(.browse-select .arco-tag) {
  display: none !important;
}
.browse-filters :deep(.browse-select.arco-select-view-has-tag)::before {
  content: attr(data-filter-label);
  color: var(--color-text-1, #1d2129);
  font-size: 13px;
  line-height: 22px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.browse-filters :deep(.browse-select.arco-select-view-has-tag .arco-select-view-input),
.browse-filters :deep(.browse-select.arco-select-view-has-tag .arco-select-view-mirror) {
  position: absolute;
  width: 0 !important;
  min-width: 0 !important;
  opacity: 0;
  pointer-events: none;
}
.browse-reset {
  flex: none;
  margin-inline-start: auto;
  color: #64748b;
  padding: 0 4px;
}
.browse-filters .selected-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.browse-filters .selected-chip {
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
}
.browse-results {
  grid-template-columns: minmax(240px, 34%) minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  height: auto;
  min-height: 0;
}
.browse-results .desktop-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  align-items: stretch;
  align-self: start;
  height: auto;
  min-height: var(--plaza-detail-height);
  max-height: none !important;
  padding: 0;
  border: 1px solid var(--desktop-line, var(--line));
  border-radius: 12px;
  background:
    linear-gradient(180deg, #ffffff 0%, #ffffff 72%, #f5f7fb 100%);
  overflow: visible !important;
}
.browse-results .desktop-list-item {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
  flex: 0 0 auto;
  gap: 8px;
  width: 100%;
  height: auto;
  margin: 0;
  padding: 14px 16px;
  border: 0;
  border-bottom: 1px solid #edf0f4;
  border-left: 3px solid transparent;
  border-radius: 0;
  background: #fff;
  color: var(--ink, #111827);
  text-align: left;
  font: inherit;
  line-height: 1.45;
  white-space: normal;
  overflow: visible;
  cursor: pointer;
  box-sizing: border-box;
}
.browse-results .desktop-list-item:last-child { border-bottom: 0; }
.browse-results .desktop-list-item:hover {
  background: #f8fafc;
}
.browse-results .desktop-list-item[aria-pressed="true"] {
  border-left-color: var(--accent);
  background: #eff6ff;
}
.browse-results .desktop-list-item[aria-pressed="true"] strong { color: #1d4ed8; }
.browse-results .desktop-list-item strong {
  display: block;
  flex: 0 0 auto;
  width: 100%;
  min-height: 1.45em;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.45;
  color: inherit;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.browse-results .result-tags {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}
.result-tags > span {
  padding: 2px 7px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}
.browse-results .result-publisher {
  display: block;
  flex: 0 0 auto;
  width: 100%;
  min-height: 1.4em;
  margin: 0;
  padding: 0;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.browse-detail-wrap {
  position: sticky;
  top: var(--plaza-detail-top);
  z-index: 5;
  align-self: start;
  min-width: 0;
  height: var(--plaza-detail-height);
  max-height: var(--plaza-detail-height);
}
.browse-detail {
  height: 100%;
  max-height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scroll-padding-block: 12px;
  border: 1px solid var(--desktop-line, var(--line));
  border-radius: 12px;
  background: #fff;
}
.detail-back-top {
  position: absolute;
  right: 16px;
  bottom: 16px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 12px;
  border: 1px solid #dbe3ee;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.1);
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.detail-back-top:hover {
  border-color: #93c5fd;
  color: #1d4ed8;
}
.browse-results .desktop-list {
  overscroll-behavior: contain;
}
@media (max-width: 900px) {
  .browse-results { grid-template-columns: minmax(200px, 34%) minmax(0, 1fr); }
  .browse-head { grid-template-columns: 1fr; }
}
.mobile-filter-entry {
  margin: -12px -12px 10px;
  padding: 10px 14px 0;
  background: var(--heading-surface);
  border-bottom: 1px solid #e8edf5;
}
.mobile-filter-search {
  width: 100%;
  min-height: 44px;
  border: 1px solid rgb(var(--primary-3));
  border-radius: 24px;
  background: #fff;
}
.mobile-filter-search :deep(.arco-input-prefix) { color: rgb(var(--primary-6)); }
.module-tabs.compact {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin: 8px 0 0;
  border-bottom: 1px solid #edf1f6;
}
.module-tabs.compact .module-tab {
  min-height: 44px;
  padding: 0 4px;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  transform: none;
  text-align: center;
}
.module-tabs.compact .module-tab.active { border-bottom-color: rgb(var(--primary-6)); }
.module-tabs.compact .module-name { font-size: 14px; white-space: nowrap; }
.mobile-count { color: #64748b; font-size: 13px; }
.mobile-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.filter-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  padding: 0 8px;
  border: 0;
  background: transparent;
  color: #334155;
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}
.filter-toggle.active { color: rgb(var(--primary-6)); }
.filter-count {
  min-width: 18px;
  padding: 0 4px;
  border-radius: 10px;
  background: rgb(var(--primary-1));
  line-height: 18px;
  text-align: center;
}
.mobile-selected-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  padding: 0 2px 10px;
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
  border: 1px solid #e8edf5;
  border-radius: 12px;
  overflow: hidden;
}
.job-card-link { display: block; padding: 14px 14px 12px; color: inherit; }
.job-card-link:active { background: #eff6ff; }
.job-card-link:focus-visible { outline: 2px solid rgb(var(--primary-6)); outline-offset: -2px; }
.job-card-top { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; }
.job-title { font-size: 16px; line-height: 1.45; overflow-wrap: anywhere; }
.seat { flex: none; font-size: 12px; font-weight: 650; color: rgb(var(--primary-6)); }
.job-meta { display: flex; flex-wrap: wrap; gap: 6px; }
.job-meta:not(:empty) { margin-top: 8px; }
.job-meta span { padding: 2px 7px; border-radius: 4px; background: #f1f5f9; color: #475569; font-size: 12px; }
.job-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 14px;
  background: #f8fafc;
}
.job-publisher { color: #475569; font-size: 13px; overflow-wrap: anywhere; }
.publisher-chip {
  flex: none;
  min-height: 44px;
  padding: 0 8px;
  border: 0;
  background: transparent;
  color: rgb(var(--primary-6));
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}
</style>

<style>
/* 下拉挂到 body，需非 scoped，保证长选项文案完整显示 */
.plaza-filter-dropdown .arco-select-dropdown,
.plaza-filter-dropdown.arco-select-dropdown {
  min-width: 280px !important;
  width: max-content !important;
  max-width: 420px;
}
.plaza-filter-dropdown .arco-select-option {
  white-space: nowrap;
}
.plaza-filter-dropdown .arco-checkbox-label {
  white-space: nowrap;
  overflow: visible;
}
</style>
