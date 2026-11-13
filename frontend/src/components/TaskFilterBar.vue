<script setup lang="ts">
import { TASK_ROLE_TYPES } from '../types'

type Role = typeof TASK_ROLE_TYPES[number]['value']
defineProps<{ modelValue: Role | ''; showAll?: boolean }>()
defineEmits<{ 'update:modelValue': [value: Role | ''] }>()
</script>

<template>
  <section class="task-filter-bar" aria-label="任务筛选">
    <nav class="filter-types" aria-label="任务类型">
      <button v-if="showAll" type="button" :aria-pressed="modelValue === ''"
        @click="$emit('update:modelValue', '')">全部类型</button>
      <button v-for="item in TASK_ROLE_TYPES" :key="item.value" type="button"
        :aria-pressed="modelValue === item.value" @click="$emit('update:modelValue', item.value)">
        {{ item.label }}
      </button>
    </nav>
    <div class="filter-controls"><slot /></div>
  </section>
</template>

<style scoped>
.task-filter-bar { flex: none; margin-bottom: 16px; background: var(--bg-surface); border: 1px solid var(--surface-line); border-radius: 12px; }
.filter-types { display: flex; gap: 8px; margin-inline: calc(0px - var(--filter-outer-inset, 0px)); padding: 0 calc(14px + var(--filter-outer-inset, 0px)); border-bottom: 1px solid var(--surface-line); overflow-x: auto; }
.filter-types button { flex: none; padding: 14px 12px 12px; border: 0; border-bottom: 2px solid transparent; background: transparent; color: var(--text-body); font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.filter-types button { font-weight: 650; }
.filter-types button:hover, .filter-types button[aria-pressed="true"] { color: rgb(var(--primary-6)); }
.filter-types button[aria-pressed="true"] { font-weight: 700; }
.filter-types button[aria-pressed="true"] { border-bottom-color: rgb(var(--primary-6)); }
.filter-types button:focus-visible { outline: 2px solid rgb(var(--primary-6)); outline-offset: -4px; }
.filter-controls { display: flex; align-items: center; gap: 10px; padding: 12px 18px; }
.filter-controls :deep(.arco-input-wrapper) { flex: 0 1 300px; min-width: 150px; }
.filter-controls :deep(.arco-select-view) { flex: 0 1 180px; min-width: 120px; }
.filter-controls :deep(.arco-input-wrapper), .filter-controls :deep(.arco-select-view) { height: 36px; background: var(--bg-surface); border: 1px solid #dbe3ee; border-radius: 8px; }
.filter-controls :deep(.arco-input-wrapper:hover), .filter-controls :deep(.arco-select-view:hover) { border-color: #93c5fd; }
@media (max-width: 767px) {
  .filter-types { gap: 0; padding: 0 calc(6px + var(--filter-outer-inset, 0px)); }
  .filter-types button { padding-inline: 10px; font-size: 13px; min-height: 44px; }
  .filter-controls { flex-wrap: wrap; padding: 12px; gap: 8px; }
  .filter-controls :deep(.arco-input-wrapper) { flex: 1 0 100%; }
  .filter-controls :deep(.arco-select-view) { flex: 1; }
}
</style>
