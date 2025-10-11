package com.river.LegalAssistant.service.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 法律文档语义分割器
 * 
 * <p>专门为法律文档设计的分割器,尊重法律文本的内在结构,确保每个数据块
 * 对应一个有完整意义的法律单元。
 * 
 * <h2>三阶段优化方案:</h2>
 * <ul>
 *   <li><b>第一阶段</b>: 以"条"为单位的基础分割,确保语义完整性</li>
 *   <li><b>第二阶段</b>: 长条文的二次分割与元数据传播,处理超长条文</li>
 *   <li><b>第三阶段</b>: 基于层级结构的上下文感知(编、章、节、条)</li>
 * </ul>
 * 
 * @author River
 * @see <a href="https://docs.langchain4j.dev/tutorials/rag">LangChain4j RAG Tutorial</a>
 */
@Slf4j
public class LegalDocumentSplitter implements DocumentSplitter {
    
    /**
     * 法律条文正则表达式 - 匹配"第X条"
     * 使用正向先行断言(?=...)来匹配位置而不消耗字符
     */
    private static final String ARTICLE_REGEX = "(?=第[一二三四五六七八九十百千零〇]+条)";
    
    /**
     * 提取条文编号的正则表达式 - 允许前面有空格
     */
    private static final Pattern ARTICLE_NUMBER_PATTERN = Pattern.compile("^\\s*(第[一二三四五六七八九十百千零〇]+条)");
    
    /**
     * 编的正则表达式 - 允许前面有空格
     */
    private static final Pattern BOOK_PATTERN = Pattern.compile("^\\s*(第[一二三四五六七八九十百]+编\\s*.*)$");
    
    /**
     * 章的正则表达式 - 允许前面有空格
     */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^\\s*(第[一二三四五六七八九十百]+章\\s*.*)$");
    
    /**
     * 节的正则表达式 - 允许前面有空格
     */
    private static final Pattern SECTION_PATTERN = Pattern.compile("^\\s*(第[一二三四五六七八九十百]+节\\s*.*)$");
    
    /**
     * 最大Token限制 (基于常见的Embedding模型限制)
     */
    private final int maxTokens;
    
    /**
     * 是否启用层级结构解析
     */
    private final boolean enableHierarchicalParsing;
    
    /**
     * 二次分割时的重叠字符数
     */
    private final int chunkOverlap;
    
    /**
     * 构造函数
     * 
     * @param maxTokens 最大Token限制
     * @param enableHierarchicalParsing 是否启用层级结构解析
     * @param chunkOverlap 二次分割时的重叠字符数
     */
    public LegalDocumentSplitter(int maxTokens, boolean enableHierarchicalParsing, int chunkOverlap) {
        this.maxTokens = maxTokens;
        this.enableHierarchicalParsing = enableHierarchicalParsing;
        this.chunkOverlap = chunkOverlap;
        log.info("初始化法律文档分割器 - maxTokens: {}, enableHierarchicalParsing: {}, chunkOverlap: {}", 
                maxTokens, enableHierarchicalParsing, chunkOverlap);
    }
    
    /**
     * 使用默认配置的构造函数
     */
    public LegalDocumentSplitter() {
        this(512, true, 50);
    }

    @Override
    public List<TextSegment> split(Document document) {
        String content = document.text();
        Metadata baseMetadata = document.metadata();
        
        log.debug("开始分割法律文档,内容长度: {} 字符", content.length());
        
        if (content == null || content.trim().isEmpty()) {
            log.warn("文档内容为空,返回空列表");
            return Collections.emptyList();
        }
        
        List<TextSegment> segments;
        
        if (enableHierarchicalParsing) {
            // 第三阶段: 基于层级结构的上下文感知分割
            segments = splitWithHierarchy(content, baseMetadata);
        } else {
            // 第一阶段: 基础的按条分割
            segments = splitByArticle(content, baseMetadata);
        }
        
        // 第二阶段: 处理超长条文
        segments = handleLongArticles(segments);
        
        log.info("文档分割完成,共生成 {} 个片段", segments.size());
        return segments;
    }

