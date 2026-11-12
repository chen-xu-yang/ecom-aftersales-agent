package com.aftersales.agent.agent;

import com.aftersales.agent.agent.model.AgentState;
import com.aftersales.agent.tools.TerminateTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 售后处理智能体的配置契约（PROJECT_CONTEXT §5.2、§5.3）。
 */
@DisplayName("售后处理智能体配置")
class AftersalesAgentConfigTest {

    private AftersalesAgent newAgent() {
        ToolCallback[] tools = ToolCallbacks.from(new TerminateTool());
        return new AftersalesAgent(tools, mock(ChatModel.class));
    }

    @Test
    @DisplayName("名称、最大步数与初始状态符合约定")
    void hasExpectedBasics() {
        AftersalesAgent agent = newAgent();
        assertEquals("aftersalesAgent", agent.getName());
        assertEquals(8, agent.getMaxSteps(), "PROJECT_CONTEXT 约定 maxSteps=8");
        assertEquals(AgentState.IDLE, agent.getState());
        assertEquals(0, agent.getCurrentStep());
        assertNotNull(agent.getChatClient(), "必须初始化 ChatClient");
    }

    @Test
    @DisplayName("系统提示要求先检索规则、不得编造政策、使用用户语言")
    void systemPromptEncodesPolicyRules() {
        String prompt = newAgent().getSystemPrompt();
        assertTrue(prompt.contains("searchPolicy"), "平台政策应强制使用 searchPolicy");
        assertTrue(prompt.contains("searchWeb"), "应明确告知模型具备联网搜索工具");
        assertTrue(prompt.contains("快递公司"), "应说明第三方主体的规则用 searchWeb 查询");
        assertTrue(prompt.contains("不要声称自己没有"), "应避免模型声称没有联网工具");
        assertTrue(prompt.contains("不得凭记忆编造"), "应禁止编造政策");
        assertTrue(prompt.contains("转人工"), "检索不到时应提示转人工");
        assertTrue(prompt.contains("使用用户所使用的语言"), "应约束回复语言");
        assertTrue(prompt.contains("售后"), "应限定在售后场景");
    }

    @Test
    @DisplayName("下一步提示说明工具选择与终止语义")
    void nextStepPromptExplainsTermination() {
        String prompt = newAgent().getNextStepPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.contains("searchPolicy"), "应说明平台政策优先 searchPolicy");
        assertTrue(prompt.contains("searchWeb"), "应说明第三方规则使用 searchWeb");
        assertTrue(prompt.contains("terminate"), "应说明 terminate 的用途");
        assertTrue(prompt.contains("一旦不再调用工具"), "应说明“不调用工具即结束”");
    }
}
