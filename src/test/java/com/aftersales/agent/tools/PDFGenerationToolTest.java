package com.aftersales.agent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PDFGenerationToolTest {

    @Test
    void generatePDF() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "售后处理单.pdf";
        String content = "订单号 20250815001 售后处理单：外包装破损，处理方案为换货，预计 2 个工作日发出。";
        String result = tool.generatePDF(fileName, content);
        assertNotNull(result);
    }
}
