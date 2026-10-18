# AI_CONTEXT：电商售后智能客服平台

> 用途：给后续接手本项目的 AI 或开发者提供上下文。**修改代码前先读本文件。**
>
> 后续修改请保持现有的命名、包结构与业务口径，不要引入与本项目无关的示例文案或演示代码。

最后更新：2026-10-18

---

## 1. 项目定位与当前状态

面向电商售后场景的 AI 应用，两个入口：

| 入口 | 职责 | 技术形态 |
|---|---|---|
| 售后客服（`AftersalesApp`） | 多轮对话 + 知识库问答，解答退换货、物流、价保、发票等问题 | ChatClient + Advisor（记忆、日志、RAG） |
| 售后处理智能体（`AftersalesAgent`） | 按售后规则自主拆解任务、调用工具、产出处理记录与文档 | ReAct 分层实现 + 工具调用 + SSE |

另有独立模块 `ecom-mcp-server`，以 MCP 服务端形式对外提供图片检索能力（stdio / SSE 双传输）。

已完成的工作：

- 业务从"通用对话 + 通用智能体"重构为"电商售后客服"
- 移除教学演示代码与高危工具，清理依赖和配置
- 修复智能体执行循环、终止语义、工具注册等一批缺陷
- 智能体接入知识库检索，政策类问题先检索再回答
- 前端去掉步骤噪声、支持轻量 Markdown 渲染、增加"正在处理…"状态
- 前端先按 Taste Skill 做过一版"售后案卷"，后按用户要求收敛为极简 2 页：首页 + 工作台（客服/智能体同页切换）；移除大图、巨字、404 与旧对话页
- 补充后端单元/契约测试（20 个测试类、97 条用例）与前端逻辑测试（29 条），见 `docs/testing/`
- 依据测试结果修复文件工具路径穿越、搜索结果不足 5 条失败、PDF 空内容异常、文件记忆会话 ID 越界、智能体实例不可复用等问题（见 §8 #12-#17）

当前运行状态：后端 `http://localhost:8123/api`，前端 dev server `http://localhost:3000`。

---

## 2. 环境与运行

| 项 | 值 |
|---|---|
| JDK | 21（本机 `D:\java\jdk`，`JAVA_HOME` 未设置） |
| Maven | 3.9.15 |
| Node / npm | 24.14.0 / 11.9.0 |
| 后端端口 | 8123，context-path `/api` |
| MCP 服务端端口 | 8127 |

启动：

```bash
# 后端（在项目根目录）
mvn spring-boot:run

# 前端
cd ecom-frontend && npm run dev

# MCP 服务端（可选，图片检索；先设置 SEARCH_API_KEY 环境变量）
cd ecom-mcp-server && mvn spring-boot:run
```

注意：

- **用 `mvn`，不要用 `mvnw`**。`JAVA_HOME` 未设置，且 `.mvn/wrapper/maven-wrapper.jar` 不存在（`distributionType=only-script`），`mvnw` 会先联网下载 Maven 3.9.9。若要使用 `mvnw`，先把 `JAVA_HOME` 指向 `D:\java\jdk`。
- 前端是 Vite 4，本机 Node 24 未经验证。若 `npm run dev` 报兼容性错误，升级 Vite 到 5.x 或改用 Node 20。
- 首次启动会加载知识库：约 17 个文档片段，每个片段调用一次模型做关键词增强，随后逐条向量化，启动约 25 秒，会产生少量 token 费用。
- MCP 图片检索已从 Pexels 切换为 SearchAPI Google Images，与联网搜索共用 `search-api.api-key`；MCP 侧通过 `SEARCH_API_KEY` 环境变量注入，不再需要 Pexels Key。
- 日志控制台编码默认跟随 JVM `stdout.encoding`（中文 Windows 通常为 GBK，与终端一致），文件日志固定 UTF-8；如果 IDE/终端使用 UTF-8，设置 `LOG_CHARSET=UTF-8` 或 `-Dstdout.encoding=UTF-8`。

---

## 3. 配置与密钥

