package com.aftersales.agent.agent;

import cn.hutool.core.util.StrUtil;
import com.aftersales.agent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * <p>
 * 流式约定（runStream）：
 * <ul>
 *   <li>{@code progress} 命名事件：步骤进行中，前端显示“正在处理…”。</li>
 *   <li>{@code token} 命名事件：模型输出的增量文本，前端追加渲染。</li>
 *   <li>默认事件：整段文本（兼容非 token 流式的子类）。</li>
 *   <li>{@code [DONE]}：无论成功、超限还是异常，连接结束前一定下发一次。</li>
 * </ul>
 */
@Data
@Slf4j
public abstract class BaseAgent {

    /**
     * 流式执行专用线程池：Agent 步骤里包含阻塞 HTTP 调用，不能占用公共 ForkJoinPool。
     */
    private static final ExecutorService STREAM_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            runnable -> {
                Thread thread = new Thread(runnable, "agent-stream");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * 当前流式连接。token 增量可能由模型 SDK 的 IO 线程回调，因此不能用 ThreadLocal
     * 保存连接上下文；同一实例同时只支持一个流式会话（Controller 每次请求创建新实例）。
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private transient volatile SseEmitter streamEmitter;

    /**
     * 当前步骤是否已经流式推送过 token（已推则不再重复下发整段答复）。
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private transient volatile boolean stepStreamedTokens;

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
     * 多轮会话开关：为 true 时同一实例再次执行会保留历史消息（由会话管理器为同 chatId 开启）。
     */
    private boolean keepHistory;

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
        if (!this.keepHistory) {
            // 单次任务：每轮清空上下文
            this.messageList.clear();
        } else {
            // 多轮会话：保留上一轮上下文，仅追加本轮用户消息
            this.messageList = new ArrayList<>(this.messageList);
        }
        this.messageList.add(new UserMessage(userPrompt));
    }

    /**
     * 运行代理（流式输出）：专用线程池执行，token 实时推送，结束时统一发送 [DONE]。
     *
     * @param userPrompt 用户提示词
     * @return SSE emitter
     */
    public SseEmitter runStream(String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(300000L);
        AtomicBoolean doneSent = new AtomicBoolean(false);

        Future<?> future = STREAM_EXECUTOR.submit(
                () -> runStreamTask(sseEmitter, userPrompt, doneSent));

        sseEmitter.onTimeout(() -> {
            log.warn("SSE connection timeout, cancelling in-flight agent run");
            state = AgentState.ERROR;
            future.cancel(true);
            finishEmitter(sseEmitter, doneSent);
        });
        sseEmitter.onError(ex -> {
            log.warn("SSE connection error, cancelling in-flight agent run: {}", ex.getMessage());
            future.cancel(true);
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 流式执行主体：校验 → 逐步执行 → 推送 progress/最终答复 → 发送 [DONE]。
     */
    private void runStreamTask(SseEmitter sseEmitter, String userPrompt, AtomicBoolean doneSent) {
        try {
            // 1、基础校验
            if (StrUtil.isBlank(userPrompt)) {
                sseEmitter.send("错误：不能使用空提示词运行代理");
                return;
            }
            try {
                startNewRun(userPrompt);
            } catch (IllegalStateException e) {
                // 同一会话并发提问：给出可读提示，而不是“无法从状态运行”
                sseEmitter.send("错误：该会话正在处理上一轮问题，请稍后再试");
                return;
            }

            // 2、开始处理时先推送状态，避免多步任务期间界面无反馈
            sseEmitter.send(SseEmitter.event().name("progress").data("正在处理…"));

            List<String> results = new ArrayList<>();
            this.streamEmitter = sseEmitter;
            try {
                // 执行循环：FINISHED 与 ERROR 都视为本轮任务结束
                for (int i = 0; i < maxSteps && state == AgentState.RUNNING; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    this.stepStreamedTokens = false;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);

                    String stepResult = step();
                    boolean streamedTokens = this.stepStreamedTokens;
                    results.add("Step " + stepNumber + ": " + stepResult);

                    if (state == AgentState.RUNNING) {
                        // 中间步骤只刷新状态，不把工具细节写进对话
                        sseEmitter.send(SseEmitter.event().name("progress").data("正在处理…"));
                    } else if (!streamedTokens) {
                        // 非 token 流式的子类：整段下发最终答复
                        sseEmitter.send(stepResult);
                    }
                }
                // 步数耗尽仍未结束，按超限终止
                if (state == AgentState.RUNNING && currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
                }
            } finally {
                this.streamEmitter = null;
                this.stepStreamedTokens = false;
            }
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            try {
                sseEmitter.send("执行错误：" + e.getMessage());
            } catch (Exception ignored) {
                log.debug("client disconnected before error notice");
            }
        } finally {
            // 3、清理资源 + 统一收尾：任何分支都以 [DONE] 结束，前端据此关闭连接
            this.cleanup();
            finishEmitter(sseEmitter, doneSent);
        }
    }

    /**
     * 发送一次 [DONE] 并完成连接；幂等，重复调用安全。
     */
    private void finishEmitter(SseEmitter sseEmitter, AtomicBoolean doneSent) {
        if (doneSent.compareAndSet(false, true)) {
            try {
                sseEmitter.send("[DONE]");
                sseEmitter.complete();
            } catch (Exception e) {
                log.debug("failed to finish SSE emitter: {}", e.getMessage());
                try {
                    sseEmitter.complete();
                } catch (Exception ignored) {
                    // 连接已断开，无需处理
                }
            }
        }
    }

    /**
     * 推送模型增量文本（token 事件）；非流式执行时静默忽略。
     *
     * @param token 增量文本
     */
    protected void emitToken(String token) {
        SseEmitter emitter = this.streamEmitter;
        if (emitter == null || StrUtil.isEmpty(token)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("token").data(token));
            this.stepStreamedTokens = true;
        } catch (IOException e) {
            // 客户端断开：抛出以中断当前步骤，由 runStreamTask 统一收尾
            throw new IllegalStateException("客户端连接已断开", e);
        }
    }

    /**
     * 定义单个步骤
     *
     * @return 步骤结果
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }
}
