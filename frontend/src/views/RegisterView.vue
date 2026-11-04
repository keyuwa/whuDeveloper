<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { setSession } from '../auth'
import type { User } from '../types'

const router = useRouter()
const loading = ref(false)
const sending = ref(false)
const devCode = ref('')
const form = reactive({
  role: 'STUDENT' as 'STUDENT' | 'PUBLISHER',
  email: '',
  code: '',
  password: '',
  displayName: '',
  realName: '',
})

async function sendCode() {
  sending.value = true
  try {
    const result = await api<{ sent: boolean; devCode?: string }>('/api/v1/auth/otp/send', {
      method: 'POST',
      body: JSON.stringify({ email: form.email.trim(), role: form.role }),
    })
    devCode.value = result.devCode ?? ''
    if (result.devCode) form.code = result.devCode
    Message.success(result.devCode ? `验证码已生成：${result.devCode}` : '验证码已发送')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '发送失败')
  } finally {
    sending.value = false
  }
}

async function submit() {
  loading.value = true
  try {
    const result = await api<{ token: string; user: User }>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        email: form.email.trim(),
        code: form.code.trim(),
        password: form.password,
        role: form.role,
        displayName: form.role === 'STUDENT' ? form.displayName : form.realName,
        realName: form.role === 'PUBLISHER' ? form.realName : null,
      }),
    })
    setSession(result.token, result.user)
    Message.success('注册成功')
    await router.push('/')
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <a-card class="auth-card" title="注册">
      <template #extra>
        <a-typography-text type="secondary">教务邮箱验证</a-typography-text>
      </template>
      <a-alert
        type="info"
        style="margin-bottom: 16px"
        :show-icon="true"
      >
        学生使用 .edu.cn 邮箱和昵称；发布方使用 .whu.edu.cn 邮箱并实名。
      </a-alert>
      <a-form :model="form" layout="vertical" @submit-success="submit">
        <a-form-item field="role" label="角色">
          <a-radio-group v-model="form.role" type="button">
            <a-radio value="STUDENT">学生</a-radio>
            <a-radio value="PUBLISHER">发布方</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item field="email" label="邮箱" :rules="[{ required: true, message: '请填写邮箱' }]">
          <a-input-search v-model="form.email" placeholder="邮箱" search-button :loading="sending" @search="sendCode">
            <template #button-default>发送验证码</template>
          </a-input-search>
        </a-form-item>
        <a-alert v-if="devCode" type="warning" style="margin-bottom: 12px">
          开发环境验证码：{{ devCode }}
        </a-alert>
        <a-form-item field="code" label="验证码" :rules="[{ required: true, message: '请填写验证码' }]">
          <a-input v-model="form.code" maxlength="6" placeholder="6 位验证码" />
        </a-form-item>
        <a-form-item field="password" label="密码" :rules="[{ required: true, message: '请填写密码' }, { minLength: 8, message: '至少 8 位' }]">
          <a-input-password v-model="form.password" placeholder="至少 8 位" />
        </a-form-item>
        <a-form-item
          v-if="form.role === 'STUDENT'"
          field="displayName"
          label="昵称"
          :rules="[{ required: true, message: '请填写昵称' }]"
        >
          <a-input v-model="form.displayName" maxlength="64" />
        </a-form-item>
        <a-form-item
          v-else
          field="realName"
          label="实名"
          :rules="[{ required: true, message: '请填写实名' }]"
        >
          <a-input v-model="form.realName" maxlength="64" />
        </a-form-item>
        <a-space direction="vertical" fill>
          <a-button type="primary" html-type="submit" long :loading="loading">完成注册</a-button>
          <a-typography-text type="secondary">
            已有账号？
            <router-link to="/auth/login">登录</router-link>
          </a-typography-text>
        </a-space>
      </a-form>
    </a-card>
  </div>
</template>
