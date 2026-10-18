package com.aftersales.agent.advisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自定义 Advisor 单元测试：Re2 改写与日志透传。
 */
@DisplayName("自定义 Advisor")
class AdvisorTest {

    @Test
    @DisplayName("ReReadingAdvisor：追加复读指令并写入 re2_input_query 上下文")
    void reReadingAdvisorRewritesPrompt() {
        Prompt prompt = new Prompt(List.of(new SystemMessage("系统提示"), new UserMessage("吊牌剪了还能退吗")));
        Map<String, Object> context = new HashMap<>();
        ChatClientRequest request = new ChatClientRequest(prompt, context);

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse chainResponse = new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage("答复")))), Map.of());
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(chainResponse);

        ChatClientResponse result = new ReReadingAdvisor().adviseCall(request, chain);

        assertSame(chainResponse, result, "Advisor 应原样返回下游响应");
        ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(captor.capture());
        ChatClientRequest forwarded = captor.getValue();
        assertEquals("吊牌剪了还能退吗", forwarded.context().get("re2_input_query"),
                "上下文应记录原始问题");
        String userText = forwarded.prompt().getUserMessage().getText();
        assertTrue(userText.contains("吊牌剪了还能退吗"));
        assertTrue(userText.contains("Read the question again: 吊牌剪了还能退吗"),
                "应追加复读指令，实际：" + userText);
        assertEquals(2, forwarded.prompt().getInstructions().size(), "不应新增消息条数");
    }

    @Test
    @DisplayName("ReReadingAdvisor：getName / getOrder 契约")
    void reReadingAdvisorMetadata() {
        ReReadingAdvisor advisor = new ReReadingAdvisor();
        assertEquals("ReReadingAdvisor", advisor.getName());
        assertEquals(0, advisor.getOrder());
    }

    @Test
    @DisplayName("MyLoggerAdvisor：透传请求与响应，不改变内容")
    void loggerAdvisorPassesThrough() {
        Prompt prompt = new Prompt(List.of(new UserMessage("你好")));
        ChatClientRequest request = new ChatClientRequest(prompt, new HashMap<>());
        ChatClientResponse response = new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage("您好，我是售后客服")))), Map.of());
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(response);

        MyLoggerAdvisor advisor = new MyLoggerAdvisor();
        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertSame(response, result);
        verify(chain).nextCall(any(ChatClientRequest.class));
        assertEquals("MyLoggerAdvisor", advisor.getName());
        assertEquals(0, advisor.getOrder());
    }
}
