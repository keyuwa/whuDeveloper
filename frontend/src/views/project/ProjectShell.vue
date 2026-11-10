<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { api } from '../../api'
import { auth } from '../../auth'
import { useProjectWorkspace } from '../../composables/useProjectWorkspace'

const route = useRoute()
const router = useRouter()
const { project, loading, basePath, isStudent, isPublisher } = useProjectWorkspace()
const leaving = ref(false)
const backTo = computed(() => (auth.user?.role === 'PUBLISHER' ? '/publisher/inbox' : '/me/workbench'))
const backLabel = computed(() => (auth.user?.role === 'PUBLISHER' ? '返回审阅' : '返回工作台'))
const canLeave = computed(
  () => isStudent.value && project.value && ['ACTIVE', 'COMPLETING'].includes(project.value.status),
)
const showApplicationTab = computed(() => isPublisher.value)

const statusLabel: Record<string, string> = {
  ACTIVE: '进行中',
  COMPLETING: '结项待验收',
  COMPLETED: '已结项',
  TERMINATED: '已终止',
}

const tabs = computed(() => {
  const items = [{ key: 'detail', label: '项目详情', path: `${basePath.value}/detail` }]
  if (showApplicationTab.value) {
    items.push({ key: 'application', label: '申请内容', path: `${basePath.value}/application` })
  }
  items.push(
    { key: 'progress', label: '周报', path: `${basePath.value}/progress` },
    { key: 'completion', label: '结项验收', path: `${basePath.value}/completion` },
  )
  return items
})

const activeTab = computed(() => {
  const path = route.path
  if (path.endsWith('/application') || path.endsWith('/ai')) return 'application'
  if (path.endsWith('/progress') || path.endsWith('/milestones') || path.endsWith('/reports')) return 'progress'
  if (path.endsWith('/completion')) return 'completion'
  return 'detail'
})

function onTabChange(key: string | number) {
  const tab = tabs.value.find((item) => item.key === key)
  if (tab) router.push(tab.path)
}

function leaveProject() {
  if (!project.value) return
  Modal.confirm({
    title: '取消协作',
    content: `确定取消「${project.value.taskTitle}」的协作吗？取消后将无法继续提交周报。`,
    okText: '确认取消',
    cancelText: '再想想',
    onOk: async () => {
      leaving.value = true
      try {
        await api(`/api/v1/projects/${project.value!.id}/leave`, { method: 'POST' })
        Message.success('已取消协作')
        await router.push('/me/workbench')
      } catch (cause) {
        Message.error(cause instanceof Error ? cause.message : '取消失败')
        throw cause
      } finally {
        leaving.value = false
      }
    },
  })
}
</script>

<template>
  <section class="project-shell stack">
    <a-spin :loading="loading && !project" style="width: 100%">
      <template v-if="project">
        <router-link :to="backTo" class="back-link">← {{ backLabel }}</router-link>

        <header class="shell-hero">
          <div class="shell-hero-main">
            <a-typography-title :heading="3" style="margin: 0 0 10px">{{ project.taskTitle }}</a-typography-title>
            <p class="shell-meta">
              {{ project.studentName }}
              <span v-if="project.abilityLevel"> · {{ project.abilityLevel }}</span>
              · 发布方 {{ project.publisherName }}
            </p>
          </div>
          <div class="shell-actions">
            <a-tag color="arcoblue" size="large">{{ statusLabel[project.status] || project.status }}</a-tag>
            <a-button v-if="canLeave" status="danger" :loading="leaving" @click="leaveProject">取消协作</a-button>
          </div>
        </header>

        <a-tabs class="workspace-tabs" :active-key="activeTab" type="line" size="large" @change="onTabChange">
          <a-tab-pane v-for="item in tabs" :key="item.key" :title="item.label" />
        </a-tabs>

        <div class="shell-body">
          <router-view />
        </div>
      </template>
      <div v-else-if="!loading" class="empty-state">
        <a-empty description="项目不存在或无权查看" />
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.back-link {
  width: fit-content;
  color: var(--color-text-3);
  font-size: 13px;
}
.back-link:hover {
  color: rgb(var(--primary-6));
}
.shell-hero {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  padding: 24px 26px;
  border-radius: 16px;
  background:
    radial-gradient(ellipse at 0% 0%, rgba(37, 99, 235, 0.08), transparent 50%),
    #fff;
  border: 1px solid #eef2f7;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.shell-kicker {
  font: 11px/1 ui-monospace, SFMono-Regular, Menlo, monospace;
  letter-spacing: 0.12em;
  color: var(--color-text-3);
  margin-bottom: 12px;
}
.shell-meta {
  margin: 0;
  color: var(--color-text-3);
  font-size: 14px;
  line-height: 1.6;
}
.shell-body {
  margin-top: 4px;
}
.shell-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
@media (max-width: 700px) {
  .shell-hero {
    flex-direction: column;
    padding: 18px;
  }
}
</style>

<style scoped>
.shell-hero { background: transparent; border: 0; border-bottom: 1px solid var(--color-border-2); border-radius: 0; padding: 18px 0; align-items: center; }
.shell-kicker { margin-bottom: 8px; font-size: 10px; }
.shell-hero :deep(h3) { font-size: 22px; }
.shell-meta { font-size: 12px; }
@media (max-width: 700px) { .shell-hero { flex-direction: row; align-items: flex-start; }.shell-hero-main { min-width: 0; } }
</style>
