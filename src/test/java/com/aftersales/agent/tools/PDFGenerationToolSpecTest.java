package com.aftersales.agent.tools;

import com.aftersales.agent.constant.FileConstant;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDFGenerationTool 规格测试：文件落盘、中文可提取、非法入参不崩溃。
 */
@DisplayName("PDF 生成工具")
class PDFGenerationToolSpecTest {

    private final PDFGenerationTool tool = new PDFGenerationTool();

    private static String pdfDir() {
        return FileConstant.FILE_SAVE_DIR + "/pdf";
    }

    private static void deleteQuietly(String path) {
        try {
            Files.deleteIfExists(new File(path).toPath());
        } catch (Exception ignored) {
            // ignore
        }
    }

    @Test
    @DisplayName("生成合法 PDF，中文内容可被文本提取")
    void generatesReadablePdfWithChinese() throws Exception {
        String fileName = "售后处理单-" + UUID.randomUUID() + ".pdf";
        String filePath = pdfDir() + "/" + fileName;
        String content = "订单号 20250815001 售后处理单：外包装破损，处理方案为换货，预计 2 个工作日发出。";
        try {
            String result = tool.generatePDF(fileName, content);
            assertTrue(result.startsWith("PDF generated successfully to:"), "实际：" + result);

            File pdfFile = new File(filePath);
            assertTrue(pdfFile.isFile(), "PDF 应落在 tmp/pdf 下");
            assertTrue(pdfFile.length() > 100, "PDF 不应为空文件");

            byte[] header = Files.readAllBytes(pdfFile.toPath());
            assertTrue(new String(header, 0, 5).equals("%PDF-"), "文件头必须是 %PDF-");

            try (PdfDocument document = new PdfDocument(new PdfReader(filePath))) {
                String text = PdfTextExtractor.getTextFromPage(document.getPage(1));
                assertTrue(text.contains("订单号"), "PDF 文本应包含中文内容，实际：" + text);
                assertTrue(text.contains("换货"), "PDF 文本应包含处理方案，实际：" + text);
            }
        } finally {
            deleteQuietly(filePath);
        }
    }

    @Test
    @DisplayName("安全：文件名禁止 ../ 路径穿越")
    void rejectsPathTraversal() {
        String fileName = "../越界-" + UUID.randomUUID() + ".pdf";
        String escapedPath = FileConstant.FILE_SAVE_DIR + "/" + fileName.substring("../".length());
        File escapedFile = new File(escapedPath);
        try {
            String result = tool.generatePDF(fileName, "越界 PDF");
            assertTrue(result.startsWith("Error generating PDF:"),
                    "路径穿越未被拦截，实际返回：" + result);
            assertTrue(!escapedFile.exists(), "不应在 tmp/pdf 之外创建文件：" + escapedFile.getAbsolutePath());
        } finally {
            deleteQuietly(escapedFile.getAbsolutePath());
        }
    }

    @Test
    @DisplayName("内容为空时返回错误文案，不应向调用方抛异常")
    void blankContentShouldNotCrash() {
        String fileName = "空内容-" + UUID.randomUUID() + ".pdf";
        String filePath = pdfDir() + "/" + fileName;
        try {
            String result = tool.generatePDF(fileName, null);
            assertTrue(result.startsWith("Error generating PDF:") || result.startsWith("PDF generated"),
                    "空内容应返回可读结果或错误提示，实际：" + result);
        } catch (RuntimeException e) {
            throw new AssertionError("generatePDF(null) 不应抛出未被封装的异常：" + e, e);
        } finally {
            deleteQuietly(filePath);
        }
    }
}
