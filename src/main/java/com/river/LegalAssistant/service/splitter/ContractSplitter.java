package com.river.LegalAssistant.service.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合同文档分割器
 * 
 * 专门用于处理合同文档的智能分割器
 * 
 * 功能特性:
 * 1. 条款识别: 识别"第X条"、"第X款"等合同条款结构
 * 2. 章节识别: 识别合同的主要章节(如"第一章"、"第二章"等)
 * 3. 结构化元数据: 保留条款编号、章节信息等结构化数据
 * 4. 智能分片: 超长条款自动分片，保持语义完整性
 * 5. 上下文保留: 保留前一条款的部分内容作为上下文
 * 
 * 设计参考: 
 * - 参考LegalDocumentSplitter的设计模式
 * - 适配中国合同文档的常见格式
 * 
 * 支持格式:
 * - 第X条: ...
 * - 第X款: ...
 * - X. ...
 * - (X) ...
 * 
 * @author River
 * @since 1.0
 */
@Component
@Slf4j
public class ContractSplitter implements DocumentSplitter {
    
    // 条款编号模式
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
        "^\\s*第([零一二三四五六七八九十百千0-9]+)条[\\s:：]+(.*)",
        Pattern.MULTILINE
    );
    
    // 款项编号模式
    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
        "^\\s*第([零一二三四五六七八九十百千0-9]+)款[\\s:：]+(.*)",
        Pattern.MULTILINE
    );
    
    // 章节编号模式
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
        "^\\s*第([零一二三四五六七八九十百千0-9]+)章[\\s:：]+(.*)",
        Pattern.MULTILINE
    );
    
    // 数字编号模式 (如 "1. " 或 "1) ")
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
        "^\\s*(\\d+)[.、)）]\\s+(.*)",
        Pattern.MULTILINE
    );
    
    @Value("${app.splitter.contract.max-segment-size:2000}")
    private int maxSegmentSize;
    
    @Value("${app.splitter.contract.context-overlap:200}")
    private int contextOverlap;
    
    /**
     * 分割合同文档
     */
    @Override
    public List<TextSegment> split(Document document) {
        log.debug("开始分割合同文档，总长度: {} 字符", document.text().length());
        
        String content = document.text();
        Metadata baseMetadata = document.metadata();
        
        List<TextSegment> segments = new ArrayList<>();
        
        try {
            // 尝试按结构化方式分割
            if (hasStructure(content)) {
                segments = splitByStructure(content, baseMetadata);
                log.debug("使用结构化分割，生成 {} 个片段", segments.size());
            } else {
                // 如果没有明显结构，使用段落分割
                segments = splitByParagraph(content, baseMetadata);
                log.debug("使用段落分割，生成 {} 个片段", segments.size());
            }
            
            // 添加统计信息
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                Map<String, Object> metadata = new HashMap<>(segment.metadata().toMap());
                metadata.put("segment_index", i);
                metadata.put("total_segments", segments.size());
                metadata.put("split_type", "contract_structured");
                
                segments.set(i, TextSegment.from(segment.text(), Metadata.from(metadata)));
            }
            
            return segments;
            
        } catch (Exception e) {
            log.error("合同文档分割失败，使用降级策略", e);
            // 降级策略：简单段落分割
            return splitByParagraph(content, baseMetadata);
        }
    }
    
    /**
     * 检查文档是否有明显的合同结构
     */
    private boolean hasStructure(String content) {
        Matcher articleMatcher = ARTICLE_PATTERN.matcher(content);
        Matcher chapterMatcher = CHAPTER_PATTERN.matcher(content);
        Matcher numberMatcher = NUMBER_PATTERN.matcher(content);
        
        // 至少找到3个结构化标记才认为有明显结构
        int structureCount = 0;
        
        while (articleMatcher.find() && structureCount < 10) {
            structureCount++;
        }
        
        while (chapterMatcher.find() && structureCount < 10) {
            structureCount++;
        }
        
        while (numberMatcher.find() && structureCount < 10) {
            structureCount++;
        }
        
        return structureCount >= 3;
    }
    
    /**
     * 按结构化方式分割(条款、章节等)
     */
    private List<TextSegment> splitByStructure(String content, Metadata baseMetadata) {
        List<TextSegment> segments = new ArrayList<>();
        
        String[] lines = content.split("\n");
        StringBuilder currentClause = new StringBuilder();
        String currentClauseNumber = null;
        String currentChapter = null;
        
        for (String line : lines) {
            // 检查是否是章节标题
            Matcher chapterMatcher = CHAPTER_PATTERN.matcher(line);
            if (chapterMatcher.find()) {
                // 保存前一个条款
                if (currentClause.length() > 0) {
                    segments.add(createSegment(
                        currentClause.toString().trim(),
                        currentClauseNumber,
                        currentChapter,
                        baseMetadata
                    ));
                    currentClause.setLength(0);
                }
                
                currentChapter = chapterMatcher.group(2).trim();
                currentClauseNumber = null;
                currentClause.append(line).append("\n");
                continue;
            }
            
            // 检查是否是条款
            Matcher articleMatcher = ARTICLE_PATTERN.matcher(line);
            if (articleMatcher.find()) {
                // 保存前一个条款
                if (currentClause.length() > 0) {
                    segments.add(createSegment(
                        currentClause.toString().trim(),
                        currentClauseNumber,
                        currentChapter,
                        baseMetadata
                    ));
                    currentClause.setLength(0);
                }
                
                currentClauseNumber = articleMatcher.group(1);
                currentClause.append(line).append("\n");
                continue;
            }
            
            // 检查是否是款项
            Matcher clauseMatcher = CLAUSE_PATTERN.matcher(line);
            if (clauseMatcher.find()) {
                // 保存前一个条款
                if (currentClause.length() > 0) {
                    segments.add(createSegment(
                        currentClause.toString().trim(),
                        currentClauseNumber,
                        currentChapter,
                        baseMetadata
                    ));
                    currentClause.setLength(0);
                }
                
                currentClauseNumber = clauseMatcher.group(1);
                currentClause.append(line).append("\n");
                continue;
            }
            
            // 检查是否是数字编号
            Matcher numberMatcher = NUMBER_PATTERN.matcher(line);
            if (numberMatcher.find()) {
                // 如果当前条款过长，先保存
                if (currentClause.length() > maxSegmentSize) {
                    segments.add(createSegment(
                        currentClause.toString().trim(),
                        currentClauseNumber,
                        currentChapter,
                        baseMetadata
                    ));
                    currentClause.setLength(0);
                }
                
                currentClause.append(line).append("\n");
                continue;
            }
            
            // 普通行，追加到当前条款
            currentClause.append(line).append("\n");
            
            // 如果条款过长，分片保存
            if (currentClause.length() > maxSegmentSize) {
                String text = currentClause.toString().trim();
                List<String> subSegments = splitLongText(text);
                
                for (int i = 0; i < subSegments.size(); i++) {
                    Map<String, Object> metadata = new HashMap<>();
                    if (baseMetadata != null) {
                        metadata.putAll(baseMetadata.toMap());
                    }
                    
                    metadata.put("clause_number", currentClauseNumber);
                    metadata.put("chapter", currentChapter);
                    metadata.put("split_type", "contract_clause");
                    metadata.put("is_fragment", true);
                    metadata.put("fragment_index", i);
                    metadata.put("total_fragments", subSegments.size());
                    
                    segments.add(TextSegment.from(subSegments.get(i), Metadata.from(metadata)));
                }
                
                currentClause.setLength(0);
            }
        }
        
        // 保存最后一个条款
        if (currentClause.length() > 0) {
            segments.add(createSegment(
                currentClause.toString().trim(),
                currentClauseNumber,
                currentChapter,
                baseMetadata
            ));
        }
        
        return segments;
    }
    
    /**
     * 按段落分割(降级策略)
     */
    private List<TextSegment> splitByParagraph(String content, Metadata baseMetadata) {
        List<TextSegment> segments = new ArrayList<>();
        
        String[] paragraphs = content.split("\n\n+");
        StringBuilder currentSegment = new StringBuilder();
        int segmentIndex = 0;
        
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // 如果添加这个段落会超出限制，先保存当前片段
            if (currentSegment.length() > 0 && 
                currentSegment.length() + trimmed.length() > maxSegmentSize) {
                
                Map<String, Object> metadata = new HashMap<>();
                if (baseMetadata != null) {
                    metadata.putAll(baseMetadata.toMap());
                }
                metadata.put("split_type", "contract_paragraph");
                metadata.put("segment_index", segmentIndex++);
                
                segments.add(TextSegment.from(currentSegment.toString().trim(), Metadata.from(metadata)));
                currentSegment.setLength(0);
            }
            
            currentSegment.append(trimmed).append("\n\n");
        }
        
        // 保存最后一个片段
        if (currentSegment.length() > 0) {
            Map<String, Object> metadata = new HashMap<>();
            if (baseMetadata != null) {
                metadata.putAll(baseMetadata.toMap());
            }
            metadata.put("split_type", "contract_paragraph");
            metadata.put("segment_index", segmentIndex);
            
            segments.add(TextSegment.from(currentSegment.toString().trim(), Metadata.from(metadata)));
        }
        
        return segments;
    }
    
    /**
     * 创建片段
     */
    private TextSegment createSegment(String text, String clauseNumber, 
                                     String chapter, Metadata baseMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        if (baseMetadata != null) {
            metadata.putAll(baseMetadata.toMap());
        }
        
        if (clauseNumber != null) {
            metadata.put("clause_number", clauseNumber);
        }
        if (chapter != null) {
            metadata.put("chapter", chapter);
        }
        metadata.put("split_type", "contract_clause");
        
        return TextSegment.from(text, Metadata.from(metadata));
    }
    
    /**
     * 分割超长文本
     */
    private List<String> splitLongText(String text) {
        List<String> fragments = new ArrayList<>();
        
        if (text.length() <= maxSegmentSize) {
            fragments.add(text);
            return fragments;
        }
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxSegmentSize, text.length());
            
            // 尝试在句子边界分割
            if (end < text.length()) {
                end = findSentenceBoundary(text, start, end);
            }
            
            fragments.add(text.substring(start, end).trim());
            start = Math.max(start + 1, end - contextOverlap);
        }
        
        return fragments;
    }
    
    /**
     * 查找句子边界
     */
    private int findSentenceBoundary(String text, int start, int suggestedEnd) {
        int searchStart = Math.max(start, suggestedEnd - 100);
        
        // 优先在句号、问号、感叹号处分割
        for (int i = suggestedEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '!' || c == '?' || c == '；' || c == '\n') {
                return i + 1;
            }
        }
        
        return suggestedEnd;
    }
    
    /**
     * 中文数字转阿拉伯数字
     */
    private String chineseNumberToArabic(String chineseNum) {
        // 简化实现，只处理常见情况
        // 使用Map.ofEntries()因为键值对超过10个
        Map<String, String> numberMap = Map.ofEntries(
            Map.entry("一", "1"),
            Map.entry("二", "2"),
            Map.entry("三", "3"),
            Map.entry("四", "4"),
            Map.entry("五", "5"),
            Map.entry("六", "6"),
            Map.entry("七", "7"),
            Map.entry("八", "8"),
            Map.entry("九", "9"),
            Map.entry("十", "10"),
            Map.entry("百", "100"),
            Map.entry("千", "1000")
        );
        
        return numberMap.getOrDefault(chineseNum, chineseNum);
    }
}

