package com.aftersales.agent.tools;

import cn.hutool.http.HttpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * WebSearchTool 规格测试。
 * <p>
 * 用 Mockito 静态 mock 掉 Hutool 的 HTTP 调用，验证结果解析与异常兜底，
 * 不发起真实网络请求，也不消耗 SearchAPI 配额。
 */
@DisplayName("联网搜索工具")
class WebSearchToolSpecTest {

    private static final String[] TITLES = {"理赔规则", "保价说明", "运输破损处理", "快递赔付", "售后政策"};

    private static String responseJson(int resultCount) {
        String results = IntStream.range(0, resultCount)
                .mapToObj(i -> "{\"title\":\"" + TITLES[i % TITLES.length] + i + "\",\"link\":\"https://example.com/" + i + "\"}")
                .collect(Collectors.joining(","));
        return "{\"organic_results\":[" + results + "]}";
    }

    @Test
    @DisplayName("返回 5 条以上结果时取前 5 条并拼接")
    void returnsTopFiveResults() {
        WebSearchTool tool = new WebSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn(responseJson(8));

            String result = tool.searchWeb("快递保价 理赔规则");

            assertFalse(result.startsWith("Error searching Baidu:"), "实际：" + result);
            assertTrue(result.contains("理赔规则0"), "应包含第 1 条结果");
            assertTrue(result.contains("售后政策4"), "应包含第 5 条结果");
            assertFalse(result.contains("售后政策5"), "超过 5 条的结果不应返回");
        }
    }

    @Test
    @DisplayName("结果不足 5 条时应返回全部结果，而不是整段搜索失败")
    void returnsAllResultsWhenFewerThanFive() {
        WebSearchTool tool = new WebSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn(responseJson(3));

            String result = tool.searchWeb("物流停滞 处理方式");

            assertFalse(result.startsWith("Error searching Baidu:"),
                    "结果数少于 5 时不应报错，实际：" + result);
            assertTrue(result.contains("理赔规则0"));
            assertTrue(result.contains("保价说明1"));
            assertTrue(result.contains("运输破损处理2"));
        }
    }

    @Test
    @DisplayName("结果为空时返回“未搜索到相关结果”，而不是报错")
    void returnsNoResultMessageWhenEmpty() {
        WebSearchTool tool = new WebSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn(responseJson(0));

            String result = tool.searchWeb("非常冷门的售后问题");

            assertFalse(result.startsWith("Error searching Baidu:"), "空结果不应按错误处理，实际：" + result);
            assertTrue(result.contains("未搜索到相关结果"), "实际：" + result);
        }
    }

    @Test
    @DisplayName("HTTP 调用异常时返回错误文案")
    void returnsErrorMessageOnHttpFailure() {
        WebSearchTool tool = new WebSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap()))
                    .thenThrow(new RuntimeException("connect timed out"));

            String result = tool.searchWeb("任意查询");

            assertTrue(result.startsWith("Error searching Baidu:"), "实际：" + result);
            assertTrue(result.contains("connect timed out"));
        }
    }

    @Test
    @DisplayName("返回体缺少 organic_results 时返回错误文案")
    void returnsErrorMessageOnUnexpectedPayload() {
        WebSearchTool tool = new WebSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn("{\"error\":\"invalid api key\"}");

            String result = tool.searchWeb("任意查询");

            assertTrue(result.startsWith("Error searching Baidu:"), "实际：" + result);
        }
    }

    @Test
    @DisplayName("请求参数应携带 q、api_key 与 engine=baidu")
    void sendsExpectedParameters() {
        WebSearchTool tool = new WebSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn(responseJson(1));

            tool.searchWeb("吊牌剪了能退吗");

            mocked.verify(() -> HttpUtil.get(anyString(), org.mockito.ArgumentMatchers.argThat(
                    (Map<String, Object> params) -> "吊牌剪了能退吗".equals(params.get("q"))
                            && "test-key".equals(params.get("api_key"))
                            && "baidu".equals(params.get("engine")))));
        }
    }
}
