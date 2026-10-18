package com.aftersales.mcp.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONNull;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品/售后参考图检索工具。
 * <p>
 * 使用 SearchAPI 的 Google Images 引擎，与主项目的联网搜索共用同一个 SearchAPI Key，
 * 不再依赖 Pexels（Pexels 申请受限时的替代方案）。
 */
@Service
public class ImageSearchTool {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private static final int MAX_RESULTS = 5;

    private final String apiKey;

    public ImageSearchTool(@Value("${search-api.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search reference images from Google Images")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        try {
            return String.join(",", searchImageUrls(query));
        } catch (Exception e) {
            return "Error search image: " + e.getMessage();
        }
    }

    /**
     * 调用 SearchAPI Google Images 并解析图片 URL。
     *
     * @param query 检索关键词
     * @return 最多 5 条图片 URL（优先原图，缺失时回退缩略图）
     */
    public List<String> searchImageUrls(String query) {
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("search-api.api-key is not configured");
        }
        if (StrUtil.isBlank(query)) {
            throw new IllegalArgumentException("search query must not be blank");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("engine", "google_images");
        params.put("q", query);
        params.put("api_key", apiKey);
        // 中文场景优先，提升商品/售后相关图片的相关性
        params.put("gl", "cn");
        params.put("hl", "zh-cn");

        String response = HttpUtil.get(SEARCH_API_URL, params);
        JSONObject json = JSONUtil.parseObj(response);
        if (json.containsKey("error")) {
            throw new IllegalStateException(json.getStr("error"));
        }

        JSONArray images = json.getJSONArray("images");
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        List<String> urls = new ArrayList<>();
        for (Object item : images) {
            if (urls.size() >= MAX_RESULTS) {
                break;
            }
            if (!(item instanceof JSONObject image)) {
                continue;
            }
            String url = image.isNull("original") ? null : extractOriginalUrl(image.get("original"));
            if (StrUtil.isBlank(url)) {
                url = image.getStr("thumbnail");
            }
            if (StrUtil.isNotBlank(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    /**
     * Google Images 的 original 字段有两种形态：对象（含 link/width/height）或字符串。
     */
    private String extractOriginalUrl(Object original) {
        if (original == null || original instanceof JSONNull) {
            return null;
        }
        if (original instanceof JSONObject originalObject) {
            return originalObject.getStr("link");
        }
        String value = String.valueOf(original);
        return "null".equalsIgnoreCase(value) ? null : value;
    }
}
