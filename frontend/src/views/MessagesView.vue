<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import { api } from '../api'
import { auth } from '../auth'
import { useIsMobile } from '../composables/useDevice'
import type { ChatMessage, Task, ThreadSummary } from '../types'

const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const threads = ref<ThreadSummary[]>([])
const messages = ref<ChatMessage[]>([])
const draft = ref('')
const loading = ref(false)
const sending = ref(false)
const activeTaskId = ref<number | null>(null)
const activeStudentId = ref<number | null>(null)

const isStudent = computed(() => auth.user?.role === 'STUDENT')
const isPublisher = computed(() => auth.user?.role === 'PUBLISHER')
const showChatPane = computed(() => !isMobile.value || Boolean(activeTaskId.value))
const showThreadPane = computed(() => !isMobile.value || !activeTaskId.value)

const activeThread = computed(() =>
  threads.value.find(
    (item) =>
      item.taskId === activeTaskId.value &&
      (!isPublisher.value || item.studentId === activeStudentId.value),
  ),
)

const peerName = computed(() => {
  if (!activeThread.value) return ''
  return isStudent.value ? activeThread.value.publisherName : activeThread.value.studentName
})

function threadKey(item: ThreadSummary) {
  return `${item.taskId}-${item.studentId}`
}

function isActive(item: ThreadSummary) {
  return item.taskId === activeTaskId.value && (!isPublisher.value || item.studentId === activeStudentId.value)
}

async function loadInbox() {
  threads.value = await api<ThreadSummary[]>('/api/v1/messages')
}

async function loadThread(taskId: number, studentId: number | null) {
  const query = studentId && isPublisher.value ? `?studentId=${studentId}` : ''
  messages.value = await api<ChatMessage[]>(`/api/v1/tasks/${taskId}/messages${query}`)
  await nextTick()
  const pane = document.querySelector('.chat-scroll')
  if (pane) pane.scrollTop = pane.scrollHeight
}

async function selectThread(item: ThreadSummary) {
  activeTaskId.value = item.taskId
  activeStudentId.value = item.studentId
  await router.replace({
    path: '/messages',
    query: isPublisher.value
      ? { taskId: String(item.taskId), studentId: String(item.studentId) }
      : { taskId: String(item.taskId) },
  })
  await loadThread(item.taskId, item.studentId)
}

function deleteThread(item: ThreadSummary, event: Event) {
  event.stopPropagation()
  Modal.confirm({
    title: '删除会话',
    content: `确定删除与「${isStudent.value ? item.publisherName : item.studentName}」关于「${item.taskTitle}」的会话吗？消息将不可恢复。`,
    okText: '删除',
    okButtonProps: { status: 'danger' },
    cancelText: '取消',
    onOk: async () => {
      try {
        const query = isPublisher.value ? `?studentId=${item.studentId}` : ''
        await api(`/api/v1/tasks/${item.taskId}/messages${query}`, { method: 'DELETE' })
        const wasActive = isActive(item)
        threads.value = threads.value.filter((row) => threadKey(row) !== threadKey(item))
        if (wasActive) {
          activeTaskId.value = null
          activeStudentId.value = null
          messages.value = []
          await router.replace({ path: '/messages' })
          if (threads.value.length) await selectThread(threads.value[0])
        }
        Message.success('会话已删除')
      } catch (cause) {
        Message.error(cause instanceof Error ? cause.message : '删除失败')
        throw cause
      }
    },
  })
}

function clearActiveChat() {
  activeTaskId.value = null
  activeStudentId.value = null
  messages.value = []
  void router.replace({ path: '/messages' })
}

