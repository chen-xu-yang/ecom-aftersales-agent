package com.aftersales.agent.tools;

import com.aftersales.agent.constant.FileConstant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

/**
 * 文件工具离线单测：不依赖 Spring 上下文与外部服务。
 * 覆盖写入/读取往返、缺失文件错误文案、越界路径拒绝。
 */
class FileOperationToolTest {

    private final FileOperationTool fileOperationTool = new FileOperationTool();
    private final String fileName = "test-" + System.nanoTime() + ".txt";

    @AfterEach
    void cleanup() throws Exception {
        File file = new File(FileConstant.FILE_SAVE_DIR + "/file", fileName);
        Files.deleteIfExists(file.toPath());
    }

    @Test
    void writeThenRead_roundTrip() {
        String content = "订单号：20250815001；问题：外包装破损；处理方案：换货，预计 2 个工作日发出";

        String writeResult = fileOperationTool.writeFile(fileName, content);
        Assertions.assertTrue(writeResult.startsWith("File written successfully"), writeResult);

        String readResult = fileOperationTool.readFile(fileName);
        Assertions.assertEquals(content, readResult);
    }

    @Test
    void readFile_missing_returnsReadableError() {
        String result = fileOperationTool.readFile("not-exists-" + System.nanoTime() + ".txt");
        Assertions.assertTrue(result.startsWith("Error reading file"), result);
    }

    @Test
    void pathTraversal_isRejected() {
        String result = fileOperationTool.writeFile("../escape.txt", "x");

        Assertions.assertTrue(result.startsWith("Error writing to file"), result);
        Assertions.assertFalse(new File(FileConstant.FILE_SAVE_DIR + "/escape.txt").exists(),
                "must not write outside tmp/file");
    }

    @Test
    void absolutePath_isRejected() {
        String result = fileOperationTool.writeFile("C:\\Windows\\evil.txt", "x");

        Assertions.assertTrue(result.startsWith("Error writing to file"), result);
        Assertions.assertFalse(new File("C:\\Windows\\evil.txt").exists(),
                "must not write to an absolute path");
    }

    @Test
    void oversizedContent_isRejected() {
        String content = "x".repeat(1024 * 1024 + 1);

        String result = fileOperationTool.writeFile(fileName, content);

        Assertions.assertTrue(result.startsWith("Error writing to file: content too large"), result);
    }
}
