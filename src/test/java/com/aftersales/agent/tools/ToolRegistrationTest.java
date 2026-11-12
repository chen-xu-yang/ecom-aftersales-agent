package com.aftersales.agent.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 工具注册测试：校验暴露给模型的工具集合（PROJECT_CONTEXT §5.4）。
 * <p>
 * 该用例同时是安全回归：已移除的终端执行、任意 URL 抓取等能力不得被重新注册。
 */
@DisplayName("工具注册")
class ToolRegistrationTest {

    private ToolCallback[] registerTools() {
        ToolRegistration registration = new ToolRegistration();
        ReflectionTestUtils.setField(registration, "searchApiKey", "test-key");
        return registration.allTools(mock(VectorStore.class));
    }

    @Test
    @DisplayName("共注册 6 个工具，名称与文档一致")
    void registersExpectedToolSet() {
        ToolCallback[] tools = registerTools();
        assertNotNull(tools);
        assertEquals(6, tools.length);

        Set<String> names = Arrays.stream(tools)
                .map(tool -> tool.getToolDefinition().name())
                .collect(Collectors.toSet());
        assertEquals(Set.of("readFile", "writeFile", "searchWeb", "generatePDF", "doTerminate", "searchPolicy"), names);
    }

    @Test
    @DisplayName("每个工具都有非空描述与非空入参 schema")
    void everyToolHasDefinition() {
        for (ToolCallback tool : registerTools()) {
            String name = tool.getToolDefinition().name();
            assertFalse(tool.getToolDefinition().description().isBlank(), name + " 缺少描述");
            assertFalse(tool.getToolDefinition().inputSchema().isBlank(), name + " 缺少入参 schema");
            assertTrue(tool.getToolDefinition().inputSchema().trim().startsWith("{"),
                    name + " 的 schema 应为 JSON 对象，实际：" + tool.getToolDefinition().inputSchema());
        }
    }

    @Test
    @DisplayName("安全回归：不得注册终端执行、下载、任意 URL 抓取等危险工具")
    void doesNotExposeDangerousTools() {
        Set<String> names = Arrays.stream(registerTools())
                .map(tool -> tool.getToolDefinition().name().toLowerCase())
                .collect(Collectors.toSet());
        assertFalse(names.stream().anyMatch(name -> name.contains("terminal") || name.contains("exec")
                || name.contains("shell") || name.contains("download") || name.contains("scrape")),
                "发现潜在危险工具：" + names);
    }
}
