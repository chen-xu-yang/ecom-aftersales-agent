# 测试说明

> 项目测试以「离线、可重复」为原则：单元/契约测试全部使用 Mockito 与 MockMvc，不调用真实模型、不联网。
> 最新结果：后端 **123 个用例（9 条真实模型用例默认跳过）**，前端 **29 条逻辑测试**。

## 1. 运行方式

```bash
# 后端（推荐离线运行，依赖本地 Maven 仓库）
mvn -o test

# 前端逻辑测试（Node 原生脚本，无额外依赖）
cd ecom-frontend && node tests/run.mjs

# 需要真实模型的集成测试（会调用 DashScope，默认跳过）
RUN_REAL_AI_TESTS=true mvn -Dtest=AftersalesAgentTest test

# 检索评测（需要 Key，输出 target/eval/retrieval-report.md）
RUN_RETRIEVAL_EVAL=true mvn -o test -Dtest=RetrievalEvalTest
```

`mvn -o test` 的 surefire 报告在 `target/surefire-reports/`，评测报告归档在 `docs/eval/`。

## 2. 覆盖范围

| 层次 | 内容 | 代表用例 |
|---|---|---|
| 智能体核心 | 状态机、终止语义、最大步数、异常兜底、流式 SSE 协议、think/act 与工具消息 | `BaseAgentLifecycleTest`、`ReActAgentStepTest`、`ToolCallAgentTest`、`AgentStreamProtocolTest` |
| 智能体会话 | 按 chatId 复用实例、LRU 淘汰、跨轮历史保留 | `AgentChatSessionManagerTest` |
| 工具与安全 | 文件读写、路径穿越/绝对路径/超大内容拒绝、PDF、联网搜索、规则检索、工具注册 | `FileOperationToolTest`、`FileOperationToolSpecTest`、`ToolRegistrationTest`、`AftersalesPolicyToolTest` |
| RAG 与知识库 | 17 个片段的 topic 元数据、切分、查询改写、空检索兜底 | `AftersalesDocumentLoaderTest`、`AftersalesDocumentLoaderSpecTest`、`RagSupportTest` |
| 检索评测 | 40 条评测集结构与关键句校验 | `EvalDatasetTest` |
| 接口 | 同步/三种 SSE/智能体接口、[DONE] 收尾、错误分支 | `AiControllerTest` |
| 配置 | CORS 来源白名单、健康检查、应用配置契约 | `CorsConfigTest`、`HealthControllerTest`、`ApplicationConfigTest` |
| 前端 | SSE URL 拼接与 [DONE]、Markdown 渲染转义、路由、对话组件契约 | `ecom-frontend/tests/*.test.mjs` |

## 3. 历史缺陷回归

2026-10-18 修复的 6 个缺陷均有对应用例（路径穿越 ×2、搜索不足 5 条、PDF 空内容、文件记忆会话 ID、
智能体实例不可复用）；智能体执行循环与终止语义问题由 `BaseAgentLifecycleTest`、`ReActAgentStepTest`、
`AgentStreamProtocolTest` 覆盖。缺陷明细见 [2026-10-18 修改记录](./../changes/2026-10-18-代码修改记录.md)。

## 4. 检索评测（2026-11-12 新增）

- 评测集：`src/test/resources/eval/aftersales_eval.jsonl`（40 条：35 可回答 + 5 转人工）；
- 指标：hit@1/3/4/5/8、MRR@8、阈值敏感度、分类别命中率、转人工空检索率；
- 当前结果：hit@4 = 100%（35/35），MRR@8 = 0.9310，转人工空检索率 80%；
- 说明见 [`docs/eval/README.md`](../eval/README.md)，报告见 [`docs/eval/retrieval-report.md`](../eval/retrieval-report.md)。

## 5. 已知局限

- 真实模型对话质量（是否引用规则、是否编造）未做自动化评估，需要人工抽检或引入引用命中率指标；
- 浏览器端到端（布局、动效）依赖人工点检，未引入 Playwright 等驱动；
- 评测只覆盖检索召回，不覆盖答案忠实度。
