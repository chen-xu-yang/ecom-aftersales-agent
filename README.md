# 电商售后智能客服平台（ecom-aftersales-agent）

面向电商售后场景的 AI 应用，由两部分组成：

- **售后客服**：面向用户的多轮对话入口，基于售后知识库回答问题，并支持工具调用与 MCP 服务调用。
- **售后处理智能体**：基于 ReAct 模式自主规划，按售后规则拆解任务、分步调用工具，产出处理记录与说明文档。

项目解决的核心问题是：售后咨询中大量重复问题（退换货政策、物流停滞、价保、发票）可以被知识库和规则覆盖，而跨步骤的任务（核查问题 → 判定责任 → 生成处理单）可以交给智能体按流程执行。

## 功能

**售后客服**

- 多轮对话，支持对话记忆（消息窗口，保留最近 20 条）
- 基于向量数据库的知识库问答，回答附带政策依据
- 查询改写，把口语化提问改写成便于检索的查询
- 结构化输出：把一次咨询的处理结果解析为 Java 对象（处理建议列表）
- 调用工具：文件读写、联网搜索、PDF 生成
- 调用 MCP 服务：接入外部能力，无需改动主工程

**售后处理智能体**

- 四层继承结构（`BaseAgent` → `ReActAgent` → `ToolCallAgent` → `AftersalesAgent`），实现"思考—行动"循环
- 关闭框架内置的工具执行循环，自行维护消息上下文，便于插入业务逻辑与终止判断
- 通过结构化函数调用解析工具请求，配合最大步骤限制与 `terminate` 工具控制终止
- 按需检索售后知识库：把 RAG 检索封装成工具，涉及政策细节时先检索规则原文再作答
- 通过服务器发送事件（Server-Sent Events, SSE）推送最终答复，中间步骤状态只写日志

**独立 MCP 服务端**

- 单独模块 `ecom-mcp-server`，以 MCP 服务端形式对外提供图片检索能力（用于售后凭证、商品参考图）
- 同时支持标准输入输出（stdio）与 SSE 两种传输方式，可被本工程或其他 MCP 客户端挂载

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言与框架 | Java 21、Spring Boot 3.4.4 |
| AI 开发框架 | Spring AI 1.0.0、Spring AI Alibaba 1.0.0.2 |
| 大模型 | 通义千问 `qwen-plus`（DashScope），向量模型用于文档向量化 |
| 检索增强生成（Retrieval-Augmented Generation, RAG） | `MarkdownDocumentReader`、`KeywordMetadataEnricher`、`TokenTextSplitter`、`QuestionAnswerAdvisor`、`RetrievalAugmentationAdvisor` |
| 向量数据库 | 内存向量存储（默认）、PGVector（可选，HNSW 索引 + 余弦距离） |
| 工具调用 | Spring AI `@Tool` 注解 + `ToolCallingManager` |
| 协议 | 模型上下文协议（Model Context Protocol, MCP），客户端与服务端双向 |
| 流式输出 | SSE（`Flux`、`ServerSentEvent`、`SseEmitter` 三种写法） |
| 工具库 | Hutool、Kryo、iText、Knife4j |
| 前端 | Vue 3、Vite、Vue Router、EventSource |
| 部署 | Docker、Nginx |

## 架构

```
                        ┌──────────────────────────────┐
   浏览器 ── SSE ──────▶ │  AiController                │
                        │  /ai/aftersales/chat/*        │
                        │  /ai/agent/chat               │
                        └───────┬──────────────┬───────┘
                                │              │
                 ┌──────────────▼───┐   ┌──────▼──────────────┐
                 │  AftersalesApp   │   │  AftersalesAgent    │
                 │  售后客服应用     │   │  售后处理智能体      │
                 └────────┬─────────┘   └──────┬──────────────┘
                          │                    │
      ┌───────────────────┼──────────┐         │ ToolCallAgent
      │                   │          │         │  ├─ BaseAgent   状态与步骤循环
 ┌────▼─────┐   ┌─────────▼───┐  ┌───▼──────┐  │  ├─ ReActAgent  think / act
 │ Advisor  │   │  RAG 链路    │  │ 工具回调  │  │  └─ ToolCallingManager 执行工具
 │ 日志      │   │  文档 → 切片  │  │          │  │
 │ 对话记忆  │   │  → 向量化    │  │          │  └──▶ ToolCallback[]
 │ 知识库问答│   │  → 检索      │  │          │        ├─ FileOperationTool
 └──────────┘   │  → 上下文增强 │  │          │        ├─ WebSearchTool
                └──────┬───────┘  └───┬──────┘        ├─ PDFGenerationTool
                       │              │                ├─ TerminateTool
                       │              │                └─ AftersalesPolicyTool
                ┌──────▼──────────────▼──────┐
                │  VectorStore               │
                │  SimpleVectorStore（默认）   │
                │  PgVectorStore（可选）       │
                └────────────────────────────┘
                          ▲
                          │ 知识库文档
              src/main/resources/document/*.md

   外部能力：MCP 服务端 ecom-mcp-server（stdio / SSE）
             DashScope API、SearchAPI
```

