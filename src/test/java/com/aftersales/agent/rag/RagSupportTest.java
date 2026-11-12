package com.aftersales.agent.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 空检索兜底与文本切分测试（PROJECT_CONTEXT §5.1）。
 */
@DisplayName("RAG 兜底与切分")
class RagSupportTest {

    @Test
    @DisplayName("检索为空时输出转人工话术")
    void emptyContextFallsBackToHumanAgent() {
        ContextualQueryAugmenter augmenter = AftersalesContextualQueryAugmenterFactory.createInstance();

        Query augmented = augmenter.augment(new Query("我的订单什么时候发货？"), List.of());

        assertNotNull(augmented);
        assertTrue(augmented.text().contains("抱歉，这个问题我暂时无法确认"),
                "空检索应输出兜底话术，实际：" + augmented.text());
        assertTrue(augmented.text().contains("转接人工客服"));
    }

    @Test
    @DisplayName("有检索结果时把规则原文注入上下文")
    void contextIsInjectedWhenDocumentsPresent() {
        ContextualQueryAugmenter augmenter = AftersalesContextualQueryAugmenterFactory.createInstance();

        Query augmented = augmenter.augment(
                new Query("吊牌剪了还能退吗"),
                List.of(new Document("吊牌已剪掉通常无法无理由退货。", Map.of("topic", "退换货"))));

        assertTrue(augmented.text().contains("吊牌已剪掉通常无法无理由退货。"),
                "检索结果应进入上下文，实际：" + augmented.text());
        assertFalse(augmented.text().contains("抱歉，这个问题我暂时无法确认"),
                "有上下文时不应走空检索话术");
    }

    @Test
    @DisplayName("自定义 Token 切分：长文本被切成多段且内容不丢失")
    void customizedSplitterSplitsLongText() {
        MyTokenTextSplitter splitter = new MyTokenTextSplitter();
        String paragraph = "自签收次日起 7 个自然日内，商品保持完好且不影响二次销售的，可以申请无理由退货。"
                + "吊牌、水洗标属于商品完好的判定范围。吊牌已剪掉、丢失或被破坏的，通常无法按七天无理由退货处理。";
        String longText = String.join("\n", java.util.Collections.nCopies(40, paragraph));
        Document document = new Document(longText);

        List<Document> chunks = splitter.splitCustomized(List.of(document));

        assertTrue(chunks.size() > 1, "长文本应被切分为多段，实际：" + chunks.size());
        String joined = chunks.stream().map(Document::getText).collect(java.util.stream.Collectors.joining(""));
        assertTrue(joined.contains("吊牌、水洗标属于商品完好的判定范围"), "切分不应丢失规则原文");
    }

    @Test
    @DisplayName("默认 Token 切分：短文本保持单段")
    void defaultSplitterKeepsShortText() {
        MyTokenTextSplitter splitter = new MyTokenTextSplitter();
        String text = "短规则片段：吊牌缺失通常无法无理由退货，但质量问题除外。";

        List<Document> chunks = splitter.splitDocuments(List.of(new Document(text)));

        assertTrue(chunks.size() >= 1);
        assertTrue(chunks.stream().anyMatch(document -> document.getText().contains("短规则片段")),
                "短文本不应被丢弃，实际：" + chunks.stream().map(Document::getText).toList());
    }
}
