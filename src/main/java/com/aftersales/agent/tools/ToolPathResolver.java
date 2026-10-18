package com.aftersales.agent.tools;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 工具文件路径解析器。
 * <p>
 * 工具参数来自模型，必须限制在指定目录内：文件名经规范化后仍需以基准目录为前缀，
 * 从而拦截 {@code ../}、绝对路径等越界写法（AI_CONTEXT §5.4）。
 */
final class ToolPathResolver {

    private ToolPathResolver() {
    }

    /**
     * 把文件名解析为基准目录下的安全文件。
     *
     * @param baseDir  允许访问的基准目录
     * @param fileName 模型给出的文件名
     * @return 规范化后的文件
     * @throws IllegalArgumentException 文件名为空或解析后越出基准目录
     */
    static File resolveInside(String baseDir, String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("file name must not be blank");
        }
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Path target = base.resolve(fileName).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("illegal file name: " + fileName);
        }
        return target.toFile();
    }
}
