package com.aftersales.agent.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 检索真实评测：启动完整 Spring 上下文（DashScope 向量化 + 知识库入库），
 * 对 40 条评测样本计算 hit@k / MRR / 阈值敏感度，并输出 Markdown 报告。
 * <p>
 * 运行方式（需要 application-local.yml 中配置真实 DashScope Key）：
 * <pre>
 *   RUN_RETRIEVAL_EVAL=true mvn -o test -Dtest=RetrievalEvalTest
 * </pre>
 * 报告输出：target/eval/retrieval-report.md
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_RETRIEVAL_EVAL", matches = "true")
class RetrievalEvalTest {

    private static final int[] K_VALUES = {1, 3, 4, 5, 8};
    private static final double[] THRESHOLDS = {0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60};
    private static final int PROD_TOP_K = 4;
    private static final double PROD_THRESHOLD = 0.4;

    @Autowired
    @Qualifier("aftersalesVectorStore")
    private VectorStore vectorStore;

    @Test
    void evaluateRetrieval() throws Exception {
        List<EvalSupport.Entry> entries = EvalSupport.loadEntries();
        List<EvalSupport.Entry> answerable = entries.stream().filter(EvalSupport.Entry::answerable).toList();
        List<EvalSupport.Entry> fallback = entries.stream().filter(e -> !e.answerable()).toList();

        // ---------- 1. 排名质量：threshold=0，取 top8，按排名截断计算 hit@k ----------
        Map<String, Integer> firstHitRank = new LinkedHashMap<>();
        Map<String, String> queryById = new LinkedHashMap<>();
        int[] hitsAtK = new int[K_VALUES.length];
        double mrrSum = 0.0;
        for (EvalSupport.Entry entry : answerable) {
            List<Document> docs = search(entry.query(), 8, 0.0);
            int rank = firstHitRank(docs, entry.keyPhrases());
            firstHitRank.put(entry.id(), rank);
            queryById.put(entry.id(), entry.query());
            if (rank > 0) {
                mrrSum += 1.0 / rank;
            }
            for (int i = 0; i < K_VALUES.length; i++) {
                if (rank > 0 && rank <= K_VALUES[i]) {
                    hitsAtK[i]++;
                }
            }
        }

        // ---------- 2. 阈值敏感度：topK=4 固定，扫描阈值 ----------
        List<double[]> thresholdRows = new ArrayList<>(); // [threshold, hit, emptyFallback]
        for (double threshold : THRESHOLDS) {
            int hit = 0;
            for (EvalSupport.Entry entry : answerable) {
                List<Document> docs = search(entry.query(), PROD_TOP_K, threshold);
                if (firstHitRank(docs, entry.keyPhrases()) > 0) {
                    hit++;
                }
            }
            int emptyFallback = 0;
            for (EvalSupport.Entry entry : fallback) {
                List<Document> docs = search(entry.query(), PROD_TOP_K, threshold);
                if (docs.isEmpty()) {
                    emptyFallback++;
                }
            }
            thresholdRows.add(new double[]{threshold, hit, emptyFallback});
        }

        // ---------- 3. 分类别 hit@4（threshold=PROD_THRESHOLD, topK=4） ----------
        Map<String, int[]> categoryHit = new LinkedHashMap<>();
        for (EvalSupport.Entry entry : answerable) {
            List<Document> docs = search(entry.query(), PROD_TOP_K, PROD_THRESHOLD);
            boolean hit = firstHitRank(docs, entry.keyPhrases()) > 0;
            int[] stat = categoryHit.computeIfAbsent(entry.category(), k -> new int[2]);
            stat[0] += hit ? 1 : 0;
            stat[1] += 1;
        }

        // ---------- 3.1 转人工样本明细（threshold=0.4 下仍被召回的情况） ----------
        Map<String, Integer> fallbackDetail = new LinkedHashMap<>();
        for (EvalSupport.Entry entry : fallback) {
            fallbackDetail.put(entry.query(), search(entry.query(), PROD_TOP_K, PROD_THRESHOLD).size());
        }

        // ---------- 4. 生成报告 ----------
        String report = buildReport(answerable.size(), fallback.size(), hitsAtK, mrrSum,
                thresholdRows, categoryHit, firstHitRank, queryById, fallbackDetail);
        Path out = Path.of("target", "eval", "retrieval-report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);

        String prodHit = String.valueOf(hitsAtK[indexOfK(PROD_TOP_K) - 1]);
        System.out.println("[EVAL] answerable=%d fallback=%d hit@4(no threshold)=%s MRR@8=%.4f report=%s"
                .formatted(answerable.size(), fallback.size(), prodHit,
                        mrrSum / Math.max(1, answerable.size()), out.toAbsolutePath()));
        assertTrue(hitsAtK[indexOfK(4) - 1] > 0, "检索完全未命中，检查向量库与 Key 配置");
    }

    private List<Document> search(String query, int topK, double threshold) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build());
    }

    /** 返回第一条命中期望片段的排名（1 开始），0 表示未命中。 */
    private int firstHitRank(List<Document> docs, List<String> phrases) {
        for (int i = 0; i < docs.size(); i++) {
            String text = docs.get(i).getText();
            if (text == null) {
                continue;
            }
            for (String phrase : phrases) {
                if (text.contains(phrase)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    private int indexOfK(int k) {
        for (int i = 0; i < K_VALUES.length; i++) {
            if (K_VALUES[i] == k) {
                return i + 1;
            }
        }
        return 1;
    }

    private String buildReport(int answerable, int fallback, int[] hitsAtK, double mrrSum,
                               List<double[]> thresholdRows, Map<String, int[]> categoryHit,
                               Map<String, Integer> firstHitRank, Map<String, String> queryById,
                               Map<String, Integer> fallbackDetail) {
        StringBuilder sb = new StringBuilder();
        sb.append("# RAG 检索评测报告\n\n");
        sb.append("> 生成时间：").append(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("> 样本：").append(answerable).append(" 条可回答 + ")
                .append(fallback).append(" 条转人工兜底\n");
        sb.append("> 指标定义：命中 = 检索片段文本包含该题的期望关键句\n\n");

        sb.append("## 1. 排名质量（threshold=0，按 topK 截断）\n\n");
        sb.append("| k | hit@k | 命中率 |\n|---|---:|---:|\n");
        for (int i = 0; i < K_VALUES.length; i++) {
            sb.append("| ").append(K_VALUES[i]).append(" | ").append(hitsAtK[i])
                    .append(" | ").append(pct(hitsAtK[i], answerable)).append(" |\n");
        }
        sb.append("\nMRR@8：**").append(String.format("%.4f", mrrSum / Math.max(1, answerable)))
                .append("**\n\n");

        sb.append("## 2. 阈值敏感度（topK=").append(PROD_TOP_K).append("）\n\n");
        sb.append("| threshold | hit@4 | 命中率 | 转人工题空检索率 |\n|---|---:|---:|---:|\n");
        for (double[] row : thresholdRows) {
            sb.append("| ").append(row[0]).append(" | ").append((int) row[1])
                    .append(" | ").append(pct((int) row[1], answerable))
                    .append(" | ").append(pct((int) row[2], fallback)).append(" |\n");
        }
        sb.append("\n");

        sb.append("## 3. 分类别 hit@4（threshold=").append(PROD_THRESHOLD)
                .append("，topK=").append(PROD_TOP_K).append("）\n\n");
        sb.append("| 分类 | 命中/总数 | 命中率 |\n|---|---:|---:|\n");
        for (Map.Entry<String, int[]> e : categoryHit.entrySet()) {
            sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()[0])
                    .append("/").append(e.getValue()[1]).append(" | ")
                    .append(pct(e.getValue()[0], e.getValue()[1])).append(" |\n");
        }
        sb.append("\n");

        sb.append("## 3.1 转人工样本明细（threshold=").append(PROD_THRESHOLD)
                .append(" 下仍被召回的情况，返回 0 条为正确兜底）\n\n");
        sb.append("| query | 返回条数 |\n|---|---:|\n");
        for (Map.Entry<String, Integer> e : fallbackDetail.entrySet()) {
            sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }
        sb.append("\n");

        // 推荐配置：优先命中率，其次转人工空检索率
        double[] best = thresholdRows.get(0);
        for (double[] row : thresholdRows) {
            if (row[1] > best[1] || (row[1] == best[1] && row[2] > best[2])) {
                best = row;
            }
        }
        sb.append("## 3.2 调参结论\n\n");
        sb.append("- 当前线上配置：topK=").append(PROD_TOP_K)
                .append("，threshold=").append(PROD_THRESHOLD).append("\n");
        sb.append("- 评测推荐：topK=").append(PROD_TOP_K)
                .append("，threshold=").append(best[0])
                .append("（hit@4=").append(pct((int) best[1], answerable))
                .append("，转人工空检索率=").append(pct((int) best[2], fallback)).append("）\n\n");

        sb.append("## 4. 明细\n\n");
        sb.append("| id | query | 首命中排名 |\n|---|---|---:|\n");
        for (Map.Entry<String, Integer> e : firstHitRank.entrySet()) {
            sb.append("| ").append(e.getKey()).append(" | ").append(queryById.get(e.getKey()))
                    .append(" | ").append(e.getValue() == 0 ? "未命中" : e.getValue())
                    .append(" |\n");
        }
        return sb.toString();
    }

    private String pct(int part, int total) {
        return total == 0 ? "-" : String.format("%.1f%%", part * 100.0 / total);
    }
}
