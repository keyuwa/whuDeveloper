<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { api } from '../../api'
import { useProjectWorkspace } from '../../composables/useProjectWorkspace'
import type { Task } from '../../types'

const { project, id } = useProjectWorkspace()
const task = ref<Task | null>(null)
const loading = ref(false)

const difficultyLabel: Record<string, string> = {
  L1: '入门',
  L2: '普通',
  L3: '进阶',
  L4: '困难',
  L5: '专家',
}

const techFieldTags = computed(() =>
  (task.value?.techStack || '')
    .split(/[,，、/|]+/)
    .map((item) => item.trim())
    .filter(Boolean),
)

const programmingTags = computed(() =>
  (task.value?.programmingLanguages || '')
    .split(/[,，、/|]+/)
    .map((item) => item.trim())
    .filter(Boolean),
)

async function loadTask() {
  if (!project.value?.taskId) return
  loading.value = true
  try {
    task.value = await api<Task>(`/api/v1/tasks/${project.value.taskId}`)
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载项目详情失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => project.value?.taskId,
  () => {
    loadTask()
  },
  { immediate: true },
)

watch(
  () => id.value,
  () => {
    task.value = null
  },
)
</script>

<template>
  <a-spin :loading="loading" style="width: 100%">
    <template v-if="task">
      <a-alert class="soft-alert" type="info" :show-icon="true" style="margin-bottom: 16px">
        发布方项目原文。周报与结项请使用上方页签。
      </a-alert>

      <div class="detail-grid">
        <div class="main-col stack">
          <a-card class="panel-card" title="项目介绍" :bordered="false">
            <a-typography-paragraph style="margin-bottom: 0">{{ task.background }}</a-typography-paragraph>
            <template v-if="task.prdFileUrl || task.prdMd">
              <a-divider style="margin: 18px 0" />
              <div v-if="task.prdFileUrl" class="doc-file">
                <strong>需求文档</strong>
                <a :href="task.prdFileUrl" target="_blank" rel="noopener">{{ task.prdFileName || '打开 PDF' }}</a>
              </div>
              <div v-if="task.prdMd" class="project-document">
                <template v-for="(line, index) in task.prdMd.replace(/\\n/g, '\n').split('\n')" :key="index">
                  <h3 v-if="/^#{1,6}\s/.test(line)">{{ line.replace(/^#{1,6}\s+/, '') }}</h3>
                  <p v-else-if="line.trim()">{{ line }}</p>
                </template>
              </div>
            </template>
          </a-card>

          <a-card class="panel-card" title="项目产出要求" :bordered="false">
            <pre class="md">{{ task.outputRequirement }}</pre>
          </a-card>

          <a-card class="panel-card" title="能力要求" :bordered="false">
            <pre class="md">{{ task.techRequirement }}</pre>
          </a-card>

          <a-card v-if="task.stipendText" class="panel-card" title="补助水平" :bordered="false">
            <pre class="md">{{ task.stipendText }}</pre>
          </a-card>

          <a-card v-if="task.learningMaterials" class="panel-card" title="学习资料" :bordered="false">
            <pre class="md">{{ task.learningMaterials }}</pre>
          </a-card>

          <a-card v-if="task.techDocFileUrl || task.techDocMd" class="panel-card" title="技术文档" :bordered="false">
            <div v-if="task.techDocFileUrl" class="doc-file">
              <a :href="task.techDocFileUrl" target="_blank" rel="noopener">{{ task.techDocFileName || '打开技术文档 PDF' }}</a>
            </div>
            <pre v-if="task.techDocMd" class="md">{{ task.techDocMd }}</pre>
          </a-card>
        </div>

        <aside class="side-col stack">
          <a-card class="meta-card" title="项目信息">
            <div class="meta-list">
              <div class="meta-row">
                <span class="meta-label">发布方</span>
                <span>{{ task.publisherName }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">{{ task.roleType === 'RA' ? '研究方向' : '技术领域' }}</span>
                <div class="tag-wrap">
                  <a-tag color="arcoblue" size="small">{{ task.techField }}</a-tag>
                  <template v-if="task.roleType === 'INTERN'">
                    <a-tag v-for="tag in techFieldTags" :key="tag" size="small">{{ tag }}</a-tag>
                  </template>
                </div>
              </div>
              <div v-if="task.roleType === 'INTERN'" class="meta-row">
                <span class="meta-label">编程语言</span>
                <div class="tag-wrap">
                  <a-tag v-for="tag in programmingTags" :key="tag" color="arcoblue" size="small">{{ tag }}</a-tag>
                </div>
              </div>
              <div v-if="task.roleType === 'INTERN'" class="meta-row">
                <span class="meta-label">项目难度</span>
                <span>{{ difficultyLabel[task.difficulty] || task.difficulty }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">{{ task.roleType === 'INTERN' ? '开发周期' : '周期' }}</span>
                <span>{{ task.durationWeeks }} 周</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">预估工时</span>
                <span>每周约 {{ task.estimatedHours }} 小时</span>
              </div>
              <div v-if="task.roleType === 'INTERN'" class="meta-row">
                <span class="meta-label">项目仓库</span>
                <a v-if="task.repoUrl" :href="task.repoUrl" target="_blank" rel="noopener" class="repo-link">{{ task.repoUrl }}</a>
                <span v-else class="muted">未提供</span>
              </div>
            </div>
          </a-card>
        </aside>
      </div>
    </template>
    <div v-else-if="!loading" class="empty-state">
      <a-empty description="暂无项目详情" />
    </div>
  </a-spin>
</template>

<style scoped>
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
  align-items: start;
}
.panel-card {
  background: var(--color-bg-2);
  border-radius: 10px;
}
.meta-card {
  border-radius: 10px;
  position: sticky;
  top: 16px;
}
.meta-card :deep(.arco-card-header) {
  background: var(--color-fill-1);
}
.meta-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.meta-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  font-size: 14px;
  line-height: 1.7;
}
.meta-label {
  color: var(--color-text-3);
}
.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.repo-link {
  overflow-wrap: anywhere;
}
.doc-file {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
}
.project-document h3 {
  font-size: 16px;
  margin: 22px 0 8px;
  color: var(--color-text-1);
}
.project-document h3:first-child {
  margin-top: 0;
}
.project-document p {
  margin-bottom: 10px;
  line-height: 1.9;
  color: var(--color-text-2);
}
@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
  .meta-card {
    position: static;
  }
}
</style>