async function openFromQuery() {
  const taskRaw = route.query.taskId
  const studentRaw = route.query.studentId
  const taskId = typeof taskRaw === 'string' ? Number(taskRaw) : NaN
  if (!Number.isFinite(taskId)) {
    activeTaskId.value = null
    activeStudentId.value = null
    messages.value = []
    // Desktop: auto-open first thread. Mobile: stay on list.
    if (!isMobile.value && threads.value.length) {
      await selectThread(threads.value[0])
    }
    return
  }
  let studentId =
    typeof studentRaw === 'string' && Number.isFinite(Number(studentRaw))
      ? Number(studentRaw)
      : isStudent.value
        ? auth.user?.id ?? null
        : null

  if (isStudent.value) {
    await api<ChatMessage[]>(`/api/v1/tasks/${taskId}/messages/open`, { method: 'POST' })
    await loadInbox()
    studentId = auth.user?.id ?? null
    const exists = threads.value.some((item) => item.taskId === taskId && item.studentId === studentId)
    if (!exists && auth.user) {
      const task = await api<Task>(`/api/v1/tasks/${taskId}`)
      threads.value.unshift({
        taskId,
        taskTitle: task.title,
        publisherId: task.publisherId,
        publisherName: task.publisherName,
        studentId: auth.user.id,
        studentName: auth.user.displayName,
        lastBody: '暂无消息，发送第一条开始沟通',
        createdAt: new Date().toISOString(),
      })
    }
  }

  activeTaskId.value = taskId
  activeStudentId.value = studentId
  if (!threads.value.find((item) => item.taskId === taskId && (!isPublisher.value || item.studentId === studentId))) {
    if (isPublisher.value && studentId) await loadInbox()
  }
  await loadThread(taskId, studentId)
}

async function send() {
  if (!activeTaskId.value || !draft.value.trim()) return
  sending.value = true
  try {
    await api(`/api/v1/tasks/${activeTaskId.value}/messages`, {
      method: 'POST',
      body: JSON.stringify({
        body: draft.value.trim(),
        studentId: isPublisher.value ? activeStudentId.value : null,
      }),
    })
    draft.value = ''
    await loadInbox()
    await loadThread(activeTaskId.value, activeStudentId.value)
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '发送失败')
  } finally {
    sending.value = false
  }
}

