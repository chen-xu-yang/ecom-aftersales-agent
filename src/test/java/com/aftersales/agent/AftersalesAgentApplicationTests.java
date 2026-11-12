package com.aftersales.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实模型集成测试：会启动完整 Spring 上下文并调用 DashScope，默认跳过。
 * 需要真实 Key 时：RUN_REAL_AI_TESTS=true mvn -Dtest=AftersalesAgentApplicationTests test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_REAL_AI_TESTS", matches = "true")
class AftersalesAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
