// SSE API 封装测试：用假的 EventSource 校验 URL 拼接、[DONE] 与错误处理
import { assert, assertEqual } from './helpers.mjs'

class FakeEventSource {
  constructor(url) {
    this.url = url
    this.closed = false
    FakeEventSource.instances.push(this)
  }

  close() {
    this.closed = true
  }
}
FakeEventSource.instances = []

export default async function run(context) {
  globalThis.EventSource = FakeEventSource
  const api = await import('../src/api/index.js')

  context.test('API：开发环境 base URL 指向本地后端', () => {
    const source = api.chatWithAftersalesApp('你好', 'chat-1')
    assert(source.url.startsWith('http://localhost:8123/api/ai/aftersales/chat/sse?'), source.url)
  })

  context.test('API：中文与特殊字符按 URI 编码', () => {
    FakeEventSource.instances.length = 0
    const source = api.chatWithAftersalesApp('吊牌剪了还能退吗', '会话 1')
    assert(source.url.includes('message=%E5%90%8A%E7%89%8C'), source.url)
    assert(!source.url.includes(' '), 'URL 中不应包含未编码空格：' + source.url)
  })

  context.test('API：智能体接口带 message 与 chatId 参数', () => {
    FakeEventSource.instances.length = 0
    const source = api.chatWithAgent('生成售后处理单', 'agent-1')
    assert(source.url.includes('/ai/agent/chat?message='), source.url)
    assert(source.url.includes('chatId=agent-1'), source.url)
  })

  context.test('API：[DONE] 哨兵原样交给调用方', () => {
    FakeEventSource.instances.length = 0
    const received = []
    const source = api.connectSSE('/test', { a: '1' }, (data) => received.push(data))
    source.onmessage({ data: '[DONE]' })
    assertEqual(received.length, 1)
    assertEqual(received[0], '[DONE]')
  })

  context.test('API：普通数据原样交给调用方', () => {
    FakeEventSource.instances.length = 0
    const received = []
    const source = api.connectSSE('/test', { a: '1' }, (data) => received.push(data))
    source.onmessage({ data: '第一段答复' })
    assertEqual(received[0], '第一段答复')
  })

  context.test('API：连接错误时回调并关闭连接', () => {
    FakeEventSource.instances.length = 0
    let errored = 0
    const source = api.connectSSE('/test', {}, undefined, () => {
      errored += 1
    })
    source.onerror(new Error('network'))
    assertEqual(errored, 1)
    assert(source.closed, '错误后应关闭 EventSource')
  })
}
