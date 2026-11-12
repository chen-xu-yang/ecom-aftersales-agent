# 项目说明：电商售后智能客服平台

> 本文件记录项目定位、架构约定、核心链路与开发规范。修改代码前先读一遍，避免破坏既有契约。
>
> 最后更新：2026-11-12

---

## 1. 项目定位与当前状态

面向电商售后场景的 AI 应用，两个入口：

| 入口 | 职责 | 技术形态 |
|---|---|---|
| 售后客服（`AftersalesApp`） | 多轮对话 + 知识库问答，解答退换货、物流、价保、发票等问题 | ChatClient + Advisor（记忆、日志） |
| 售后处理智能体（`AftersalesAgent`） | 按售后规则自主拆解任务、调用工具、产出处理记录与文档 | ReAct 分层实现 + 工具调用 + SSE 流式 |

另有独立模块 `ecom-mcp-server`，以 MCP 服务端形式对外提供图片检索能力（stdio / SSE 双传输）。

**当前状态（2026-11-12）**

- 智能体最终答复已改为 token 级 SSE 流式，`progress` / `token` / `[DONE]` 三类事件统一；
- 支持按 `chatId` 的多轮会话（会话实例池，LRU + 空闲过期）；
- 新增 40 条售后问答评测集与检索评测脚本，`topK=4 / 阈值 0.4` 经评测确认处于最优区间；
- 安全层补齐：工具路径真实路径校验 + 1 MB 限制、CORS 来源白名单、日志截断、密钥环境变量注入；
- 测试可离线复现：`mvn -o test` 123 个用例（9 条真实模型用例默认跳过），前端 29 条逻辑测试。

运行入口：后端 `http://localhost:8123/api`，前端 dev server `http://localhost:3000`。

---

## 2. 环境与运行

| 项 | 值 |
|---|---|
| JDK | 21 |
| Maven | 3.9.15 |
| Node / npm | 24.x / 11.x |
| 后端端口 | 8123，context-path `/api` |
| MCP 服务端端口 | 8127 |

```bash
# 后端（项目根目录）
mvn spring-boot:run

# 前端
cd ecom-frontend && npm run dev

# MCP 服务端（可选，图片检索；需要 SEARCH_API_KEY）
cd ecom-mcp-server && mvn package -DskipTests
java -jar target/ecom-mcp-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=sse
```

注意事项：

- 使用 `mvn`，不用 `mvnw`（`JAVA_HOME` 未设置时 `mvnw` 会先下载 Maven）。
- 首次启动加载知识库：约 17 个片段，每片段一次关键词增强模型调用，随后向量化，启动约 20~30 秒，产生少量 token 费用。
- MCP 图片检索使用 SearchAPI Google Images，与联网搜索共用同一 Key；MCP 侧通过 `SEARCH_API_KEY` 环境变量注入。
- 日志控制台编码默认跟随 JVM `stdout.encoding`；文件日志固定 UTF-8。需要时用 `LOG_CHARSET` 或 `-Dstdout.encoding` 调整。

---

## 3. 配置与密钥

| 文件 | 是否提交 | 用途 |
|---|---|---|
| `src/main/resources/application.yml` | 是 | 主配置，只放占位值 |
| `src/main/resources/application-local.yml` | **否**（`.gitignore`） | 本地真实密钥，profile 为 `local` 时覆盖主配置 |
| `src/main/resources/application-prod.yml` | 是 | 生产配置，密钥全部来自环境变量 |

需要的密钥：

| 密钥 | 用途 | 是否必需 |
|---|---|---|
| `DASHSCOPE_API_KEY` | 对话模型与向量模型 | 必需 |
| `SEARCH_API_KEY` | `WebSearchTool` 联网搜索、MCP 图片检索 | 使用对应功能时必需 |

规则：

- **任何密钥都不得写进受版本控制的文件、代码、文档或提交信息。**
- 新增配置项先写占位值到 `application.yml`，真实值放 `application-local.yml`（本地）或环境变量（部署）。

---

## 4. 目录地图

