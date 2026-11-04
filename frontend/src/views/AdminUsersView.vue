<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import type { User } from '../types'

const users = ref<User[]>([])
const loading = ref(false)
const levels = ['LV0', 'LV1', 'LV2', 'LV3', 'LV4', 'LV5']

async function load() {
  users.value = await api<User[]>('/api/v1/admin/users')
}

onMounted(async () => {
  loading.value = true
  try {
    await load()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loading.value = false
  }
})

async function setLevel(user: User, abilityLevel: string) {
  try {
    await api(`/api/v1/admin/users/${user.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        abilityLevel,
        apprentice: Boolean(user.abilityApprentice),
        mgmt: Boolean(user.abilityMgmt),
      }),
    })
    await load()
    Message.success('已更新等级')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '更新失败')
  }
}

async function toggleFlag(user: User, key: 'abilityApprentice' | 'abilityMgmt', value: boolean) {
  try {
    await api(`/api/v1/admin/users/${user.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        abilityLevel: user.abilityLevel || 'LV0',
        apprentice: key === 'abilityApprentice' ? value : Boolean(user.abilityApprentice),
        mgmt: key === 'abilityMgmt' ? value : Boolean(user.abilityMgmt),
      }),
    })
    await load()
    Message.success('已更新标注')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '更新失败')
  }
}
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <a-typography-title :heading="4" style="margin: 0">用户</a-typography-title>
        <p class="subtitle">管理学生 LV0–LV5、见习与 M；积分可在 TOP 评级页查看。</p>
      </div>
    </header>
    <a-spin :loading="loading" style="width: 100%">
      <a-table :data="users" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column title="邮箱" data-index="email" />
          <a-table-column title="角色" data-index="role" />
          <a-table-column title="昵称" data-index="displayName" />
          <a-table-column title="实名">
            <template #cell="{ record }">{{ record.realName || '—' }}</template>
          </a-table-column>
          <a-table-column title="积分" data-index="points" :width="80" />
          <a-table-column title="标签">
            <template #cell="{ record }">{{ record.abilityLabel || record.abilityLevel || '—' }}</template>
          </a-table-column>
          <a-table-column title="等级" :width="100">
            <template #cell="{ record }">
              <a-select
                v-if="record.role === 'STUDENT'"
                :model-value="record.abilityLevel || 'LV0'"
                size="small"
                style="width: 88px"
                @change="(value) => setLevel(record, String(value))"
              >
                <a-option v-for="level in levels" :key="level" :value="level">{{ level }}</a-option>
              </a-select>
              <span v-else>—</span>
            </template>
          </a-table-column>
          <a-table-column title="见习" :width="70">
            <template #cell="{ record }">
              <a-switch
                v-if="record.role === 'STUDENT'"
                :model-value="Boolean(record.abilityApprentice)"
                size="small"
                @change="(value) => toggleFlag(record, 'abilityApprentice', Boolean(value))"
              />
              <span v-else>—</span>
            </template>
          </a-table-column>
          <a-table-column title="M" :width="70">
            <template #cell="{ record }">
              <a-switch
                v-if="record.role === 'STUDENT'"
                :model-value="Boolean(record.abilityMgmt)"
                size="small"
                @change="(value) => toggleFlag(record, 'abilityMgmt', Boolean(value))"
              />
              <span v-else>—</span>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-spin>
  </section>
</template>
