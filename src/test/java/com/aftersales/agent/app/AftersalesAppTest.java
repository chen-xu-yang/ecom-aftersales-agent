package com.aftersales.agent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class AftersalesAppTest {

    @Resource
    private AftersalesApp aftersalesApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我上周买的商品到现在还没有发货";
        String answer = aftersalesApp.doChat(message, chatId);
        // 第二轮
        message = "订单号是 20250815001，麻烦帮我查一下什么时候能发出";
        answer = aftersalesApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮：验证对话记忆
        message = "我刚才给你的订单号是多少？帮我回忆一下";
        answer = aftersalesApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "订单 20250815001 的商品到货后发现外包装破损、外壳有划痕，我想申请换货，该怎么做";
        AftersalesApp.AftersalesReport report = aftersalesApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(report);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "商品吊牌已经剪掉了，还能申请退换货吗？";
        String answer = aftersalesApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索：查外部政策
        testMessage("快递保价商品在运输途中损坏，理赔规则是怎么规定的？");

        // 测试文件操作：保存售后处理记录
        testMessage("把这次售后处理的过程记录保存为文件");

        // 测试 PDF 生成：输出售后处理单
        testMessage("生成一份售后处理单 PDF，包含订单号、问题描述、处理方案和预计时效");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = aftersalesApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        // 测试图片搜索 MCP：为售后凭证提供参考图
        String message = "帮我搜索一些商品外包装破损的参考图片";
        String answer = aftersalesApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
