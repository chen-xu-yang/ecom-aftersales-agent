package com.aftersales.agent.tools;

import cn.hutool.core.io.FileUtil;
import com.aftersales.agent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 文件操作工具类（提供文件读写功能）
 * <p>
 * 仅允许访问 {@code tmp/file} 目录，文件名由 {@link ToolPathResolver} 做越界校验；
 * 单文件读写均限制 1 MB，避免模型一次写入/读取超大内容拖垮上下文与内存。
 */
public class FileOperationTool {

    private static final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";
    private static final long MAX_FILE_BYTES = 1024 * 1024L;

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of a file to read") String fileName) {
        try {
            File file = ToolPathResolver.resolveInside(FILE_DIR, fileName);
            if (file.length() > MAX_FILE_BYTES) {
                return "Error reading file: file too large (" + file.length() + " bytes, limit 1 MB)";
            }
            return FileUtil.readUtf8String(file);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content
    ) {
        try {
            File file = ToolPathResolver.resolveInside(FILE_DIR, fileName);
            byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_FILE_BYTES) {
                return "Error writing to file: content too large (" + bytes.length + " bytes, limit 1 MB)";
            }
            // 创建目录
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, file);
            return "File written successfully to: " + file.getPath();
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
