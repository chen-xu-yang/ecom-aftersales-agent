package com.aftersales.agent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.aftersales.agent.agent.model.AgentState;
import com.aftersales.agent.tools.TerminateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ToolCallAgent 的 think / act 测试。
 * <p>
 * 用 Mockito 模拟 ChatModel 的流式接口（think 已改为 stream + 本地聚合），不产生真实模型调用；覆盖：
 * - 有/无工具调用时的状态与消息上下文（PROJECT_CONTEXT §8 #1）
 * - 流式分片文本与工具调用的聚合
 * - 模型异常时置 ERROR（不得空转）
 * - 终止工具优先返回助手文本（PROJECT_CONTEXT §8 #3）
 * - 传给模型的 Prompt 是否自带工具与“禁用内置工具执行”选项
 */
@DisplayName("ToolCallAgent 思考与行动")
class ToolCallAgentTest {

    /**
     * 测试用业务工具：回显文本。
     */
    static class EchoTool {
        @Tool(description = "Echo the given text")
        public String echo(@ToolParam(description = "text to echo") String text) {
            return "echo:" + text;
        }
    }

    private ChatModel chatModel;
    private EchoTool echoTool;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        echoTool = new EchoTool();
    }

    private ChatResponse response(String text, List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage message = new AssistantMessage(text, Map.of(), toolCalls);
        return new ChatResponse(List.of(new Generation(message)));
    }

    private void stubStream(ChatResponse... responses) {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(responses));
    }

    private static AssistantMessage.ToolCall toolCall(String name, String arguments) {
        return new AssistantMessage.ToolCall("call-" + name, "function", name, arguments);
    }

    private ToolCallAgent newAgent(ToolCallback[] tools) {
        ToolCallAgent agent = new ToolCallAgent(tools);
        agent.setName("testToolCallAgent");
        agent.setSystemPrompt("你是售后处理智能体");
        agent.setNextStepPrompt("继续处理");
        agent.setChatClient(ChatClient.builder(chatModel).build());
        return agent;
    }

    @Test
    @DisplayName("模型返回工具调用：think 返回 true 且不重复写入助手消息")
    void thinkWithToolCallsReturnsTrue() {
        stubStream(response("我先检索规则", List.of(toolCall("echo", "{\"text\":\"你好\"}"))));
        ToolCallAgent agent = newAgent(ToolCallbacks.from(echoTool));

        boolean shouldAct = agent.think();

        assertTrue(shouldAct, "有工具调用时应进入 act");
        assertEquals("我先检索规则", agent.getLastAssistantText());
        assertEquals(1, agent.getMessageList().size(), "工具调用消息由 ToolCallingManager 落库，此处不应重复添加");
        assertInstanceOf(UserMessage.class, agent.getMessageList().get(0));
        assertNotNull(agent.getToolCallChatResponse());
    }

    @Test
    @DisplayName("流式分片聚合：文本与工具调用参数跨分片合并为完整响应")
    void thinkAggregatesStreamingChunks() {
        AssistantMessage.ToolCall first = new AssistantMessage.ToolCall(
                "call-echo", "function", "echo", "{\"text\":");
        AssistantMessage.ToolCall second = new AssistantMessage.ToolCall(
                "call-echo", "function", "", "\"你好\"}");
        stubStream(
                response("我先", List.of(first)),
                response("检索规则", List.of(second)));

        ToolCallAgent agent = newAgent(ToolCallbacks.from(echoTool));
        assertTrue(agent.think());

        ChatResponse aggregated = agent.getToolCallChatResponse();
        assertEquals("我先检索规则", aggregated.getResult().getOutput().getText(),
                "分片文本应被拼接为完整文本");
        assertEquals(1, aggregated.getResult().getOutput().getToolCalls().size(),
                "同一 id 的工具调用应合并为一条");
        assertEquals("{\"text\":\"你好\"}",
                aggregated.getResult().getOutput().getToolCalls().get(0).arguments(),
                "跨分片参数应被合并为合法 JSON");
    }

    @Test
    @DisplayName("模型不返回工具调用：think 返回 false 并保存助手消息")
    void thinkWithoutToolCallsReturnsFalse() {
        stubStream(response("直接答复用户", List.of()));
        ToolCallAgent agent = newAgent(ToolCallbacks.from(echoTool));

        boolean shouldAct = agent.think();

        assertFalse(shouldAct);
        assertEquals(2, agent.getMessageList().size(), "应包含 nextStepPrompt 与助手消息");
        Message last = agent.getMessageList().get(1);
        assertInstanceOf(AssistantMessage.class, last);
        assertEquals("直接答复用户", ((AssistantMessage) last).getText());
    }

    @Test
    @DisplayName("模型调用异常：置 ERROR，step 返回错误文案且不继续")
    void thinkFailureMarksError() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new IllegalStateException("模型不可用")));
        ToolCallAgent agent = newAgent(ToolCallbacks.from(echoTool));

        String stepResult = agent.step();

        assertEquals(AgentState.ERROR, agent.getState(), "模型异常应置 ERROR 并退出循环");
        assertTrue(stepResult.contains("处理时遇到了错误"), "应返回错误文案，实际：" + stepResult);
        assertTrue(stepResult.contains("模型不可用"));
    }

    @Test
    @DisplayName("act 执行普通工具：返回“已调用工具”，并把工具结果写回上下文")
    void actExecutesToolAndRecordsContext() {
        stubStream(response("调用回显工具", List.of(toolCall("echo", "{\"text\":\"你好\"}"))));
        ToolCallAgent agent = newAgent(ToolCallbacks.from(echoTool));
        assertTrue(agent.think());

        String actResult = agent.act();

        assertEquals("已调用工具 echo", actResult);
        Message last = agent.getMessageList().get(agent.getMessageList().size() - 1);
        assertInstanceOf(ToolResponseMessage.class, last);
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) last;
        assertEquals("echo", toolResponseMessage.getResponses().get(0).name());
        assertTrue(toolResponseMessage.getResponses().get(0).responseData().contains("echo:你好"),
                "工具真实返回值应写回上下文，实际：" + toolResponseMessage.getResponses().get(0).responseData());
        assertEquals(AgentState.IDLE, agent.getState(), "普通工具调用不结束任务");
    }

    @Test
    @DisplayName("调用 terminate：状态置 FINISHED 且优先返回本轮助手文本（回归 #3）")
    void actTerminateReturnsAssistantText() {
        stubStream(response("这个问题超出范围，建议转人工", List.of(toolCall("doTerminate", "{}"))));
        ToolCallAgent agent = newAgent(ToolCallbacks.from(new TerminateTool(), echoTool));
        assertTrue(agent.think());

        String actResult = agent.act();

        assertEquals(AgentState.FINISHED, agent.getState(), "终止工具应结束任务");
        assertEquals("这个问题超出范围，建议转人工", actResult, "答复不应被工具返回值覆盖");
    }

    @Test
    @DisplayName("没有工具调用时 act 返回固定提示")
    void actWithoutToolCalls() {
        ToolCallAgent agent = newAgent(ToolCallbacks.from(echoTool));
        agent.setToolCallChatResponse(response("只是文本", List.of()));

        assertEquals("没有工具需要调用", agent.act());
    }

    @Test
    @DisplayName("传给模型的 Prompt 应携带工具定义、单一系统消息，并禁用内置工具执行")
    void promptCarriesToolsAndDisablesInternalExecution() {
        stubStream(response("检索", List.of(toolCall("echo", "{\"text\":\"你好\"}"))));
        ToolCallAgent agent = newAgent(ToolCallbacks.from(echoTool));

        agent.think();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        Prompt sentPrompt = captor.getValue();

        long systemCount = sentPrompt.getInstructions().stream()
                .filter(SystemMessage.class::isInstance)
                .count();
        assertEquals(1, systemCount, "系统提示词应只注入一次");

        assertInstanceOf(DashScopeChatOptions.class, sentPrompt.getOptions(),
                "智能体依赖 DashScopeChatOptions 关闭内置工具执行循环");
        DashScopeChatOptions options = (DashScopeChatOptions) sentPrompt.getOptions();
        assertFalse(Boolean.TRUE.equals(options.getInternalToolExecutionEnabled()),
                "必须关闭内置工具执行，否则 act() 手工执行会与框架重复");
        assertEquals(1, options.getToolCallbacks().size(), "工具定义应随请求下发");
    }
}
