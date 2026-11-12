package com.aftersales.agent.agent;

import com.aftersales.agent.agent.model.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BaseAgent.runStream 的 SSE 协议测试（AI_CONTEXT §6 接口约定）。
 * <p>
 * 用 MockMvc 直接驱动一个只做转发的测试控制器，验证真实的事件顺序与负载：
 * progress("正在处理…") → 最终答复 → [DONE]。
 */
@DisplayName("智能体 SSE 协议")
class AgentStreamProtocolTest {

    /**
     * 测试专用控制器：把 runStream 的 SseEmitter 原样返回。
     */
    @RestController
    static class StreamController {
        BaseAgent agent;
        String prompt = "测试任务";

        @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream() {
            return agent.runStream(prompt);
        }
    }

    private MockMvc mockMvc;
    private StreamController controller;

    @BeforeEach
    void setUp() {
        controller = new StreamController();
        // Spring Boot 会把 StringHttpMessageConverter 的默认字符集设为 UTF-8；
        // standalone MockMvc 需要显式对齐，否则 SSE 中文会被写成 '?'。
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    private String runAndReadBody() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult)).andReturn();
        return asyncResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("正常结束：progress 事件 + 最终答复 + [DONE]，且顺序正确")
    void successfulRunEmitsProgressAnswerAndDone() throws Exception {
        controller.agent = new BaseAgent() {
            @Override
            public String step() {
                setState(AgentState.FINISHED);
                return "最终答复内容";
            }
        };
        controller.agent.setName("streamAgent");
        controller.agent.setMaxSteps(3);

        String body = runAndReadBody();

        int progressIndex = body.indexOf("event:progress");
        int answerIndex = body.indexOf("data:最终答复内容");
        int doneIndex = body.indexOf("data:[DONE]");
        assertTrue(progressIndex >= 0, "应推送 progress 命名事件，实际：" + body);
        assertTrue(answerIndex >= 0, "最终答复应作为默认事件下发，实际：" + body);
        assertTrue(doneIndex >= 0, "结束哨兵 [DONE] 缺失，前端会按错误重连，实际：" + body);
        assertTrue(progressIndex < answerIndex, "progress 应先于最终答复");
        assertTrue(answerIndex < doneIndex, "[DONE] 必须在最后");
        assertTrue(body.contains("正在处理…"), "progress 负载应为“正在处理…”，实际：" + body);
        assertEquals("最终答复内容", answerLoadedOnce(body));
    }

    private String answerLoadedOnce(String body) {
        // 默认事件在 SSE 中以 "data:" 开头，这里只取一次最终答复负载用于断言
        int idx = body.indexOf("data:最终答复内容");
        int end = body.indexOf('\n', idx);
        return body.substring(idx + "data:".length(), end).trim();
    }

    @Test
    @DisplayName("多步任务：中间步骤只刷新 progress，不进入对话内容")
    void intermediateStepsOnlyPushProgress() throws Exception {
        controller.agent = new BaseAgent() {
            private final AtomicInteger calls = new AtomicInteger();

            @Override
            public String step() {
                if (calls.incrementAndGet() < 3) {
                    return "Step " + calls.get() + ": 已调用工具 searchPolicy";
                }
                setState(AgentState.FINISHED);
                return "整理后的最终答复";
            }
        };
        controller.agent.setName("streamAgent");
        controller.agent.setMaxSteps(5);

        String body = runAndReadBody();

        assertTrue(body.contains("data:整理后的最终答复"));
        assertFalseContains(body, "Step 1", "中间步骤文案不得下发给前端");
        assertFalseContains(body, "已调用工具", "工具调用细节不得下发给前端");
        long progressCount = countOccurrences(body, "event:progress");
        assertEquals(3, progressCount, "开始一次 + 前两步各一次，共 3 次 progress，实际：" + progressCount);
    }

    @Test
    @DisplayName("步数耗尽：先下发超限提示，再下发 [DONE]")
    void maxStepsSendsTerminationNotice() throws Exception {
        controller.agent = new BaseAgent() {
            @Override
            public String step() {
                return "继续处理";
            }
        };
        controller.agent.setName("streamAgent");
        controller.agent.setMaxSteps(2);

        String body = runAndReadBody();

        assertTrue(body.contains("执行结束：达到最大步骤（2）"), "应提示达到步骤上限，实际：" + body);
        assertTrue(body.contains("data:[DONE]"));
        assertTrue(body.indexOf("执行结束") < body.indexOf("data:[DONE]"));
        assertEquals(AgentState.FINISHED, controller.agent.getState());
    }

    @Test
    @DisplayName("运行中再次启动：返回明确的错误文本并结束连接")
    void rejectsRunWhenNotIdle() throws Exception {
        controller.agent = new BaseAgent() {
            @Override
            public String step() {
                return "不应执行";
            }
        };
        controller.agent.setName("streamAgent");
        controller.agent.setState(AgentState.RUNNING);

        String body = runAndReadBody();

        assertTrue(body.contains("错误：该会话正在处理上一轮问题，请稍后再试"), "实际：" + body);
        assertEquals(0, controller.agent.getCurrentStep(), "校验失败时不应执行步骤");
    }

    @Test
    @DisplayName("空提示词：返回明确错误文本且不执行步骤")
    void rejectsBlankPrompt() throws Exception {
        controller.agent = new BaseAgent() {
            @Override
            public String step() {
                return "不应执行";
            }
        };
        controller.agent.setName("streamAgent");
        controller.prompt = "   ";

        String body = runAndReadBody();

        assertTrue(body.contains("错误：不能使用空提示词运行代理"), "实际：" + body);
        assertEquals(0, controller.agent.getCurrentStep(), "校验失败时不应执行步骤");
        assertEquals(AgentState.IDLE, controller.agent.getState(), "校验失败不应进入 RUNNING");
    }

    @Test
    @DisplayName("token 流式：增量文本以 token 事件下发，且不再重复整段答复")
    void tokenStreamingEmitsIncrementalEvents() throws Exception {
        controller.agent = new BaseAgent() {
            @Override
            public String step() {
                emitToken("你");
                emitToken("好，已受理");
                setState(AgentState.FINISHED);
                return "你好，已受理";
            }
        };
        controller.agent.setName("streamAgent");
        controller.agent.setMaxSteps(2);

        String body = runAndReadBody();

        assertEquals(2, countOccurrences(body, "event:token"), "应推送 2 个 token 事件，实际：" + body);
        assertTrue(body.contains("data:你"), "实际：" + body);
        assertTrue(body.contains("data:好，已受理"), "实际：" + body);
        assertFalseContains(body, "data:你好，已受理", "已流式推送的文本不应再整段重复下发");
        assertTrue(body.contains("data:[DONE]"), "实际：" + body);
    }

    private static long countOccurrences(String text, String needle) {
        long count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void assertFalseContains(String body, String needle, String message) {
        assertTrue(!body.contains(needle), message + "；实际响应：" + body);
    }
}