| 文件 | 是否提交 | 用途 |
|---|---|---|
| `src/main/resources/application.yml` | 是 | 主配置，只放占位值 |
| `src/main/resources/application-local.yml` | **否**（`.gitignore`） | 本地真实密钥，profile 为 `local` 时覆盖主配置 |

需要的密钥：

| 密钥 | 用途 | 是否必需 |
|---|---|---|
| DashScope API Key（阿里云百炼） | 对话模型与向量模型 | 必需 |
| SearchAPI Key | `WebSearchTool` 联网搜索、`ecom-mcp-server` 图片检索（Google Images） | 使用对应功能时必需 |

规则：

- **任何密钥都不得写进受版本控制的文件、代码、文档或提交信息。**
- 新增配置项先写占位值到 `application.yml`，真实值放 `application-local.yml`。

---

## 4. 目录地图

```
src/main/java/com/aftersales/agent/
├── AftersalesAgentApplication.java     启动类（排除 DataSourceAutoConfiguration）
├── advisor/
│   ├── MyLoggerAdvisor.java            请求/响应日志
│   └── ReReadingAdvisor.java           Re2 改写，默认未启用（评测无稳定收益）
├── agent/
│   ├── BaseAgent.java                  状态机、步骤循环、SSE 推送
│   ├── ReActAgent.java                 think / act 抽象、终止判定
│   ├── ToolCallAgent.java              工具调用、消息上下文、终止工具处理
│   ├── AftersalesAgent.java            售后处理智能体（提示词、maxSteps=8）
│   └── model/AgentState.java           IDLE / RUNNING / FINISHED / ERROR
├── app/AftersalesApp.java              售后客服（对话、RAG、工具、MCP、结构化输出）
├── chatmemory/FileBasedChatMemory.java Kryo 文件记忆（可选，默认未启用）
├── config/CorsConfig.java
├── constant/FileConstant.java          临时产物目录 tmp/
├── controller/AiController.java        对话接口
├── controller/HealthController.java    /health
├── rag/
│   ├── AftersalesDocumentLoader.java   文档加载，解析 topic 元数据
│   ├── AftersalesVectorStoreConfig.java  SimpleVectorStore Bean
│   ├── MyKeywordEnricher.java          入库前关键词增强（每片段一次模型调用）
│   ├── MyTokenTextSplitter.java
│   ├── QueryRewriter.java              查询改写
│   ├── AftersalesRagCustomAdvisorFactory.java  元数据过滤 + 阈值 + topK
│   ├── AftersalesContextualQueryAugmenterFactory.java  空检索兜底话术
│   └── PgVectorVectorStoreConfig.java  可选持久化向量库（@Configuration 已注释）
└── tools/
    ├── AftersalesPolicyTool.java       searchPolicy：知识库检索工具
    ├── FileOperationTool.java          readFile / writeFile（tmp/file）
    ├── WebSearchTool.java              searchWeb（SearchAPI）
    ├── PDFGenerationTool.java          generatePDF（tmp/pdf）
    ├── TerminateTool.java              doTerminate：无法继续时中止
    └── ToolRegistration.java           集中注册全部工具

src/main/resources/
├── application.yml / application-prod.yml
├── document/                           知识库：售后规则 - 退换货 / 物流 / 商品与价保.md
└── mcp-servers.json                    MCP stdio 客户端配置

ecom-frontend/                          Vue 3 + Vite 前端
ecom-mcp-server/                        MCP 服务端模块
```

---

## 5. 核心链路

### 5.1 知识库与 RAG

- 文档放在 `src/main/resources/document/`，命名约定 **「主题 - 分类.md」**，例如 `售后规则 - 退换货.md`。
- `AftersalesDocumentLoader.parseTopic()` 取「 - 」之后的分类，写入元数据 `topic`，供检索过滤。
- 文档按 `---` 水平线切分为独立片段（当前约 17 个）。
- 入库：`KeywordMetadataEnricher` 补充关键词 → `SimpleVectorStore.add()` 向量化。
- 客服应用两条 RAG 路径：`QuestionAnswerAdvisor`（默认启用）与 `AftersalesRagCustomAdvisorFactory`（元数据过滤 + 阈值 0.5 + topK 3，按需启用）。
- 检索为空时走 `AftersalesContextualQueryAugmenterFactory` 的兜底话术，提示转人工。

