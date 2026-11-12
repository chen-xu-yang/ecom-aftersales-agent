package com.aftersales.agent.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 评测集读取工具：供离线自检（EvalDatasetTest）与真实检索评测（RetrievalEvalTest）共用。
 */
final class EvalSupport {

    static final String DATASET = "eval/aftersales_eval.jsonl";

    private EvalSupport() {
    }

    record Entry(
            String id,
            String query,
            String category,
            String difficulty,
            boolean answerable,
            String expectedDoc,
            String expectedSection,
            List<String> keyPhrases
    ) {
    }

    static List<Entry> loadEntries() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Entry> entries = new ArrayList<>();

        InputStream in = EvalSupport.class.getClassLoader().getResourceAsStream(DATASET);
        if (in == null) {
            throw new IllegalStateException("评测集文件不存在: " + DATASET);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = mapper.readTree(line);
                List<String> phrases = new ArrayList<>();
                node.path("key_phrases").forEach(p -> phrases.add(p.asText()));
                entries.add(new Entry(
                        node.path("id").asText(),
                        node.path("query").asText(),
                        node.path("category").asText(),
                        node.path("difficulty").asText(),
                        node.path("answerable").asBoolean(),
                        node.path("expected_doc").isNull() ? null : node.path("expected_doc").asText(),
                        node.path("expected_section").isNull() ? null : node.path("expected_section").asText(),
                        phrases
                ));
            }
        }
        return entries;
    }
}
