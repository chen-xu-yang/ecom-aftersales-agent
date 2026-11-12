package com.aftersales.agent.rag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;

/**
 * 文档加载离线单测：直接读取 classpath:document/*.md，不启动 Spring 上下文。
 * 覆盖文档加载与 topic 元数据注入（RAG 召回过滤依赖该元数据）。
 */
class AftersalesDocumentLoaderTest {

    @Test
    void loadMarkdowns_returnsDocumentsWithTopicMetadata() {
        AftersalesDocumentLoader loader =
                new AftersalesDocumentLoader(new PathMatchingResourcePatternResolver());

        List<Document> documents = loader.loadMarkdowns();

        Assertions.assertFalse(documents.isEmpty(), "should load at least one document");
        Assertions.assertTrue(
                documents.stream().allMatch(d -> d.getMetadata().containsKey("topic")),
                "every document should carry topic metadata");
        Assertions.assertTrue(
                documents.stream()
                        .map(d -> String.valueOf(d.getMetadata().get("topic")))
                        .anyMatch(topic -> topic.contains("退换货")),
                "should cover 退换货 knowledge base");
    }
}
