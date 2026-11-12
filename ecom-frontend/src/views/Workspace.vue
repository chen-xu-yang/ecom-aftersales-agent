<template>
  <div class="workspace-page">
    <AppHeader />

    <main id="main" class="workspace wrap">
      <div class="workspace-head">
        <div>
          <h1>工作台</h1>
          <p>同一套售后规则，两种处理方式。</p>
        </div>

        <div class="mode-switch" role="tablist" aria-label="处理方式">
          <button
            type="button"
            role="tab"
            aria-controls="workspace-panel"
            :aria-selected="activeMode === 'chat'"
            :class="{ active: activeMode === 'chat' }"
            @click="setMode('chat')"
          >
            售后客服
          </button>
          <button
            type="button"
            role="tab"
            aria-controls="workspace-panel"
            :aria-selected="activeMode === 'agent'"
            :class="{ active: activeMode === 'agent' }"
            @click="setMode('agent')"
          >
            处理智能体
          </button>
        </div>
      </div>

      <div class="workspace-grid">
        <section
          id="workspace-panel"
          class="chat-panel"
          role="tabpanel"
          :aria-label="activeMode === 'agent' ? '处理智能体对话' : '售后客服对话'"
        >
          <ChatRoom
            :key="activeMode"
            :messages="activeSession.messages"
            :connection-status="activeSession.connectionStatus"
            :ai-type="activeMode"
            :status-text="activeStatusText"
            @send-message="sendMessage"
          />
        </section>

        <aside class="rail">
          <template v-if="activeMode === 'chat'">
            <h2>本次会话</h2>
            <dl>
              <div>
                <dt>回答依据</dt>
                <dd>三份售后规则文档</dd>
              </div>
              <div>
                <dt>覆盖问题</dt>
                <dd>退换货、物流、价保、发票</dd>
              </div>
              <div>
                <dt>返回方式</dt>
                <dd>流式答复</dd>
              </div>
            </dl>
            <p>没有命中规则时，会提示转人工，不编造政策。</p>
            <router-link class="text-link" to="/">回到首页</router-link>
          </template>

          <template v-else>
            <h2>处理边界</h2>
            <dl>
              <div>
                <dt>规则来源</dt>
                <dd>售后规则知识库，按需检索</dd>
              </div>
              <div>
                <dt>可用动作</dt>
                <dd>检索规则、读写文本、生成 PDF、主动中止</dd>
              </div>
              <div>
                <dt>结束方式</dt>
                <dd>最终答复 / 主动中止 / 8 步上限</dd>
              </div>
            </dl>
            <p>回复逐字实时输出；调用工具时显示处理进度，收到 [DONE] 后结束。</p>
            <router-link class="text-link" to="/">回到首页</router-link>
          </template>
        </aside>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useHead } from '@vueuse/head'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import AppFooter from '../components/AppFooter.vue'
import ChatRoom from '../components/ChatRoom.vue'
import { chatWithAftersalesApp, chatWithAgent } from '../api'

const route = useRoute()
const router = useRouter()

const chatId = `aftersales_${Math.random().toString(36).slice(2, 10)}`
const isUnmounted = ref(false)

const sessions = reactive({
  chat: {
    messages: [],
    connectionStatus: 'disconnected',
    processing: false
  },
  agent: {
    messages: [],
    connectionStatus: 'disconnected',
    processing: false
  }
})

const sources = {
  chat: null,
  agent: null
}

const activeMode = computed(() => (route.query.mode === 'agent' ? 'agent' : 'chat'))
const activeSession = computed(() => sessions[activeMode.value])
const activeStatusText = computed(() =>
  activeMode.value === 'agent' && sessions.agent.processing ? '正在处理…' : ''
)

useHead({
  title: computed(() =>
    activeMode.value === 'agent'
      ? '处理智能体 - 电商售后智能客服平台'
      : '售后客服 - 电商售后智能客服平台'
  ),
  meta: [
    {
      name: 'description',
      content:
        '售后客服解释退换货、物流、价保与发票政策；处理智能体拆解任务、调用工具并生成处理说明。'
    }
  ]
})

const addMessage = (mode, content, isUser, type = '') => {
  sessions[mode].messages.push({
    content,
    isUser,
    type,
    time: Date.now()
  })
}

const setMode = (mode) => {
  if (mode === activeMode.value) return
  router.replace({ path: '/workspace', query: { mode } })
}

const sendMessage = (message) => {
  if (activeMode.value === 'agent') {
    sendAgentMessage(message)
  } else {
    sendChatMessage(message)
  }
}

