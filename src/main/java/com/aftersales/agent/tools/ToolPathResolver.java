package com.aftersales.agent.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 工具文件路径解析器。
 * <p>
 * 工具参数来自模型，必须限制在指定目录内：文件名经规范化后仍需以基准目录（真实路径）为前缀，
 * 并检查最近存在的父目录真实路径，从而拦截 {@code ../}、绝对路径与符号链接目录逃逸
 * （AI_CONTEXT §5.4）。
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
     * @throws IllegalArgumentException 文件名为空、越出基准目录或目标为符号链接
     * @throws IOException              基准目录创建/解析失败
     */
    static File resolveInside(String baseDir, String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("file name must not be blank");
        }
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Files.createDirectories(base);
        Path baseReal = base.toRealPath();

        Path target = baseReal.resolve(fileName).normalize();
        if (!target.startsWith(baseReal)) {
            throw new IllegalArgumentException("illegal file name: " + fileName);
        }

        // 最近存在的父目录也必须在基准目录内：防止通过已存在的符号链接目录逃逸
        Path parent = target.getParent();
        while (parent != null && !Files.exists(parent)) {
            parent = parent.getParent();
        }
        if (parent != null && !parent.toRealPath().startsWith(baseReal)) {
            throw new IllegalArgumentException("illegal file name: " + fileName);
        }

        if (Files.isSymbolicLink(target)) {
            throw new IllegalArgumentException("symbolic link not allowed: " + fileName);
        }
        return target.toFile();
    }
}
