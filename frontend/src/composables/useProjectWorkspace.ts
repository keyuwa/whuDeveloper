import { computed, ref, watch, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { auth } from '../auth'
import type { Project } from '../types'
import { routeId } from '../format'

const project = ref<Project | null>(null) as Ref<Project | null>
const loading = ref(false)
const saving = ref(false)
let loadedId: number | null = null
let inflight: Promise<void> | null = null

async function fetchProject(id: number, force = false) {
  if (!force && loadedId === id && project.value?.id === id) return
  if (inflight && loadedId === id) return inflight
  loadedId = id
  loading.value = true
  inflight = (async () => {
    try {
      project.value = await api<Project>(`/api/v1/projects/${id}`)
    } catch (cause) {
      Message.error(cause instanceof Error ? cause.message : '加载失败')
      project.value = null
      loadedId = null
    } finally {
      loading.value = false
      inflight = null
    }
  })()
  return inflight
}

export function useProjectWorkspace() {
  const route = useRoute()
  const id = computed(() => routeId(route.params.id))
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