const sendChatMessage = (message) => {
  const session = sessions.chat

  addMessage('chat', message, true)

  if (sources.chat) sources.chat.close()

  const aiIndex = session.messages.length
  addMessage('chat', '', false)

  session.connectionStatus = 'connecting'
  const source = chatWithAftersalesApp(message, chatId)
  sources.chat = source

  let receivedData = false

  source.onmessage = (event) => {
    const data = event.data
    if (data && data !== '[DONE]') {
      receivedData = true
      if (aiIndex < session.messages.length) {
        session.messages[aiIndex].content += data
      }
    }

    if (data === '[DONE]') {
      session.connectionStatus = 'disconnected'
      source.close()
    }
  }

  source.onerror = () => {
    // 网络异常兜底：若已收到内容，按正常收尾展示，避免把已完成答复标记为失败。
    session.connectionStatus = receivedData ? 'disconnected' : 'error'
    source.close()
  }
}

const sendAgentMessage = (message) => {
  const session = sessions.agent

  addMessage('agent', message, true, 'user-question')

  if (sources.agent) sources.agent.close()

  session.connectionStatus = 'connecting'
  session.processing = true

  // 当前正在追加 token 的气泡下标；-1 表示尚未开始下一条
  let answerIndex = -1

  const ensureAnswerBubble = () => {
    if (answerIndex >= 0) return
    addMessage('agent', '', false, 'ai-answer')
    answerIndex = session.messages.length - 1
  }

  const source = chatWithAgent(message)
  sources.agent = source

  // token：模型增量文本，实时追加渲染
  source.addEventListener('token', (event) => {
    session.processing = true
    ensureAnswerBubble()
    if (answerIndex >= 0 && event.data) {
      session.messages[answerIndex].content += event.data
    }
  })

  // progress：工具调用/步骤切换，结束当前气泡并等待后续 token
  source.addEventListener('progress', () => {
    session.processing = true
    if (answerIndex >= 0) {
      session.messages[answerIndex].type = 'ai-progress'
      answerIndex = -1
    }
  })

  source.onmessage = (event) => {
    const data = event.data
    if (!data) return

    if (data === '[DONE]') {
      if (answerIndex >= 0) {
        session.messages[answerIndex].type = 'ai-final'
      }
      session.processing = false
      session.connectionStatus = 'disconnected'
      source.close()
      return
    }

    // 非 token 的整段消息（终止提示、错误提示等）
    addMessage('agent', data, false, 'ai-final')
  }

  source.onerror = () => {
    session.processing = false
    session.connectionStatus = 'error'
    source.close()
  }
}

onMounted(() => {
  addMessage(
    'chat',
    '您好，我是售后客服助手，可以帮您处理退换货、物流、商品质量与价保等问题。请描述遇到的问题并提供订单号。',
    false
  )
  addMessage(
    'agent',
    '你好，我是售后处理智能体。我可以在售后规则范围内处理退换货、物流异常等问题，并生成处理说明文档。请问需要什么帮助？',
    false
  )
})

onBeforeUnmount(() => {
  isUnmounted.value = true
  if (sources.chat) sources.chat.close()
  if (sources.agent) sources.agent.close()
})
</script>

<style scoped>
.workspace-page {
  display: flex;
  flex-direction: column;
  min-height: 100svh;
}

.workspace {
  flex: 1;
  padding: 56px 0 64px;
}

.workspace-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 24px;
}

.workspace-head h1 {
  font-size: var(--step-workspace);
  line-height: 1.2;
}

.workspace-head p {
  margin-top: 8px;
  color: var(--muted);
  font-size: var(--step-body);
}

.mode-switch {
  display: flex;
  gap: 20px;
}

.mode-switch button {
  position: relative;
  padding: 0 0 6px;
  border: 0;
  background: none;
  color: var(--muted);
  font-size: var(--step-body);
}

.mode-switch button.active {
  color: var(--ink);
  font-weight: 600;
}

.mode-switch button.active::after {
  content: "";
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  background: var(--accent);
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 32px;
  align-items: start;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  height: 600px;
  min-height: 0;
  background: var(--surface);
}

.rail {
  padding-top: 2px;
}

.rail h2 {
  margin-bottom: 18px;
  font-size: 18px;
  line-height: 1.3;
}

.rail dl {
  display: grid;
  gap: 14px;
  margin: 0;
}

.rail dt {
  color: var(--muted);
  font-size: 11px;
}

.rail dd {
  margin: 2px 0 0;
  font-size: 13px;
  line-height: 1.6;
}

.rail p {
  margin-top: 18px;
  color: var(--muted);
  font-size: var(--step-small);
  line-height: 1.7;
}

.rail .text-link {
  display: inline-block;
  margin-top: 18px;
  font-size: 13px;
}

@media (max-width: 900px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .chat-panel {
    height: 560px;
  }

  .rail {
    padding-top: 8px;
  }
}

@media (max-width: 640px) {
  .workspace {
    padding: 36px 0 48px;
  }

  .workspace-head {
    display: block;
    margin-bottom: 18px;
  }

  .workspace-head p {
    font-size: 13px;
  }

  .mode-switch {
    margin-top: 16px;
    gap: 18px;
  }

  .chat-panel {
    height: calc(100svh - 280px);
    min-height: 420px;
    max-height: 600px;
  }

  .rail {
    margin-top: 8px;
  }
}
</style>
