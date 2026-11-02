<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { setSession } from '../auth'
import type { User } from '../types'

const router = useRouter()
const role = ref<'STUDENT' | 'PUBLISHER'>('STUDENT')
const email = ref('')
const code = ref('')
const password = ref('')
const displayName = ref('')
const realName = ref('')
const devCode = ref('')
const error = ref('')
const loading = ref(false)

async function sendCode() {
  error.value = ''
  loading.value = true
  try {
    const result = await api<{ sent: boolean; devCode?: string }>('/api/v1/auth/otp/send', {
      method: 'POST',
      body: JSON.stringify({ email: email.value.trim(), role: role.value }),
    })
    devCode.value = result.devCode ?? ''
    if (result.devCode) code.value = result.devCode
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '发送失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const result = await api<{ token: string; user: User }>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        email: email.value.trim(),
        code: code.value.trim(),
        password: password.value,
        role: role.value,
        displayName: role.value === 'STUDENT' ? displayName.value : realName.value,
        realName: role.value === 'PUBLISHER' ? realName.value : null,
      }),
    })
    setSession(result.token, result.user)
    await router.push('/')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="card login-wrap form" @submit.prevent="submit">
    <div>
      <p class="kicker">Create account</p>
      <h1>注册</h1>
      <p class="muted">学生使用 .edu.cn 邮箱和昵称；发布方使用 .whu.edu.cn 邮箱并实名。</p>
    </div>
    <label>角色
      <select v-model="role">
        <option value="STUDENT">学生</option>
        <option value="PUBLISHER">发布方</option>
      </select>
    </label>
    <label>邮箱<input v-model="email" type="email" required /></label>
    <button class="btn" type="button" :disabled="loading" @click="sendCode">发送验证码</button>
    <p v-if="devCode" class="note">开发环境验证码：{{ devCode }}（未配置 SMTP 时打印在服务日志，并在此显示）</p>
    <label>验证码<input v-model="code" required maxlength="6" /></label>
    <label>密码<input v-model="password" type="password" required minlength="8" /></label>
    <label v-if="role === 'STUDENT'">昵称<input v-model="displayName" required maxlength="64" /></label>
    <label v-else>实名<input v-model="realName" required maxlength="64" /></label>
    <p v-if="error" class="banner">{{ error }}</p>
    <button class="btn primary" type="submit" :disabled="loading">完成注册</button>
  </form>
</template>
