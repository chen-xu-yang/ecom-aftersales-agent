package com.aftersales.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 售后知识库文档加载器
 */
@Component
@Slf4j
public class AftersalesDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public AftersalesDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载 classpath 下所有售后知识库 Markdown 文档
     * <p>
     * 文件名约定为「主题 - 分类.md」，例如「售后规则 - 退换货.md」，
     * 分类写入元数据 topic，供检索时做元数据过滤。
     *
     * @return 文档列表
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String topic = parseTopic(filename);
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("topic", topic)
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(markdownDocumentReader.get());
            }
        } catch (IOException e) {
           log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }

    /**
     * 从文件名解析分类标签，约定为「主题 - 分类.md」
     *
     * @param filename 文件名
     * @return 分类标签
     */
    private String parseTopic(String filename) {
        String name = filename.endsWith(".md")
                ? filename.substring(0, filename.length() - ".md".length())
                : filename;
        int separatorIndex = name.lastIndexOf(" - ");
        return separatorIndex >= 0 ? name.substring(separatorIndex + " - ".length()) : name;
    }
}