```
src/main/java/com/aftersales/agent/
├── AftersalesAgentApplication.java     启动类（排除 DataSourceAutoConfiguration）
├── advisor/
│   ├── MyLoggerAdvisor.java            请求/响应日志（截断 500 字符）
│   └── ReReadingAdvisor.java           Re2 改写，默认未启用
├── agent/
│   ├── BaseAgent.java                  状态机、步骤循环、SSE 推送
│   ├── ReActAgent.java                 think / act 抽象、终止判定
│   ├── ToolCallAgent.java              流式聚合、工具调用、消息上下文
│   ├── AgentChatSessionManager.java    按 chatId 的会话实例池（多轮）
│   ├── AftersalesAgent.java            售后处理智能体（提示词、maxSteps=8）
│   └── model/AgentState.java           IDLE / RUNNING / FINISHED / ERROR
├── app/AftersalesApp.java              售后客服（对话、RAG、工具、MCP、结构化输出）
├── chatmemory/FileBasedChatMemory.java Kryo 文件记忆（可选，默认未启用）
├── config/CorsConfig.java              跨域来源白名单
├── constant/FileConstant.java          临时产物目录 tmp/
├── controller/AiController.java        对话接口
├── controller/HealthController.java    /health
├── rag/
│   ├── AftersalesDocumentLoader.java   文档加载，解析 topic 元数据
│   ├── AftersalesVectorStoreConfig.java  SimpleVectorStore Bean
│   ├── MyKeywordEnricher.java          入库前关键词增强
│   ├── MyTokenTextSplitter.java
│   ├── QueryRewriter.java              查询改写
│   ├── AftersalesRagCustomAdvisorFactory.java  元数据过滤 + 阈值 + topK
│   ├── AftersalesContextualQueryAugmenterFactory.java  空检索兜底话术
│   └── PgVectorVectorStoreConfig.java  可选持久化向量库（@Configuration 已注释）
└── tools/
    ├── AftersalesPolicyTool.java       searchPolicy：知识库检索工具
    ├── FileOperationTool.java          readFile / writeFile（tmp/file，限 1 MB）
    ├── WebSearchTool.java              searchWeb（SearchAPI）
    ├── PDFGenerationTool.java          generatePDF（tmp/pdf）
    ├── TerminateTool.java              doTerminate：无法继续时中止
    ├── ToolPathResolver.java           文件名越界/符号链接校验
    └── ToolRegistration.java           集中注册全部工具

src/main/resources/
├── application.yml / application-prod.yml / application-mcp.yml
├── document/                           知识库：售后规则 - 退换货 / 物流 / 商品与价保.md
└── mcp-servers.json                    MCP stdio 客户端配置

ecom-frontend/                          Vue 3 + Vite 前端
ecom-mcp-server/                        MCP 服务端模块（独立 Maven 工程）
docs/                                   评测、变更记录、设计稿、本说明
```

---

## 5. 核心链路

### 5.1 知识库与 RAG

- 文档放在 `src/main/resources/document/`，命名约定 **「主题 - 分类.md」**，例如 `售后规则 - 退换货.md`。
- `AftersalesDocumentLoader.parseTopic()` 取「 - 」之后的分类，写入元数据 `topic`。
- 文档按 `---` 水平线切分为独立片段（当前 17 个，5/6/6）。
- 入库：`MyKeywordEnricher` 补充关键词 → `SimpleVectorStore.add()` 向量化（PgVector 配置可选）。
- 客服应用提供 `QuestionAnswerAdvisor` 与自定义 `AftersalesRagCustomAdvisorFactory`（元数据过滤 + 阈值 0.5 + topK 3，按需启用）；空检索兜底话术在 `AftersalesContextualQueryAugmenterFactory`。

### 5.2 智能体的知识库检索

`AftersalesPolicyTool.searchPolicy` 把 RAG 检索做成**工具**，而不是常驻 Advisor：

- 由模型决定何时检索，普通寒暄不触发向量检索；
- 政策类问题在系统提示中被要求先检索；
- 检索参数：topK 4、相似度阈值 0.4（经 40 条评测集验证处于最优区间）；
- 返回结果带 `【topic】` 标签，便于模型引用；
- 检索为空时返回"请提示用户转人工"的文案。

