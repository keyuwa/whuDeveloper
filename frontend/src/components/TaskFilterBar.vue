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
.filter-types {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin: 0;
  padding: 0 10px;
  background: #fff;
  border-bottom: 1px solid var(--surface-line);
  overflow-x: auto;
}
.filter-types button {
  flex: none;
  padding: 13px 14px 11px;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  color: #111827;
  font: inherit;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.2;
  cursor: pointer;
}
.filter-types button:hover {
  background: transparent;
  color: #1d4ed8;
}
.filter-types button[aria-pressed="true"] {
  border-bottom-color: #2563eb;
  background: transparent;
  color: #1d4ed8;
  font-weight: 650;
}
.filter-types button:focus-visible { outline: 2px solid #2563eb; outline-offset: -4px; }
.filter-controls { display: flex; align-items: center; gap: 10px; padding: 12px 18px; }
.filter-controls :deep(.arco-input-wrapper) { flex: 0 1 300px; min-width: 150px; }
.filter-controls :deep(.arco-select-view) { flex: 0 1 180px; min-width: 120px; }
.filter-controls :deep(.arco-input-wrapper), .filter-controls :deep(.arco-select-view) { height: 36px; background: var(--bg-surface); border: 1px solid #dbe3ee; border-radius: 8px; }
.filter-controls :deep(.arco-input-wrapper:hover), .filter-controls :deep(.arco-select-view:hover) { border-color: #93c5fd; }
@media (max-width: 767px) {
  .filter-types { padding: 0 6px; }
  .filter-types button { padding: 12px 10px 10px; font-size: 13px; min-height: 44px; }
  .filter-controls { flex-wrap: wrap; padding: 12px; gap: 8px; }
  .filter-controls :deep(.arco-input-wrapper) { flex: 1 0 100%; }
  .filter-controls :deep(.arco-select-view) { flex: 1; }
}
</style>
