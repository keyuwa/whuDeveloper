<script setup lang="ts">
import { provide, ref, toRef, watch } from 'vue'
import { WORKSPACE_PROJECT_ID, WORKSPACE_UPDATED } from '../composables/useProjectWorkspace'
import ProjectDetailView from '../views/project/ProjectDetailView.vue'
import ProjectApplicationView from '../views/project/ProjectApplicationView.vue'
import ProjectMilestonesView from '../views/project/ProjectMilestonesView.vue'
import ProjectCompletionView from '../views/project/ProjectCompletionView.vue'

const props = defineProps<{ projectId: number; initialTab: string }>()
const emit = defineEmits<{ updated: []; 'tab-change': [value: string] }>()
const tab = ref(props.initialTab)
provide(WORKSPACE_PROJECT_ID, toRef(props, 'projectId'))
provide(WORKSPACE_UPDATED, () => emit('updated'))
watch(() => props.initialTab, value => { tab.value = value })
watch(tab, value => emit('tab-change', value))
</script>

<template>
  <div class="project-panel">
    <a-tabs v-model:active-key="tab">
      <a-tab-pane key="detail" title="项目概况" />
      <a-tab-pane key="application" title="申请内容" />
      <a-tab-pane key="progress" title="周报记录" />
      <a-tab-pane key="completion" title="结项" />
    </a-tabs>
    <ProjectDetailView v-if="tab === 'detail'" compact />
    <ProjectApplicationView v-else-if="tab === 'application'" compact />
    <ProjectMilestonesView v-else-if="tab === 'progress'" compact />
    <ProjectCompletionView v-else compact />
  </div>
</template>

<style scoped>
.project-panel :deep(.arco-tabs-content) { padding:0; }

.project-panel :deep(.drawer-section) { padding: 24px 20px; }
.project-panel :deep(.drawer-section > h3) { display: none; }
.project-panel :deep(.drawer-section .stack) { gap: 20px; }
.project-panel :deep(.drawer-section .field-label) { color: #475569; font-size: 13px; margin-bottom: 8px; }
.project-panel :deep(.drawer-section .arco-textarea-wrapper),
.project-panel :deep(.drawer-section .arco-input-wrapper) { border: 1px solid #e2e8f0; border-radius: 6px; background: #fafbfd; }
.project-panel :deep(.drawer-section .arco-textarea) { padding: 12px 14px; line-height: 1.8; }
.project-panel :deep(.drawer-section .submit-box) { margin: 0; padding: 0 0 24px; border: 0; }
.project-panel :deep(.drawer-section .focus-label) { font-size: 15px; font-weight: 600; color: #172033; letter-spacing: normal; }
.project-panel :deep(.drawer-section .last-plan) { border: 0; border-left: 3px solid #dbe7fc; background: #f6f8fc; border-radius: 0; }
.project-panel :deep(.drawer-section .history-title) { margin: 20px 0 4px; font-size: 15px; color: #172033; }
.project-panel :deep(.drawer-section .report-item) { padding: 24px 0; margin: 0; background: transparent; border-bottom: 1px solid #edf0f5; }
.project-panel :deep(.drawer-section .report-section) { margin: 14px 0; }
.project-panel :deep(.drawer-section .report-head) { margin-bottom: 16px; }
.project-panel :deep(.drawer-section .section-label) { color: #64748b; font-size: 13px; letter-spacing: normal; }
.project-panel :deep(.drawer-section .report-preview) { border: 0; background: transparent; padding: 0; font: inherit; line-height: 1.85; white-space: pre-wrap; overflow-wrap: anywhere; }
.project-panel :deep(.drawer-section .check-tip) { background: #f6f8fc; border: 0; margin-bottom: 20px; }
.project-panel :deep(.drawer-section .drop) { background: #f8fafc; border: 1px dashed #cbd5e1; border-radius: 8px; box-shadow: none; }
.project-panel :deep(.drawer-section .review-note) { padding: 14px 16px; background: #f5f8fd; border: 0; border-left: 3px solid #dbe7fc; }
.project-panel :deep(.drawer-section .proposal-view) { line-height: 1.9; color: #334155; }
.project-panel :deep(.drawer-section .proposal-view h1),
.project-panel :deep(.drawer-section .proposal-view h2),
.project-panel :deep(.drawer-section .proposal-view h3) { font-size: 16px; margin: 24px 0 12px; }
</style>
