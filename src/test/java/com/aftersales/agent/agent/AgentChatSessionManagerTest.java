package com.aftersales.agent.agent;

import com.aftersales.agent.agent.model.AgentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 多轮会话相关测试：会话复用、LRU 淘汰、跨轮历史保留。
 */
@DisplayName("智能体会话管理")
class AgentChatSessionManagerTest {

    private static AftersalesAgent newAgent() {
        return new AftersalesAgent(new org.springframework.ai.tool.ToolCallback[0], mock(ChatModel.class));
    }

    @Test
    @DisplayName("同一 chatId 复用同一实例，不同 chatId 隔离")
    void reusesAgentPerChatId() {
        AgentChatSessionManager manager = new AgentChatSessionManager();
        AtomicInteger created = new AtomicInteger();

        AftersalesAgent first = manager.getOrCreate("c1", () -> {
            created.incrementAndGet();
            return newAgent();
        });
        AftersalesAgent second = manager.getOrCreate("c1", () -> {
            created.incrementAndGet();
            return newAgent();
        });
        AftersalesAgent other = manager.getOrCreate("c2", () -> {
            created.incrementAndGet();
            return newAgent();
        });

        assertSame(first, second, "同一 chatId 应复用实例");
        assertNotSame(first, other, "不同 chatId 应相互隔离");
        assertEquals(2, created.get(), "只应创建 2 个实例");
        assertEquals(2, manager.size());
        assertTrue(first.isKeepHistory(), "会话实例应开启多轮历史");
    }

    @Test
    @DisplayName("超过 32 个会话后淘汰最久未访问的会话")
    void evictsLeastRecentlyUsedSession() {
        AgentChatSessionManager manager = new AgentChatSessionManager();
        for (int i = 0; i < 33; i++) {
            manager.getOrCreate("chat-" + i, AgentChatSessionManagerTest::newAgent);
        }
        assertEquals(32, manager.size(), "会话数应被限制在 32");

        AtomicInteger recreated = new AtomicInteger();
        manager.getOrCreate("chat-0", () -> {
            recreated.incrementAndGet();
            return newAgent();
        });
        assertEquals(1, recreated.get(), "被淘汰的 chat-0 应重新创建");
    }

    @Test
    @DisplayName("keepHistory=true 时跨轮保留历史，false 时每轮清空")
    void keepsHistoryOnlyWhenEnabled() {
        class EchoAgent extends BaseAgent {
            @Override
            public String step() {
                setState(AgentState.FINISHED);
                return "ok";
            }
        }

        EchoAgent multiTurn = new EchoAgent();
        multiTurn.setKeepHistory(true);
        multiTurn.setMaxSteps(2);
        multiTurn.run("第一轮");
        multiTurn.run("第二轮");
        assertEquals(2, multiTurn.getMessageList().size(), "多轮模式应保留两轮用户消息");
        assertTrue(textOf(multiTurn.getMessageList().get(0)).contains("第一轮"));
        assertTrue(textOf(multiTurn.getMessageList().get(1)).contains("第二轮"));

        EchoAgent oneShot = new EchoAgent();
        oneShot.setMaxSteps(2);
        oneShot.run("第一轮");
        oneShot.run("第二轮");
        assertEquals(1, oneShot.getMessageList().size(), "单次模式应只保留本轮消息");
    }

    private static String textOf(Message message) {
        return message.getText();
    }
}