    /**
     * 第一阶段: 以"条"为单位的基础分割
     * 
     * @param content 文档内容
     * @param baseMetadata 基础元数据
     * @return 分割后的文本片段列表
     */
    private List<TextSegment> splitByArticle(String content, Metadata baseMetadata) {
        log.debug("使用基础分割模式(按条分割)");
        
        // 使用正则表达式分割文本
        String[] articles = content.split(ARTICLE_REGEX);
        
        List<TextSegment> segments = Arrays.stream(articles)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .filter(text -> text.matches("^\\s*第.*")) // 允许前面有空格，只保留以"第"开头的条文
                .map(text -> {
                    // 提取条文编号
                    String articleNumber = extractArticleNumber(text);
                    
                    // 构建元数据（与层级分割保持一致）
                    Map<String, Object> metadata = new HashMap<>();
                    if (baseMetadata != null) {
                        metadata.putAll(baseMetadata.toMap());
                    }
                    metadata.put("article_number", articleNumber);
                    metadata.put("split_type", "article");
                    
                    // 增强元数据：提取法律名称
                    if (baseMetadata != null) {
                        String filename = (String) baseMetadata.toMap().get("original_filename");
                        if (filename != null) {
                            String lawName = filename.replaceAll("\\.(pdf|docx|txt|doc)$", "");
                            metadata.put("law_name", lawName);
                            metadata.put("law_category", categorizeLaw(lawName));
                        }
                    }
                    
                    // 添加层级路径（基础模式下只有条文编号）
                    metadata.put("hierarchy_path", articleNumber);
                    
                    return TextSegment.from(text, Metadata.from(metadata));
                })
                .collect(Collectors.toList());
        
        // 如果基础分割也失败了，降级到简单的长度分割
        if (segments.isEmpty()) {
            log.warn("基础分割模式未识别到任何条文，降级到简单长度分割");
            return fallbackSplit(content, baseMetadata);
        }
        
        return segments;
    }
    
