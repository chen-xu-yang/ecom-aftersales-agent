package com.aftersales.agent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.aftersales.agent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类。
 * <p>
 * think() 采用流式调用：模型增量文本通过 token 事件实时下发，同时在本地聚合
 * 文本与工具调用增量，聚合完成后交给 act() 执行工具。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用哪些工具）
    private ChatResponse toolCallChatResponse;

    // 最近一次思考的文本输出，调用终止工具时作为最终答复返回
    private String lastAssistantText;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2、流式调用模型：增量文本实时推送，同时聚合工具调用
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            StreamCollector collector = new StreamCollector();
            getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    // 这里传入的是已构建好的 ToolCallback，必须用 toolCallbacks 而不是 tools
                    .toolCallbacks(availableTools)
                    .stream()
                    .chatResponse()
                    .doOnNext(collector::accept)
                    .blockLast();

            ChatResponse chatResponse = collector.toChatResponse();
            this.toolCallChatResponse = chatResponse;

            // 3、解析聚合结果，获取要调用的工具
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 记录本轮文本，若同轮调用了终止工具，用它作为最终答复
            this.lastAssistantText = assistantMessage.getText();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            log.info(getName() + "的思考：" + assistantMessage.getText());
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            // 如果不需要调用工具，返回 false
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                return false;
            }
            // 需要调用工具时无需记录助手消息，因为调用工具时会自动记录
            return true;
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            // 标记错误状态，让执行循环退出而不是继续空转
            setState(AgentState.ERROR);
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
            // 模型可能在同一轮既给出答复又调用终止工具，答复优先返回给用户
            if (StrUtil.isNotBlank(this.lastAssistantText)) {
                return this.lastAssistantText;
            }
        }
        // 完整结果只写日志：Spring AI 默认会对工具返回值做 JSON 序列化，
        // 直接展示会带出 \n、\" 等转义符，且检索片段会刷屏
        String detail = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(detail);

        // 对外只展示调用了哪些工具，具体内容由模型的最终答复承载
        return toolResponseMessage.getResponses().stream()
                .map(response -> "已调用工具 " + response.name())
                .collect(Collectors.joining("；"));
    }

    /**
     * 流式响应聚合器：文本增量实时推送，工具调用增量按 id 合并（兼容累计式与增量式两种分片）。
     */
    private final class StreamCollector {

        private final StringBuilder text = new StringBuilder();
        private final Map<String, ToolCallBuilder> toolCalls = new LinkedHashMap<>();

        void accept(ChatResponse response) {
            if (response == null || response.getResult() == null) {
                return;
            }
            AssistantMessage output = response.getResult().getOutput();
            if (output == null) {
                return;
            }
            String delta = output.getText();
            if (StrUtil.isNotEmpty(delta)) {
                text.append(delta);
                // 实时推送 token（客户端断开时抛出，由上层统一收尾）
                emitToken(delta);
            }
            if (output.getToolCalls() != null) {
                output.getToolCalls().forEach(this::mergeToolCall);
            }
        }

        private void mergeToolCall(AssistantMessage.ToolCall call) {
            String key = StrUtil.isNotBlank(call.id())
                    ? call.id()
                    : (StrUtil.isNotBlank(call.name()) ? "name:" + call.name() : "anonymous-" + toolCalls.size());
            ToolCallBuilder builder = toolCalls.computeIfAbsent(key, ignored -> new ToolCallBuilder());
            if (StrUtil.isNotBlank(call.id())) {
                builder.id = call.id();
            }
            if (StrUtil.isNotBlank(call.name())) {
                builder.name = call.name();
            }
            String arguments = call.arguments();
            if (StrUtil.isNotEmpty(arguments)) {
                String existing = builder.arguments.toString();
                if (!existing.isEmpty() && arguments.startsWith(existing)) {
                    // 累计式分片：整体替换
                    builder.arguments.setLength(0);
                }
                builder.arguments.append(arguments);
            }
        }

        ChatResponse toChatResponse() {
            List<AssistantMessage.ToolCall> mergedCalls = toolCalls.values().stream()
                    .filter(builder -> StrUtil.isNotBlank(builder.name))
                    .map(builder -> new AssistantMessage.ToolCall(
                            StrUtil.isNotBlank(builder.id) ? builder.id : "call-" + UUID.randomUUID(),
                            "function",
                            builder.name,
                            builder.arguments.toString()))
                    .toList();
            AssistantMessage merged = new AssistantMessage(text.toString(), Map.of(), mergedCalls);
            return new ChatResponse(List.of(new Generation(merged)));
        }
    }

    /**
     * 工具调用增量构建器。
     */
    private static final class ToolCallBuilder {
        private String id = "";
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();
    }
}
