<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from './api'
import { auth, logout } from './auth'

const router = useRouter()
const unread = ref(0)
const user = computed(() => auth.user)

onMounted(async () => {
  if (!auth.token) return
  try {
    unread.value = (await api<{ count: number }>('/api/v1/notifications/unread-count')).count
  } catch { /* ignore */ }
})

function onLogout() {
  logout()
  router.push('/auth/login')
}
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <router-link to="/" class="brand">
        <span class="mark">WHU</span>
        <span>TOP Developer<small>WHUDEV</small></span>
      </router-link>
      <nav class="nav">
        <router-link to="/">任务广场</router-link>
        <template v-if="user?.role === 'STUDENT'">
          <router-link to="/me/applications">我的申请</router-link>
          <router-link to="/me/projects">工作台</router-link>
          <router-link to="/messages">私信</router-link>
        </template>
        <template v-else-if="user?.role === 'PUBLISHER'">
          <router-link to="/publisher/tasks">我的任务</router-link>
          <router-link to="/publisher/inbox">审阅</router-link>
          <router-link to="/messages">私信</router-link>
        </template>
        <template v-else-if="user?.role === 'ADMIN'">
          <router-link to="/admin/users">用户</router-link>
          <router-link to="/admin/tasks">任务</router-link>
          <router-link to="/admin/reminders">催办队列</router-link>
          <router-link to="/admin/settings">SLA</router-link>
        </template>
      </nav>
      <div class="who" v-if="user">
        <span class="pill accent">{{ user.role === 'STUDENT' ? user.abilityLevel : user.role === 'PUBLISHER' ? '发布方' : '管理' }}</span>
        <span>{{ user.displayName }}</span>
        <span v-if="unread" class="pill warn">{{ unread }} 条未读</span>
        <button type="button" @click="onLogout">退出</button>
      </div>
      <div class="who" v-else>
        <router-link to="/auth/login">登录</router-link>
        <router-link class="btn primary" to="/auth/register">注册</router-link>
      </div>
    </header>
    <main>
      <router-view />
    </main>
  </div>
</template>
