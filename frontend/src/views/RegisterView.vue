<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { setSession } from '../auth'
import type { User } from '../types'

const router = useRouter()
const loading = ref(false)
const sending = ref(false)
const cooldown = ref(0)
const devCode = ref('')
let cooldownTimer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  role: 'STUDENT' as 'STUDENT' | 'PUBLISHER',
  email: '',
  code: '',
  password: '',
  displayName: '',
  realName: '',
})

const campusHint = computed(() =>
  form.role === 'STUDENT'
    ? '建议使用学校邮箱（如 @stu.edu.cn / @xxx.edu.cn），便于身份可信；也可用普通邮箱注册。'
    : '建议使用学校/单位邮箱（如 @xxx.whu.edu.cn）；也可用普通邮箱注册。',
)

const emailLooksCampus = computed(() => {
  const email = form.email.trim().toLowerCase()
  if (!email.includes('@')) return false
  if (form.role === 'PUBLISHER') return email.endsWith('.whu.edu.cn')
  return email.endsWith('.edu.cn')
})

function startCooldown(seconds = 60) {
  cooldown.value = seconds
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}

async function sendCode() {
  if (!form.email.trim()) {
    Message.warning('请先填写邮箱')
    return
  }
  if (cooldown.value > 0 || sending.value) return
  sending.value = true
  try {
    const result = await api<{ sent: boolean; devCode?: string }>('/api/v1/auth/otp/send', {
      method: 'POST',
      body: JSON.stringify({ email: form.email.trim(), role: form.role }),
    })
    devCode.value = result.devCode ?? ''
    if (result.devCode) form.code = result.devCode
    Message.success(result.devCode ? `验证码已生成：${result.devCode}` : '验证码已发送到邮箱，请查收')
    startCooldown(60)
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
        <a-typography-text type="secondary">邮箱验证注册</a-typography-text>
      </template>
      <a-alert type="info" style="margin-bottom: 16px" :show-icon="true" :title="campusHint" />
      <a-form :model="form" layout="vertical" @submit-success="submit">
        <a-form-item field="role" label="角色">
          <a-radio-group v-model="form.role" type="button">
            <a-radio value="STUDENT">学生</a-radio>
            <a-radio value="PUBLISHER">发布方</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item field="email" label="邮箱" :rules="[{ required: true, message: '请填写邮箱' }]">
          <a-input-search
            v-model="form.email"
            placeholder="name@example.com"
            search-button
            :loading="sending || cooldown > 0"
            @search="sendCode"
          >
            <template #button-default>
              {{ cooldown > 0 ? `${cooldown}s 后重发` : '发送验证码' }}
            </template>
          </a-input-search>
          <template #extra>
            <span v-if="form.email && !emailLooksCampus" class="field-tip">
              当前为普通邮箱，可用；学校邮箱更利于身份识别。
            </span>
            <span v-else-if="emailLooksCampus" class="field-tip ok">已识别为校园邮箱。</span>
          </template>
        </a-form-item>
        <a-alert v-if="devCode" type="warning" style="margin-bottom: 12px">
          开发环境未配置真实发信时，验证码会直接显示：{{ devCode }}
        </a-alert>
        <a-form-item field="code" label="验证码" :rules="[{ required: true, message: '请填写验证码' }]">
          <a-input v-model="form.code" maxlength="6" placeholder="邮箱收到的 6 位验证码" />
          <template #extra>
            <span class="field-tip">点「发送验证码」后查收邮件；有效期见邮件正文（默认数分钟）。</span>
          </template>
        </a-form-item>
        <a-form-item
          field="password"
          label="密码"
          :rules="[
            { required: true, message: '请填写密码' },
            { minLength: 8, message: '至少 8 位' },
          ]"
        >
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

<style scoped>
.field-tip {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
.field-tip.ok {
  color: #059669;
}
</style>
