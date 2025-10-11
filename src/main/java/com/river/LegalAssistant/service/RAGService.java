package com.river.LegalAssistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG (检索增强生成) 服务
 * 
 * 职责:
 * - 执行RAG流程(检索 -> 构建上下文 -> 生成回答)
 * - 管理RAG相关的提示词模板
 * - 协调ChatClient和VectorStore的交互
 * 
 * 这个服务将RAG核心逻辑从AiService中提取出来,使其更加专注和可复用
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RAGService {

    private final ChatClient chatClient;
    private final VectorStoreService vectorStoreService;
    private final TextProcessingService textProcessingService;
    
    @Value("${app.ai.prompts.legal-qa}")
    private Resource legalQaPromptResource;

    /**
     * 执行标准RAG流程: 检索 -> 增强 -> 生成
     * 
     * @param question 用户问题
     * @param maxResults 最大检索结果数
     * @return RAG结果
     */
    public RagResult performRAG(String question, int maxResults) {
        log.info("执行RAG流程,问题: {}, 最大结果数: {}", question, maxResults);
        
        try {
            // 1. 向量检索相关文档
            List<Document> similarDocs = vectorStoreService.searchSimilar(question, maxResults);
            
            // 2. 检查是否找到相关文档
            if (similarDocs.isEmpty()) {
                log.info("未找到相关文档,返回默认回答");
                return new RagResult(
                    "根据您提供的法律条文，我无法找到该问题的直接答案。",
                    false,
                    0,
                    similarDocs
                );
            }
            
            // 3. 过滤和处理文档
            List<Document> filteredDocs = filterDocuments(similarDocs);
            
            // 4. 构建上下文
            String context = buildContext(filteredDocs);
            
            // 5. 生成最终提示词
            String finalPrompt = buildFinalPrompt(question, context, filteredDocs);
            
            // 6. 调用LLM生成回答
            String response = chatClient.prompt()
                    .user(finalPrompt)
                    .call()
                    .content();
            
            log.info("RAG流程完成,使用了 {} 个文档片段", filteredDocs.size());
            
            return new RagResult(response, true, filteredDocs.size(), filteredDocs);
            
        } catch (Exception e) {
            log.error("RAG流程执行失败", e);
            throw new RuntimeException("RAG服务暂时不可用", e);
        }
    }

    /**
     * 使用指定的ChatClient执行RAG流程
     * 用于支持不同模型或带记忆的场景
     */
    public RagResult performRAGWithClient(String question, int maxResults, ChatClient customChatClient) {
        log.info("使用自定义ChatClient执行RAG流程");
        
        try {
            // 向量检索
            List<Document> similarDocs = vectorStoreService.searchSimilar(question, maxResults);
            
            if (similarDocs.isEmpty()) {
                return new RagResult(
                    "根据您提供的法律条文，我无法找到该问题的直接答案。",
                    false,
                    0,
                    similarDocs
                );
            }
            
            // 过滤和构建上下文
            List<Document> filteredDocs = filterDocuments(similarDocs);
            String context = buildContext(filteredDocs);
            String finalPrompt = buildFinalPrompt(question, context, filteredDocs);
            
            // 使用自定义ChatClient生成回答
            String response = customChatClient.prompt()
                    .user(finalPrompt)
                    .call()
                    .content();
            
            log.info("RAG流程完成(自定义Client),使用了 {} 个文档片段", filteredDocs.size());
            
            return new RagResult(response, true, filteredDocs.size(), filteredDocs);
            
        } catch (Exception e) {
            log.error("RAG流程执行失败(自定义Client)", e);
            throw new RuntimeException("RAG服务暂时不可用", e);
        }
    }

    /**
     * 过滤文档,排除不相关的类型
     */
    private List<Document> filterDocuments(List<Document> documents) {
        return documents.stream()
            .filter(doc -> {
                // 排除合同审查相关的文档
                String sourceType = doc.getMetadata().getOrDefault("source_type", "").toString();
                return !"contract_review".equals(sourceType);
            })
            .collect(Collectors.toList());
    }

    /**
     * 构建RAG上下文
     */
    private String buildContext(List<Document> documents) {
        return documents.stream()
            .map(doc -> {
                String source = textProcessingService.getCleanDocumentSource(doc);
                return "来源: " + source + "\n内容:\n" + doc.getText();
            })
            .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 构建最终提示词
     */
    private String buildFinalPrompt(String question, String context, List<Document> documents) {
        PromptTemplate promptTemplate = new PromptTemplate(legalQaPromptResource);
        
        String sources = documents.stream()
                .map(doc -> textProcessingService.getCleanDocumentSource(doc))
                .distinct()
                .collect(Collectors.joining(", "));
        
        Map<String, Object> promptValues = Map.of(
            "context", context,
            "question", question,
            "source", sources
        );
        
        return promptTemplate.create(promptValues).getContents();
    }

    /**
     * RAG结果记录类
     */
    public record RagResult(
        String answer,
        boolean hasKnowledgeMatch,
        int sourceCount,
        List<Document> sourceDocuments
    ) {}
}

