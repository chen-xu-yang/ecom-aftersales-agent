// Markdown 渲染测试：从 ChatRoom.vue 中提取真实实现，避免复制逻辑导致测试失真
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { assert, assertContains, assertEqual, assertNotContains } from './helpers.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const chatRoomPath = path.resolve(here, '../src/components/ChatRoom.vue')

function loadRenderer() {
  const source = fs.readFileSync(chatRoomPath, 'utf8')
  const scriptMatch = source.match(/<script setup>([\s\S]*?)<\/script>/)
  assert(scriptMatch, 'ChatRoom.vue 中未找到 <script setup>')
  const script = scriptMatch[1]

  const start = script.indexOf('const escapeHtml')
  const end = script.indexOf('const scrollToBottom')
  assert(start >= 0 && end > start, '未找到 escapeHtml / renderMarkdown 实现')

  const factory = new Function(
    `${script.slice(start, end)}\nreturn { escapeHtml, renderInline, renderMarkdown }`
  )
  return factory()
}

export default function run(context) {
  let renderer
  try {
    renderer = loadRenderer()
  } catch (error) {
    context.test('加载 ChatRoom.vue 渲染函数', () => {
      throw error
    })
    return
  }
  const { renderMarkdown } = renderer

  context.test('渲染器：空输入返回空字符串', () => {
    assertEqual(renderMarkdown(''), '')
    assertEqual(renderMarkdown(null), '')
    assertEqual(renderMarkdown(undefined), '')
  })

  context.test('渲染器：普通文本原样输出', () => {
    assertEqual(renderMarkdown('你好，我是售后客服'), '你好，我是售后客服')
  })

  context.test('渲染器：纯文本中的 > < & 被转义', () => {
    assertEqual(renderMarkdown('1 < 2 & 3 > 0'), '1 &lt; 2 &amp; 3 &gt; 0')
  })

  context.test('渲染器：XSS - script 标签不得原样输出', () => {
    const html = renderMarkdown('<script>alert(1)</script>')
    assertNotContains(html, '<script>', 'script 标签被直接输出')
    assertContains(html, '&lt;script&gt;alert(1)&lt;/script&gt;')
  })

  context.test('渲染器：XSS - img onerror 不得原样输出', () => {
    const html = renderMarkdown('<img src=x onerror=alert(1)>')
    assertNotContains(html, '<img', 'img 标签被直接输出')
    assertContains(html, '&lt;img')
  })

  context.test('渲染器：加粗语法渲染为 strong', () => {
    assertEqual(renderMarkdown('**加粗内容**'), '<strong>加粗内容</strong>')
  })

  context.test('渲染器：行内代码渲染为 code', () => {
    assertEqual(renderMarkdown('请使用 `searchPolicy` 工具'), '请使用 <code>searchPolicy</code> 工具')
  })

  context.test('渲染器：标题渲染为 md-heading', () => {
    assertEqual(renderMarkdown('## 退换货规则'), '<strong class="md-heading">退换货规则</strong>')
  })

  context.test('渲染器：无序列表渲染为 ul', () => {
    assertEqual(
      renderMarkdown('- 七天无理由\n- 运费承担'),
      '<ul class="md-list"><li>七天无理由</li><li>运费承担</li></ul>'
    )
  })

  context.test('渲染器：有序列表渲染为 ol', () => {
    assertEqual(
      renderMarkdown('1. 提交申请\n2. 客服审核'),
      '<ol class="md-list"><li>提交申请</li><li>客服审核</li></ol>'
    )
  })

  context.test('渲染器：列表类型切换时正确闭合', () => {
    assertEqual(
      renderMarkdown('- 无序项\n1. 有序项'),
      '<ul class="md-list"><li>无序项</li></ul><ol class="md-list"><li>有序项</li></ol>'
    )
  })

  context.test('渲染器：列表内加粗仍生效', () => {
    assertEqual(
      renderMarkdown('- **重要**：48 小时内申请'),
      '<ul class="md-list"><li><strong>重要</strong>：48 小时内申请</li></ul>'
    )
  })

  context.test('渲染器：换行转换为 br', () => {
    assertEqual(renderMarkdown('第一行\n第二行'), '第一行<br>第二行')
  })

  context.test('渲染器：未闭合的加粗标记保持原文', () => {
    assertEqual(renderMarkdown('**未闭合'), '**未闭合')
  })

  context.test('渲染器：转义后再解析，不会因为加粗产生注入', () => {
    const html = renderMarkdown('**<svg onload=alert(1)>**')
    assertNotContains(html, '<svg', '注入标签未被转义')
    assertContains(html, '&lt;svg')
  })

  context.test('渲染器：链接语法不会生成 a 标签（当前仅支持纯文本）', () => {
    const html = renderMarkdown('[点我](javascript:alert(1))')
    assertNotContains(html, '<a', '不应生成链接标签')
    assertContains(html, 'javascript:alert(1)')
  })
}
