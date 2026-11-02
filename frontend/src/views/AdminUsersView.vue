<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { User } from '../types'

const users = ref<User[]>([])
const error = ref('')
async function load() { users.value = await api<User[]>('/api/v1/admin/users') }
onMounted(() => load().catch((cause) => { error.value = cause instanceof Error ? cause.message : '加载失败' }))
async function setLevel(user: User, abilityLevel: string) {
  await api(`/api/v1/admin/users/${user.id}`, { method: 'PATCH', body: JSON.stringify({ abilityLevel }) })
  await load()
}
</script>

<template>
  <section>
    <header class="page-head"><div><p class="kicker">Admin</p><h1>用户</h1></div></header>
    <p v-if="error" class="banner">{{ error }}</p>
    <table class="table">
      <thead><tr><th>邮箱</th><th>角色</th><th>名称</th><th>实名</th><th>等级</th></tr></thead>
      <tbody>
        <tr v-for="user in users" :key="user.id">
          <td>{{ user.email }}</td>
          <td>{{ user.role }}</td>
          <td>{{ user.displayName }}</td>
          <td>{{ user.realName || '—' }}</td>
          <td>
            <select v-if="user.role === 'STUDENT'" :value="user.abilityLevel || 'L1'" @change="setLevel(user, ($event.target as HTMLSelectElement).value)">
              <option>L1</option><option>L2</option><option>L3</option><option>L4</option><option>L5</option>
            </select>
            <span v-else>—</span>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
