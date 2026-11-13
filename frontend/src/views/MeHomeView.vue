<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { api } from '../api'
import { auth, logout } from '../auth'

const router = useRouter()
const unread = ref(0)

const user = computed(() => auth.user)

onMounted(async () => {
  if (!auth.token) return
  try {
    unread.value = (await api<{ count: number }>('/api/v1/notifications/unread-count')).count
  } catch {
    unread.value = 0
  }
})

function goLogin() {
  router.push('/auth/login')
}

function onLogout() {
  logout()
  Message.success('已退出')
  router.push('/auth/login')
}
</script>

<template>
  <section class="me-home">
    <header class="me-hero">
      <template v-if="user">
        <div class="avatar">{{ (user.displayName || 'U').slice(0, 1) }}</div>
        <div class="me-meta">
          <h1>{{ user.displayName }}</h1>
          <p v-if="user.school">{{ user.school }}</p>
          <p v-else>学生账号</p>
        </div>
      </template>
      <template v-else>
        <div class="avatar">?</div>
        <div class="me-meta">
          <h1>未登录</h1>
          <p>登录后管理简历与申请进度</p>
        </div>
        <a-button type="primary" size="small" @click="goLogin">登录</a-button>
      </template>
    </header>

    <div class="me-list">
      <button type="button" class="me-row" @click="router.push('/me/resume')">
        <span>我的简历</span>
        <span class="arrow">→</span>
      </button>
      <button type="button" class="me-row" @click="router.push('/messages')">
        <span>消息</span>
        <span class="arrow">→</span>
      </button>
      <button type="button" class="me-row" @click="router.push('/me/workbench')">
        <span>工作台</span>
        <span class="arrow">→</span>
      </button>
    </div>

    <a-button v-if="user" long status="danger" type="outline" style="margin-top: 24px" @click="onLogout">
      退出登录
    </a-button>
    <p v-if="unread" class="hint">你有 {{ unread }} 条未读通知，可点顶部铃铛查看。</p>
  </section>
</template>

<style scoped>
.me-home { max-width: 560px; margin: 0 auto; }
.me-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 4px 20px;
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 22px;
  font-weight: 800;
  flex: none;
}
.me-meta { flex: 1; min-width: 0; }
.me-meta h1 { margin: 0; font-size: 20px; }
.me-meta p { margin: 4px 0 0; color: #6b7280; font-size: 13px; }
.me-list {
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 16px;
  overflow: hidden;
}
.me-row {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px;
  border: 0;
  border-bottom: 1px solid #f1f5f9;
  background: #fff;
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  cursor: pointer;
  text-align: left;
}
.me-row:last-child { border-bottom: 0; }
.arrow { color: #9ca3af; font-weight: 500; }
.hint { margin-top: 16px; font-size: 12px; color: #9ca3af; text-align: center; }
</style>
