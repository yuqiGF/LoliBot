package com.bot.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * RAG (检索增强生成) 配置
 */
@Configuration
public class RagConfig {
    
    @Resource
    private EmbeddingModel qwenEmbeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;
    
    @Bean
    public ContentRetriever contentRetriever() {
        // 加载文档
        Document document = loadDocument();
        
        // 文档切割：按段落切割，最大100字符，重叠50字符
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(100, 50);
        
        // 文档向量化并存储
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter) 
                .textSegmentTransformer(segment -> 
                    TextSegment.from(
                        segment.metadata().getString("file_name") + "\n" + segment.text(),
                        segment.metadata()
                    )
                )
                .embeddingModel(qwenEmbeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        
        ingestor.ingest(document);
        
        // 创建内容检索器
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(qwenEmbeddingModel)
                .maxResults(5)
                .minScore(0.75)
                .build();
    }
    
    /**
     * 加载文档（支持开发环境和生产环境）
     */
    private Document loadDocument() {
        try {
            // 开发环境：从文件系统加载
            File file = new File("src/main/resources/docs/如何交到朋友 - 实用建议指南.md");
            if (file.exists()) {
                return FileSystemDocumentLoader.loadDocument(file.getAbsolutePath());
            }
            
            // 生产环境：从 classpath 加载
            ClassPathResource resource = new ClassPathResource("docs/如何交到朋友 - 实用建议指南.md");
            String content = FileCopyUtils.copyToString(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );
            Metadata metadata = Metadata.from("file_name", resource.getFilename());
            return Document.from(content, metadata);
            
        } catch (IOException e) {
            throw new RuntimeException("无法加载 RAG 文档：" + e.getMessage(), e);
        }
    }
}