### 5.2 智能体的知识库检索

`AftersalesPolicyTool.searchPolicy` 把 RAG 检索做成**工具**，而不是常驻 Advisor：

- 由模型决定何时检索，普通寒暄不触发向量检索
- 政策类问题在系统提示中被强制要求先检索
- 检索参数：topK 4、相似度阈值 0.4
- 返回结果带 `【topic】` 标签，便于模型引用

### 5.3 智能体的终止语义（三层）

1. **模型不再发起工具调用** → 视为给出最终答复，标记 `FINISHED`，返回该文本
2. **`terminate` 工具** → 用于无法继续时主动中止
3. **`maxSteps`（8）** → 兜底，防止异常情况下无限循环

`FINISHED` 与 `ERROR` 都会退出循环；`ERROR` 不再被当作"继续执行"。

### 5.4 工具集

| 工具 | 说明 |
|---|---|
| `readFile` / `writeFile` | 读写 `tmp/file` 下的文本 |
| `searchWeb` | 第三方主体（快递公司、品牌方等）公开规则；平台政策优先 `searchPolicy` |
| `generatePDF` | 生成 PDF 到 `tmp/pdf` |
| `searchPolicy` | 检索售后规则知识库 |
| `doTerminate` | 主动中止任务（不是交付答复的手段） |

智能体系统提示与下一步提示都明确：平台政策用 `searchPolicy`，第三方主体的公开规则用 `searchWeb`，不要声称自己不具备联网工具。

已移除：终端执行、任意 URL 抓取、任意资源下载。原因是 `@Tool` 暴露的每个能力都是提示词注入的可达面，终端执行等价于把命令执行权限交给模型。新增工具时必须限制参数取值范围与可访问资源，并做路径/域名校验。

---

## 6. 接口

服务端口 8123，统一前缀 `/api`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查，返回 `ok` |
| GET | `/api/ai/aftersales/chat/sync` | 客服同步对话 |
| GET | `/api/ai/aftersales/chat/sse` | 客服流式对话（`Flux<String>`） |
| GET | `/api/ai/aftersales/chat/server_sent_event` | 客服流式对话（`Flux<ServerSentEvent>`） |
| GET | `/api/ai/aftersales/chat/sse_emitter` | 客服流式对话（`SseEmitter`） |
| GET | `/api/ai/agent/chat` | 智能体流式对话 |

`/api/ai/agent/chat` 的 SSE 协议：

```
event:progress
data:正在处理…            ← 处理期间推送（开始时一次 + 每个中间步骤后一次），前端只更新状态条

data:<最终答复>            ← 默认事件，进入对话气泡
data:[DONE]               ← 结束哨兵，前端据此关闭连接，不再按错误重连
```

客服页接口暂未发送 `[DONE]`；前端把已收到内容后的连接关闭视为正常收尾，内容不受影响。

---

## 7. 前端约定

### 7.1 设计系统

- 页面只有两个：首页 `/` 与工作台 `/workspace`。工作台用 `?mode=chat|agent` 切换客服/智能体；`/aftersales`、`/agent` 重定向到对应模式；未知路由回首页，**不再单独做 404 页**。
- 令牌集中在 `src/style.css` 的 `:root`：纸色 `#f5f3ed`、表面 `#fffdf8`、浅表面 `#eeebe2`、墨色 `#1e1d19`、灰 `#6e6a60`、强调绿 `#2f7a52`、淡绿 `#e4efe8`。
- 字体：标题 `Noto Serif SC`，界面与正文 `Noto Sans SC`；使用本机字体，不加载外部字体。
- 字号：桌面 H1 40px、区块标题 24px、正文 15px；手机 H1 28px、区块标题 21px、正文 13px。**不要恢复 100px 以上的展示级大字。**
- **不要引入渐变、静态阴影、图片、玻璃拟态、装饰性分割线和圆角药丸按钮。** 形状统一为方形直角，信息层级用字号、留白和浅表面解决。
- 工作台初始只显示对应模式的欢迎语，**不预置问答示例**；首页也不放问答预览。
- 首页的规则索引是静态说明列表，不是弹层或独立页面；四个主题都指向工作台客服模式。

