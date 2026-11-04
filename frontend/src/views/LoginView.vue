<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { setSession } from '../auth'
import type { User } from '../types'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  email: 'wang.student@stu.edu.cn',
  password: 'student123',
})

async function submit() {
  loading.value = true
  try {
    const result = await api<{ token: string; user: User }>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: form.email.trim(), password: form.password }),
    })
    setSession(result.token, result.user)
    Message.success('登录成功')
    await router.push('/')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '登录失败')
  } finally {
    loading.value = false
  }
}

function fill(account: 'student' | 'publisher' | 'admin') {
  if (account === 'student') {
    form.email = 'wang.student@stu.edu.cn'
    form.password = 'student123'
  } else if (account === 'publisher') {
    form.email = 'li.teacher@cs.whu.edu.cn'
    form.password = 'publisher123'
  } else {
    form.email = 'admin@whu.edu.cn'
    form.password = 'admin123456'
  }
}
</script>

<template>
  <div class="auth-page">
    <a-card class="auth-card" :bordered="true" title="登录">
      <template #extra>
        <a-typography-text type="secondary">开源项目协作平台</a-typography-text>
      </template>
      <a-form :model="form" layout="vertical" @submit-success="submit">
        <a-form-item field="email" label="邮箱" :rules="[{ required: true, message: '请填写邮箱' }]">
          <a-input v-model="form.email" allow-clear placeholder="name@stu.edu.cn" />
        </a-form-item>
        <a-form-item field="password" label="密码" :rules="[{ required: true, message: '请填写密码' }]">
          <a-input-password v-model="form.password" allow-clear placeholder="密码" />
        </a-form-item>
        <a-space direction="vertical" fill :size="12">
          <a-button type="primary" html-type="submit" long :loading="loading">进入平台</a-button>
          <a-space wrap>
            <a-button size="small" @click="fill('student')">学生演示</a-button>
            <a-button size="small" @click="fill('publisher')">发布方演示</a-button>
            <a-button size="small" @click="fill('admin')">管理演示</a-button>
          </a-space>
          <a-typography-text type="secondary">
            没有账号？
            <router-link to="/auth/register">注册</router-link>
          </a-typography-text>
        </a-space>
      </a-form>
    </a-card>
  </div>
</template>
