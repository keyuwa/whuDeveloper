<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { setSession } from '../auth'
import type { User } from '../types'

const router = useRouter()
const email = ref('wang.student@stu.edu.cn')
const password = ref('student123')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const result = await api<{ token: string; user: User }>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: email.value.trim(), password: password.value }),
    })
    setSession(result.token, result.user)
    await router.push('/')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="card login-wrap form" @submit.prevent="submit">
    <div>
      <p class="kicker">Sign in</p>
      <h1>登录</h1>
    </div>
    <label>邮箱<input v-model="email" type="email" required autocomplete="username" /></label>
    <label>密码<input v-model="password" type="password" required autocomplete="current-password" /></label>
    <p v-if="error" class="banner">{{ error }}</p>
    <button class="btn primary" type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
    <p class="muted">没有账号？<router-link to="/auth/register">注册</router-link></p>
    <div class="note">
      种子账号：学生 wang.student@stu.edu.cn / student123，发布方 li.teacher@cs.whu.edu.cn / publisher123，管理员 admin@whu.edu.cn / admin123456
    </div>
  </form>
</template>