    /**
     * 第三阶段: 基于层级结构的上下文感知分割
     * 
     * @param content 文档内容
     * @param baseMetadata 基础元数据
     * @return 分割后的文本片段列表
     */
    private List<TextSegment> splitWithHierarchy(String content, Metadata baseMetadata) {
        log.debug("使用层级结构感知分割模式");
        
        List<TextSegment> segments = new ArrayList<>();
        String[] lines = content.split("\n");
        
        // 状态机变量
        String currentBook = null;
        String currentChapter = null;
        String currentSection = null;
        StringBuilder currentArticleContent = new StringBuilder();
        String currentArticleNumber = null;
        
        for (String line : lines) {
            line = line.trim();
            
            if (line.isEmpty()) {
                continue;
            }
            
            // 检查是否是"编"
            Matcher bookMatcher = BOOK_PATTERN.matcher(line);
            if (bookMatcher.matches()) {
                // 保存之前的条文
                if (currentArticleNumber != null) {
                    segments.add(createSegment(
                            currentArticleContent.toString(),
                            currentArticleNumber,
                            currentBook,
                            currentChapter,
                            currentSection,
                            baseMetadata
                    ));
                    currentArticleContent = new StringBuilder();
                    currentArticleNumber = null;
                }
                
                currentBook = bookMatcher.group(1);
                currentChapter = null;
                currentSection = null;
                log.trace("识别到编: {}", currentBook);
                continue;
            }
            
            // 检查是否是"章"
            Matcher chapterMatcher = CHAPTER_PATTERN.matcher(line);
            if (chapterMatcher.matches()) {
                // 保存之前的条文
                if (currentArticleNumber != null) {
                    segments.add(createSegment(
                            currentArticleContent.toString(),
                            currentArticleNumber,
                            currentBook,
                            currentChapter,
                            currentSection,
                            baseMetadata
                    ));
                    currentArticleContent = new StringBuilder();
                    currentArticleNumber = null;
                }
                
                currentChapter = chapterMatcher.group(1);
                currentSection = null;
                log.trace("识别到章: {}", currentChapter);
                continue;
            }
            
            // 检查是否是"节"
            Matcher sectionMatcher = SECTION_PATTERN.matcher(line);
            if (sectionMatcher.matches()) {
                // 保存之前的条文
                if (currentArticleNumber != null) {
                    segments.add(createSegment(
                            currentArticleContent.toString(),
                            currentArticleNumber,
                            currentBook,
                            currentChapter,
                            currentSection,
                            baseMetadata
                    ));
                    currentArticleContent = new StringBuilder();
                    currentArticleNumber = null;
                }
                
                currentSection = sectionMatcher.group(1);
                log.trace("识别到节: {}", currentSection);
                continue;
            }
            
            // 检查是否是"条"的开头
            Matcher articleMatcher = ARTICLE_NUMBER_PATTERN.matcher(line);
            if (articleMatcher.find()) {
                // 保存之前的条文
                if (currentArticleNumber != null) {
                    segments.add(createSegment(
                            currentArticleContent.toString(),
                            currentArticleNumber,
                            currentBook,
                            currentChapter,
                            currentSection,
                            baseMetadata
                    ));
                }
                
                // 开始新的条文
                currentArticleNumber = articleMatcher.group(1);
                currentArticleContent = new StringBuilder(line);
                log.trace("识别到条: {}", currentArticleNumber);
            } else if (currentArticleNumber != null) {
                // 追加到当前条文内容
                currentArticleContent.append("\n").append(line);
            }
        }
        
        // 保存最后一个条文
        if (currentArticleNumber != null) {
            segments.add(createSegment(
                    currentArticleContent.toString(),
                    currentArticleNumber,
                    currentBook,
                    currentChapter,
                    currentSection,
                    baseMetadata
            ));
        }
        
        log.debug("层级结构分割完成,共识别 {} 个条文", segments.size());
        
        // 如果没有识别到任何条文，降级到基础分割模式
        if (segments.isEmpty()) {
            log.warn("层级结构分割未识别到任何条文，降级到基础分割模式");
            return splitByArticle(content, baseMetadata);
        }
        
        return segments;
    }
    
    /**
     * 创建带有完整层级信息的文本片段
     */
    private TextSegment createSegment(String content, String articleNumber,
                                     String book, String chapter, String section,
                                     Metadata baseMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        
        if (baseMetadata != null) {
            metadata.putAll(baseMetadata.toMap());
        }
        
        // 基础信息
        metadata.put("article_number", articleNumber);
        metadata.put("split_type", "article_hierarchical");
        
        // 层级结构信息
        if (book != null) {
            metadata.put("book", book);
        }
        if (chapter != null) {
            metadata.put("chapter", chapter);
        }
        if (section != null) {
            metadata.put("section", section);
        }
        
        // 增强元数据：提取法律名称（从文件名中）
        if (baseMetadata != null) {
            String filename = (String) baseMetadata.toMap().get("original_filename");
            if (filename != null) {
                // 提取法律名称（去除扩展名）
                String lawName = filename.replaceAll("\\.(pdf|docx|txt|doc)$", "");
                metadata.put("law_name", lawName);
                
                // 添加法律分类标签（基于文件名关键词）
                metadata.put("law_category", categorizeLaw(lawName));
            }
        }
        
        // 构建完整的层级路径（便于过滤和检索）
        StringBuilder hierarchyPath = new StringBuilder();
        if (book != null) hierarchyPath.append(book).append(" > ");
        if (chapter != null) hierarchyPath.append(chapter).append(" > ");
        if (section != null) hierarchyPath.append(section).append(" > ");
        hierarchyPath.append(articleNumber);
        metadata.put("hierarchy_path", hierarchyPath.toString());
        
        return TextSegment.from(content.trim(), Metadata.from(metadata));
    }
    
