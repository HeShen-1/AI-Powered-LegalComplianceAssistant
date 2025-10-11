package com.river.LegalAssistant.service.advanced;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 法律内容注入器
 * 基于LangChain4j Advanced RAG框架实现
 * 
 * 功能：
 * 1. 将检索到的法律内容注入到用户消息中
 * 2. 格式化法律条文和案例引用
 * 3. 提供结构化的法律上下文
 * 4. 优化prompt结构以提高AI回答质量
 */
@Component
@Slf4j
public class LegalContentInjector implements ContentInjector {

    @Value("${app.langchain4j.injector.max-content-length:3000}")
    private int maxContentLength;
    
    @Value("${app.langchain4j.injector.include-sources:true}")
    private boolean includeSources;
    
    @Value("${app.langchain4j.injector.format-legal-citations:true}")
    private boolean formatLegalCitations;

    // 法律内容注入模板
    private static final String LEGAL_CONTEXT_TEMPLATE = """
        你是一名专业的中国法律顾问。请基于以下法律条文，回答用户的问题。
        
        法律条文上下文：
        %s
        
        用户问题：%s
        
        请根据以上法律依据，提供准确、专业的法律建议。如果法律依据不充分，请明确说明并建议咨询专业律师。
        """;
    
    private static final String SIMPLE_CONTEXT_TEMPLATE = """
        参考信息：
        %s
        
        问题：%s
        
        请基于上述参考信息回答问题。
        """;

    @Override
    public ChatMessage inject(List<Content> contents, ChatMessage userMessage) {
        log.info("注入法律内容到用户消息，内容数量: {}", contents.size());
        
        try {
            if (contents.isEmpty()) {
                log.debug("没有内容需要注入，返回原始消息");
                return userMessage;
            }
            
            // 1. 处理和格式化内容
            String formattedContent = formatLegalContent(contents);
            
            // 2. 获取原始用户消息
            String originalUserText = extractUserText(userMessage);
            
            // 3. 构建增强的消息
            String enhancedMessage = buildEnhancedMessage(formattedContent, originalUserText);
            
            // 4. 创建新的用户消息
            UserMessage newUserMessage = UserMessage.from(enhancedMessage);
            
            log.info("法律内容注入完成，增强消息长度: {}", enhancedMessage.length());
            return newUserMessage;
            
        } catch (Exception e) {
            log.error("内容注入失败，返回原始消息", e);
            return userMessage;
        }
    }

    /**
     * 格式化法律内容
     */
    private String formatLegalContent(List<Content> contents) {
        StringBuilder formattedContent = new StringBuilder();
        int totalLength = 0;
        
        // 首先去重
        List<Content> deduplicatedContents = removeDuplicateContents(contents);
        
        // 分类内容
        List<Content> lawProvisions = deduplicatedContents.stream()
            .filter(this::isLawProvision)
            .limit(2) // 减少法律条文数量以减少重复
            .collect(Collectors.toList());
        
        List<Content> caseReferences = deduplicatedContents.stream()
            .filter(this::isCaseReference)
            .limit(1) // 减少案例数量
            .collect(Collectors.toList());
        
        List<Content> generalContent = deduplicatedContents.stream()
            .filter(content -> !isLawProvision(content) && !isCaseReference(content))
            .limit(2) // 减少一般内容数量
            .collect(Collectors.toList());
        
        // 1. 添加法律条文
        if (!lawProvisions.isEmpty()) {
            formattedContent.append("【法律依据】\n");
            for (int i = 0; i < lawProvisions.size(); i++) {
                String contentText = lawProvisions.get(i).textSegment().text();
                String formattedText = formatLawProvision(contentText);
                
                if (totalLength + formattedText.length() > maxContentLength) {
                    break;
                }
                
                formattedContent.append(String.format("%d. %s\n\n", 
                    i + 1, formattedText));
                totalLength += formattedText.length();
            }
        }
        
        // 2. 添加案例参考（只在没有法律条文时添加）
        if (!caseReferences.isEmpty() && lawProvisions.isEmpty()) {
            formattedContent.append("【相关案例】\n");
            for (int i = 0; i < caseReferences.size(); i++) {
                String contentText = caseReferences.get(i).textSegment().text();
                String formattedText = formatCaseReference(contentText);
                
                if (totalLength + formattedText.length() > maxContentLength) {
                    break;
                }
                
                formattedContent.append(String.format("%d. %s\n\n", 
                    i + 1, formattedText));
                totalLength += formattedText.length();
            }
        }
        
        // 3. 添加一般内容（只在没有法律条文和案例时添加）
        if (!generalContent.isEmpty() && lawProvisions.isEmpty() && caseReferences.isEmpty() && totalLength < maxContentLength * 0.6) {
            formattedContent.append("【参考资料】\n");
            for (int i = 0; i < generalContent.size() && i < 1; i++) { // 只取一个最相关的
                String contentText = generalContent.get(i).textSegment().text();
                String truncatedText = truncateContent(contentText, 200);
                
                if (totalLength + truncatedText.length() > maxContentLength) {
                    break;
                }
                
                formattedContent.append(String.format("%d. %s\n\n", 
                    i + 1, truncatedText));
                totalLength += truncatedText.length();
            }
        }
        
        // 4. 添加来源信息（简化版）
        if (includeSources && formattedContent.length() > 0) {
            String sourceInfo = formatSimpleSourceInformation(contents);
            if (!sourceInfo.isEmpty()) {
                formattedContent.append(sourceInfo);
            }
        }
        
        return formattedContent.toString().trim();
    }

