# 演示脚本（5 分钟）

> 目标：用最短路径展示「客服 / 智能体 / 流式 / 多轮 / 评测 / 测试」六个点。

## 0. 前置

```bash
# 后端（默认 local profile，使用 application-local.yml 中的 Key）
mvn package -DskipTests
java -jar target/ecom-aftersales-agent-0.0.1-SNAPSHOT.jar
# 前端
cd ecom-frontend && npm run dev
```

## 1. 客服多轮对话（30s）

浏览器打开 `http://localhost:3000/workspace`，客服模式发送：

1. `吊牌剪掉了还能退货吗？` → 回答引用规则并说明质量问题例外；
2. `那如果是质量问题呢？` → 利用多轮记忆回答。

## 2. 智能体流式（60s）

切到「处理智能体」，发送：

> 我上周买的商品到货后发现外包装破损、有划痕，帮我梳理处理方案并生成处理说明 PDF

观察：
- 文本逐字出现（token 事件）；
- 工具调用期间显示「正在处理…」（progress）；
- 结束后自动收起（`[DONE]`）。

命令行等价验证：

```bash
curl -N "http://localhost:8123/api/ai/agent/chat?chatId=demo1&message=吊牌剪了还能退吗"
# 预期：多个 event:token + event:progress + data:[DONE]
```

## 3. 多轮记忆（30s）

同一个 `chatId` 连续两次请求：

```bash
curl -N "http://localhost:8123/api/ai/agent/chat?chatId=demo2&message=订单号是20250815001，帮我查发货"
curl -N "http://localhost:8123/api/ai/agent/chat?chatId=demo2&message=我刚才给你的订单号是多少？"
# 第二轮回答应包含 20250815001
```

## 4. 测试与评测（90s）

```bash
mvn -o test                                    # 123 run / 9 skipped
cd ecom-frontend && node tests/run.mjs         # 29 passed
RUN_RETRIEVAL_EVAL=true mvn -o test -Dtest=RetrievalEvalTest   # 输出 target/eval/retrieval-report.md
```

展示 `docs/eval/retrieval-report.md`：hit@4 100%、MRR 0.931、转人工空检索 80%。

## 5. 代码走读（90s）

按顺序打开：

1. `src/main/java/com/aftersales/agent/agent/BaseAgent.java`：`runStream` 的 token/progress/[DONE] 契约、专用线程池；
2. `ToolCallAgent.java`：`StreamCollector` 如何聚合文本 + 工具调用分片；
3. `AgentChatSessionManager.java`：chatId 实例池与 LRU；
4. `ToolPathResolver.java`：`toRealPath` 越界校验；
5. `docs/changes/2026-11-12-改造记录.md`：改动清单与已知问题。

## 6. 已知问题（主动说明）

- MCP 服务端可独立启动，但主工程作为 MCP 客户端接入时初始化超时（Spring AI 1.0.0 / MCP 0.10.0），
  已保留配置与文档待升级验证；
- 1 条转人工问题仍会被召回，已在评测报告标注。
