package com.aftersales.agent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Bean
    public ToolCallback[] allTools(@Qualifier("aftersalesVectorStore") VectorStore aftersalesVectorStore) {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();
        // 售后规则检索工具，让智能体按需检索知识库
        AftersalesPolicyTool policyTool = new AftersalesPolicyTool(aftersalesVectorStore);
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                pdfGenerationTool,
                terminateTool,
                policyTool
        );
    }
}
