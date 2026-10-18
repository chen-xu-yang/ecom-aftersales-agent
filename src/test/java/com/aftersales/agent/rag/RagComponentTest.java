package com.aftersales.agent.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 查询改写与自定义 RAG Advisor 工厂的构造测试。
 */
@DisplayName("RAG 组件")
class RagComponentTest {

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    @DisplayName("查询改写：把口语化问题改写为检索查询")
    void queryRewriterReturnsModelOutput() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("吊牌缺失 七天无理由退货 例外"));

        QueryRewriter rewriter = new QueryRewriter(chatModel);
        String rewritten = rewriter.doQueryRewrite("我吊牌剪了还能不能退啊");

        assertEquals("吊牌缺失 七天无理由退货 例外", rewritten);
    }

    @Test
    @DisplayName("自定义 RAG Advisor 可正常构建，并携带元数据过滤")
    void buildsCustomAdvisor() {
        Advisor advisor = AftersalesRagCustomAdvisorFactory
                .createAftersalesRagCustomAdvisor(mock(VectorStore.class), "退换货");

        assertNotNull(advisor);
        assertInstanceOf(RetrievalAugmentationAdvisor.class, advisor);
    }
}
