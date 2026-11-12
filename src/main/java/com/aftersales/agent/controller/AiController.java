package com.aftersales.agent.controller;

import com.aftersales.agent.agent.AftersalesAgent;
import com.aftersales.agent.app.AftersalesApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AftersalesApp aftersalesApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 同步调用电商售后客服应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/aftersales/chat/sync")
    public String doChatWithAftersalesAppSync(String message, String chatId) {
        return aftersalesApp.doChat(message, chatId);
    }

    /**
     * SSE 流式调用电商售后客服应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/aftersales/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithAftersalesAppSSE(String message, String chatId) {
        // 与智能体接口统一：流末尾追加 [DONE] 哨兵，前端据此正常关闭而不是按错误重连
        return aftersalesApp.doChatByStream(message, chatId).concatWith(Flux.just("[DONE]"));
    }

    /**
     * SSE 流式调用电商售后客服应用（返回 ServerSentEvent）
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/aftersales/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithAftersalesAppServerSentEvent(String message, String chatId) {
        return aftersalesApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE]").build()));
    }

    /**
     * SSE 流式调用电商售后客服应用（返回 SseEmitter）
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/aftersales/chat/sse_emitter")
    public SseEmitter doChatWithAftersalesAppServerSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        aftersalesApp.doChatByStream(message, chatId)
                .concatWith(Flux.just("[DONE]"))
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 流式调用售后处理智能体
     *
     * @param message
     * @return
     */
    @GetMapping("/agent/chat")
    public SseEmitter doChatWithAgent(String message) {
        AftersalesAgent aftersalesAgent = new AftersalesAgent(allTools, dashscopeChatModel);
        return aftersalesAgent.runStream(message);
    }
}
