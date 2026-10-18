// 示意图的小交互：切换工作台的两个模式。
// 这不是最终实现，只用于确认信息层级与页面结构。

const buttons = [...document.querySelectorAll('.mode-switch button')]
const panels = [...document.querySelectorAll('[data-panel]')]
const input = document.querySelector('.chat-input input')

const placeholders = {
  chat: '描述你遇到的售后问题…',
  agent: '说明需要处理的售后任务…'
}

const setMode = (mode) => {
  buttons.forEach((button) => {
    const active = button.dataset.mode === mode
    button.classList.toggle('active', active)
    button.setAttribute('aria-selected', String(active))
  })

  panels.forEach((panel) => {
    panel.hidden = panel.dataset.panel !== mode
  })

  if (input) input.placeholder = placeholders[mode] || placeholders.chat
}

buttons.forEach((button) => {
  button.addEventListener('click', () => setMode(button.dataset.mode))
})

const initialMode = new URLSearchParams(window.location.search).get('mode')
setMode(initialMode === 'agent' ? 'agent' : 'chat')
