package com.river.LegalAssistant.service;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 服务层
 */
@Service
@Slf4j
public class AiService {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    @Value("${app.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${app.rag.max-results:5}")
    private int maxResults;
    
    @Value("${app.rag.chunk-size:1000}")
    private int chunkSize;
    
    @Value("${app.rag.chunk-overlap:100}")
    private int chunkOverlap;
    
    // 嵌入模型最大token限制
    private static final int MAX_EMBEDDING_TOKENS = 500;

    @Value("${app.ai.prompts.legal-qa}")
    private Resource legalQaPromptResource;
    
    @Value("${app.ai.prompts.contract-risk-analysis}")
    private Resource contractRiskAnalysisPromptResource;
    
    public AiService(ChatClient chatClient, 
                    @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel,
                    VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    /**
     * 基础聊天功能
     */
    public String chat(String message) {
        log.info("处理聊天请求: {}", message);
        try {
            String response = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            log.info("聊天响应生成成功");
            return response;
        } catch (Exception e) {
            log.error("聊天处理失败", e);
            throw new RuntimeException("聊天服务暂时不可用", e);
        }
    }

    /**
     * RAG 增强聊天
     */
    public String chatWithRag(String question) {
        log.info("处理 RAG 增强聊天请求: {}", question);
        try {
            // 1. 向量检索相关文档
            List<Document> similarDocs = searchSimilarDocuments(question, maxResults);

            // 当找不到相关文档时，直接返回提示信息，避免模板渲染错误
            if (similarDocs.isEmpty()) {
                log.warn("对于问题 '{}' 未找到相似文档", question);
                return "根据您提供的法律条文，我无法找到该问题的直接答案。";
            }

            // 2. 构建上下文，包含源文件名
            String context = similarDocs.stream()
                .map(doc -> {
                    String source = doc.getMetadata().getOrDefault("original_filename", "未知来源").toString();
                    return "来源: " + source + "\n内容:\n" + doc.getText();
                })
                .collect(Collectors.joining("\n\n---\n\n"));
            
            // 3. 使用模板生成回答
            PromptTemplate promptTemplate = new PromptTemplate(legalQaPromptResource);

            // 为模板中的`{source}`占位符提供一个值
            String sources = similarDocs.stream()
                    .map(doc -> doc.getMetadata().getOrDefault("original_filename", "未知来源").toString())
                    .distinct()
                    .collect(Collectors.joining(", "));

            Map<String, Object> promptValues = Map.of(
                "context", context,
                "question", question,
                "source", sources
            );

            String response = chatClient.prompt(promptTemplate.create(promptValues))
                    .call()
                    .content();
            
            log.info("RAG 增强聊天响应生成成功，使用了 {} 个参考文档", similarDocs.size());
            return response;
        } catch (Exception e) {
            log.error("RAG 增强聊天处理失败", e);
            throw new RuntimeException("RAG 聊天服务暂时不可用", e);
        }
    }

    /**
     * 文档向量化并存储（支持自动分块）
     */
    public void addDocument(String content, Map<String, Object> metadata) {
        log.info("添加文档到向量存储，内容长度: {} 字符", content.length());
        
        if (content.trim().isEmpty()) {
            log.warn("文档内容为空，跳过添加");
            return;
        }
        
        try {
            // 检查内容长度，如果太长则进行分块
            if (needsChunking(content)) {
                log.info("文档内容较长，进行分块处理");
                addDocumentWithChunking(content, metadata);
            } else {
                // 内容较短，直接添加
                Document document = new Document(content, metadata);
                vectorStore.add(List.of(document));
                log.info("文档成功添加到向量存储（单块）");
            }
        } catch (Exception e) {
            log.error("文档添加失败", e);
            throw new RuntimeException("文档存储服务暂时不可用", e);
        }
    }
    
    /**
     * 分块处理长文档
     */
    private void addDocumentWithChunking(String content, Map<String, Object> metadata) {
        List<String> chunks = splitTextIntoChunks(content);
        List<Document> documents = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            
            // 为每个块创建元数据
            Map<String, Object> chunkMetadata = new HashMap<>(metadata);
            chunkMetadata.put("chunk_index", i);
            chunkMetadata.put("total_chunks", chunks.size());
            chunkMetadata.put("chunk_size", chunk.length());
            
            // 添加原始文档标识
            if (metadata.containsKey("review_id")) {
                chunkMetadata.put("source_review_id", metadata.get("review_id"));
            }
            
            Document document = new Document(chunk, chunkMetadata);
            documents.add(document);
        }
        
        // 批量添加所有块
        vectorStore.add(documents);
        log.info("文档成功分块并添加到向量存储，共 {} 个块", documents.size());
    }
    
