package com.aftersales.agent.tools;

import cn.hutool.core.io.FileUtil;
import com.aftersales.agent.constant.FileConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileOperationTool 规格测试。
 * <p>
 * 覆盖：正常读写、UTF-8 中文、错误提示，以及 PROJECT_CONTEXT §5.4 要求的“路径校验”边界。
 */
@DisplayName("文件读写工具")
class FileOperationToolSpecTest {

    private final FileOperationTool tool = new FileOperationTool();

    private static String fileDir() {
        return FileConstant.FILE_SAVE_DIR + "/file";
    }

    private static void deleteQuietly(String path) {
        try {
            FileUtil.del(path);
        } catch (Exception ignored) {
            // 清理失败不影响断言结果
        }
    }

    @Test
    @DisplayName("写入后可以读回，中文与换行保持原样")
    void writeThenReadRoundTrip() {
        String fileName = "用例-" + UUID.randomUUID() + ".txt";
        String content = "订单号：20250815001\n问题：外包装破损\n处理方案：换货";
        String filePath = fileDir() + "/" + fileName;
        try {
            String writeResult = tool.writeFile(fileName, content);
            assertTrue(writeResult.startsWith("File written successfully to:"), "实际：" + writeResult);
            assertTrue(new File(filePath).isFile(), "文件应落在 tmp/file 下");

            String readResult = tool.readFile(fileName);
            assertEquals(content, readResult, "读回内容应与写入一致（UTF-8）");
        } finally {
            deleteQuietly(filePath);
        }
    }

    @Test
    @DisplayName("写入落在 tmp/file 目录，并保证目录存在")
    void writeTargetsFileDir() {
        File dir = new File(fileDir());
        String fileName = "目录-" + UUID.randomUUID() + ".txt";
        String filePath = fileDir() + "/" + fileName;
        try {
            String result = tool.writeFile(fileName, "内容");
            assertTrue(result.startsWith("File written successfully to:"), "实际：" + result);
            assertTrue(dir.isDirectory(), "tmp/file 目录应存在");
            assertTrue(new File(filePath).isFile(), "文件应落在 tmp/file 下：" + filePath);
        } finally {
            deleteQuietly(filePath);
        }
    }

    @Test
    @DisplayName("读取不存在的文件返回错误文案而不是抛异常")
    void readMissingFileReturnsError() {
        String result = tool.readFile("不存在的-" + UUID.randomUUID() + ".txt");
        assertTrue(result.startsWith("Error reading file:"), "实际：" + result);
    }

    @Test
    @DisplayName("写入失败（非法路径）返回错误文案而不是抛异常")
    void writeInvalidPathReturnsError() {
        String result = tool.writeFile("非法\0名称.txt", "内容");
        assertTrue(result.startsWith("Error writing to file:"), "实际：" + result);
    }

    @Test
    @DisplayName("安全：读取必须限制在 tmp/file 内，禁止 ../ 路径穿越")
    void readFileRejectsPathTraversal() {
        // ../../pom.xml 会解析到项目根目录的 pom.xml；PROJECT_CONTEXT §5.4 明确要求限制可访问资源
        String result = tool.readFile("../../pom.xml");
        assertTrue(result.startsWith("Error reading file:"),
                "路径穿越未被拦截，实际返回：" + abbreviate(result));
        assertFalse(result.contains("<project"), "不得读出项目文件内容");
    }

    @Test
    @DisplayName("安全：写入必须限制在 tmp/file 内，禁止 ../ 路径穿越")
    void writeFileRejectsPathTraversal() {
        String fileName = "../../穿越-" + UUID.randomUUID() + ".txt";
        File escapedFile;
        try {
            escapedFile = new File(fileDir(), fileName).getCanonicalFile();
        } catch (Exception e) {
            throw new AssertionError("无法解析测试路径", e);
        }
        try {
            String result = tool.writeFile(fileName, "越界写入");
            assertTrue(result.startsWith("Error writing to file:"),
                    "路径穿越未被拦截，实际返回：" + result);
            assertFalse(escapedFile.exists(), "不应在 tmp/file 之外创建文件：" + escapedFile.getAbsolutePath());
        } finally {
            deleteQuietly(escapedFile.getAbsolutePath());
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 160 ? text : text.substring(0, 160) + "...";
    }

    @Test
    @DisplayName("读取自身写入的中文文件名不受平台编码影响")
    void supportsChineseFileName() {
        String fileName = "售后处理记录-" + UUID.randomUUID() + ".txt";
        String content = "中文内容";
        String filePath = fileDir() + "/" + fileName;
        try {
            tool.writeFile(fileName, content);
            assertEquals(content, tool.readFile(fileName));
            assertEquals(content, FileUtil.readString(new File(filePath), StandardCharsets.UTF_8));
        } finally {
            deleteQuietly(filePath);
        }
    }
}
