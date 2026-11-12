package com.aftersales.agent.agent;

import com.aftersales.agent.agent.model.AgentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ReActAgent 单步语义测试：think 为 false 时视为最终答复并终止（PROJECT_CONTEXT §5.3、§8 #1）。
 */
@DisplayName("ReActAgent 单步执行语义")
class ReActAgentStepTest {

    /**
     * 可编排的 ReAct 代理。
     */
    private static class StubReActAgent extends ReActAgent {
        boolean thinkResult;
        boolean throwOnThink;
        boolean thinkSetsError;
        boolean recordAssistantMessage;
        String assistantText = "模型最终答复";
        String actResult = "行动结果";

        @Override
        public boolean think() {
            if (throwOnThink) {
                throw new IllegalStateException("思考阶段失败");
            }
            if (thinkSetsError) {
                setState(AgentState.ERROR);
            }
            if (recordAssistantMessage) {
                getMessageList().add(new AssistantMessage(assistantText));
            }
            return thinkResult;
        }

        @Override
        public String act() {
            return actResult;
        }
    }

    @Test
    @DisplayName("模型不再调用工具：标记 FINISHED 并返回该文本")
    void thinksNoActReturnsAssistantText() {
        StubReActAgent agent = new StubReActAgent();
        agent.thinkResult = false;
        agent.recordAssistantMessage = true;

        String result = agent.step();

        assertEquals("模型最终答复", result);
        assertEquals(AgentState.FINISHED, agent.getState(), "无工具调用应视为任务结束");
    }

    @Test
    @DisplayName("think 返回 false 但没有助手消息：兜底文案“任务完成”")
    void fallsBackWhenNoAssistantMessage() {
        StubReActAgent agent = new StubReActAgent();
        agent.thinkResult = false;

        String result = agent.step();

        assertEquals("任务完成", result);
        assertEquals(AgentState.FINISHED, agent.getState());
    }

    @Test
    @DisplayName("think 抛异常：置 ERROR 并返回失败原因")
    void thinkExceptionMarksError() {
        StubReActAgent agent = new StubReActAgent();
        agent.throwOnThink = true;

        String result = agent.step();

        assertEquals("步骤执行失败：思考阶段失败", result);
        assertEquals(AgentState.ERROR, agent.getState());
    }

    @Test
    @DisplayName("think 已置 ERROR 时不得被覆盖成 FINISHED（回归 #2）")
    void doesNotOverrideErrorState() {
        StubReActAgent agent = new StubReActAgent();
        agent.thinkResult = false;
        agent.thinkSetsError = true;

        agent.step();

        assertEquals(AgentState.ERROR, agent.getState(), "ERROR 不应被当作正常完成");
    }

    @Test
    @DisplayName("需要行动：返回 act 结果，不改变状态")
    void thinksActReturnsActionResult() {
        StubReActAgent agent = new StubReActAgent();
        agent.thinkResult = true;

        String result = agent.step();

        assertEquals("行动结果", result);
        assertEquals(AgentState.IDLE, agent.getState(), "step 本身不负责切换 RUNNING");
    }
}
