package com.aftersales.agent.controller;

import com.aftersales.agent.agent.AftersalesAgent;
import com.aftersales.agent.app.AftersalesApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 接口测试：同步、三种 SSE 写法与智能体接口的接线。
 * <p>
 * 依赖用 Mockito 替换，不启动 Spring 容器、不调用真实模型，因此不受 API Key 影响。
 */
@DisplayName("AI 接口")
class AiControllerTest {

    private AftersalesApp aftersalesApp;
    private ChatModel chatModel;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aftersalesApp = mock(AftersalesApp.class);
        chatModel = mock(ChatModel.class);
        AiController controller = new AiController();
        ReflectionTestUtils.setField(controller, "aftersalesApp", aftersalesApp);
        ReflectionTestUtils.setField(controller, "allTools", new ToolCallback[0]);
        ReflectionTestUtils.setField(controller, "dashscopeChatModel", chatModel);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    private MvcResult asyncResult(MvcResult initial) throws Exception {
        return mockMvc.perform(asyncDispatch(initial)).andReturn();
    }

    @Test
    @DisplayName("GET /ai/aftersales/chat/sync 返回客服答复")
    void syncChatReturnsAnswer() throws Exception {
        when(aftersalesApp.doChat("你好", "c1")).thenReturn("您好，请描述售后问题");

        MvcResult result = mockMvc.perform(get("/ai/aftersales/chat/sync")
                        .param("message", "你好")
                        .param("chatId", "c1"))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8)
                .contains("您好，请描述售后问题"));
    }

    @Test
    @DisplayName("GET /ai/aftersales/chat/sse 逐块推送 Flux<String>")
    void sseChatStreamsChunks() throws Exception {
        when(aftersalesApp.doChatByStream("你好", "c1")).thenReturn(Flux.just("第一段", "第二段"));

        MvcResult initial = mockMvc.perform(get("/ai/aftersales/chat/sse")
                        .param("message", "你好")
                        .param("chatId", "c1"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = asyncResult(initial).getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(body.contains("第一段"), "实际：" + body);
        assertTrue(body.contains("第二段"), "实际：" + body);
        assertTrue(body.contains("data:[DONE]"), "客服 SSE 也应以 [DONE] 收尾，实际：" + body);
    }

    @Test
    @DisplayName("GET /ai/aftersales/chat/server_sent_event 包装为 ServerSentEvent")
    void serverSentEventChatWrapsData() throws Exception {
        when(aftersalesApp.doChatByStream("你好", "c1")).thenReturn(Flux.just("甲", "乙"));

        MvcResult initial = mockMvc.perform(get("/ai/aftersales/chat/server_sent_event")
                        .param("message", "你好")
                        .param("chatId", "c1"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult result = asyncResult(initial);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(result.getResponse().getContentType().contains(MediaType.TEXT_EVENT_STREAM_VALUE),
                "实际 Content-Type：" + result.getResponse().getContentType());
        assertTrue(body.contains("data:甲"), "实际：" + body);
        assertTrue(body.contains("data:乙"), "实际：" + body);
    }

    @Test
    @DisplayName("GET /ai/aftersales/chat/sse_emitter 通过 SseEmitter 推送")
    void sseEmitterChatStreamsChunks() throws Exception {
        when(aftersalesApp.doChatByStream("你好", "c1")).thenReturn(Flux.just("一", "二"));

        MvcResult initial = mockMvc.perform(get("/ai/aftersales/chat/sse_emitter")
                        .param("message", "你好")
                        .param("chatId", "c1"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = asyncResult(initial).getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(body.contains("一"), "实际：" + body);
        assertTrue(body.contains("二"), "实际：" + body);
    }

    @Test
    @DisplayName("GET /ai/agent/chat：模型异常时仍以 [DONE] 收尾，前端不会按错误重连")
    void agentChatSendsDoneEvenWhenModelFails() throws Exception {
        when(chatModel.stream(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("模型不可用")));

        MvcResult initial = mockMvc.perform(get("/ai/agent/chat").param("message", "吊牌剪了还能退吗"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = asyncResult(initial).getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(body.contains("event:progress"), "应先推送处理状态，实际：" + body);
        assertTrue(body.contains("处理时遇到了错误"), "应把错误作为可见答复返回，实际：" + body);
        assertTrue(body.contains("data:[DONE]"), "结束哨兵缺失会导致前端重连，实际：" + body);
    }

    @Test
    @DisplayName("GET /ai/agent/chat 会为每次请求创建独立的智能体实例")
    void agentChatCreatesFreshAgentPerRequest() throws Exception {
        when(chatModel.stream(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("模型不可用")));

        for (int i = 0; i < 2; i++) {
            MvcResult initial = mockMvc.perform(get("/ai/agent/chat").param("message", "第 " + i + " 次"))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            String body = asyncResult(initial).getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertTrue(body.contains("[DONE]"), "第 " + i + " 次请求应正常收尾");
        }
    }
}