    /**
     * 判断是否为法律条文
     */
    private boolean isLawProvision(Content content) {
        String text = content.textSegment().text();
        return text.contains("第") && text.contains("条") && 
               (text.contains("法") || text.contains("典") || text.contains("条例"));
    }

    /**
     * 判断是否为案例参考
     */
    private boolean isCaseReference(Content content) {
        String text = content.textSegment().text();
        return text.contains("案例") || text.contains("判决") || 
               text.contains("法院") || text.contains("裁决");
    }

    /**
     * 格式化法律条文
     */
    private String formatLawProvision(String text) {
        if (!formatLegalCitations) {
            return truncateContent(text, 400);
        }
        
        // 尝试提取法律条文的结构化信息
        String formatted = text;
        
        // 标准化条文格式
        formatted = formatted.replaceAll("第(\\d+)条", "第$1条");
        formatted = formatted.replaceAll("第(\\d+)款", "第$1款");
        formatted = formatted.replaceAll("第(\\d+)项", "第$1项");
        
        return truncateContent(formatted, 400);
    }

    /**
     * 格式化案例参考
     */
    private String formatCaseReference(String text) {
        String formatted = text;
        
        // 简化案例描述
        if (text.length() > 300) {
            // 尝试保留案例的核心信息
            String[] sentences = text.split("[。！？]");
            StringBuilder summary = new StringBuilder();
            
            for (String sentence : sentences) {
                if (summary.length() + sentence.length() > 250) {
                    break;
                }
                if (sentence.trim().length() > 10) {
                    summary.append(sentence.trim()).append("。");
                }
            }
            
            formatted = summary.toString();
        }
        
        return formatted;
    }

    /**
     * 截断内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        
        // 尝试在句子边界截断
        String truncated = content.substring(0, maxLength);
        int lastPeriod = Math.max(
            Math.max(truncated.lastIndexOf('。'), truncated.lastIndexOf('！')),
            truncated.lastIndexOf('？')
        );
        
        if (lastPeriod > maxLength * 0.7) {
            return content.substring(0, lastPeriod + 1);
        } else {
            return truncated + "...";
        }
    }

    /**
     * 去除重复内容
     */
    private List<Content> removeDuplicateContents(List<Content> contents) {
        List<Content> unique = new ArrayList<>();
        Set<String> seenTexts = new HashSet<>();
        
        for (Content content : contents) {
            String normalizedText = content.textSegment().text()
                .replaceAll("\\s+", " ")
                .toLowerCase()
                .trim();
            
            // 检查是否与已有内容重复
            boolean isDuplicate = false;
            for (String seenText : seenTexts) {
                if (calculateContentSimilarity(normalizedText, seenText) > 0.85) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
                unique.add(content);
                seenTexts.add(normalizedText);
            }
        }
        
        return unique;
    }
    
    /**
     * 计算内容相似度
     */
    private double calculateContentSimilarity(String text1, String text2) {
        if (text1.equals(text2)) {
            return 1.0;
        }
        
        String[] words1 = text1.split("\\s+");
        String[] words2 = text2.split("\\s+");
        
        Set<String> set1 = new HashSet<>(Arrays.asList(words1));
        Set<String> set2 = new HashSet<>(Arrays.asList(words2));
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
    
    /**
     * 格式化简化的来源信息
     */
    private String formatSimpleSourceInformation(List<Content> contents) {
        Set<String> uniqueSources = contents.stream()
            .map(this::extractSource)
            .filter(source -> !source.isEmpty() && !"法律知识库".equals(source))
            .distinct()
            .limit(3)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        
        if (uniqueSources.isEmpty()) {
            return "";
        }
        
        return "\n【来源信息】\n" + 
               String.join(", ", uniqueSources) + "\n";
    }

    /**
     * 提取内容来源
     */
    private String extractSource(Content content) {
        // 尝试从内容中提取来源信息
        String text = content.textSegment().text();
        
        // 检查是否包含明确的法律文件名称
        if (text.contains("民法典")) return "《中华人民共和国民法典》";
        if (text.contains("合同法")) return "《中华人民共和国合同法》";
        if (text.contains("公司法")) return "《中华人民共和国公司法》";
        if (text.contains("劳动法")) return "《中华人民共和国劳动法》";
        
        // 默认来源
        return "法律知识库";
    }

    /**
     * 提取用户消息文本
     */
    private String extractUserText(ChatMessage userMessage) {
        if (userMessage instanceof UserMessage) {
            return ((UserMessage) userMessage).singleText();
        }
        return userMessage.toString();
    }

    /**
     * 构建增强的消息
     */
    private String buildEnhancedMessage(String formattedContent, String originalUserText) {
        if (formattedContent.trim().isEmpty()) {
            return originalUserText;
        }
        
        // 选择合适的模板
        if (formattedContent.contains("【法律依据】") || formattedContent.contains("【相关案例】")) {
            return String.format(LEGAL_CONTEXT_TEMPLATE, formattedContent, originalUserText);
        } else {
            return String.format(SIMPLE_CONTEXT_TEMPLATE, formattedContent, originalUserText);
        }
    }
}
