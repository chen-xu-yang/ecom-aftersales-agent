package com.aftersales.agent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 自定义日志 Advisor：打印用户提示词与 AI 回复，但做长度截断，避免把完整 prompt / PII
 * 大段写进日志（生产环境建议整体关闭 INFO 或改成 DEBUG）。
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int MAX_LOG_CHARS = 500;

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private ChatClientRequest before(ChatClientRequest request) {
        log.info("AI Request: {}", abbreviate(String.valueOf(request.prompt())));
        return request;
    }

    private void observeAfter(ChatClientResponse chatClientResponse) {
        String text = chatClientResponse.chatResponse() != null
                && chatClientResponse.chatResponse().getResult() != null
                ? chatClientResponse.chatResponse().getResult().getOutput().getText()
                : null;
        log.info("AI Response: {}", abbreviate(text));
    }

    /**
     * 日志截断：最多保留 {@value #MAX_LOG_CHARS} 字符，避免日志膨胀与敏感信息整段落盘。
     */
    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_LOG_CHARS) {
            return text;
        }
        return text.substring(0, MAX_LOG_CHARS) + "...(truncated, total " + text.length() + " chars)";
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        chatClientRequest = before(chatClientRequest);
        ChatClientResponse chatClientResponse = chain.nextCall(chatClientRequest);
        observeAfter(chatClientResponse);
        return chatClientResponse;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        chatClientRequest = before(chatClientRequest);
        Flux<ChatClientResponse> chatClientResponseFlux = chain.nextStream(chatClientRequest);
        return (new ChatClientMessageAggregator()).aggregateChatClientResponse(chatClientResponseFlux, this::observeAfter);
    }
}
