// 路由契约静态检查：确认 2 页结构与重定向规则（AI_CONTEXT §7.1）
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { assert, assertContains, assertNotContains } from './helpers.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export default function run(context) {
  const routerSource = fs.readFileSync(path.resolve(here, '../src/router/index.js'), 'utf8')

  context.test('路由：首页与工作台两个页面', () => {
    assertContains(routerSource, "path: '/'")
    assertContains(routerSource, "path: '/workspace'")
  })

  context.test('路由：/aftersales 重定向到工作台客服模式', () => {
    const block = routerSource.slice(routerSource.indexOf("path: '/aftersales'"))
    assertContains(block.slice(0, 160), "path: '/workspace'")
    assertContains(block.slice(0, 160), "mode: 'chat'")
  })

  context.test('路由：/agent 重定向到工作台智能体模式', () => {
    const block = routerSource.slice(routerSource.indexOf("path: '/agent'"))
    assertContains(block.slice(0, 160), "path: '/workspace'")
    assertContains(block.slice(0, 160), "mode: 'agent'")
  })

  context.test('路由：未知路径回首页，不再单独做 404 页', () => {
    assertContains(routerSource, "path: '/:pathMatch(.*)*'")
    assertNotContains(routerSource, 'NotFound')
    assertNotContains(routerSource, "path: '/404'")
  })

  const chatRoomSource = fs.readFileSync(path.resolve(here, '../src/components/ChatRoom.vue'), 'utf8')

  context.test('对话组件：AI 消息走 Markdown 渲染，用户消息保持纯文本插值', () => {
    assertContains(chatRoomSource, 'v-html="renderMarkdown(msg.content)"')
    assertContains(chatRoomSource, '<div v-if="msg.isUser" class="user-bubble">{{ msg.content }}</div>')
  })

  context.test('对话组件：progress 事件由 Workspace 监听（组件只接收状态文案）', () => {
    const workspace = fs.readFileSync(path.resolve(here, '../src/views/Workspace.vue'), 'utf8')
    assertContains(workspace, "addEventListener('progress'")
    assertContains(workspace, '正在处理…')
  })

  context.test('对话组件：无头像、无图片（极简设计约束）', () => {
    assertNotContains(chatRoomSource, '<img')
    assertNotContains(chatRoomSource, 'avatar')
  })
}