async function bootstrap() {
  loading.value = true
  try {
    await loadInbox()
    await openFromQuery()
  } catch (cause) {
    Message.error(cause instanceof Error ? cause.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void bootstrap()
})

watch(
  () => [route.query.taskId, route.query.studentId],
  async (next, prev) => {
    if (!prev || (next[0] === prev[0] && next[1] === prev[1])) return
    try {
      await loadInbox()
      await openFromQuery()
    } catch (cause) {
      Message.error(cause instanceof Error ? cause.message : '加载失败')
    }
  },
)
</script>

<template>
  <section class="messages-page" :class="{ 'messages-mobile': isMobile }">
    <div v-if="!isMobile" class="page-head messages-head">
      <div>
        <h1>私信</h1>
        <p class="subtitle">围绕任务与对方沟通，会话按任务分开</p>
      </div>
    </div>
    <a-spin :loading="loading" class="chat-shell-spin">
      <div class="chat-shell" :class="{ 'mobile-mode': isMobile }">
        <aside v-if="showThreadPane" class="thread-pane">
          <div class="pane-title">会话</div>
          <a-empty v-if="!loading && threads.length === 0" description="还没有对话，去任务详情点发布方开聊" />
          <div
            v-for="item in threads"
            :key="threadKey(item)"
            class="thread-item"
            :class="{ active: isActive(item) }"
            role="button"
            tabindex="0"
            @click="selectThread(item)"
            @keydown.enter="selectThread(item)"
          >
            <div class="thread-top">
              <strong>{{ isStudent ? item.publisherName : item.studentName }}</strong>
              <div class="thread-actions">
                <span class="thread-time">{{ item.createdAt?.replace('T', ' ').slice(0, 16) }}</span>
                <button type="button" class="thread-delete" title="删除会话" @click="deleteThread(item, $event)">删除</button>
              </div>
            </div>
            <div class="thread-task">{{ item.taskTitle }}</div>
            <div class="thread-preview">{{ item.lastBody }}</div>
          </div>
        </aside>

        <div v-if="showChatPane" class="chat-pane">
          <template v-if="activeTaskId">
            <div class="chat-header">
              <div class="chat-header-main">
                <button v-if="isMobile" type="button" class="back-chat" @click="clearActiveChat">← 返回</button>
                <div class="chat-header-text">
                  <strong>{{ peerName || '对话' }}</strong>
                  <div class="chat-task">
                    <router-link :to="`/tasks/${activeTaskId}`">{{ activeThread?.taskTitle || `任务 #${activeTaskId}` }}</router-link>
                  </div>
                </div>
              </div>
              <a-button size="small" @click="router.push(`/tasks/${activeTaskId}`)">查看任务</a-button>
            </div>
            <div class="chat-scroll">
              <div
                v-for="item in messages"
                :key="item.id"
                class="bubble-row"
                :class="{ mine: item.senderId === auth.user?.id }"
              >
                <div class="bubble">
                  <div class="bubble-meta">{{ item.senderName }} · {{ item.createdAt.replace('T', ' ').slice(0, 16) }}</div>
                  <div class="bubble-body">{{ item.body }}</div>
                </div>
              </div>
              <a-empty v-if="messages.length === 0" description="还没有消息，打个招呼吧" />
            </div>
            <div class="chat-composer">
              <a-textarea
                v-model="draft"
                :auto-size="{ minRows: 2, maxRows: 4 }"
                placeholder="输入消息，Ctrl + Enter 发送"
                @keydown.ctrl.enter="send"
              />
              <a-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send">发送</a-button>
            </div>
          </template>
          <div v-else class="chat-empty">
            <a-empty description="选择左侧会话，或在任务页点击发布方开始沟通" />
          </div>
        </div>
      </div>
    </a-spin>
  </section>
</template>

<style scoped>
.messages-page {
  width: 100%;
}
.messages-head {
  margin-bottom: 16px;
}
.chat-shell-spin {
  width: 100%;
  display: block;
}
.chat-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  height: min(680px, calc(100vh - 200px));
  min-height: 480px;
  gap: 0;
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}
.thread-pane {
  border-right: 1px solid #eef2f7;
  background: #fafbfc;
  overflow-y: auto;
  padding: 12px 10px;
}
.pane-title {
  padding: 4px 10px 12px;
  font-size: 13px;
  font-weight: 650;
  color: #6b7280;
}
.thread-item {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  border-radius: 10px;
  background: transparent;
  padding: 10px 12px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background 0.15s ease;
}
.thread-item:hover {
  background: #f3f4f6;
}
.thread-item.active {
  background: #eff6ff;
}
.thread-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: baseline;
}
.thread-top strong {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.thread-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.thread-time {
  font-size: 11px;
  color: #9ca3af;
  white-space: nowrap;
}
.thread-delete {
  border: 0;
  background: transparent;
  color: #9ca3af;
  font-size: 11px;
  padding: 0;
  cursor: pointer;
  opacity: 0;
}
.thread-item:hover .thread-delete,
.thread-item.active .thread-delete {
  opacity: 1;
}
.thread-delete:hover {
  color: #dc2626;
}
.thread-task {
  margin-top: 4px;
  font-size: 12px;
  color: #2563eb;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.thread-preview {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  background: #fff;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid #eef2f7;
  flex: none;
}
.chat-header-text {
  min-width: 0;
}
.chat-header strong {
  display: block;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-task {
  margin-top: 2px;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-task a {
  color: #2563eb;
  font-weight: 500;
}
.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  background: #f8fafc;
  min-height: 0;
}
.bubble-row {
  display: flex;
  margin-bottom: 10px;
}
.bubble-row.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: min(420px, 78%);
  padding: 9px 12px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #eef2f7;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}
.bubble-row.mine .bubble {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
  box-shadow: none;
}
.bubble-meta {
  font-size: 11px;
  opacity: 0.72;
  margin-bottom: 3px;
}
.bubble-body {
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.chat-composer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: end;
  padding: 12px 18px 14px;
  border-top: 1px solid #eef2f7;
  background: #fff;
  flex: none;
}
.chat-empty {
  flex: 1;
  display: grid;
  place-items: center;
  background: #f8fafc;
}
.chat-header-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}
.back-chat {
  border: 0;
  background: transparent;
  color: #2563eb;
  font-size: 13px;
  font-weight: 650;
  padding: 2px 0;
  cursor: pointer;
  flex: none;
}
.chat-shell.mobile-mode {
  grid-template-columns: 1fr;
  height: auto;
  min-height: calc(100vh - 120px);
  border: 0;
  border-radius: 0;
  box-shadow: none;
  background: transparent;
}
.messages-mobile .thread-pane {
  max-height: none;
  border-right: 0;
  padding: 0;
  background: transparent;
}
.messages-mobile .chat-pane {
  min-height: calc(100vh - 110px);
  max-height: calc(100vh - 110px);
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  overflow: hidden;
}
.messages-mobile .chat-scroll {
  background: #f8fafc;
}
.messages-mobile .thread-delete {
  opacity: 1;
}
@media (max-width: 800px) {
  .chat-shell:not(.mobile-mode) {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 420px;
  }
}
</style>