### 5.3 智能体的终止语义（三层）

1. **模型不再发起工具调用** → 视为给出最终答复，标记 `FINISHED`，返回该文本；
2. **`terminate` 工具** → 用于无法继续时主动中止；
3. **`maxSteps`（8）** → 兜底，防止异常情况下无限循环。

`FINISHED` 与 `ERROR` 都会退出循环；`ERROR` 不被当作"继续执行"。

### 5.4 工具集与安全边界

| 工具 | 说明 |
|---|---|
| `readFile` / `writeFile` | 读写 `tmp/file` 下的文本，单文件限 1 MB |
| `searchWeb` | 第三方主体（快递公司、品牌方等）公开规则；平台政策优先 `searchPolicy` |
| `generatePDF` | 生成 PDF 到 `tmp/pdf` |
| `searchPolicy` | 检索售后规则知识库 |
| `doTerminate` | 主动中止任务（不是交付答复的手段） |

安全约定：

- 工具参数来自模型，必须限制参数取值范围与可访问资源；
- 文件工具统一走 `ToolPathResolver`：规范化后必须位于基准目录内，并校验最近存在父目录的真实路径、拒绝符号链接；
- 已移除终端执行、任意 URL 抓取、任意资源下载三个工具——`@Tool` 暴露的每个能力都是提示词注入的可达面。

---

## 6. 接口与 SSE 协议

服务端口 8123，统一前缀 `/api`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查，返回 `ok` |
| GET | `/api/ai/aftersales/chat/sync` | 客服同步对话 |
| GET | `/api/ai/aftersales/chat/sse` | 客服流式对话（`Flux<String>`） |
| GET | `/api/ai/aftersales/chat/server_sent_event` | 客服流式对话（`Flux<ServerSentEvent>`） |
| GET | `/api/ai/aftersales/chat/sse_emitter` | 客服流式对话（`SseEmitter`） |
| GET | `/api/ai/agent/chat?message=&chatId=` | 智能体流式对话（同 `chatId` 多轮） |
| GET | `/api/ai/aftersales/chat/mcp` | 客服对话 + MCP 工具（需 `mcp` profile） |

`/api/ai/agent/chat` 的 SSE 协议：

```
event:progress
data:正在处理…            ← 工具调用/步骤切换时推送，前端只更新状态条

event:token
data:根据                  ← 模型增量文本，前端逐字追加

data:[DONE]               ← 结束哨兵；正常结束、步数上限、异常、超时都会下发
```

客服三种 SSE 接口同样在流末尾追加 `[DONE]`，前端据此关闭连接而不是按错误重连。

---

## 7. 前端约定

### 7.1 设计系统

- 页面只有两个：首页 `/` 与工作台 `/workspace`；工作台用 `?mode=chat|agent` 切换客服/智能体；`/aftersales`、`/agent` 重定向到对应模式；未知路由回首页，不单独做 404 页。
- 令牌集中在 `src/style.css` 的 `:root`：纸色 `#f5f3ed`、表面 `#fffdf8`、浅表面 `#eeebe2`、墨色 `#1e1d19`、灰 `#6e6a60`、强调绿 `#2f7a52`、淡绿 `#e4efe8`。
- 字体：标题 `Noto Serif SC`，界面与正文 `Noto Sans SC`；使用本机字体，不加载外部字体。
- 字号：桌面 H1 40px、区块标题 24px、正文 15px；手机 H1 28px、区块标题 21px、正文 13px。
- 不使用渐变、静态阴影、图片、玻璃拟态、装饰性分割线与圆角药丸按钮；形状统一为方形直角。
- 工作台初始只显示对应模式的欢迎语，不预置问答示例。

### 7.2 组件与页面

- `components/AppHeader.vue`：首页 / 工作台 / 规则三个链接，按当前路由标记当前页。
- `components/AppFooter.vue`：一行版权与说明。
- `components/ChatRoom.vue`：对话、状态、输入；AI 消息带小方块标记，用户消息是浅底气泡，不使用头像。
- `views/Home.vue`：极简首页（说明、两个入口、规则索引、三步流程）。
- `views/Workspace.vue`：两个模式共用一页；`sessions.chat` 与 `sessions.agent` 各自保存消息和连接状态，切换模式不丢另一边的对话。

