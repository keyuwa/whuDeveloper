import { computed, ref } from 'vue'
import { api } from '../api'
import {
  DIFFICULTIES,
  DURATION_OPTIONS,
  EDUCATION_LEVELS,
  PROGRAMMING_LANGUAGES,
  RA_SCHEDULE_OPTIONS,
  SUPPORT_LANGUAGES,
  TA_SCHEDULE_OPTIONS,
  RESEARCH_DIRECTIONS,
  TECH_FIELDS,
  WEEKLY_HOURS_OPTIONS,
} from '../types'

export interface DictItem {
  id?: number
  dictType: string
  dictTypeLabel?: string
  value: string
  label: string
  sortOrder?: number
  enabled?: boolean
}

type DictCatalog = Record<string, DictItem[]>

const catalog = ref<DictCatalog>({})
const loaded = ref(false)
const loading = ref(false)

function fallback(type: string): DictItem[] {
  switch (type) {
    case 'STUDENT_IDENTITY':
      return EDUCATION_LEVELS.map((value) => ({ dictType: type, value, label: value }))
    case 'TA_SCHEDULE':
      return TA_SCHEDULE_OPTIONS.filter((item) => item.value).map((item) => ({
        dictType: type,
        value: item.value,
        label: item.label,
      }))
    case 'RA_SCHEDULE':
      return RA_SCHEDULE_OPTIONS.filter((item) => item.value).map((item) => ({
        dictType: type,
        value: item.value,
        label: item.label,
      }))
    case 'TECH_FIELD':
      return TECH_FIELDS.map((value) => ({ dictType: type, value, label: value }))
    case 'RESEARCH_DIRECTION':
      return RESEARCH_DIRECTIONS.map((value) => ({ dictType: type, value, label: value }))
    case 'SUPPORT_LANGUAGE':
      return SUPPORT_LANGUAGES.map((value) => ({ dictType: type, value, label: value }))
    case 'PROGRAMMING_LANGUAGE':
      return PROGRAMMING_LANGUAGES.map((value) => ({ dictType: type, value, label: value }))
    case 'DIFFICULTY':
      return DIFFICULTIES.map((value) => ({ dictType: type, value, label: value }))
    case 'DURATION_RANGE':
      return DURATION_OPTIONS.filter((item) => item.value).map((item) => ({
        dictType: type,
        value: item.value,
        label: item.label,
      }))
    case 'WEEKLY_HOURS_RANGE':
      return WEEKLY_HOURS_OPTIONS.filter((item) => item.value).map((item) => ({
        dictType: type,
        value: item.value,
        label: item.label,
      }))
    default:
      return []
  }
}

export async function loadDicts(force = false) {
  if (loaded.value && !force) return catalog.value
  if (loading.value) return catalog.value
  loading.value = true
  try {
    catalog.value = await api<DictCatalog>('/api/v1/dicts')
    loaded.value = true
  } catch {
    catalog.value = {}
  } finally {
    loading.value = false
  }
  return catalog.value
}

export function dictItems(type: string) {
  return computed(() => {
    const list = catalog.value[type]
    if (list && list.length) return list
    return fallback(type)
  })
}

export function dictOptions(type: string) {
  return computed(() => dictItems(type).value.map((item) => ({ label: item.label, value: item.value })))
}

export function scheduleLabelMap() {
  return computed(() => {
    const map: Record<string, string> = {}
    for (const item of [...dictItems('TA_SCHEDULE').value, ...dictItems('RA_SCHEDULE').value]) {
      map[item.value] = item.label
    }
    return map
  })
}

export function useDicts() {
  return {
    catalog,
    loaded,
    loading,
    loadDicts,
    dictItems,
    dictOptions,
    scheduleLabelMap,
  }
}