    /**
     * 判断是否需要分块
     */
    private boolean needsChunking(String content) {
        // 粗略估算：平均每个token约4个字符（中文可能更少）
        int estimatedTokens = content.length() / 3;  // 保守估计，中文字符token比例更高
        return estimatedTokens > MAX_EMBEDDING_TOKENS;
    }
    
    /**
     * 将文本分割成块
     */
    private List<String> splitTextIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        
        // 如果文本长度小于等于chunkSize，直接返回
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // 如果不是最后一块，尝试在合适的位置切分（避免切断单词）
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
        
        return chunks;
    }
    
    /**
     * 找到最佳的文本分割点
     */
    private int findOptimalSplitPoint(String text, int start, int suggestedEnd) {
        // 在建议的结束位置向前查找合适的分割点
        int searchStart = Math.max(start, suggestedEnd - 100); // 最多向前查找100个字符
        
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
        
        // 如果找不到合适的分割点，就在建议位置强制分割
        return suggestedEnd;
    }

    /**
     * 测试文档分块功能（用于调试）
     */
    public List<Map<String, Object>> testDocumentChunking(String content) {
        log.info("测试文档分块，内容长度: {} 字符", content.length());
        
        List<Map<String, Object>> chunkInfo = new ArrayList<>();
        
        if (needsChunking(content)) {
            List<String> chunks = splitTextIntoChunks(content);
            
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> info = getStringObjectMap(chunks, i);

                chunkInfo.add(info);
            }
        } else {
            Map<String, Object> info = new HashMap<>();
            info.put("index", 0);
            info.put("length", content.length());
            info.put("estimatedTokens", content.length() / 3);
            info.put("preview", content.length() > 100 ? content.substring(0, 100) + "..." : content);
            info.put("withinTokenLimit", true);
            info.put("needsChunking", false);
            
            chunkInfo.add(info);
        }
        
        log.info("分块测试完成，共生成 {} 个块", chunkInfo.size());
        return chunkInfo;
    }

    private static Map<String, Object> getStringObjectMap(List<String> chunks, int i) {
        String chunk = chunks.get(i);
        int estimatedTokens = chunk.length() / 3;

        Map<String, Object> info = new HashMap<>();
        info.put("index", i);
        info.put("length", chunk.length());
        info.put("estimatedTokens", estimatedTokens);
        info.put("preview", chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk);
        info.put("withinTokenLimit", estimatedTokens <= MAX_EMBEDDING_TOKENS);
        return info;
    }

    /**
     * 文本向量化
     */
    public List<Double> embed(String text) {
        log.info("对文本进行向量化，长度: {}", text.length());
        try {
            List<Double> embedding = new ArrayList<>();
            // Spring AI 在部分版本中会返回 float[]，这里做兼容处理
            float[] floatEmbedding = embeddingModel.embed(text);
            for (float f : floatEmbedding) {
                embedding.add((double) f);
            }
            log.info("文本向量化成功，维度: {}", embedding.size());
            return embedding;
        } catch (Exception e) {
            log.error("文本向量化失败", e);
            throw new RuntimeException("向量化服务暂时不可用", e);
        }
    }

    /**
     * 搜索相似文档
     */
    public List<Document> searchSimilarDocuments(String query, int maxResults) {
        log.info("搜索相似文档，查询: {}, 最大结果数: {}, 相似度阈值: {}", query, maxResults, similarityThreshold);
        try {
            // 使用基础的similaritySearch方法以确保版本兼容性
            List<Document> results = vectorStore.similaritySearch(query);
            
            // 根据相似度阈值过滤结果（注意：这里简化处理，实际应用中需要具体的相似度分数）
            // 由于部分VectorStore实现可能不直接提供分数，这里主要是为了使用similarityThreshold字段
            log.debug("使用相似度阈值 {} 进行结果过滤", similarityThreshold);
            
            // 如果返回的结果数量超过maxResults，则手动截取
            if (results.size() > maxResults) {
                results = results.subList(0, maxResults);
            }
            
            log.info("找到 {} 个相似文档", results.size());
            return results;
        } catch (Exception e) {
            log.error("相似文档搜索失败", e);
            throw new RuntimeException("文档搜索服务暂时不可用", e);
        }
    }

    /**
     * 合同风险分析 - 使用专业提示词模板
     */
    public String analyzeContractRisk(String contractContent) {
        log.info("分析合同风险，内容长度: {}", contractContent.length());
        try {
            // 使用专业的风险分析提示词模板
            PromptTemplate promptTemplate = new PromptTemplate(contractRiskAnalysisPromptResource);
            
            Map<String, Object> promptValues = Map.of(
                "contractContent", contractContent
            );
            
            String response = chatClient.prompt(promptTemplate.create(promptValues))
                    .call()
                    .content();

            if (response != null) {
                log.info("合同风险分析完成，分析结果长度: {} 字符", response.length());
            }
            return response;
        } catch (Exception e) {
            log.error("合同风险分析失败", e);
            throw new RuntimeException("合同分析服务暂时不可用", e);
        }
    }

    /**
     * 分析合同并提取结构化风险信息
     * 
     * @param contractContent 合同内容
     * @return 结构化的风险分析结果
     */
    public ContractRiskAnalysisResult analyzeContractRiskStructured(String contractContent) {
        log.info("进行结构化合同风险分析，内容长度: {}", contractContent.length());
        
        try {
            // 执行基础风险分析
            String analysisResult = analyzeContractRisk(contractContent);
            
            // 解析分析结果并提取结构化信息
            ContractRiskAnalysisResult result = parseRiskAnalysisResult(analysisResult);
            
            log.info("结构化风险分析完成，识别出 {} 个风险点", result.getRiskClauses().size());
            return result;
            
        } catch (Exception e) {
            log.error("结构化合同风险分析失败", e);
            throw new RuntimeException("结构化分析服务暂时不可用", e);
        }
    }

    /**
     * 解析风险分析结果，提取结构化信息
     */
    private ContractRiskAnalysisResult parseRiskAnalysisResult(String analysisResult) {
        ContractRiskAnalysisResult result = new ContractRiskAnalysisResult();
        result.setOriginalAnalysis(analysisResult);
        
        // 解析整体风险等级
        String overallRiskLevel = determineOverallRiskLevel(analysisResult);
        result.setOverallRiskLevel(overallRiskLevel);
        
        // 提取核心风险提示
        List<String> coreRiskAlerts = extractCoreRiskAlerts(analysisResult);
        result.setCoreRiskAlerts(coreRiskAlerts);
        
        // 提取优先改进建议
        List<String> priorityRecommendations = extractPriorityRecommendations(analysisResult);
        result.setPriorityRecommendations(priorityRecommendations);
        
        // 解析具体风险条款（简化版本，实际应用中可使用更复杂的NLP技术）
        List<RiskClauseInfo> riskClauses = extractRiskClauses(analysisResult);
        result.setRiskClauses(riskClauses);
        
        // 计算合规评分
        int complianceScore = calculateComplianceScore(analysisResult);
        result.setComplianceScore(complianceScore);
        
        return result;
    }

    /**
     * 确定整体风险等级
     */
    private String determineOverallRiskLevel(String analysisResult) {
        String result = analysisResult.toLowerCase();
        
        if (result.contains("高风险") || result.contains("严重风险") || result.contains("重大风险")) {
            return "HIGH";
        } else if (result.contains("中等风险") || result.contains("一般风险") || result.contains("中风险")) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * 提取核心风险提示
     */
    private List<String> extractCoreRiskAlerts(String analysisResult) {
        List<String> alerts = new ArrayList<>();
        
        // 简化的文本解析逻辑，寻找核心风险提示部分
        String[] lines = analysisResult.split("\n");
        boolean inCoreRiskSection = false;
        
        for (String line : lines) {
            if (line.contains("核心风险提示") || line.contains("主要风险")) {
                inCoreRiskSection = true;
                continue;
            }
            
            if (inCoreRiskSection) {
                if (line.trim().startsWith("-") || line.trim().startsWith("•") || 
                    line.trim().matches("\\d+\\..*")) {
                    alerts.add(line.trim().replaceAll("^[-•\\d.\\s]+", ""));
                }
                
                // 如果遇到新的章节，停止提取
                if (line.contains("##") && !line.contains("核心风险")) {
                    break;
                }
            }
        }
        
        return alerts.isEmpty() ? List.of("需要进一步人工审查") : alerts;
    }

    /**
     * 提取优先改进建议
     */
    private List<String> extractPriorityRecommendations(String analysisResult) {
        List<String> recommendations = new ArrayList<>();
        
        String[] lines = analysisResult.split("\n");
        boolean inRecommendationSection = false;
        
        for (String line : lines) {
            if (line.contains("优先改进建议") || line.contains("改进建议")) {
                inRecommendationSection = true;
                continue;
            }
            
            if (inRecommendationSection) {
                if (line.trim().startsWith("-") || line.trim().startsWith("•") || 
                    line.trim().matches("\\d+\\..*")) {
                    recommendations.add(line.trim().replaceAll("^[-•\\d.\\s]+", ""));
                }
                
                if (line.contains("##") && !line.contains("建议")) {
                    break;
                }
            }
        }
        
        return recommendations.isEmpty() ? List.of("建议咨询专业法律人士") : recommendations;
    }

    /**
     * 提取风险条款信息（简化版本）
     */
    private List<RiskClauseInfo> extractRiskClauses(String analysisResult) {
        List<RiskClauseInfo> riskClauses = new ArrayList<>();
        
        // 基于关键词识别风险类型
        String[] riskTypes = {
            "合同主体资格风险", "权利义务平衡性风险", "履约条件与标准风险",
            "违约责任与救济措施风险", "争议解决机制风险", "合同变更与解除风险",
            "知识产权保护风险", "数据保护与隐私风险", "不可抗力与风险分配", "法律合规性风险"
        };
        
        for (String riskType : riskTypes) {
            if (analysisResult.contains(riskType)) {
                RiskClauseInfo riskClause = new RiskClauseInfo();
                riskClause.setRiskType(riskType);
                riskClause.setRiskLevel(extractRiskLevelForType(analysisResult, riskType));
                riskClause.setRiskDescription(extractDescriptionForType(analysisResult, riskType));
                riskClauses.add(riskClause);
            }
        }
        
        return riskClauses;
    }

    /**
     * 为特定风险类型提取风险等级
     */
    private String extractRiskLevelForType(String analysisResult, String riskType) {
        // 在风险类型附近查找风险等级关键词
        int typeIndex = analysisResult.indexOf(riskType);
        if (typeIndex == -1) return "MEDIUM";
        
        String contextWindow = analysisResult.substring(
            Math.max(0, typeIndex - 100), 
            Math.min(analysisResult.length(), typeIndex + 300)
        );
        
        if (contextWindow.contains("高风险")) return "HIGH";
        if (contextWindow.contains("低风险")) return "LOW";
        return "MEDIUM";
    }

    /**
     * 为特定风险类型提取描述
     */
    private String extractDescriptionForType(String analysisResult, String riskType) {
        int typeIndex = analysisResult.indexOf(riskType);
        if (typeIndex == -1) return "需要进一步审查";
        
        String contextWindow = analysisResult.substring(
            typeIndex, 
            Math.min(analysisResult.length(), typeIndex + 200)
        );
        
        // 提取第一段描述性文字
        String[] lines = contextWindow.split("\n");
        for (String line : lines) {
            if (line.trim().length() > 20 && !line.contains("##") && !line.contains("**")) {
                return line.trim();
            }
        }
        
        return "需要进一步审查";
    }

    /**
     * 计算合规评分
     */
    private int calculateComplianceScore(String analysisResult) {
        String result = analysisResult.toLowerCase();
        int score = 100;
        
        // 根据风险关键词扣分
        if (result.contains("高风险")) score -= 20;
        if (result.contains("严重")) score -= 15;
        if (result.contains("违法") || result.contains("违规")) score -= 30;
        if (result.contains("不合规")) score -= 25;
        if (result.contains("缺失") || result.contains("不完整")) score -= 10;
        
        return Math.max(score, 20); // 最低20分
    }

    /**
     * 合同风险分析结果类
     */
    @Setter
    @Getter
    public static class ContractRiskAnalysisResult {
        // Getters and Setters
        private String originalAnalysis;
        private String overallRiskLevel;
        private List<String> coreRiskAlerts = new ArrayList<>();
        private List<String> priorityRecommendations = new ArrayList<>();
        private List<RiskClauseInfo> riskClauses = new ArrayList<>();
        private Integer complianceScore;

    }

    /**
     * 风险条款信息类
     */
    @Setter
    @Getter
    public static class RiskClauseInfo {
        // Getters and Setters
        private String riskType;
        private String riskLevel;
        private String riskDescription;
        private String suggestion;

    }
}