### 7.2 组件与页面

- `components/AppHeader.vue`：首页 / 工作台 / 规则三个链接，按当前路由标记当前页。
- `components/AppFooter.vue`：只有一行版权与说明，不再有完整/紧凑变体。
- `components/ChatRoom.vue`：对话、状态、输入；flex 纵向排列，输入框在普通流中，始终可见。AI 消息带小方块标记，用户消息是浅底气泡，不使用头像。
- `views/Home.vue`：极简首页，四块内容：说明、两个入口、规则索引、三步流程。
- `views/Workspace.vue`：两个模式共用一页；`sessions.chat` 与 `sessions.agent` 各自保存消息和连接状态，切换模式不丢另一边的对话；`ChatRoom` 以 `mode` 为 key 重新挂载以重置输入。
- 旧的 `AftersalesChat.vue`、`AgentConsole.vue`、`Workbench.vue`、`NotFound.vue` 已移除。

### 7.3 对话与 SSE

- **只渲染最终答复**，智能体的中间步骤只写后端日志，不进入对话。
- `progress` 命名事件通过 `eventSource.addEventListener('progress')` 监听，驱动输入框上方的“正在处理…”状态行。
- 客服接口不发送 `[DONE]`；前端在 `onerror` 中区分“已收到内容后的正常关闭”和“未收到内容就断开”，前者回到 `disconnected`，后者才显示连接错误。
- AI 消息做轻量 Markdown 渲染：先转义 `&`、`<`、`>`，再处理 `**加粗**`、`` `行内代码` ``、`#` 小标题与 `-` / `1.` 列表；生成的标签只来自渲染器，用 `v-html` 输出。**不要直接渲染模型原始输出，也不要在转义前插入 HTML。**
- 用户消息保持纯文本插值；输入框是单行 `<input>`，Enter 发送。
- 智能体模式有气泡节流：按句末标点、双换行或长度阈值切分气泡。

### 7.4 动效

- 极简版不再做滚动入场和首屏遮罩；只保留悬停、按下、状态方块的轻微反馈。
- 状态方块与打字光标在 `prefers-reduced-motion: reduce` 下停止动画。
- 不要再引入 `data-reveal` / `IntersectionObserver` 那套隐藏式入场，避免内容默认不可见。

### 7.5 验证

改前端后至少执行：

```bash
cd ecom-frontend
npm run build
```

并用真实浏览器在 1440 与 390 宽度检查：

- 首页 H1 为 40px / 28px，首页没有问答预览；
- 工作台输入框在首屏可见；
- 点击模式切换后 URL 变为 `?mode=agent|chat`，两个会话互不覆盖；
- `/aftersales`、`/agent` 重定向正确，未知路径回首页；
- 用真实后端各发一条消息，确认客服页不出现错误条、智能体进度与列表渲染正常。

Taste Skill 仍安装在工作区 `.dsh/skills/`，但当前极简版**不以它的大字、图片数量门槛为目标**；保留它仅作为源码规则检查工具。

---

