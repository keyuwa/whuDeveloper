import { computed, inject, ref, watch, type InjectionKey, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { auth } from '../auth'
import type { Project } from '../types'
import { routeId } from '../format'

const project = ref<Project | null>(null) as Ref<Project | null>
const loading = ref(false)
const saving = ref(false)
export const WORKSPACE_PROJECT_ID: InjectionKey<Ref<number>> = Symbol('workspaceProjectId')
export const WORKSPACE_UPDATED: InjectionKey<() => void> = Symbol('workspaceUpdated')
let loadedId: number | null = null
let inflight: Promise<void> | null = null

async function fetchProject(id: number, force = false) {
  if (!force && loadedId === id && project.value?.id === id) return
  if (inflight && loadedId === id) return inflight
  loadedId = id
  if (project.value?.id !== id) project.value = null
  loading.value = true
  inflight = (async () => {
    try {
      const result = await api<Project>(`/api/v1/projects/${id}`)
      if (loadedId === id) project.value = result
    } catch (cause) {
      Message.error(cause instanceof Error ? cause.message : '加载失败')
      if (loadedId === id) {
        project.value = null
        loadedId = null
      }
    } finally {
      if (loadedId === id || loadedId === null) {
        loading.value = false
        inflight = null
      }
    }
  })()
  return inflight
}

export function useProjectWorkspace() {
  const route = useRoute()
  const embeddedId = inject(WORKSPACE_PROJECT_ID, null)
  const onUpdated = inject(WORKSPACE_UPDATED, null)
  const id = computed(() => embeddedId?.value ?? routeId(route.params.id))
  const basePath = computed(() =>
    auth.user?.role === 'PUBLISHER' ? `/publisher/projects/${id.value}` : `/me/projects/${id.value}`,
  )
  const isStudent = computed(() => auth.user?.role === 'STUDENT' && project.value?.studentId === auth.user.id)
  const isPublisher = computed(() => auth.user?.role === 'PUBLISHER' && project.value?.publisherId === auth.user.id)

  async function load(force = false) {
    if (!id.value) return
    await fetchProject(id.value, force)
  }

  async function run(path: string, body: unknown) {
    saving.value = true
    try {
      project.value = await api<Project>(path, { method: 'POST', body: JSON.stringify(body) })
      loadedId = project.value.id
      Message.success('已更新')
      onUpdated?.()
      return project.value
    } catch (cause) {
      Message.error(cause instanceof Error ? cause.message : '操作失败')
      throw cause
    } finally {
      saving.value = false
    }
  }

  watch(
    () => id.value,
    (next, prev) => {
      if (next && next !== prev) {
        if (prev != null) project.value = null
        load()
      } else if (next) {
        load()
      }
    },
    { immediate: true },
  )

  return { project, loading, saving, id, basePath, isStudent, isPublisher, load, run }
}
