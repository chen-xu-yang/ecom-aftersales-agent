package com.aftersales.agent.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AftersalesPolicyTool 测试：检索参数、结果格式化与空结果兜底（PROJECT_CONTEXT §5.2）。
 */
@DisplayName("售后规则检索工具")
class AftersalesPolicyToolTest {

    @Test
    @DisplayName("检索参数固定为 topK=4、相似度阈值 0.4")
    void usesDocumentedSearchParameters() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        AftersalesPolicyTool tool = new AftersalesPolicyTool(vectorStore);

        tool.searchPolicy("吊牌剪掉能否退货");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertEquals("吊牌剪掉能否退货", request.getQuery());
        assertEquals(4, request.getTopK());
        assertEquals(0.4, request.getSimilarityThreshold(), 0.0001);
    }

    @Test
    @DisplayName("检索结果带【topic】标签并用分隔线拼接")
    void formatsDocumentsWithTopicLabels() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("吊牌剪掉通常无法无理由退货。", Map.of("topic", "退换货")),
                new Document("物流停滞超过 72 小时可发起核查。", Map.of("topic", "物流"))
        ));
        AftersalesPolicyTool tool = new AftersalesPolicyTool(vectorStore);

        String result = tool.searchPolicy("吊牌与物流");

        assertTrue(result.startsWith("【退换货】\n吊牌剪掉通常无法无理由退货。"), "实际：" + result);
        assertTrue(result.contains("\n\n---\n\n【物流】\n物流停滞超过 72 小时可发起核查。"), "实际：" + result);
    }

    @Test
    @DisplayName("文档缺少 topic 时使用默认标签“售后规则”")
    void fallsBackToDefaultTopic() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("无元数据的规则片段", Map.of())
        ));
        AftersalesPolicyTool tool = new AftersalesPolicyTool(vectorStore);

        String result = tool.searchPolicy("任意问题");

        assertTrue(result.startsWith("【售后规则】\n无元数据的规则片段"), "实际：" + result);
    }

    @Test
    @DisplayName("未检索到规则时提示转人工，而不是返回空字符串")
    void emptyResultSuggestsHumanAgent() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        AftersalesPolicyTool tool = new AftersalesPolicyTool(vectorStore);

        assertEquals("未检索到相关售后规则，请提示用户转人工客服确认。", tool.searchPolicy("平台外的问题"));
    }

    @Test
    @DisplayName("向量库返回 null 时按空结果处理，不应抛 NPE")
    void handlesNullSearchResult() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(null);
        AftersalesPolicyTool tool = new AftersalesPolicyTool(vectorStore);

        assertEquals("未检索到相关售后规则，请提示用户转人工客服确认。", tool.searchPolicy("任意问题"));
    }
}
