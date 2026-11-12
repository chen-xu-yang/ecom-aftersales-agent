package com.aftersales.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置契约测试（PROJECT_CONTEXT §3、§6）：
 * 端口、接口前缀、模型名与“受版本控制的配置不得包含真实密钥”。
 * <p>
 * 直接读取源码目录下的 application.yml，不启动 Spring 容器，避免触发知识库初始化。
 */
@DisplayName("应用配置契约")
class ApplicationConfigTest {

    private static String mainConfig() throws IOException {
        return Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("后端端口 8123、接口前缀 /api 与文档一致")
    void serverSettingsMatchDocumentation() throws IOException {
        String yaml = mainConfig();
        assertTrue(yaml.contains("port: 8123"), "端口应为 8123");
        assertTrue(yaml.contains("context-path: /api"), "接口前缀应为 /api");
    }

    @Test
    @DisplayName("默认模型为 qwen-plus，默认 profile 为 local")
    void modelSettingsMatchDocumentation() throws IOException {
        String yaml = mainConfig();
        assertTrue(yaml.contains("model: qwen-plus"), "对话模型应为 qwen-plus");
        assertTrue(yaml.contains("active: local"), "默认 profile 应为 local");
    }

    @Test
    @DisplayName("安全：提交到仓库的配置只允许占位密钥")
    void committedConfigHasNoRealSecrets() throws IOException {
        String yaml = mainConfig();
        String lower = yaml.toLowerCase();

        assertFalse(lower.contains("sk-"), "application.yml 中出现了疑似真实 DashScope Key");
        assertTrue(yaml.contains("your-api-key"), "DashScope 应使用占位值");
        assertTrue(yaml.contains("你的 API Key"), "SearchAPI 应使用占位值");
    }

    @Test
    @DisplayName("本地密钥文件不得被 Git 跟踪")
    void localSecretFileIsIgnored() throws IOException {
        String gitignore = Files.readString(Path.of(".gitignore"), StandardCharsets.UTF_8);
        assertTrue(gitignore.lines().anyMatch(line -> line.trim().equals("application-local.yml")),
                ".gitignore 必须忽略 application-local.yml");
    }
}
