package com.aftersales.agent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实联网测试：会调用 SearchAPI，默认跳过。
 * 需要真实 Key 时：RUN_REAL_AI_TESTS=true mvn -Dtest=WebSearchToolTest test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_REAL_AI_TESTS", matches = "true")
class WebSearchToolTest {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Test
    void searchWeb() {
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        String query = "快递保价商品运输损坏 理赔规则";
        String result = webSearchTool.searchWeb(query);
        Assertions.assertNotNull(result);
    }
}
