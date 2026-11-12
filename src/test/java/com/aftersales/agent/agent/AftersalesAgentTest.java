package com.aftersales.agent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实模型集成测试：完整跑一轮智能体（含工具调用），默认跳过。
 * 需要真实 Key 时：RUN_REAL_AI_TESTS=true mvn -Dtest=AftersalesAgentTest test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_REAL_AI_TESTS", matches = "true")
class AftersalesAgentTest {

    @Resource
    private AftersalesAgent aftersalesAgent;

    @Test
    public void run() {
        String userPrompt = """
                我上周买的商品到货后发现外包装破损，商品本身也有划痕，
                请帮我梳理一下处理方案，把过程记录保存为文件，
                并生成一份售后处理说明文档（PDF）""";
        String answer = aftersalesAgent.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}
