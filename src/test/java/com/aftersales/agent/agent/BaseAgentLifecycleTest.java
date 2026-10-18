package com.aftersales.agent.agent;

import com.aftersales.agent.agent.model.AgentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BaseAgent 状态机与执行循环测试。
 * <p>
 * 覆盖需求：AI_CONTEXT §5.3 终止语义、§8 已修复问题 #2/#4。
 * 全部为纯单元测试：用匿名子类控制 step() 行为，不调用大模型。
 */
@DisplayName("BaseAgent 状态机与执行循环")
class BaseAgentLifecycleTest {

    /**
     * 可编排的测试代理：按调用次数决定每步的返回值与结束状态。
     */
    private static class ProgrammableAgent extends BaseAgent {

        final AtomicInteger stepCalls = new AtomicInteger();
        int cleanupCalls = 0;
        int finishAtStep;
        String finalAnswer = "最终答复";
        RuntimeException stepError;

        ProgrammableAgent(int finishAtStep) {
            this.finishAtStep = finishAtStep;
            setName("testAgent");
        }

        @Override
        public String step() {
            int call = stepCalls.incrementAndGet();
            if (stepError != null) {
                throw stepError;
            }
            if (call >= finishAtStep) {
                setState(AgentState.FINISHED);
                return finalAnswer;
            }
            return "中间结果" + call;
        }

        @Override
        protected void cleanup() {
            cleanupCalls++;
        }
    }

    @Nested
    @DisplayName("运行前置校验")
    class GuardTests {

        @Test
        @DisplayName("空提示词（空白字符串）必须拒绝执行")
        void rejectsBlankPromptByBlankString() {
            ProgrammableAgent agent = new ProgrammableAgent(1);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> agent.run("   "));
            assertTrue(ex.getMessage().contains("empty user prompt"),
                    "异常信息应说明提示词为空，实际：" + ex.getMessage());
            assertEquals(0, agent.stepCalls.get(), "校验失败时不应执行任何步骤");
        }

        @Test
        @DisplayName("空提示词（null）必须拒绝执行")
        void rejectsNullPrompt() {
            ProgrammableAgent agent = new ProgrammableAgent(1);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> agent.run(null));
            assertTrue(ex.getMessage().contains("empty user prompt"));
        }

        @Test
        @DisplayName("非 IDLE 状态不得重复启动")
        void rejectsRunWhenNotIdle() {
            ProgrammableAgent agent = new ProgrammableAgent(1);
            agent.setState(AgentState.RUNNING);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> agent.run("任务"));
            assertTrue(ex.getMessage().contains("Cannot run agent from state: RUNNING"));
            assertEquals(0, agent.stepCalls.get());
        }
    }

    @Nested
    @DisplayName("执行循环与终止语义")
    class LoopTests {

        @Test
        @DisplayName("模型直接给出答复：返回该答复且不带 Step 前缀")
        void returnsFinalAnswerWithoutStepPrefix() {
            ProgrammableAgent agent = new ProgrammableAgent(1);
            String result = agent.run("查询退货规则");
            assertEquals("最终答复", result, "末步答复应原样返回，不应拼接 Step 前缀");
            assertEquals(AgentState.FINISHED, agent.getState());
            assertEquals(1, agent.stepCalls.get());
            assertEquals(1, agent.getCurrentStep());
        }

        @Test
        @DisplayName("多步执行：中间步骤带 Step 标记，末步只保留答复")
        void prefixesIntermediateStepsOnly() {
            ProgrammableAgent agent = new ProgrammableAgent(3);
            agent.setMaxSteps(5);
            String result = agent.run("复杂售后任务");
            assertEquals("Step 1: 中间结果1\nStep 2: 中间结果2\n最终答复", result);
            assertEquals(3, agent.stepCalls.get(), "FINISHED 后必须立即退出循环");
            assertEquals(3, agent.getCurrentStep());
        }

        @Test
        @DisplayName("步数耗尽：标记 FINISHED 并追加超限提示")
        void terminatesWhenMaxStepsReached() {
            ProgrammableAgent agent = new ProgrammableAgent(Integer.MAX_VALUE);
            agent.setMaxSteps(3);
            String result = agent.run("无法结束的任务");
            assertEquals(3, agent.stepCalls.get(), "最多执行 maxSteps 次");
            assertTrue(result.contains("Terminated: Reached max steps (3)"),
                    "应输出超限提示，实际：" + result);
            assertEquals(AgentState.FINISHED, agent.getState(), "超限后状态应为 FINISHED");
        }

        @Test
        @DisplayName("第 maxSteps 步恰好完成：不得误报“达到最大步骤”（回归 #4）")
        void doesNotReportMaxStepsWhenFinishedOnLastStep() {
            ProgrammableAgent agent = new ProgrammableAgent(3);
            agent.setMaxSteps(3);
            String result = agent.run("刚好三步完成");
            assertEquals("Step 1: 中间结果1\nStep 2: 中间结果2\n最终答复", result);
            assertTrue(!result.contains("Reached max steps"), "恰好在最后一步完成时不应报超限");
            assertEquals(AgentState.FINISHED, agent.getState());
        }

        @Test
        @DisplayName("步骤抛异常：捕获后返回错误文案并置 ERROR")
        void catchesStepException() {
            ProgrammableAgent agent = new ProgrammableAgent(1);
            agent.stepError = new IllegalStateException("boom");
            String result = agent.run("会失败的任务");
            assertTrue(result.startsWith("执行错误"), "应返回错误文案，实际：" + result);
            assertTrue(result.contains("boom"), "错误文案应包含原因");
            assertEquals(AgentState.ERROR, agent.getState(), "异常后状态应为 ERROR");
        }

        @Test
        @DisplayName("无论成功失败都要执行 cleanup")
        void alwaysRunsCleanup() {
            ProgrammableAgent okAgent = new ProgrammableAgent(1);
            okAgent.run("成功任务");
            assertEquals(1, okAgent.cleanupCalls, "成功路径应清理一次");

            ProgrammableAgent failAgent = new ProgrammableAgent(1);
            failAgent.stepError = new IllegalStateException("boom");
            failAgent.run("失败任务");
            assertEquals(1, failAgent.cleanupCalls, "异常路径也应清理一次");
        }

        @Test
        @DisplayName("执行后记录用户消息与步数，便于排查")
        void recordsUserMessageAndStep() {
            ProgrammableAgent agent = new ProgrammableAgent(2);
            agent.run("记录上下文");
            assertEquals(1, agent.getMessageList().size(), "初始应只有一条用户消息");
            assertEquals(2, agent.getCurrentStep());
        }

        @Test
        @DisplayName("同一实例可以再次 run，且上一轮上下文已清理（回归 BUG-06）")
        void sameInstanceCanRunAgainWithFreshContext() {
            ProgrammableAgent agent = new ProgrammableAgent(1);

            assertEquals("最终答复", agent.run("第一次"));
            assertEquals("最终答复", agent.run("第二次"));

            assertEquals(2, agent.stepCalls.get(), "第二次调用应真正执行步骤");
            assertEquals(1, agent.getMessageList().size(), "新一轮应清空上一轮消息");
            assertEquals("第二次", ((UserMessage) agent.getMessageList().get(0)).getText());
            assertEquals(AgentState.FINISHED, agent.getState());
        }
    }
}
