package com.aftersales.agent.app;

import com.aftersales.agent.rag.QueryRewriter;
import com.aftersales.agent.tools.TerminateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 售后客服应用（AftersalesApp）测试。
 * <p>
 * 用 Mockito 模拟 ChatModel / VectorStore / QueryRewriter，验证编排逻辑与提示词内容，
 * 不产生真实模型调用费用。
 */
@DisplayName("售后客服应用")
class AftersalesAppUnitTest {

    private ChatModel chatModel;
    private AftersalesApp app;

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(response("这是客服答复"));
        app = new AftersalesApp(chatModel);
    }

    private List<Message> capturedMessages(int invocation) {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, org.mockito.Mockito.atLeast(invocation)).call(captor.capture());
        return captor.getAllValues().get(invocation - 1).getInstructions();
    }

    private String joinedText(List<Message> messages) {
        return messages.stream().map(Message::getText).collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("同步对话返回模型文本，并注入售后客服系统提示")
    void doChatReturnsModelText() {
        String answer = app.doChat("我买的商品还没发货", "chat-1");

        assertEquals("这是客服答复", answer);
        String promptText = joinedText(capturedMessages(1));
        assertTrue(promptText.contains("售后客服助手"), "应注入客服系统提示，实际：" + promptText);
        assertTrue(promptText.contains("我买的商品还没发货"), "应携带用户问题");
    }

    @Test
    @DisplayName("同一 chatId 的多轮对话携带历史消息")
    void doChatKeepsConversationMemory() {
        String chatId = "chat-memory";
        app.doChat("第一问：吊牌剪了能退吗", chatId);
        app.doChat("第二问：那运费谁出", chatId);

        List<Message> secondPrompt = capturedMessages(2);
        String promptText = joinedText(secondPrompt);
        assertTrue(promptText.contains("第一问：吊牌剪了能退吗"), "第二轮应携带第一轮用户消息");
        assertTrue(promptText.contains("这是客服答复"), "第二轮应携带第一轮助手答复");
        assertTrue(promptText.contains("第二问：那运费谁出"), "第二轮应携带本轮问题");
    }

    @Test
    @DisplayName("不同 chatId 的会话互相隔离")
    void doChatIsolatesDifferentChatIds() {
        app.doChat("A 的问题", "chat-a");
        app.doChat("B 的问题", "chat-b");

        String promptText = joinedText(capturedMessages(2));
        assertTrue(promptText.contains("B 的问题"));
        assertTrue(!promptText.contains("A 的问题"), "不同会话不应串上下文，实际：" + promptText);
    }

    @Test
    @DisplayName("流式对话按块返回内容")
    void doChatByStreamEmitsChunks() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(response("第一段"), response("第二段")));

        List<String> chunks = app.doChatByStream("你好", "chat-stream").collectList().block();

        assertNotNull(chunks);
        assertEquals(List.of("第一段", "第二段"), chunks);
    }

    @Test
    @DisplayName("结构化输出解析为售后处理报告")
    void doChatWithReportParsesEntity() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "{\"title\":\"订单 20250815001 的售后处理建议\","
                        + "\"suggestions\":[\"优先换货并承担运费\",\"48 小时内提交凭证\"]}"));

        AftersalesApp.AftersalesReport report = app.doChatWithReport("外包装破损想换货", "chat-report");

        assertNotNull(report);
        assertEquals("订单 20250815001 的售后处理建议", report.title());
        assertEquals(List.of("优先换货并承担运费", "48 小时内提交凭证"), report.suggestions());
    }

    @Test
    @DisplayName("RAG 对话：先改写查询，再把检索到的规则原文注入模型提示")
    void doChatWithRagInjectsRetrievedContext() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("吊牌已剪掉通常无法按七天无理由退货处理。", Map.of("topic", "退换货"))));
        QueryRewriter queryRewriter = mock(QueryRewriter.class);
        when(queryRewriter.doQueryRewrite(anyString())).thenReturn("吊牌缺失 七天无理由退货");

        ReflectionTestUtils.setField(app, "aftersalesVectorStore", vectorStore);
        ReflectionTestUtils.setField(app, "queryRewriter", queryRewriter);

        String answer = app.doChatWithRag("吊牌剪了还能退吗", "chat-rag");

        assertEquals("这是客服答复", answer);
        verify(queryRewriter).doQueryRewrite("吊牌剪了还能退吗");
        verify(vectorStore).similaritySearch(any(SearchRequest.class));

        String promptText = joinedText(capturedMessages(1));
        assertTrue(promptText.contains("吊牌已剪掉通常无法按七天无理由退货处理。"),
                "检索到的规则原文应进入提示词，实际：" + promptText);
    }

    @Test
    @DisplayName("工具对话返回模型文本")
    void doChatWithToolsReturnsText() {
        ToolCallback[] tools = ToolCallbacks.from(new TerminateTool());
        ReflectionTestUtils.setField(app, "allTools", tools);

        assertEquals("这是客服答复", app.doChatWithTools("帮我生成售后处理单", "chat-tools"));
    }

    @Test
    @DisplayName("MCP 对话返回模型文本")
    void doChatWithMcpReturnsText() {
        ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
        ReflectionTestUtils.setField(app, "toolCallbackProvider", provider);

        assertEquals("这是客服答复", app.doChatWithMcp("帮我找破损参考图", "chat-mcp"));
    }
}