## 8. 已修复的问题与根因（重要）

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | 智能体连续 8 步"思考完成 - 无需行动"，没有回答 | `ToolCallAgent.think()` 用 `.tools(availableTools)` 传入 `ToolCallback[]`，Spring AI 要求用 `.toolCallbacks()`；异常被 `think()` 捕获后返回 false，`step()` 又替换成固定文案，循环没有结束出口 | 改为 `.toolCallbacks()`；无工具调用视为最终答复并标记 `FINISHED` |
| 2 | 循环跑到步数上限 | 循环条件只看 `FINISHED`，`ERROR` 也会继续 | 循环条件改为 `state == RUNNING`，`FINISHED` / `ERROR` 都退出 |
| 3 | 模型调用 `terminate` 时答复丢失 | `act()` 只返回工具结果 | 记录本轮助手文本，终止时优先返回文本 |
| 4 | 恰好第 8 步完成却被报"达到最大步骤" | `currentStep >= maxSteps` 判断没有区分是否已结束 | 增加 `state == RUNNING` 前置条件 |
| 5 | 界面出现 `\n`、`\"` 转义符与整段规则原文 | Spring AI 默认 `DefaultToolCallResultConverter` 会对工具返回值做 JSON 序列化，`act()` 把 `responseData()` 直接推给前端 | 完整结果只写日志，界面只保留简短状态 |
| 6 | 步骤提示混进回答 | `runStream` 把每一步结果都作为对话数据推送 | 中间步骤改用 `progress` 命名事件，只有最终答复走默认事件 |
| 7 | "正在处理…"显示在页面左上角 | `.chat-messages` 与 `.chat-input-container` 都是绝对定位，状态块是唯一普通流元素 | 改为 flex 纵向布局，状态条、错误条、输入框都在普通流中，不再依赖 `bottom` 计算 |
| 8 | `**加粗**` 原样显示 | 前端用文本插值渲染 | 轻量 Markdown 渲染（先转义再解析） |
| 9 | 智能体不依据知识库作答 | 智能体的 ChatClient 没有 RAG | 新增 `searchPolicy` 工具，系统提示强制先检索 |
| 10 | 智能体用英文回答中文问题 | 系统提示是英文且未约束语言 | 提示词改为中文并明确"使用用户的语言" |
| 11 | 流结束后前端进入 error 状态并可能重连 | 智能体接口缺少结束哨兵；客服接口本身不发送 `[DONE]` | 智能体结束前发送 `[DONE]`；客服页在已收到内容时把连接关闭视为正常收尾 |
| 12 | 文件工具可用 `../` 读写 `tmp/file` 之外的文件 | 文件名直接拼接，未规范化、未校验基准目录 | 新增 `ToolPathResolver`，规范化后必须仍位于基准目录内 |
| 13 | PDF 工具可把文件写到 `tmp/pdf` 之外 | 与 #12 同源 | 复用 `ToolPathResolver` |
| 14 | 联网搜索结果少于 5 条时整段搜索失败 | `organicResults.subList(0, 5)` 未按实际数量截取 | 改为 `min(5, size)`；空结果单独返回提示，缺失 `organic_results` 按错误处理 |
| 15 | `generatePDF` 内容为 null 时抛未封装异常 | 只捕获 `IOException`，未校验入参 | 入参校验 + 捕获 `Exception`，统一返回错误文案 |
| 16 | 文件记忆的 `chatId` 可路径穿越 | 会话 ID 未校验即拼进文件名 | 规范化后必须仍位于记忆目录内，空值/超长/越界 ID 抛 `IllegalArgumentException` |
| 17 | 智能体实例执行一次后无法再次 `run` | 状态停留在 FINISHED/ERROR，消息跨轮累积 | 新增 `startNewRun()`：仅 RUNNING 时拒绝，复位状态/步数并清空上轮消息 |
| 18 | MCP 图片检索依赖 Pexels，Key 申请受限时不可用 | 工具写死 Pexels API；Key 无法配置 | 改用 SearchAPI Google Images，`SEARCH_API_KEY` 环境变量注入 |
| 19 | 智能体声称“没有查询外部信息的权限或工具”，不调用 searchWeb | 系统提示只强调 searchPolicy，未说明第三方主体可用 searchWeb | 系统提示列出工具清单与适用场景，明确第三方快递/品牌规则用 searchWeb |

---

## 9. 已知限制与下一步

