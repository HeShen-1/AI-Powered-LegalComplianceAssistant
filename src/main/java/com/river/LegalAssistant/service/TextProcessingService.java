package com.river.LegalAssistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本处理服务
 * 
 * 职责:
 * - 文本分块(Chunking)
 * - 文本清理和规范化
 * - 文档源信息提取和清理
 * 
 * 从AiService中提取的文本处理相关功能,使其成为独立可复用的工具
 */
@Service
@Slf4j
public class TextProcessingService {

    @Value("${app.rag.chunk-size:1000}")
    private int chunkSize;
    
    @Value("${app.rag.chunk-overlap:100}")
    private int chunkOverlap;
    
    // 嵌入模型最大token限制
    private static final int MAX_EMBEDDING_TOKENS = 500;

    /**
     * 判断文本是否需要分块
     */
    public boolean needsChunking(String content) {
        // 粗略估算:平均每个token约3-4个字符(中文)
        int estimatedTokens = content.length() / 3;
        return estimatedTokens > MAX_EMBEDDING_TOKENS;
    }

    /**
     * 将文本分割成块
     */
    public List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        
        // 如果文本长度小于等于chunkSize,直接返回
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // 如果不是最后一块,尝试在合适的位置切分
            if (end < text.length()) {
                end = findOptimalSplitPoint(text, start, end);
            }
            
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            // 下一块的开始位置考虑重叠
            start = Math.max(start + 1, end - chunkOverlap);
        }
        
        log.debug("文本分块完成,共 {} 个块", chunks.size());
        return chunks;
    }

    /**
     * 找到最佳的文本分割点
     * 优先在句子边界分割,其次是逗号、空格等
     */
    public int findOptimalSplitPoint(String text, int start, int suggestedEnd) {
        int searchStart = Math.max(start, suggestedEnd - 100);
        
        // 优先在句号、问号、感叹号处分割
        for (int i = suggestedEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '!' || c == '?' || c == '；') {
                return i + 1;
            }
        }
        
        // 其次在逗号、分号处分割
        for (int i = suggestedEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == ',' || c == '，' || c == ';') {
                return i + 1;
            }
        }
        
        // 最后在空格或换行符处分割
        for (int i = suggestedEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                return i + 1;
            }
        }
        
        // 如果找不到合适的分割点,就在建议位置强制分割
        return suggestedEnd;
    }

    /**
     * 获取清理后的文档源信息
     * 用于在RAG结果中显示友好的来源名称
     */
    public String getCleanDocumentSource(Document doc) {
        // 优先使用 original_filename
        String originalFilename = (String) doc.getMetadata().get("original_filename");
        if (originalFilename != null && !originalFilename.trim().isEmpty()) {
            return cleanFilename(originalFilename);
        }
        
        // 尝试从 source 字段获取
        String source = (String) doc.getMetadata().get("source");
        if (source != null && !source.trim().isEmpty()) {
            return cleanFilename(source);
        }
        
        // 尝试从 file_name 字段获取
        String fileName = (String) doc.getMetadata().get("file_name");
        if (fileName != null && !fileName.trim().isEmpty()) {
            return cleanFilename(fileName);
        }
        
        // 如果是合同审查文档,返回通用名称
        String sourceType = (String) doc.getMetadata().get("source_type");
        if ("contract_review".equals(sourceType)) {
            return "合同文档";
        }
        
        return "未知来源";
    }

    /**
     * 清理文件名,移除路径和哈希前缀
     */
    public String cleanFilename(String filename) {
        if (filename == null) {
            return "未知文件";
        }
        
        // 移除路径(处理Windows和Unix路径)
        String cleaned = filename.replaceAll(".*[/\\\\]", "");
        
        // 移除SHA哈希前缀(64字符的十六进制字符串后跟下划线)
        cleaned = cleaned.replaceAll("^[a-fA-F0-9]{64}_", "");
        
        // 移除uploads路径前缀
        cleaned = cleaned.replaceAll("^uploads/[^/]+/", "");
        
        return cleaned.trim().isEmpty() ? "未知文件" : cleaned;
    }

    /**
     * 清理和规范化文本内容
     */
    public String cleanAndNormalizeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        return text
            .replace("\0", "")  // 移除空字符
            .replaceAll("\\s+", " ")  // 规范化空白字符
            .trim();
    }

    /**
     * 估算文本的token数量(粗略估计)
     */
    public int estimateTokenCount(String text) {
        // 中文文本:约3个字符=1个token
        // 英文文本:约4个字符=1个token
        // 这里取保守估计
        return text.length() / 3;
    }

    /**
     * 截断文本到指定的token限制
     */
    public String truncateToTokenLimit(String text, int maxTokens) {
        int estimatedTokens = estimateTokenCount(text);
        
        if (estimatedTokens <= maxTokens) {
            return text;
        }
        
        // 计算需要保留的字符数
        int targetLength = maxTokens * 3;
        
        if (text.length() <= targetLength) {
            return text;
        }
        
        // 在合适的位置截断
        int cutPoint = findOptimalSplitPoint(text, 0, targetLength);
        return text.substring(0, cutPoint) + "...";
    }
}

