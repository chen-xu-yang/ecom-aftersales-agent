package com.aftersales.mcp.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * MCP 图片检索工具测试：SearchAPI Google Images 解析、数量限制与错误兜底。
 * <p>
 * 用 Mockito 静态 mock 掉 Hutool HTTP 调用，不发起真实网络请求，也不消耗 SearchAPI 配额。
 */
@DisplayName("MCP 图片检索工具")
class ImageSearchToolTest {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private static String imagesJson(int count) {
        JSONArray images = new JSONArray();
        for (int i = 0; i < count; i++) {
            JSONObject image = new JSONObject();
            image.set("position", i + 1);
            image.set("title", "图片" + i);
            if (i % 2 == 0) {
                JSONObject original = new JSONObject()
                        .set("link", "https://img.example.com/" + i + ".jpg")
                        .set("width", 800)
                        .set("height", 600);
                image.set("original", original);
            } else {
                image.set("original", "https://img.example.com/" + i + ".jpg");
            }
            image.set("thumbnail", "https://thumb.example.com/" + i + ".jpg");
            images.add(image);
        }
        return new JSONObject().set("images", images).toString();
    }

    @Test
    @DisplayName("解析 original.link / original 字符串，最多返回 5 条")
    void parsesImagesAndLimitsToFive() {
        ImageSearchTool tool = new ImageSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn(imagesJson(7));

            List<String> urls = tool.searchImageUrls("快递外包装破损");

            assertEquals(5, urls.size(), "最多返回 5 条");
            assertEquals("https://img.example.com/0.jpg", urls.get(0));
            assertEquals("https://img.example.com/4.jpg", urls.get(4));
        }
    }

    @Test
    @DisplayName("original 缺失时回退到 thumbnail")
    void fallsBackToThumbnail() {
        ImageSearchTool tool = new ImageSearchTool("test-key");
        String json = "{\"images\":[{\"thumbnail\":\"https://thumb/1.jpg\"},"
                + "{\"original\":null,\"thumbnail\":\"https://thumb/2.jpg\"}]}";
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn(json);

            assertEquals(List.of("https://thumb/1.jpg", "https://thumb/2.jpg"), tool.searchImageUrls("破损"));
        }
    }

    @Test
    @DisplayName("searchImage 用逗号拼接图片 URL")
    void joinsUrlsWithComma() {
        ImageSearchTool tool = new ImageSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn(imagesJson(2));

            String result = tool.searchImage("外包装破损");

            assertEquals("https://img.example.com/0.jpg,https://img.example.com/1.jpg", result);
        }
    }

    @Test
    @DisplayName("没有图片时返回空列表与空字符串")
    void emptyResults() {
        ImageSearchTool tool = new ImageSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn("{\"images\":[]}");

            assertTrue(tool.searchImageUrls("非常冷门的关键词").isEmpty());
            assertEquals("", tool.searchImage("非常冷门的关键词"));
        }
    }

    @Test
    @DisplayName("API 返回 error 时返回可读错误")
    void apiError() {
        ImageSearchTool tool = new ImageSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap()))
                    .thenReturn("{\"error\":\"Invalid API key\"}");

            String result = tool.searchImage("破损");

            assertTrue(result.startsWith("Error search image:"), "实际：" + result);
            assertTrue(result.contains("Invalid API key"), "实际：" + result);
        }
    }

    @Test
    @DisplayName("未配置 Key 时不发请求，直接返回错误")
    void missingKey() {
        ImageSearchTool tool = new ImageSearchTool("");

        String result = tool.searchImage("破损");

        assertTrue(result.startsWith("Error search image:"), "实际：" + result);
        assertTrue(result.contains("search-api.api-key"), "实际：" + result);
    }

    @Test
    @DisplayName("请求参数包含 engine=google_images、q、api_key、gl、hl")
    void sendsExpectedParameters() {
        ImageSearchTool tool = new ImageSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn(imagesJson(1));

            tool.searchImageUrls("快递破损");

            mocked.verify(() -> HttpUtil.get(eq(SEARCH_API_URL), argThat(
                    (Map<String, Object> params) -> "google_images".equals(params.get("engine"))
                            && "快递破损".equals(params.get("q"))
                            && "test-key".equals(params.get("api_key"))
                            && "cn".equals(params.get("gl"))
                            && "zh-cn".equals(params.get("hl")))));
        }
    }

    @Test
    @DisplayName("解析异常时返回错误文案而不是抛出")
    void malformedResponse() {
        ImageSearchTool tool = new ImageSearchTool("test-key");
        try (MockedStatic<HttpUtil> mocked = mockStatic(HttpUtil.class)) {
            mocked.when(() -> HttpUtil.get(anyString(), anyMap())).thenReturn("not-json");

            String result = tool.searchImage("破损");

            assertTrue(result.startsWith("Error search image:"), "实际：" + result);
            assertFalse(result.contains("not-json,not-json"));
        }
    }
}
