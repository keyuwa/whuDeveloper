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

const threadSearch = ref('')
const shownThreads = computed(() => {
  const query = threadSearch.value.trim().toLowerCase()
  if (isMobile.value || !query) return threads.value
  return threads.value.filter((item) =>
    [isStudent.value ? item.publisherName : item.studentName, item.taskTitle].some((value) =>
      value?.toLowerCase().includes(query),
    ),
  )
})

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

function peerOf(item: ThreadSummary) {
  return isStudent.value ? item.publisherName : item.studentName
}

function avatarLetter(name: string) {
  const text = (name || '?').trim()
  return text ? text.slice(0, 1) : '?'
}

function formatThreadTime(raw?: string) {
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw.replace('T', ' ').slice(0, 16)
  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (sameDay) {
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  }
  const sameYear = date.getFullYear() === now.getFullYear()
  if (sameYear) {
    return `${date.getMonth() + 1}/${date.getDate()}`
  }
  return `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()}`
}

function formatBubbleTime(raw: string) {
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw.replace('T', ' ').slice(0, 16)
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
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
    content: `确定删除与「${peerOf(item)}」关于「${item.taskTitle}」的会话吗？消息将不可恢复。`,
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
    <a-spin :loading="loading" class="chat-shell-spin">
      <div class="chat-shell" :class="{ 'mobile-mode': isMobile }">
        <aside v-if="showThreadPane" class="thread-pane">
          <div class="thread-pane-head">
            <h1 class="thread-pane-title">消息</h1>
            <a-input
              v-if="!isMobile"
              v-model="threadSearch"
              allow-clear
              size="small"
              class="thread-search"
              aria-label="搜索会话"
              placeholder="搜索联系人或任务"
            />
          </div>
          <div class="thread-list">
            <a-empty v-if="threads.length && !shownThreads.length" description="没有匹配的会话" />
            <a-empty v-if="!loading && threads.length === 0" description="还没有对话，去任务详情开始沟通" />
            <button
              v-for="item in shownThreads"
              :key="threadKey(item)"
              type="button"
              class="thread-item"
              :class="{ active: isActive(item) }"
              @click="selectThread(item)"
            >
              <span class="thread-avatar" aria-hidden="true">{{ avatarLetter(peerOf(item)) }}</span>
              <span class="thread-body">
                <span class="thread-top">
                  <strong>{{ peerOf(item) }}</strong>
                  <span class="thread-time">{{ formatThreadTime(item.createdAt) }}</span>
                </span>
                <span class="thread-task">{{ item.taskTitle }}</span>
                <span class="thread-preview">{{ item.lastBody }}</span>
              </span>
              <span
                class="thread-delete"
                role="button"
                tabindex="0"
                title="删除会话"
                @click="deleteThread(item, $event)"
                @keydown.enter="deleteThread(item, $event)"
              >
                删除
              </span>
            </button>
          </div>
        </aside>

        <div v-if="showChatPane" class="chat-pane">
          <template v-if="activeTaskId">
            <div class="chat-header">
              <div class="chat-header-main">
                <button v-if="isMobile" type="button" class="back-chat" @click="clearActiveChat">←</button>
                <span class="chat-avatar" aria-hidden="true">{{ avatarLetter(peerName || '对') }}</span>
                <div class="chat-header-text">
                  <strong>{{ peerName || '对话' }}</strong>
                  <div class="chat-task">
                    <router-link :to="`/tasks/${activeTaskId}`">
                      {{ activeThread?.taskTitle || `任务 #${activeTaskId}` }}
                    </router-link>
                  </div>
                </div>
              </div>
              <a-button v-if="!isMobile" type="outline" size="small" @click="router.push(`/tasks/${activeTaskId}`)">
                查看任务
              </a-button>
            </div>
            <div class="chat-scroll">
              <div
                v-for="item in messages"
                :key="item.id"
                class="bubble-row"
                :class="{ mine: item.senderId === auth.user?.id }"
              >
                <span
                  v-if="item.senderId !== auth.user?.id"
                  class="bubble-avatar"
                  aria-hidden="true"
                >
                  {{ avatarLetter(item.senderName) }}
                </span>
                <div class="bubble-stack">
                  <div class="bubble">{{ item.body }}</div>
                  <div class="bubble-time">{{ formatBubbleTime(item.createdAt) }}</div>
                </div>
              </div>
              <a-empty v-if="messages.length === 0" description="还没有消息，打个招呼吧" />
            </div>
            <div class="chat-composer">
              <a-textarea
                v-model="draft"
                :auto-size="{ minRows: 3, maxRows: 6 }"
                class="composer-input"
                placeholder="输入消息，Ctrl + Enter 发送"
                @keydown.ctrl.enter="send"
              />
              <div class="composer-bar">
                <span class="composer-hint">围绕当前任务沟通</span>
                <a-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send">
                  发送
                </a-button>
              </div>
            </div>
          </template>
          <div v-else class="chat-empty">
            <div class="chat-empty-card">
              <strong>选择左侧会话开始沟通</strong>
              <p>也可以在任务详情页点击发布方开启对话</p>
            </div>
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
.chat-shell-spin {
  width: 100%;
  display: block;
}
.chat-shell {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  height: min(680px, calc(100vh - 200px));
  min-height: 480px;
  gap: 0;
  background: #fff;
  border: 1px solid #e5eaf1;
  border-radius: 12px;
  overflow: hidden;
}
.chat-shell:not(.mobile-mode) {
  grid-template-columns: 320px minmax(0, 1fr);
  height: calc(100dvh - var(--desktop-header-height, 58px) - 36px);
  min-height: 560px;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}

.thread-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid #e5eaf1;
  background: #fff;
}
.thread-pane-head {
  flex: none;
  padding: 14px 14px 10px;
  border-bottom: 1px solid #eef2f7;
}
.thread-pane-title {
  margin: 0 0 10px;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #111827;
}
.thread-search {
  width: 100%;
}
.thread-search :deep(.arco-input-wrapper) {
  background: #f3f5f8;
  border-color: transparent;
  border-radius: 8px;
}
.thread-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 6px 0;
}
.thread-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  margin: 0;
  padding: 12px 14px;
  border: 0;
  border-left: 3px solid transparent;
  border-radius: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: inherit;
  color: inherit;
}
.thread-item:hover {
  background: #f5f7fa;
}
.thread-item.active {
  background: #eef5ff;
  border-left-color: #2563eb;
}
.thread-avatar,
.chat-avatar,
.bubble-avatar {
  flex: none;
  display: inline-grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(145deg, #60a5fa, #2563eb);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}
.chat-avatar {
  width: 36px;
  height: 36px;
  font-size: 14px;
}
.bubble-avatar {
  width: 32px;
  height: 32px;
  font-size: 13px;
  background: linear-gradient(145deg, #94a3b8, #64748b);
}
.thread-body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.thread-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: baseline;
}
.thread-top strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 650;
  color: #111827;
}
.thread-time {
  flex: none;
  font-size: 11px;
  color: #94a3b8;
}
.thread-task {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: #2563eb;
  font-weight: 500;
}
.thread-preview {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: #64748b;
}
.thread-delete {
  position: absolute;
  right: 10px;
  bottom: 10px;
  border: 0;
  background: transparent;
  color: #94a3b8;
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

.chat-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  background: #f7f8fa;
}
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  border-bottom: 1px solid #e5eaf1;
  background: #fff;
  flex: none;
}
.chat-header-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.chat-header-text {
  min-width: 0;
}
.chat-header strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}
.chat-task {
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.chat-task a {
  color: #64748b;
}
.chat-task a:hover {
  color: #2563eb;
}
.chat-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 20px 12px;
}
.bubble-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  margin-bottom: 14px;
}
.bubble-row.mine {
  justify-content: flex-end;
}
.bubble-stack {
  max-width: min(460px, 72%);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.bubble-row.mine .bubble-stack {
  align-items: flex-end;
}
.bubble {
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e8edf3;
  color: #111827;
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  box-shadow: 0 1px 1px rgba(15, 23, 42, 0.03);
}
.bubble-row.mine .bubble {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
  box-shadow: none;
}
.bubble-time {
  font-size: 11px;
  color: #94a3b8;
  padding-inline: 2px;
}
.chat-composer {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 16px 14px;
  border-top: 1px solid #e5eaf1;
  background: #fff;
}
.composer-input :deep(.arco-textarea-wrapper),
.composer-input:deep(textarea) {
  background: #fff;
  border: 0 !important;
  box-shadow: none !important;
  padding: 0;
}
.composer-input :deep(textarea) {
  min-height: 72px;
  resize: none;
  font-size: 14px;
  line-height: 1.55;
}
.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.composer-hint {
  font-size: 12px;
  color: #94a3b8;
}
.chat-empty {
  flex: 1;
  display: grid;
  place-items: center;
  background:
    radial-gradient(ellipse 50% 40% at 50% 40%, rgba(37, 99, 235, 0.06), transparent 70%),
    #f7f8fa;
}
.chat-empty-card {
  text-align: center;
  color: #64748b;
}
.chat-empty-card strong {
  display: block;
  margin-bottom: 6px;
  font-size: 15px;
  color: #334155;
}
.chat-empty-card p {
  margin: 0;
  font-size: 13px;
}
.back-chat {
  border: 0;
  background: transparent;
  color: #2563eb;
  font-size: 18px;
  font-weight: 650;
  padding: 0 2px;
  cursor: pointer;
  flex: none;
}

.chat-shell.mobile-mode {
  grid-template-columns: 1fr;
  height: auto;
  min-height: calc(100vh - 120px);
  border: 0;
  border-radius: 0;
  background: transparent;
}
.messages-mobile .thread-pane {
  border-right: 0;
  background: transparent;
}
.messages-mobile .thread-pane-head {
  padding: 0 0 10px;
  border-bottom: 0;
}
.messages-mobile .thread-pane-title {
  font-size: 16px;
  margin-bottom: 0;
}
.messages-mobile .thread-list {
  background: #fff;
  border: 1px solid #e5eaf1;
  border-radius: 12px;
}
.messages-mobile .chat-pane {
  min-height: calc(100vh - 110px);
  max-height: calc(100vh - 110px);
  background: #f7f8fa;
  border: 1px solid #e5eaf1;
  border-radius: 12px;
  overflow: hidden;
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