    /**
     * 根据法律名称推断法律分类
     */
    private String categorizeLaw(String lawName) {
        if (lawName == null) return "其他";
        
        String name = lawName.toLowerCase();
        
        // 环境类
        if (name.contains("环境") || name.contains("污染")) {
            return "环境保护";
        }
        // 劳动类
        else if (name.contains("劳动") || name.contains("社保") || name.contains("工伤")) {
            return "劳动社保";
        }
        // 民事类
        else if (name.contains("民法") || name.contains("合同") || name.contains("物权") || name.contains("侵权")) {
            return "民事法律";
        }
        // 刑事类
        else if (name.contains("刑法") || name.contains("刑事")) {
            return "刑事法律";
        }
        // 行政类
        else if (name.contains("行政") || name.contains("处罚")) {
            return "行政法律";
        }
        // 诉讼类
        else if (name.contains("诉讼")) {
            return "诉讼程序";
        }
        // 基本法
        else if (name.contains("宪法")) {
            return "基本法律";
        }
        
        return "其他";
    }
    
    /**
     * 第二阶段: 处理超长条文
     * 
     * @param segments 原始片段列表
     * @return 处理后的片段列表
     */
    private List<TextSegment> handleLongArticles(List<TextSegment> segments) {
        List<TextSegment> result = new ArrayList<>();
        
        for (TextSegment segment : segments) {
            int estimatedTokens = estimateTokens(segment.text());
            
            if (estimatedTokens <= maxTokens) {
                // 条文长度在限制内,直接添加
                result.add(segment);
            } else {
                // 条文过长,需要二次分割
                log.debug("条文过长(估算 {} tokens),进行二次分割", estimatedTokens);
                result.addAll(splitLongArticle(segment));
            }
        }
        
        return result;
    }
    
    /**
     * 对超长条文进行二次分割
     * 
     * @param segment 原始片段
     * @return 分割后的片段列表
     */
    private List<TextSegment> splitLongArticle(TextSegment segment) {
        String content = segment.text();
        Metadata parentMetadata = segment.metadata();
        
        List<TextSegment> subSegments = new ArrayList<>();
        List<String> chunks = recursiveSplit(content);
        
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>(parentMetadata.toMap());
            metadata.put("part", i + 1);
            metadata.put("total_parts", chunks.size());
            metadata.put("split_type", "article_part");
            
            subSegments.add(TextSegment.from(chunks.get(i), Metadata.from(metadata)));
        }
        
