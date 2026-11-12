package com.aftersales.agent.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评测集离线自检测试：不依赖模型与网络。
 * <p>
 * 校验 40 条评测样本的结构完整性，并逐条核验「期望片段」确实存在于知识库文档中，
 * 防止知识库改动后评测集悄悄失真（这正是评测可信度的基础）。
 */
class EvalDatasetTest {

    private static final String DATASET = "eval/aftersales_eval.jsonl";

    private static final List<JsonNode> entries = new ArrayList<>();
    private static final Map<String, String> docs = new HashMap<>();

    @BeforeAll
    static void loadDatasetAndDocs() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        InputStream in = EvalDatasetTest.class.getClassLoader().getResourceAsStream(DATASET);
        assertNotNull(in, "评测集文件不存在: " + DATASET);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    entries.add(mapper.readTree(line));
                }
            }
        }

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (Resource resource : resolver.getResources("classpath:document/*.md")) {
            try (InputStream docIn = resource.getInputStream()) {
                docs.put(resource.getFilename(),
                        new String(docIn.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void dataset_has40UniqueEntries() {
        assertEquals(40, entries.size(), "评测集应固定为 40 条");
        Set<String> ids = new HashSet<>();
        for (JsonNode entry : entries) {
            assertTrue(ids.add(entry.path("id").asText()), "重复 id: " + entry.path("id").asText());
        }
    }

    @Test
    void everyEntry_hasRequiredFields() {
        for (JsonNode entry : entries) {
            String id = entry.path("id").asText();
            assertFalse(entry.path("query").asText().isBlank(), id + " 缺少 query");
            assertFalse(entry.path("category").asText().isBlank(), id + " 缺少 category");
            assertTrue(entry.hasNonNull("answerable"), id + " 缺少 answerable");
        }
    }

    @Test
    void answerableEntries_referenceExistingSectionsAndPhrases() {
        int answerable = 0;
        for (JsonNode entry : entries) {
            if (!entry.path("answerable").asBoolean()) {
                continue;
            }
            answerable++;
            String id = entry.path("id").asText();
            String doc = entry.path("expected_doc").asText();
            String section = entry.path("expected_section").asText();

            assertTrue(docs.containsKey(doc), id + " 期望文档不存在: " + doc);
            String text = docs.get(doc);
            assertTrue(text.contains(section), id + " 期望小节不存在于文档: " + section);

            JsonNode phrases = entry.path("key_phrases");
            assertTrue(phrases.isArray() && phrases.size() > 0, id + " 缺少 key_phrases");
            for (JsonNode phrase : phrases) {
                assertTrue(text.contains(phrase.asText()),
                        id + " 关键句不在知识库中: " + phrase.asText());
            }
        }
        assertTrue(answerable >= 34, "可回答样本过少: " + answerable);
    }

    @Test
    void unanswerableEntries_haveNoExpectedDoc() {
        int unanswerable = 0;
        for (JsonNode entry : entries) {
            if (entry.path("answerable").asBoolean()) {
                continue;
            }
            unanswerable++;
            assertTrue(entry.path("expected_doc").isNull(),
                    entry.path("id").asText() + " 转人工样本不应有期望文档");
        }
        assertTrue(unanswerable >= 4, "转人工样本过少: " + unanswerable);
    }

    @Test
    void dataset_coversAllKnowledgeTopics() {
        Set<String> expectedDocs = new HashSet<>();
        Set<String> sections = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (JsonNode entry : entries) {
            categories.add(entry.path("category").asText());
            if (entry.path("answerable").asBoolean()) {
                expectedDocs.add(entry.path("expected_doc").asText());
                sections.add(entry.path("expected_section").asText());
            }
        }
        assertEquals(3, expectedDocs.size(), "应覆盖 3 份知识库文档");
        assertTrue(sections.size() >= 14, "应覆盖全部 14 个规则小节，当前: " + sections.size());
        assertTrue(categories.containsAll(Set.of("退换货", "物流", "价保", "发票", "赔付", "质量", "转人工")),
                "分类覆盖不全: " + categories);
    }
}
