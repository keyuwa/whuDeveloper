<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { auth, logout } from './auth'
import { roleLabel } from './labels'

const router = useRouter()
const user = computed(() => auth.user)

function onLogout() {
  logout()
  router.push('/login')
}
</script>

<template>
  <div class="app" :class="{ guest: !user }">
    <header v-if="user" class="topbar">
      <router-link to="/tasks" class="brand">
        <span class="mark">WHU</span>
        <span class="brand-text">
          <strong>TOP Developer</strong>
          <small>第一期演示</small>
        </span>
      </router-link>
      <nav>
        <router-link to="/tasks">任务</router-link>
        <router-link to="/projects">项目</router-link>
      </nav>
      <div class="who">
        <span class="role">{{ roleLabel[user.role] }}</span>
        <span>{{ user.displayName }}</span>
        <button type="button" class="ghost" @click="onLogout">退出</button>
      </div>
    </header>
    <main>
      <router-view />
    </main>
  </div>
</template>
