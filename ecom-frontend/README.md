# 电商售后智能客服平台 - 前端

基于 Vue 3 + Vite 的极简售后工作台，只有两个页面：

- **首页** `/`：简短说明、两个入口、规则索引与处理流程
- **工作台** `/workspace`：售后客服与处理智能体共用一页，用顶部模式切换

两个入口的对话都通过 EventSource 消费后端的服务器发送事件（Server-Sent Events, SSE）接口，实时展示回复内容。

对话内容会做轻量 Markdown 渲染（加粗、行内代码、小标题与列表）：先转义 `&`、`<`、`>`，再生成受控标签，避免模型输出注入 HTML。

智能体回复为逐字流式：前端监听 `token` 命名事件把增量文本追加到当前气泡，`progress` 事件用于显示“正在处理…”状态，`[DONE]` 到达后关闭连接。

## 技术栈

- Vue 3（`<script setup>` 单文件组件）
- Vue Router
- `@vueuse/head` 管理页面标题与元信息
- EventSource 消费 SSE 流式接口

## 设计约定

- 颜色：纸色 `#f5f3ed`、表面 `#fffdf8`、浅表面 `#eeebe2`、墨色 `#1e1d19`、灰 `#6e6a60`、强调绿 `#2f7a52`；全部定义在 `src/style.css` 的 `:root`。
- 字体：标题 `Noto Serif SC`，界面与正文 `Noto Sans SC`；使用本机字体，不加载外部字体。
- 字号：桌面 H1 40px、区块标题 24px、正文 15px；手机 H1 28px、区块标题 21px、正文 13px。没有展示级巨字。
- 形状：方形直角、平涂表面；不使用渐变、静态阴影、图片、玻璃拟态和装饰性分割线。
- 页面：只有首页与工作台。工作台的状态通过 `?mode=chat|agent` 区分；旧地址 `/aftersales`、`/agent` 重定向到工作台对应模式；未知路由回到首页，不再单独做 404 页。
- 初始状态：工作台只显示对应模式的欢迎语，**不预置问答示例**。
- 共享组件：`AppHeader`（导航）、`AppFooter`（一行页脚）、`ChatRoom`（对话、状态、输入）。

## 环境要求

- Node.js >= 16
- npm >= 7

## 本地开发

```bash
npm install
npm run dev
```

开发服务器默认运行在 `http://localhost:3000`，接口请求指向 `http://localhost:8123/api`（见 `src/api/index.js`）。

## 构建

```bash
npm run build
```

## 依赖的后端接口

| 接口 | 说明 |
|---|---|
| `GET /api/ai/aftersales/chat/sse` | 售后客服流式对话（参数 `message`、`chatId`） |
| `GET /api/ai/agent/chat` | 售后处理智能体流式对话（参数 `message`、可选 `chatId`，同一 chatId 多轮） |

## 部署

`Dockerfile` 分两阶段构建：Node 阶段执行 `npm run build`，运行阶段使用 Nginx 托管 `dist`。

`nginx.conf` 已针对 SSE 关闭反向代理缓冲并延长读取超时（`proxy_buffering off`、`proxy_read_timeout 600s`），否则流式输出会被 Nginx 缓冲成一次性返回。

反向代理的后端地址在 `nginx.conf` 的 `proxy_pass` 中配置，按实际部署方式修改（同机部署填写宿主机地址，容器编排填写后端服务名）。

