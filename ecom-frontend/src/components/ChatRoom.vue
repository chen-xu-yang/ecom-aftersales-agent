<template>
  <div class="chat-room">
    <div class="chat-head">
      <span>对话记录</span>
      <span class="chat-state" :class="`chat-state--${stateTone}`" role="status" aria-live="polite">
        <i class="state-mark" aria-hidden="true"></i>
        {{ stateLabel }}
      </span>
    </div>

    <div ref="messagesContainer" class="chat-messages" role="log" aria-label="对话消息">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="message"
        :class="{ 'message--user': msg.isUser }"
      >
        <span
          v-if="!msg.isUser"
          class="ai-mark"
          :class="{ 'is-continuous': isContinuousAi(index) }"
          aria-hidden="true"
        >
          {{ aiType === 'agent' ? '智' : '客' }}
        </span>

        <div class="message-body">
          <div v-if="!msg.isUser" class="message-meta">
            <span>{{ aiLabel }}</span>
            <time>{{ formatTime(msg.time) }}</time>
          </div>

          <div v-if="msg.isUser" class="user-bubble">{{ msg.content }}</div>
          <div v-else class="message-text">
            <div v-html="renderMarkdown(msg.content)"></div>
            <span v-if="showCaret(index)" class="typing-caret" aria-hidden="true"></span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="statusText" class="chat-progress" role="status" aria-live="polite">
      <i aria-hidden="true"></i>
      <span>{{ statusText }}</span>
    </div>

    <div v-if="connectionStatus === 'error'" class="chat-error" role="alert">
      连接中断。重新发送一条消息即可重试。
    </div>

    <div class="chat-input">
      <label class="sr-only" for="chat-input">输入消息</label>
      <input
        id="chat-input"
        v-model="inputMessage"
        type="text"
        :placeholder="placeholder"
        :disabled="connectionStatus === 'connecting'"
        autocomplete="off"
        @keydown.enter.exact.prevent="sendMessage"
      />
      <button
        type="button"
        :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
        @click="sendMessage"
      >
        发送
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  connectionStatus: {
    type: String,
    default: 'disconnected'
  },
  aiType: {
    type: String,
    default: 'aftersales'
  },
  statusText: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['send-message'])

const inputMessage = ref('')
const messagesContainer = ref(null)

const aiLabel = computed(() => (props.aiType === 'agent' ? '售后处理智能体' : '售后客服'))

const placeholder = computed(() =>
  props.aiType === 'agent' ? '说明需要处理的售后任务…' : '描述你遇到的售后问题…'
)

const stateTone = computed(() => {
  if (props.statusText || props.connectionStatus === 'connecting') return 'busy'
  if (props.connectionStatus === 'error') return 'error'
  return 'idle'
})

const stateLabel = computed(() => {
  if (props.statusText) return '处理中'
  if (props.connectionStatus === 'connecting') return '连接中'
  if (props.connectionStatus === 'error') return '连接中断'
  return '在线'
})

const sendMessage = () => {
  if (!inputMessage.value.trim() || props.connectionStatus === 'connecting') return

  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const isContinuousAi = (index) => {
  if (index === 0) return false
  return !props.messages[index].isUser && !props.messages[index - 1].isUser
}

const showCaret = (index) => {
  return (
    props.connectionStatus === 'connecting' &&
    index === props.messages.length - 1 &&
    !props.messages[index].isUser
  )
}

const escapeHtml = (text) =>
  text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const renderInline = (text) =>
  text
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')

// 先转义，再按行生成受控的段落、列表与小标题。
const renderMarkdown = (text) => {
  if (!text) return ''

  const lines = escapeHtml(text).split('\n')
  let html = ''
  let listType = null

  const closeList = () => {
    if (!listType) return
    html += `</${listType}>`
    listType = null
  }

  for (const line of lines) {
    const unordered = line.match(/^\s*[-*]\s+(.*)$/)
    const ordered = line.match(/^\s*\d+\.\s+(.*)$/)

    if (unordered || ordered) {
      const type = unordered ? 'ul' : 'ol'
      if (listType !== type) {
        closeList()
        html += `<${type} class="md-list">`
        listType = type
      }
      html += `<li>${renderInline((unordered || ordered)[1])}</li>`
      continue
    }

    closeList()

    if (!line.trim()) {
      html += '<br>'
      continue
    }

    if (/^#{1,4}\s+/.test(line)) {
      html += `<strong class="md-heading">${renderInline(line.replace(/^#{1,4}\s+/, ''))}</strong><br>`
      continue
    }

    html += `${renderInline(line)}<br>`
  }

  closeList()
  return html.replace(/<br>$/, '')
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(
  () => props.messages.length,
  () => {
    scrollToBottom()
  }
)

watch(
  () => props.messages.map((message) => message.content).join(''),
  () => {
    scrollToBottom()
  }
)

onMounted(() => {
  scrollToBottom()
})
</script>

<style scoped>
.chat-room {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--surface);
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  font-size: var(--step-small);
  color: var(--muted);
}

.chat-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--step-small);
  color: var(--accent);
}

