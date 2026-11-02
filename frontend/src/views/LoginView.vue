<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { setSession } from '../auth'
import PathSteps from '../components/PathSteps.vue'
import type { User } from '../types'

const router = useRouter()
const username = ref('student')
const password = ref('student123')
const error = ref('')
const loading = ref(false)

const accounts = [
  { label: '学生 · 王同学', username: 'student', password: 'student123', hint: '申请、入学、周报、结项' },
  { label: '发布者 · 李老师', username: 'publisher', password: 'publisher123', hint: '查看申请并录取' },
]

async function submit(account?: { username: string; password: string }) {
  if (account) {
    username.value = account.username
    password.value = account.password
  }
  error.value = ''
  loading.value = true
  try {
    const result = await api<{ token: string; user: User }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: username.value.trim(), password: password.value }),
    })
    setSession(result.token, result.user)
    await router.push('/tasks')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="login">
    <div class="login-intro">
      <p class="eyebrow">武汉大学 · 开发者培养</p>
      <h1>WHU TOP Developer</h1>
      <p class="lede">从一条任务开始：申请、录取、入学，再交周报，并用 Pull Request 结项。</p>
      <PathSteps :current="1" />
      <p class="fine">本演示不包含教务校验、邮箱验证码、面试和文件上传。</p>
    </div>
    <form class="card login-card" @submit.prevent="submit()">
      <h2>登录</h2>
      <p class="muted">演示账号已预填，也可以一键进入。</p>
      <label>
        用户名
        <input v-model="username" autocomplete="username" required />
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="error" class="banner">{{ error }}</p>
      <button class="btn" type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
      <div class="account-list">
        <button
          v-for="account in accounts"
          :key="account.username"
          type="button"
          class="account"
          :disabled="loading"
          @click="submit(account)"
        >
          <strong>{{ account.label }}</strong>
          <span>{{ account.username }} / {{ account.password }}</span>
          <small>{{ account.hint }}</small>
        </button>
      </div>
    </form>
  </section>
</template>