### 7.3 对话与 SSE

- AI 消息做轻量 Markdown 渲染：先转义 `&`、`<`、`>`，再处理加粗、行内代码、小标题与列表；生成的标签只来自渲染器。
- `progress` 事件驱动输入框上方的"正在处理…"状态行；`token` 事件追加到当前气泡；`[DONE]` 收尾。
- 智能体模式有气泡节流：按句末标点、双换行或长度阈值切分气泡。

### 7.4 动效

- 只保留悬停、按下、状态方块的轻微反馈；`prefers-reduced-motion: reduce` 下停止动画。

### 7.5 验证

```bash
cd ecom-frontend
npm run build
```

并用真实浏览器在 1440 与 390 宽度检查：首页字号、工作台输入框可见、模式切换 URL、旧路由重定向、两个模式各发一条消息。

---

## 8. 已修复的问题与根因（重要）

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | 智能体连续 8 步"思考完成 - 无需行动"，没有回答 | 用 `.tools()` 传入 `ToolCallback[]`，Spring AI 要求 `.toolCallbacks()`；异常被吞后返回 false，循环没有出口 | 改为 `.toolCallbacks()`；无工具调用视为最终答复并标记 `FINISHED` |
| 2 | 循环跑到步数上限 | 循环条件只看 `FINISHED`，`ERROR` 也会继续 | 循环条件改为 `state == RUNNING` |
| 3 | 模型调用 `terminate` 时答复丢失 | `act()` 只返回工具结果 | 记录本轮助手文本，终止时优先返回文本 |
| 4 | 恰好第 8 步完成却被报"达到最大步骤" | `currentStep >= maxSteps` 未区分是否已结束 | 增加 `state == RUNNING` 前置条件 |
| 5 | 界面出现 `\n`、`\"` 转义符与整段规则原文 | 框架对工具返回值做 JSON 序列化，`act()` 直接推给前端 | 完整结果只写日志，界面只保留简短状态 |
| 6 | 步骤提示混进回答 | 每一步结果都作为对话数据推送 | 中间步骤改用 `progress` 命名事件 |
| 7 | "正在处理…"显示在页面左上角 | 布局使用绝对定位 | 改为 flex 纵向布局，状态条/错误条/输入框在普通流中 |
| 8 | `**加粗**` 原样显示 | 前端用文本插值渲染 | 轻量 Markdown 渲染（先转义再解析） |
| 9 | 智能体不依据知识库作答 | 智能体 ChatClient 没有 RAG | 新增 `searchPolicy` 工具，系统提示要求先检索 |
| 10 | 智能体用英文回答中文问题 | 系统提示为英文且未约束语言 | 提示词改中文并明确"使用用户的语言" |
| 11 | 流结束后前端进入 error 状态并可能重连 | 缺少结束哨兵 | 智能体与客服 SSE 均以 `[DONE]` 收尾 |
| 12 | 文件工具可用 `../` 读写 `tmp/file` 之外 | 文件名直接拼接，未校验 | `ToolPathResolver` 规范化 + 真实路径校验 |
| 13 | PDF 工具可写到 `tmp/pdf` 之外 | 与 #12 同源 | 复用 `ToolPathResolver` |
| 14 | 联网搜索结果少于 5 条时整段失败 | `subList(0, 5)` 未按实际数量截取 | 改为 `min(5, size)` |
| 15 | `generatePDF` 内容为 null 时抛未封装异常 | 未校验入参、只捕获 IOException | 入参校验 + 统一错误文案 |
| 16 | 文件记忆的 `chatId` 可路径穿越 | 会话 ID 未校验即拼进文件名 | 规范化校验，越界拒绝 |
| 17 | 智能体实例执行一次后无法再次 `run` | 状态停留在 FINISHED/ERROR | `startNewRun()` 复位状态与步数 |
| 18 | MCP 图片检索依赖 Pexels，Key 受限 | 工具写死 Pexels | 改用 SearchAPI Google Images + 环境变量 |
| 19 | 智能体声称没有联网工具、不调用 searchWeb | 系统提示未说明第三方规则场景 | 提示词列出工具清单与适用场景 |