## 目录结构

```
ecom-aftersales-agent/
├── src/main/java/com/aftersales/agent/
│   ├── AftersalesAgentApplication.java     # 启动类
│   ├── advisor/                            # 自定义 Advisor（日志、复读增强）
│   ├── agent/                              # 智能体分层实现
│   │   ├── BaseAgent.java                  # 状态与执行循环
│   │   ├── ReActAgent.java                 # 思考—行动抽象
│   │   ├── ToolCallAgent.java              # 工具调用实现
│   │   ├── AftersalesAgent.java            # 售后处理智能体
│   │   └── model/AgentState.java
│   ├── app/AftersalesApp.java              # 售后客服应用
│   ├── chatmemory/FileBasedChatMemory.java # 基于文件的对话记忆（可选）
│   ├── config/CorsConfig.java              # 全局跨域配置
│   ├── constant/                           # 常量
│   ├── controller/                         # 聊天与健康检查接口
│   ├── rag/                                # RAG 链路：文档加载、向量存储、检索增强
│   └── tools/                              # 工具定义与集中注册
├── src/main/resources/
│   ├── application.yml                     # 主配置（含占位密钥）
│   ├── document/                           # 售后知识库 Markdown 文档
│   └── mcp-servers.json                    # MCP stdio 客户端配置
├── ecom-frontend/                          # Vue 3 前端（含 Dockerfile 与 nginx.conf）
├── ecom-mcp-server/                        # 独立 MCP 服务端模块
└── Dockerfile                              # 后端镜像构建
```

## 快速开始

### 1. 环境要求

- JDK 21
- Maven 3.9+
- Node.js 16+（仅前端需要）

### 2. 申请密钥

| 密钥 | 用途 | 是否必需 |
|---|---|---|
| DashScope API Key（阿里云百炼） | 对话模型与向量模型 | 必需 |
| SearchAPI Key | `WebSearchTool` 联网搜索、`ecom-mcp-server` 图片检索（Google Images） | 使用对应功能时必需 |

### 3. 配置密钥

`application.yml` 中只保留占位值，真实密钥写在 `application-local.yml`（已被 `.gitignore` 忽略，不会提交）。仓库提供了模板：

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

然后填入：

```yaml
# src/main/resources/application-local.yml
spring:
  ai:
    dashscope:
      api-key: sk-你的密钥

search-api:
  api-key: 你的 SearchAPI 密钥
```

### 4. 启动后端

```bash
mvn spring-boot:run
```

> 项目没有提交 `maven-wrapper.jar`，请使用本机 Maven（`mvn`），不要依赖 `./mvnw`。

启动后：

- 健康检查：`http://localhost:8123/api/health` 返回 `ok`
- 接口文档（Knife4j）：`http://localhost:8123/api/doc.html`

> 日志编码：控制台默认跟随 JVM 的 `stdout.encoding`（中文 Windows 通常是 GBK，与终端一致），
> 文件日志固定 UTF-8。如果 IDE 或终端使用 UTF-8，请设置环境变量 `LOG_CHARSET=UTF-8`
> （或在 VM options 中加 `-Dstdout.encoding=UTF-8`）；Windows 终端也可先执行 `chcp 65001`。

首次启动会加载 `resources/document` 下的知识库文档并写入向量存储，需要调用一次向量模型，因此启动耗时与文档量相关。

### 5. 启动前端（可选）

```bash
cd ecom-frontend
npm install
npm run dev
```

访问 `http://localhost:3000`。

### 6. 启动 MCP 服务端（可选）

图片检索使用 SearchAPI 的 Google Images 引擎，与联网搜索共用同一把 SearchAPI Key，通过 `SEARCH_API_KEY` 环境变量注入：

```bash
# macOS / Linux
export SEARCH_API_KEY=你的SearchAPI密钥
cd ecom-mcp-server
mvn spring-boot:run          # 以 SSE 方式运行，端口 8127
```

```powershell
# Windows PowerShell
$env:SEARCH_API_KEY = "你的SearchAPI密钥"
cd ecom-mcp-server
mvn spring-boot:run
```

启用 MCP 客户端需要把 `application.yml` 中注释掉的 `spring.ai.mcp.client` 配置恢复，并按 `mcp-servers.json` 的描述配置 stdio 方式的服务端。

## 接口

服务端口 `8123`，统一前缀 `/api`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| GET | `/api/ai/aftersales/chat/sync` | 售后客服同步对话 |
| GET | `/api/ai/aftersales/chat/sse` | 售后客服流式对话（返回 `Flux<String>`） |
| GET | `/api/ai/aftersales/chat/server_sent_event` | 售后客服流式对话（返回 `Flux<ServerSentEvent>`） |
| GET | `/api/ai/aftersales/chat/sse_emitter` | 售后客服流式对话（返回 `SseEmitter`） |
| GET | `/api/ai/agent/chat` | 售后处理智能体流式对话，推送最终答复；处理期间发送 `progress` 事件 |