        log.debug("超长条文被分割为 {} 个子片段", subSegments.size());
        return subSegments;
    }
    
    /**
     * 递归字符分割器
     * 按优先级使用不同的分隔符: 段落 > 句号 > 分号 > 逗号
     */
    private List<String> recursiveSplit(String text) {
        List<String> chunks = new ArrayList<>();
        
        // 分隔符优先级
        String[] separators = {"\n\n", "。", "；", "，"};
        
        splitRecursively(text, separators, 0, chunks);
        
        return chunks;
    }
    
    /**
     * 递归分割辅助方法
     */
    private void splitRecursively(String text, String[] separators, int separatorIndex, List<String> result) {
        // 检查是否已经足够短
        if (estimateTokens(text) <= maxTokens) {
            if (!text.trim().isEmpty()) {
                result.add(text.trim());
            }
            return;
        }
        
        // 如果已经尝试了所有分隔符,强制按字符分割
        if (separatorIndex >= separators.length) {
            forceCharacterSplit(text, result);
            return;
        }
        
        // 使用当前分隔符分割
        String separator = separators[separatorIndex];
        String[] parts = text.split(Pattern.quote(separator));
        
        if (parts.length == 1) {
            // 当前分隔符无法分割,尝试下一个分隔符
            splitRecursively(text, separators, separatorIndex + 1, result);
            return;
        }
        
        // 合并小片段
        StringBuilder currentChunk = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String testChunk = currentChunk.length() == 0 
                    ? part 
                    : currentChunk + separator + part;
            
            if (estimateTokens(testChunk) <= maxTokens) {
                if (currentChunk.length() > 0) {
                    currentChunk.append(separator);
                }
                currentChunk.append(part);
            } else {
                // 当前块已满,保存并开始新块
                if (currentChunk.length() > 0) {
                    // 递归处理当前块(可能仍然太长)
                    splitRecursively(currentChunk.toString(), separators, separatorIndex + 1, result);
                    
                    // 开始新块,添加重叠
                    currentChunk = new StringBuilder();
                    if (chunkOverlap > 0 && result.size() > 0) {
                        String lastChunk = result.get(result.size() - 1);
                        int overlapStart = Math.max(0, lastChunk.length() - chunkOverlap);
                        currentChunk.append(lastChunk.substring(overlapStart));
                        if (currentChunk.length() > 0) {
                            currentChunk.append(separator);
                        }
                    }
                }
                currentChunk.append(part);
            }
        }
        
        // 保存最后一块
        if (currentChunk.length() > 0) {
            splitRecursively(currentChunk.toString(), separators, separatorIndex + 1, result);
        }
    }
    
    /**
     * 强制按字符数分割(最后的手段)
     */
    private void forceCharacterSplit(String text, List<String> result) {
        int chunkSize = maxTokens * 3; // 粗略估算:每token约3个字符
        
        for (int i = 0; i < text.length(); i += chunkSize - chunkOverlap) {
            int end = Math.min(i + chunkSize, text.length());
            String chunk = text.substring(i, end).trim();
            if (!chunk.isEmpty()) {
                result.add(chunk);
            }
        }
    }
    
    /**
     * 提取条文编号
     */
    private String extractArticleNumber(String text) {
        Matcher matcher = ARTICLE_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "未知条号";
    }
    
    /**
     * 估算文本的Token数量
     * 粗略估算: 中文平均每个token约3-4个字符
     */
    private int estimateTokens(String text) {
        return text.length() / 3;
    }
    
    /**
     * 降级分割方法 - 当无法识别法律条文结构时使用
     * 简单地按段落和长度进行分割
     */
    private List<TextSegment> fallbackSplit(String content, Metadata baseMetadata) {
        log.info("使用降级分割策略 - 按段落和长度分割");
        
        List<TextSegment> segments = new ArrayList<>();
        
        // 首先按段落分割（双换行符）
        String[] paragraphs = content.split("\n\n+");
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            
            // 如果段落太长，进一步分割
            if (estimateTokens(paragraph) > maxTokens) {
                List<String> chunks = recursiveSplit(paragraph);
                for (String chunk : chunks) {
                    if (!chunk.trim().isEmpty()) {
                        Map<String, Object> metadata = new HashMap<>();
                        if (baseMetadata != null) {
                            metadata.putAll(baseMetadata.toMap());
                        }
                        metadata.put("split_type", "fallback_chunk");
                        segments.add(TextSegment.from(chunk.trim(), Metadata.from(metadata)));
                    }
                }
            } else {
                // 段落长度合适，直接添加
                Map<String, Object> metadata = new HashMap<>();
                if (baseMetadata != null) {
                    metadata.putAll(baseMetadata.toMap());
                }
                metadata.put("split_type", "fallback_paragraph");
                segments.add(TextSegment.from(paragraph, Metadata.from(metadata)));
            }
        }
        
        // 如果仍然没有分割出任何段落（整个文档是一个段落），强制分割
        if (segments.isEmpty() && !content.trim().isEmpty()) {
            log.warn("文档无法按段落分割，强制按长度分割");
            List<String> chunks = recursiveSplit(content);
            for (String chunk : chunks) {
                if (!chunk.trim().isEmpty()) {
                    Map<String, Object> metadata = new HashMap<>();
                    if (baseMetadata != null) {
                        metadata.putAll(baseMetadata.toMap());
                    }
                    metadata.put("split_type", "fallback_forced");
                    segments.add(TextSegment.from(chunk.trim(), Metadata.from(metadata)));
                }
            }
        }
        
        log.info("降级分割完成，生成 {} 个片段", segments.size());
        return segments;
    }

    @Override
    public List<TextSegment> splitAll(List<Document> documents) {
        return documents.stream()
                .flatMap(document -> split(document).stream())
                .collect(Collectors.toList());
    }
}

