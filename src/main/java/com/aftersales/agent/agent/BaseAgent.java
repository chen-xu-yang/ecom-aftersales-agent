package com.aftersales.agent.agent;

import cn.hutool.core.util.StrUtil;
import com.aftersales.agent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;

    // 代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxSteps = 10;

    // LLM 大模型
    private ChatClient chatClient;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    /**
     * 运行代理
     * <p>
     * 执行结束后状态保留为 FINISHED / ERROR 便于调用方检查；同一实例可以再次调用，
     * 新一轮开始时会重置步骤计数与消息上下文。
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt) {
        // 1、基础校验
        if (this.state == AgentState.RUNNING) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        // 2、开始新一轮执行：复位状态并记录消息上下文
        startNewRun(userPrompt);
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            // 执行循环：FINISHED 与 ERROR 都视为本轮任务结束
            for (int i = 0; i < maxSteps && state == AgentState.RUNNING; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 单步执行
                String stepResult = step();
                // 末步的答复直接输出，中间步骤保留步骤标记
                results.add(state == AgentState.RUNNING
                        ? "Step " + stepNumber + ": " + stepResult
                        : stepResult);
            }
            // 步数耗尽仍未结束，按超限终止
            if (state == AgentState.RUNNING && currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3、清理资源
            this.cleanup();
        }
    }

    /**
     * 开始新一轮执行：状态置为 RUNNING、步骤计数归零、清空上一轮消息并记录本轮用户提示词。
     * <p>
     * 加锁避免同一实例被并发启动两次。
     *
     * @param userPrompt 用户提示词
     */
    private synchronized void startNewRun(String userPrompt) {
        if (this.state == AgentState.RUNNING) {
            throw new IllegalStateException("Cannot run agent from state: " + this.state);
        }
        this.state = AgentState.RUNNING;
        this.currentStep = 0;
        this.messageList.clear();
        this.messageList.add(new UserMessage(userPrompt));
    }

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时
        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            // 1、基础校验
            try {
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
                try {
                    // 开始新一轮执行：复位状态并记录消息上下文
                    startNewRun(userPrompt);
                } catch (IllegalStateException e) {
                    sseEmitter.send("错误：无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
                return;
            }
            // 保存结果列表
            List<String> results = new ArrayList<>();
            try {
                // 开始处理时先推送状态，避免多步任务期间界面无反馈
                sseEmitter.send(SseEmitter.event().name("progress").data("正在处理…"));
                // 执行循环：FINISHED 与 ERROR 都视为本轮任务结束
                for (int i = 0; i < maxSteps && state == AgentState.RUNNING; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    // 单步执行
                    String stepResult = step();
                    results.add("Step " + stepNumber + ": " + stepResult);
                    // 中间步骤用 progress 事件刷新状态，只有最终答复进入对话内容
                    if (state == AgentState.RUNNING) {
                        sseEmitter.send(SseEmitter.event().name("progress").data("正在处理…"));
                    } else {
                        sseEmitter.send(stepResult);
                    }
                }
                // 步数耗尽仍未结束，按超限终止
                if (state == AgentState.RUNNING && currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
                }
                // 发送结束标记，前端据此关闭连接而不是按错误重连
                sseEmitter.send("[DONE]");
                // 正常完成
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                // 3、清理资源
                this.cleanup();
            }
        });

        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }
}
