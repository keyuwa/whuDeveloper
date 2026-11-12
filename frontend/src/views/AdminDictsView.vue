<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { api } from '../api'
import { loadDicts, type DictItem } from '../composables/useDicts'

interface DictType {
  value: string
  label: string
}

const types = ref<DictType[]>([])
const items = ref<DictItem[]>([])
const loading = ref(false)
const saving = ref(false)
const activeType = ref('STUDENT_IDENTITY')
const editing = ref<DictItem | null>(null)
const form = reactive({
  itemValue: '',
  itemLabel: '',
  sortOrder: 10,
  enabled: true,
})

const typeLabel = computed(() => types.value.find((item) => item.value === activeType.value)?.label || activeType.value)

async function loadTypes() {
  types.value = await api<DictType[]>('/api/v1/dicts/types')
  if (!types.value.find((item) => item.value === activeType.value) && types.value[0]) {
    activeType.value = types.value[0].value
  }
}

async function loadItems() {
  loading.value = true
  try {
    items.value = await api<DictItem[]>(`/api/v1/admin/dicts?type=${activeType.value}`)
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function startCreate() {
  editing.value = null
  form.itemValue = ''
  form.itemLabel = ''
  form.sortOrder = (items.value.at(-1)?.sortOrder || 0) + 10
  form.enabled = true
}

function startEdit(item: DictItem) {
  editing.value = item
  form.itemValue = item.value
  form.itemLabel = item.label
  form.sortOrder = item.sortOrder || 0
  form.enabled = item.enabled !== false
}

async function save() {
  if (!form.itemValue.trim() || !form.itemLabel.trim()) {
    Message.error('请填写字典值与显示名称')
    return
  }
  saving.value = true
  try {
    const body = {
      dictType: activeType.value,
      itemValue: form.itemValue.trim(),
      itemLabel: form.itemLabel.trim(),
      sortOrder: form.sortOrder,
      enabled: form.enabled,
    }
    if (editing.value?.id) {
      await api(`/api/v1/admin/dicts/${editing.value.id}`, { method: 'PUT', body: JSON.stringify(body) })
      Message.success('已更新')
    } else {
      await api('/api/v1/admin/dicts', { method: 'POST', body: JSON.stringify(body) })
      Message.success('已新增')
    }
    startCreate()
    await loadItems()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function remove(item: DictItem) {
  Modal.confirm({
    title: '删除字典项',
    content: `确定删除「${item.label}」吗？已有任务中的历史值不会自动改写。`,
    onOk: async () => {
      try {
        await api(`/api/v1/admin/dicts/${item.id}`, { method: 'DELETE' })
        Message.success('已删除')
        if (editing.value?.id === item.id) startCreate()
        await loadItems()
      } catch (cause) {
        await loadDicts(true)
        Message.error(cause instanceof Error ? cause.message : '删除失败')
      }
    },
  })
}

async function switchType(type: string) {
  activeType.value = type
  startCreate()
  await loadItems()
}

onMounted(async () => {
  try {
    await loadTypes()
    startCreate()
    await loadItems()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  }
})
</script>

<template>
  <section class="dict-page">
    <header class="page-head">
      <div>
        <a-typography-title :heading="4" style="margin: 0">字典配置</a-typography-title>
        <p class="subtitle">管理任务广场筛选项、发布表单选项与简历在读身份等。</p>
      </div>
    </header>

    <div class="dict-layout">
      <aside class="type-pane">
        <button
          v-for="item in types"
          :key="item.value"
          type="button"
          class="type-item"
          :class="{ active: activeType === item.value }"
          @click="switchType(item.value)"
        >
          {{ item.label }}
        </button>
      </aside>

      <div class="main-pane">
        <a-card :title="typeLabel" :bordered="false">
          <a-spin :loading="loading" style="width: 100%">
            <a-table :data="items" :pagination="false" row-key="id" size="small">
              <template #columns>
                <a-table-column title="值" data-index="value" />
                <a-table-column title="显示名" data-index="label" />
                <a-table-column title="排序" data-index="sortOrder" :width="80" />
                <a-table-column title="状态" :width="90">
                  <template #cell="{ record }">
                    <a-tag :color="record.enabled ? 'green' : 'gray'" size="small">
                      {{ record.enabled ? '启用' : '停用' }}
                    </a-tag>
                  </template>
                </a-table-column>
                <a-table-column title="操作" :width="140">
                  <template #cell="{ record }">
                    <a-space>
                      <a-button type="text" size="small" @click="startEdit(record)">编辑</a-button>
                      <a-button type="text" status="danger" size="small" @click="remove(record)">删除</a-button>
                    </a-space>
                  </template>
                </a-table-column>
              </template>
            </a-table>
          </a-spin>
        </a-card>

        <a-card :title="editing ? '编辑字典项' : '新增字典项'" style="margin-top: 16px">
          <a-form :model="form" layout="vertical">
            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="值（写入业务数据）" required>
                  <a-input v-model="form.itemValue" placeholder="如 CURRENT_SEMESTER 或 本科生" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="显示名称" required>
                  <a-input v-model="form.itemLabel" placeholder="界面展示文案" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="排序">
                  <a-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="启用">
                  <a-switch v-model="form.enabled" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-space>
              <a-button type="primary" :loading="saving" @click="save">{{ editing ? '保存修改' : '新增' }}</a-button>
              <a-button v-if="editing" @click="startCreate">取消编辑</a-button>
            </a-space>
          </a-form>
        </a-card>
      </div>
    </div>
  </section>
</template>

<style scoped>
.dict-page { max-width: 1100px; margin: 0 auto; }
.subtitle { margin: 6px 0 0; color: #6b7c90; font-size: 13px; }
.dict-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 16px; margin-top: 16px; }
.type-pane { background: #fff; border: 1px solid #e6ebf1; border-radius: 8px; overflow: hidden; height: fit-content; }
.type-item {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  border-bottom: 1px solid #eef2f6;
  background: transparent;
  padding: 12px 14px;
  cursor: pointer;
  font-size: 13px;
  color: #334155;
}
.type-item:hover { background: #f8fafc; }
.type-item.active { background: #e8f1fb; color: #1d4f91; font-weight: 600; }
@media (max-width: 800px) {
  .dict-layout { grid-template-columns: 1fr; }
}
</style>