---

## 9. 已知限制与下一步

- 对话记忆在内存中（`MessageWindowChatMemory`，保留 20 条）；智能体会话为单机实例池，多实例部署需改为外部存储。
- 接口无鉴权、无限流，公网部署前必须补。
- 智能体每一步产生一次模型调用，暂无 token 成本统计。
- 业务工具尚未接入：查订单、查物流、建工单、转人工。
- MCP 客户端接入为实验性：服务端可独立启动，主工程作为客户端初始化超时（Spring AI 1.0.0 / MCP 0.10.0），待升级或改传输方式。
- 评测只覆盖检索环节（hit@k / MRR / 转人工空检索率），尚未覆盖"答案是否忠实引用原文"。
- 仍有 1 条转人工问题（快递员投诉）会被召回，需意图分类或提示词兜底。
- 智能体可能同时调用 `generatePDF` 与 `writeFile`，产出重复，需在工具描述里明确边界。
- 前端 Markdown 支持加粗、行内代码、小标题与列表；表格、链接、图片仍按纯文本显示。

---

## 10. 开发约定

1. 每次修改前先提交当前状态，提交信息说明"做了什么、为什么"。
2. 改后端后执行 `mvn -o test`（无网络时）或 `mvn test`；改前端后执行 `npm run build` 与 `node tests/run.mjs`。
3. 重启后端并确认 `/api/health` 返回 `ok` 后再验证接口。
4. **不要提交** `application-local.yml`、`target/`、`node_modules/`、`tmp/`、`.idea/`、日志文件。
5. 验证中文接口不要用 Git Bash 的 `curl`（GBK 编码会导致乱码），用 Python `urllib.parse.urlencode` 或浏览器。
6. 接口路径别忘了 `/ai` 段：`/api/ai/aftersales/...`、`/api/ai/agent/chat`。
7. 保持中文业务口径与现有命名风格；新增代码放在 `com.aftersales.agent` 包下。
8. 开发说明：部分样板代码与文档初稿借助 AI 编程助手完成，方案设计、代码审查与验证由作者负责。

---

## 11. 验证方式

```bash
# 健康检查
curl http://localhost:8123/api/health

# 智能体（含流式事件）
curl -N "http://localhost:8123/api/ai/agent/chat?chatId=test-1&message=吊牌剪了还能退吗"
```

预期：

- 智能体接口先收到 `event:progress`，随后多个 `event:token`，最后 `data:[DONE]`；
- 答复中不应出现 `Step`、`已调用工具`、`\n` 字面量或 `\"`；
- 工作台智能体模式：处理期间显示"正在处理…"，答复逐字出现，结束后状态消失。

---

## 12. 设计取舍与常见问题

- **为什么把 RAG 做成工具而不是 Advisor**：Advisor 会在每一步注入检索上下文，多步任务下浪费 token 且干扰判断；做成工具后由模型决定检索时机，普通寒暄不触发。
- **智能体如何判断任务结束**：不再发起工具调用即视为最终答复；`terminate` 用于无法继续时中止；最大步数兜底。
- **如何避免工具结果污染界面**：区分"给模型的数据"和"给人的数据"；原始载荷只写日志，前端只接收最终答复与进度/增量事件。
- **流式聚合为什么在 Agent 层自己做**：Spring AI 的 `MessageAggregator` 只聚合文本、不聚合工具调用，Agent 需要完整的 tool_calls 才能执行工具，因此在 `ToolCallAgent` 中同时聚合文本与工具调用分片。
- **多轮会话为什么用实例池**：保留 Agent 内部 `messageList`（含 tool_use/tool_result 配对），避免手工拼消息破坏消息协议。
- **安全边界**：移除了终端执行、任意 URL 抓取、任意资源下载三个工具；新增工具必须限制参数与可访问资源。