三种流式写法分别对应不同的返回类型，`SseEmitter` 方式配合 `CompletableFuture` 在独立线程中执行，避免阻塞请求线程。

知识库问答、工具调用、MCP 调用三类能力由 `AftersalesApp` 提供（`doChatWithRag`、`doChatWithTools`、`doChatWithMcp`），当前通过 `src/test` 下的测试类调用验证。

## 关键设计说明

**RAG 链路**

知识库文档存放在 `resources/document` 下，文件名约定为「主题 - 分类.md」，分类（如 `退换货`、`物流`）会写入文档元数据，检索时可以按分类做元数据过滤。文档按 `---` 水平线切分为独立片段，经关键词增强后写入向量存储。检索阶段使用 `VectorStoreDocumentRetriever` 组合相似度阈值与 topK，并配置上下文查询增强器，在检索结果为空时返回兜底话术，而不是让模型自由生成。

**智能体分层**

`BaseAgent` 负责状态机与步骤循环，`ReActAgent` 抽象出 `think` 与 `act`，`ToolCallAgent` 基于 `ToolCallingManager` 实现工具执行。这里关闭了 Spring AI 内置的工具执行循环（`withInternalToolExecutionEnabled(false)`），由智能体自行维护消息上下文，目的是把"是否继续""是否终止"的控制权留给业务代码。

终止条件分三层：模型不再发起工具调用，即视为已给出最终答复并结束本轮；`terminate` 工具用于无法继续时主动中止；最大步数作为兜底，避免异常情况下无限循环。执行状态同样区分 `FINISHED` 与 `ERROR`，两类状态都会退出循环，不会被当作"继续执行"。

**工具与安全边界**

当前保留的工具为文件读写、联网搜索、PDF 生成、任务终止与售后规则检索。原型的终端执行、任意 URL 抓取、任意资源下载三个工具已被移除：`@Tool` 暴露的每一个能力都是提示词注入的可达面，终端执行等价于把命令执行权限交给模型。后续新增工具（查订单、查物流、建工单、转人工）时，同样需要限制参数取值范围与可访问资源。

售后规则检索被做成工具而不是常驻 Advisor：由模型自主决定何时检索，政策类问题在系统提示中被强制要求先检索，普通寒暄则不必触发向量检索。这样既减少了无效的检索调用，也让"这一轮回答依据了哪些规则"直接体现在步骤日志里，便于排查模型编造。

**步骤展示与数据分离**

智能体的中间步骤状态只写日志，SSE 只推送最终答复；工具返回的原始数据同样只写日志。处理期间通过命名事件 `progress` 推送"正在处理…"，前端只更新状态提示，不写入对话内容。Spring AI 默认会对工具返回值做 JSON 序列化，如果把 `responseData()` 直接推给前端，界面会出现 `\n`、`\"` 这类转义符；检索类工具还会把整段规则原文刷在对话里。工具结果是给模型的数据，最终答复才是给人的内容。

**流式与线程**

`SseEmitter` 方式的智能体调用在 `CompletableFuture` 中异步执行，并设置了超时回调与完成回调，用于在连接中断时清理状态。生产环境建议替换为独立的线程池，避免占用公共线程池。

## 部署

**后端**：`Dockerfile` 使用 `maven:3.9-amazoncorretto-21` 镜像，复制源码后在镜像内执行 `mvn clean package -DskipTests`，以 `prod` profile 启动。

```bash
docker build -t ecom-aftersales-agent:latest .
docker run -d -p 8123:8123 --name aftersales-agent ecom-aftersales-agent:latest
```

**前端**：`ecom-frontend/Dockerfile` 分两阶段构建，Node 阶段执行 `npm run build`，Nginx 阶段托管 `dist`。`nginx.conf` 已针对 SSE 关闭反向代理缓冲并延长读取超时，否则流式输出会被缓冲成一次性返回。

```bash
cd ecom-frontend
docker build -t ecom-aftersales-web:latest .
docker run -d -p 80:80 --name aftersales-web ecom-aftersales-web:latest
```

## 可选：切换 PgVector 向量数据库

默认使用内存向量存储，进程重启后向量数据不保留。需要持久化或做大规模检索时切换到 PgVector，需要同时修改三处：

1. `PgVectorVectorStoreConfig` 上的 `@Configuration` 取消注释
2. `application.yml` 中的数据源与 `vectorstore.pgvector` 配置取消注释并填写连接信息
3. `AftersalesAgentApplication` 中移除 `DataSourceAutoConfiguration.class` 排除项

## 已知限制与后续计划

- 对话记忆默认存在内存中，多实例部署需要切换到数据库持久化
- 接口未做鉴权与限流，公网部署前需要补充
- 智能体每一步都会产生一次模型调用，缺少 token 成本统计
- 工具集中的业务工具（查订单、查物流、建工单、转人工）待接入
- 缺少检索效果评测集，检索参数（相似度阈值、topK）尚未做定量对比

## 开发文档

- [AI_CONTEXT.md](./docs/AI_CONTEXT.md)：项目上下文、关键设计、已修复问题与开发约定