- 对话记忆在内存中（`MessageWindowChatMemory`，保留 20 条），多实例部署需改为数据库持久化。
- 接口无鉴权、无限流，公网部署前必须补。
- 智能体每一步都产生一次模型调用，没有 token 成本统计。
- 业务工具尚未接入：查订单、查物流、建工单、转人工。
- 没有 RAG 评测集。当前 `topK=4 / 阈值 0.4` 偏松，检索结果会混入无关片段，需要用评测集（召回率@k、引用正确率）定参。
- 智能体可能同时调用 `generatePDF` 与 `writeFile`，产出重复，需要在工具描述里明确边界。
- SearchAPI 已用于联网搜索与 MCP 图片检索（Google Images）；智能体是否调用 `searchWeb` 仍由模型判断，尚无稳定评测。
- 前端 Markdown 支持加粗、行内代码、小标题与列表；表格、链接、图片仍按纯文本显示。
- 极简版按设计不包含图片、视频和单独 404 页；如果后续要加素材，先重新确认信息密度与页面数量。
- 工作台初始只显示欢迎语，不预置问答示例；两个模式的会话各自保存在内存，切换不丢，刷新页面会重置。
- 客服接口仍不发送 `[DONE]`，前端按“已收到内容后关闭”处理；长期应由后端补上统一结束哨兵。

---

## 10. 开发约定

1. **每轮修改前先提交 Git**（用户明确要求）。提交信息用中文，说明做了什么。
2. 改后端后执行 `mvn -B -q compile`；改前端后执行 `npm run build`。
3. 重启后端并确认 `/api/health` 返回 `ok` 后再验证接口。
4. **不要提交** `application-local.yml`、`target/`、`node_modules/`、`tmp/`、`.idea/`。
5. 验证中文接口不要用 Git Bash 的 `curl`（会按 GBK 编码，模型收到乱码）。用 Python 的 `urllib.parse.urlencode` 或浏览器。
6. 接口路径别忘了 `/ai` 段：`/api/ai/aftersales/...`、`/api/ai/agent/chat`。
7. 保持中文业务口径与现有命名风格；新增代码放在 `com.aftersales.agent` 包下。

---

## 11. 验证方式

```bash
# 健康检查
curl http://localhost:8123/api/health

# 客服 RAG（浏览器打开即可）
http://localhost:8123/api/ai/aftersales/chat/sync?message=吊牌剪了还能退吗&chatId=test-1

# 智能体（Python，避免中文编码问题）
python - <<'PY'
import urllib.request, urllib.parse
msg = "商品吊牌已经剪掉了，还能申请退换货吗？"
url = "http://localhost:8123/api/ai/agent/chat?" + urllib.parse.urlencode({"message": msg})
print(urllib.request.urlopen(url, timeout=180).read().decode("utf-8"))
PY
```

预期：

- 客服接口返回引用知识库原文的中文答复。
- 智能体接口先收到若干 `event:progress`，随后一个 `data:<答复>`，最后 `data:[DONE]`；答复中不应出现 `Step`、`已调用工具`、`\n` 字面量或 `\"`。
- 工作台智能体模式：处理期间输入框上方显示绿色状态行 + 脉冲方块 + "正在处理…"，答复到达后消失；`**加粗**` 渲染为粗体。

---

## 12. 设计取舍与常见问题

- **为什么把 RAG 做成工具而不是 Advisor**：Advisor 会在每一步注入检索上下文，多步任务下浪费 token 且干扰判断；做成工具后由模型决定检索时机，普通寒暄不触发，同时"依据了哪些规则"会体现在调用记录里，便于排查编造。
- **智能体如何判断任务结束**：不再发起工具调用即视为最终答复；`terminate` 用于无法继续时中止；最大步数兜底。三层终止，且 `ERROR` 与 `FINISHED` 都会退出循环。
- **如何避免工具结果污染界面**：区分"给模型的数据"和"给人的数据"。框架会对工具返回值做 JSON 序列化，原始载荷只写日志，前端只接收最终答复与进度事件。
- **安全边界**：移除了终端执行、任意 URL 抓取、任意资源下载三个工具；`@Tool` 暴露的每个能力都是提示词注入的可达面，新增工具必须限制参数与可访问资源。
- **待补的短板**：没有检索评测集，参数靠经验设置；这是下一步最值得投入的地方。
