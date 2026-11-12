# 电商售后智能客服平台（ecom-aftersales-agent）

> 基于开源 ReAct Agent 骨架**二次开发**的电商售后 AI 应用：客服多轮对话 + 售后处理智能体（工具调用 / RAG / SSE 流式）+ 独立 MCP 服务端。
> 本仓库在教程骨架基础上完成了业务改造、真流式、多轮会话、检索评测与工程加固，改造记录见 [`docs/changes/2026-11-12-改造记录.md`](docs/changes/2026-11-12-改造记录.md)。

## 本次改造做了什么（2026-11-12）

| 改造项 | 说明 | 证据 |
|---|---|---|
| 测试可复现 | 固定 surefire、旧真实 API 测试改为环境变量开关，`mvn -o test` 离线全绿 | 123 run / 9 skipped |
| 检索评测闭环 | 40 条售后问答评测集 + 真实检索评测（hit@k、MRR、阈值扫描） | `docs/eval/`、recall@4 = 100% |
| 真流式 | `stream().chatResponse()` + 本地聚合，token 级 SSE + `[DONE]` 统一收尾 | 实测 46 个 token 事件 |
| 多轮会话 | 按 `chatId` 复用智能体实例（LRU + 空闲过期），保留上下文 | 两轮对话实测引用订单号 |
| 安全加固 | 工具路径 `realpath` 校验 + 读写 1 MB 限制、CORS 来源收敛、日志截断、密钥环境变量 | 单测覆盖越界/绝对路径/超大内容 |
| 工程化 | 专用流式线程池、超时取消、MCP profile、生产配置模板、评测/演示文档 | 见下文 |

## 功能

**售后客服（AftersalesApp）**
- 多轮对话（`MessageWindowChatMemory`，保留最近 20 条），按 `chatId` 隔离会话
- 知识库问答、查询改写、结构化输出（售后处理报告）、工具调用、MCP 工具调用

**售后处理智能体（AftersalesAgent）**
- 四层继承：`BaseAgent → ReActAgent → ToolCallAgent → AftersalesAgent`
- 关闭 Spring AI 内置工具执行，自行维护 think/act 与消息上下文
- 6 个工具：规则检索、联网搜索、文件读写、PDF 生成、任务终止
- 三层终止：模型不再调用工具 / `doTerminate` / `maxSteps=8`
- SSE 流式：`progress`（步骤状态）+ `token`（增量文本）+ `[DONE]`（结束哨兵）

**独立 MCP 服务端（ecom-mcp-server）**
- 图片检索工具，支持 SSE（8127）与 stdio 两种传输

## 架构

```
浏览器 ──SSE──▶ AiController
                 ├─ /ai/aftersales/chat/{sync|sse|server_sent_event|sse_emitter}
                 │     └─ AftersalesApp：ChatClient + 记忆/日志 Advisor + RAG + 工具 + MCP
                 └─ /ai/agent/chat?chatId=xxx
                       └─ AftersalesAgent（ReAct）──▶ ToolCallingManager
                             ├─ searchPolicy（RAG 检索工具，topK=4 / 0.4）
                             ├─ searchWeb / readFile / writeFile / generatePDF / doTerminate
                             └─ SSE：progress / token / [DONE]
知识库：src/main/resources/document/*.md ──▶ Markdown 读取 ──▶ 关键词增强 ──▶ SimpleVectorStore(PGVector 可选)
评测：eval/aftersales_eval.jsonl ──▶ RetrievalEvalTest ──▶ docs/eval/retrieval-report.md
```

## 快速开始

### 1. 后端

```bash
# 1) 准备密钥（不要提交到仓库）
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# 编辑 application-local.yml 填入 DASHSCOPE_API_KEY 与可选 SEARCH_API_KEY

# 2) 启动（默认 profile=local，内存向量库）
mvn spring-boot:run
# 或
mvn package -DskipTests && java -jar target/ecom-aftersales-agent-0.0.1-SNAPSHOT.jar
```

### 2. 前端

```bash
cd ecom-frontend
npm install
npm run dev        # http://localhost:3000
```

### 3. 独立 MCP 服务端（可选）

```bash
cd ecom-mcp-server
mvn package -DskipTests
# SSE 模式（端口 8127）
java -jar target/ecom-mcp-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=sse
# 主工程按需启用 MCP 客户端：
# java -jar target/ecom-aftersales-agent-0.0.1-SNAPSHOT.jar --spring.profiles.active=local,mcp
```

## 配置

密钥全部走环境变量或本地文件（`application-local.yml` 已被 `.gitignore` 忽略）：

