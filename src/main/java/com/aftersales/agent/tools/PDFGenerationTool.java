package com.aftersales.agent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.aftersales.agent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

/**
 * PDF 生成工具
 * <p>
 * 仅允许写入 {@code tmp/pdf} 目录；入参非法或生成失败时返回错误文案，不向调用方抛异常。
 */
public class PDFGenerationTool {

    private static final String PDF_DIR = FileConstant.FILE_SAVE_DIR + "/pdf";

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        try {
            File target = ToolPathResolver.resolveInside(PDF_DIR, fileName);
            if (content == null) {
                return "Error generating PDF: content must not be null";
            }
            // 创建目录
            FileUtil.mkdir(PDF_DIR);
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(target);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                // 使用内置中文字体
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);
                // 创建段落
                Paragraph paragraph = new Paragraph(content);
                // 添加段落并关闭文档
                document.add(paragraph);
            }
            return "PDF generated successfully to: " + target.getPath();
        } catch (Exception e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }
}
