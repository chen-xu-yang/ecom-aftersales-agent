package com.aftersales.agent.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识库加载测试：文档数量、topic 元数据与文件名约定（AI_CONTEXT §5.1）。
 * <p>
 * 直接加载 classpath 下真实的售后规则文档，不调用大模型。
 */
@DisplayName("知识库文档加载")
class AftersalesDocumentLoaderSpecTest {

    private AftersalesDocumentLoader newLoader() {
        return new AftersalesDocumentLoader(new PathMatchingResourcePatternResolver());
    }

    @Test
    @DisplayName("加载三份规则文档，切分为 17 个片段（6/6/5）")
    void loadsAllRuleDocuments() {
        List<Document> documents = newLoader().loadMarkdowns();

        assertFalse(documents.isEmpty(), "知识库不应为空");
        assertEquals(17, documents.size(), "AI_CONTEXT 约定约 17 个片段，数量变化需同步更新文档与测试");
        for (Document document : documents) {
            assertNotNull(document.getText());
            assertFalse(document.getText().isBlank(), "片段内容不应为空白");
        }

        Map<String, Long> countByTopic = documents.stream().collect(Collectors.groupingBy(
                document -> String.valueOf(document.getMetadata().get("topic")), Collectors.counting()));
        assertEquals(6L, countByTopic.get("退换货"));
        assertEquals(6L, countByTopic.get("物流"));
        assertEquals(5L, countByTopic.get("商品与价保"));
    }

    @Test
    @DisplayName("每个片段都带 filename 与 topic 元数据，topic 取值符合约定")
    void everyChunkHasMetadata() {
        List<Document> documents = newLoader().loadMarkdowns();

        Set<String> topics = documents.stream()
                .map(document -> String.valueOf(document.getMetadata().get("topic")))
                .collect(Collectors.toSet());

        assertEquals(Set.of("退换货", "物流", "商品与价保"), topics, "topic 应与文件名「售后规则 - 分类.md」一致");
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            assertTrue(String.valueOf(metadata.get("filename")).endsWith(".md"),
                    "filename 元数据缺失或非法：" + metadata);
            assertNotNull(metadata.get("topic"));
        }
    }

    @Test
    @DisplayName("知识库内容包含关键售后规则原文")
    void containsKeyPolicyText() {
        String allText = newLoader().loadMarkdowns().stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        assertTrue(allText.contains("七天无理由退货"), "缺少退换货规则");
        assertTrue(allText.contains("吊牌"), "缺少吊牌相关规则");
        assertTrue(allText.contains("超时未发货"), "缺少发货时效规则");
        assertTrue(allText.contains("价保"), "缺少价保规则");
    }

    @Test
    @DisplayName("文件名解析：取最后一个「 - 」之后的分类")
    void parseTopicFromFileName() {
        AftersalesDocumentLoader loader = newLoader();

        assertEquals("退换货",
                ReflectionTestUtils.invokeMethod(loader, "parseTopic", "售后规则 - 退换货.md"));
        assertEquals("商品与价保",
                ReflectionTestUtils.invokeMethod(loader, "parseTopic", "售后规则 - 商品与价保.md"));
        assertEquals("B",
                ReflectionTestUtils.invokeMethod(loader, "parseTopic", "规则 - A - B.md"));
        assertEquals("售后规则",
                ReflectionTestUtils.invokeMethod(loader, "parseTopic", "售后规则.md"));
    }
}
