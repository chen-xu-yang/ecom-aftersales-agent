package com.aftersales.agent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
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