| 变量 | 说明 |
|---|---|
| `DASHSCOPE_API_KEY` | 通义千问对话与向量化（必需） |
| `SEARCH_API_KEY` | 联网搜索与 MCP 图片检索（可选） |
| `app.cors.allowed-origin-patterns` | 允许的跨域来源，默认本地 3000 端口 |
| `spring.ai.dashscope.chat.options.model` | 默认 `qwen-plus` |

## 接口

| 接口 | 说明 |
|---|---|
| `GET /api/ai/aftersales/chat/sync` | 同步客服对话 |
| `GET /api/ai/aftersales/chat/sse` | 客服 SSE（Flux） |
| `GET /api/ai/aftersales/chat/server_sent_event` | 客服 SSE（`ServerSentEvent`） |
| `GET /api/ai/aftersales/chat/sse_emitter` | 客服 SSE（`SseEmitter`） |
| `GET /api/ai/agent/chat?message=&chatId=` | 售后处理智能体（SSE；同 chatId 多轮） |
| `GET /api/ai/aftersales/chat/mcp` | 客服对话 + MCP 工具（需 mcp profile） |
| `GET /api/health` | 健康检查 |

### SSE 协议

```
event:progress
data:正在处理…

event:token
data:根据

event:token
data:平台

data:[DONE]
```

- `token`：模型增量文本，前端逐字追加；
- `progress`：工具调用/步骤切换，作为前端气泡边界；
- `[DONE]`：**任何分支**（正常结束、步数上限、异常、超时）都会下发一次，前端据此关闭连接；
- 非流式子类仍兼容整段 `data:` 下发。

## RAG 检索评测

40 条评测集（35 条可回答 + 5 条应转人工），覆盖 3 份规则文档、14 个规则小节：

| 指标 | 结果 |
|---|---:|
| hit@1 / hit@3 / hit@4 | 88.6% / 97.1% / **100%** |
| MRR@8 | **0.9310** |
| 转人工空检索率 | 80% |

```bash
mvn -o test -Dtest=EvalDatasetTest                                  # 离线自检（无需 Key）
RUN_RETRIEVAL_EVAL=true mvn -o test -Dtest=RetrievalEvalTest       # 真实评测
```

说明与局限见 [`docs/eval/README.md`](docs/eval/README.md)：阈值 0.45 起开始丢召回，0.5 掉到 91.4%，
当前 `topK=4 / threshold=0.4` 已是最优区间；1 条转人工问题（快递员投诉）仍会被召回。

## 测试

```bash
mvn -o test                       # 123 run / 0 fail / 9 skipped（全 mock，不联网）
cd ecom-frontend && node tests/run.mjs   # 29 passed
```

需要真实模型的集成测试默认跳过，显式开启：`RUN_REAL_AI_TESTS=true mvn -Dtest=AftersalesAgentTest test`。

## 部署

```bash
# 后端镜像（构建时跳过测试，运行时通过环境变量注入密钥）
docker build -t ecom-aftersales-agent .
docker run -p 8123:8123 -e DASHSCOPE_API_KEY=xxx ecom-aftersales-agent

# 前端镜像
cd ecom-frontend && docker build -t ecom-aftersales-web .
```

Nginx 已配置 SSE：`proxy_buffering off`、`proxy_read_timeout 600s`（见 `ecom-frontend/nginx.conf`）。

## 已知局限

1. **MCP 客户端接入为实验性**：MCP 服务端可独立启动并注册工具（SSE/stdio 均验证启动），
   但主工程 Spring AI 1.0.0 / MCP 0.10.0 的 SSE 客户端初始化 20s 超时，待升级或换传输方式解决；
   `doChatWithMcp` 暂无路由保障，默认关闭。
2. 无鉴权与限流，仅适合本地/内网演示；生产需加 API Key 校验与限流。
3. 会话与向量库为单机内存实现，重启丢失；多实例部署需外部存储（PGVector/Redis）。
4. 检索评测只覆盖召回，未覆盖“答案忠实引用原文”的自动评测。
5. 客服侧 RAG 以「检索工具」形式在智能体链路生效，客服 `doChatByStream` 本身不挂 RAG Advisor。

## 二次开发说明与致谢

本项目骨架来自开源 ReAct Agent 教程（BaseAgent/ReActAgent/ToolCallAgent 分层、Advisor、ChatMemory、MCP 等设计），
本次在其上完成业务化改造与工程加固，具体改动与原因见 [`docs/changes/2026-11-12-改造记录.md`](docs/changes/2026-11-12-改造记录.md)。

开发说明：部分样板代码与文档初稿借助 AI 编程助手完成，方案设计、代码审查与验证由作者负责。