.state-mark {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: currentColor;
}

.chat-state--busy {
  color: var(--muted);
}

.chat-state--busy .state-mark {
  animation: state-pulse 1.2s ease-in-out infinite;
}

.chat-state--error {
  color: #8a2d21;
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  scrollbar-gutter: stable;
}

.message {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.message--user {
  display: flex;
  justify-content: flex-end;
}

.ai-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.ai-mark.is-continuous {
  visibility: hidden;
}

.message-body {
  min-width: 0;
  max-width: 60ch;
}

.message-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.4;
}

.message-text {
  font-size: var(--step-body);
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.message-text :deep(strong) {
  font-weight: 600;
}

.message-text :deep(code) {
  padding: 0.08em 0.3em;
  background: var(--surface-2);
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: 0.9em;
}

.message-text :deep(.md-heading) {
  display: block;
  margin-top: 0.7em;
  font-weight: 600;
}

.message-text :deep(.md-list) {
  margin: 0.5em 0 0.5em 1.1em;
  padding: 0;
}

.message-text :deep(.md-list li) {
  margin: 0.25em 0;
}

.message-text :deep(ul.md-list) {
  list-style: none;
}

.message-text :deep(ul.md-list li) {
  position: relative;
  padding-left: 0.8em;
}

.message-text :deep(ul.md-list li::before) {
  content: "";
  position: absolute;
  left: 0;
  top: 0.62em;
  width: 0.3em;
  height: 0.3em;
  background: var(--accent);
}

.message-text :deep(ol.md-list) {
  list-style: decimal;
}

.user-bubble {
  max-width: 70%;
  padding: 10px 14px;
  background: var(--surface-2);
  font-size: var(--step-body);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.typing-caret {
  display: inline-block;
  width: 4px;
  height: 1.05em;
  margin-left: 2px;
  vertical-align: -0.15em;
  background: var(--accent);
  animation: caret-blink 0.8s steps(1) infinite;
}

.chat-progress {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 20px 10px;
  color: var(--accent);
  font-size: var(--step-small);
  line-height: 1.4;
}

.chat-progress i {
  width: 6px;
  height: 6px;
  background: var(--accent);
  animation: state-pulse 1.2s ease-in-out infinite;
}

.chat-error {
  margin: 0 20px 10px;
  padding: 8px 10px;
  background: #f4e5e1;
  color: #8a2d21;
  font-size: var(--step-small);
  line-height: 1.5;
}

.chat-input {
  display: flex;
  gap: 8px;
  padding: 12px 20px 20px;
}

.chat-input input {
  flex: 1;
  min-width: 0;
  height: 42px;
  padding: 0 12px;
  border: 0;
  background: var(--bg);
  color: var(--ink);
  font-size: 13px;
}

.chat-input input::placeholder {
  color: var(--muted);
}

.chat-input input:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.chat-input input:disabled {
  opacity: 0.55;
}

.chat-input button {
  height: 42px;
  padding: 0 18px;
  border: 0;
  background: var(--ink);
  color: var(--surface);
  font-size: 13px;
  transition: background-color 0.2s ease;
}

.chat-input button:hover:not(:disabled) {
  background: var(--accent);
}

.chat-input button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@keyframes state-pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.8);
  }

  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes caret-blink {
  0%,
  49% {
    opacity: 1;
  }

  50%,
  100% {
    opacity: 0;
  }
}

@media (max-width: 640px) {
  .chat-head {
    padding: 14px;
  }

  .chat-messages {
    padding: 0 14px 14px;
    gap: 16px;
  }

  .message {
    gap: 8px;
  }

  .message-body {
    max-width: 100%;
  }

  .chat-progress {
    padding-inline: 14px;
  }

  .chat-error {
    margin-inline: 14px;
  }

  .chat-input {
    gap: 6px;
    padding: 10px 14px 14px;
  }

  .chat-input input {
    height: 40px;
    font-size: 13px;
  }

  .chat-input button {
    height: 40px;
    padding: 0 14px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .state-mark,
  .chat-progress i,
  .typing-caret {
    animation: none;
  }
}
</style>
