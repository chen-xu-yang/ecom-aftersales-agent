package com.aftersales.agent.tools;

import cn.hutool.core.collection.CollUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 售后规则检索工具
 * <p>
 * 把 RAG 检索能力作为工具暴露给智能体，由模型自主决定检索时机：
 * 涉及政策细节的回答必须依据检索到的规则原文，避免模型凭记忆编造。
 */
public class AftersalesPolicyTool {

    private static final int TOP_K = 4;

    private static final double SIMILARITY_THRESHOLD = 0.4;

    private final VectorStore vectorStore;

    public AftersalesPolicyTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = """
            检索平台售后规则。涉及退换货条件、时效、运费承担、赔付标准、价保、发票等政策问题时，
            必须先调用本工具获取规则原文，再依据返回内容回答，不要凭记忆作答。
            """)
    public String searchPolicy(
            @ToolParam(description = "检索问题，尽量具体，例如：吊牌剪掉能否无理由退货") String query) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(TOP_K)
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .build()
        );
        if (CollUtil.isEmpty(documents)) {
            return "未检索到相关售后规则，请提示用户转人工客服确认。";
        }
        return documents.stream()
                .map(this::formatDocument)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 格式化检索结果，保留分类标签便于模型引用
     */
    private String formatDocument(Document document) {
        Object topic = document.getMetadata().get("topic");
        return "【" + (topic == null ? "售后规则" : topic) + "】\n" + document.getText();
    }
}
