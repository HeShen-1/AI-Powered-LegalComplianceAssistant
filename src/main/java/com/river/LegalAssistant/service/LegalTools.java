package com.river.LegalAssistant.service;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 法律相关工具类，供 LangChain4j Agent 调用
 * 提供知识库检索、计算等专业工具功能
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegalTools {

    private final AiService aiService;

    /**
     * 在法律知识库中搜索相关文档
     * 
     * @param query 搜索查询内容
     * @param maxResults 最大返回结果数量，默认5个
     * @return 相关文档内容的摘要
     */
    @Tool("在法律知识库中搜索相关文档和案例。输入查询关键词，返回最相关的法律条文和合同条款。")
    public String searchLegalKnowledge(String query, Integer maxResults) {
        log.info("Agent调用法律知识库搜索工具，查询: {}", query);
        
        try {
            if (maxResults == null || maxResults <= 0) {
                maxResults = 5;
            }
            
            // 限制最大结果数量，避免返回过多内容
            maxResults = Math.min(maxResults, 10);
            
            List<Document> documents = aiService.searchSimilarDocuments(query, maxResults);
            
            if (documents.isEmpty()) {
                return "未找到与查询相关的法律文档。建议重新组织查询词汇或咨询专业法律人士。";
            }
            
            // 构建结构化的搜索结果
            StringBuilder result = new StringBuilder();
            result.append(String.format("找到 %d 个相关法律文档：\n\n", documents.size()));
            
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                String source = doc.getMetadata().getOrDefault("original_filename", "未知来源").toString();
                String content = doc.getText();
                
                // 截取内容避免过长
                if (content != null && content.length() > 300) {
                    content = content.substring(0, 300) + "...";
                }

                result.append(String.format("【文档 %d】来源: %s\n", i + 1, source));
                result.append("内容: ").append(content).append("\n\n");
            }
            
            log.info("法律知识库搜索完成，返回 {} 个结果", documents.size());
            return result.toString();
            
        } catch (Exception e) {
            log.error("法律知识库搜索失败", e);
            return "搜索过程中出现错误，请稍后重试。错误信息: " + e.getMessage();
        }
    }

    /**
     * 搜索特定类型的法律风险信息
     * 注意：此方法通过LangChain4j的@Tool注解供AI Agent调用，IDE可能显示未使用但实际会被AI调用
     * 
     * @param riskType 风险类型，如"合同主体风险"、"违约责任风险"等
     * @return 风险相关的法律条文和建议
     */
    @SuppressWarnings("unused") // 通过AI Agent的工具调用机制使用
    @Tool("搜索特定类型的法律风险信息和防范措施。输入风险类型，返回相关的法律条文和专业建议。")
    public String searchRiskInformation(String riskType) {
        log.info("Agent调用风险信息搜索工具，风险类型: {}", riskType);
        
        try {
            // 构建针对性的查询
            String query = riskType + " 法律风险 条文 防范措施 建议";
            
            List<Document> documents = aiService.searchSimilarDocuments(query, 3);
            
            if (documents.isEmpty()) {
                return String.format("未找到关于'%s'的具体法律条文。建议咨询专业法律顾问获取针对性的风险防范建议。", riskType);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("关于'%s'的法律条文和风险防范信息：\n\n", riskType));
            
            for (Document doc : documents) {
                String source = doc.getMetadata().getOrDefault("original_filename", "未知来源").toString();
                String content = doc.getText();

                if (content != null && content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }

                result.append("参考文献: ").append(source).append("\n");
                result.append("相关条文: ").append(content).append("\n\n");
            }
            
            result.append("建议: 具体的风险防范措施应根据实际情况制定，建议结合具体合同条款咨询专业法律人士。");
            
            log.info("风险信息搜索完成，风险类型: {}", riskType);
            return result.toString();
            
        } catch (Exception e) {
            log.error("风险信息搜索失败，风险类型: {}", riskType, e);
            return String.format("搜索'%s'风险信息时出现错误，请稍后重试。", riskType);
        }
    }

    /**
     * 计算违约金和赔偿金
     * 注意：此方法通过LangChain4j的@Tool注解供AI Agent调用，IDE可能显示未使用但实际会被AI调用
     * 
     * @param contractValue 合同价值（元）
     * @param penaltyRate 违约金比例（百分比，如20表示20%）
     * @param actualLoss 实际损失（元）
     * @return 计算结果和法律依据
     */
    @SuppressWarnings("unused") // 通过AI Agent的工具调用机制使用
    @Tool("计算合同违约金和赔偿金额。输入合同价值、违约金比例和实际损失，返回计算结果和相关法律依据。")
    public String calculatePenalty(double contractValue, double penaltyRate, Double actualLoss) {
        log.info("Agent调用违约金计算工具，合同价值: {}, 违约金比例: {}%", contractValue, penaltyRate);
        
        try {
            if (contractValue <= 0) {
                return "合同价值必须大于0，请检查输入参数。";
            }
            
            if (penaltyRate < 0 || penaltyRate > 100) {
                return "违约金比例应在0-100%之间，请检查输入参数。";
            }
            
            // 计算约定违约金
            double agreedPenalty = contractValue * (penaltyRate / 100.0);
            
            StringBuilder result = new StringBuilder();
            result.append("违约金计算结果：\n\n");
            result.append(String.format("合同价值: %.2f 元\n", contractValue));
            result.append(String.format("约定违约金比例: %.1f%%\n", penaltyRate));
            result.append(String.format("约定违约金额: %.2f 元\n\n", agreedPenalty));
            
            // 如果提供了实际损失，进行比较分析
            if (actualLoss != null && actualLoss >= 0) {
                result.append(String.format("实际损失: %.2f 元\n", actualLoss));
                
                double difference = agreedPenalty - actualLoss;
                if (Math.abs(difference) < 0.01) {
                    result.append("约定违约金与实际损失基本相当，符合法律规定。\n");
                } else if (difference > 0) {
                    double excessPercentage = (difference / actualLoss) * 100;
                    result.append(String.format("约定违约金超过实际损失 %.2f 元（%.1f%%）\n", difference, excessPercentage));
                    
                    if (excessPercentage > 30) {
                        result.append("超过部分可能被认定为过高，当事人可请求人民法院或仲裁机构予以适当减少。\n");
                    }
                } else {
                    result.append(String.format("约定违约金低于实际损失 %.2f 元，当事人可要求增加赔偿。\n", Math.abs(difference)));
                }
            }
            
            result.append("\n法律依据：\n");
            result.append("《中华人民共和国民法典》第585条：当事人可以约定一方违约时应当根据违约情况向对方支付一定数额的违约金...\n");
            result.append("约定的违约金低于造成的损失的，人民法院或者仲裁机构可以根据当事人的请求予以增加；\n");
            result.append("约定的违约金过分高于造成的损失的，人民法院或者仲裁机构可以根据当事人的请求予以适当减少。");
            
            log.info("违约金计算完成");
            return result.toString();
            
        } catch (Exception e) {
            log.error("违约金计算失败", e);
            return "计算过程中出现错误，请检查输入参数并重试。";
        }
    }

    /**
     * 查询法律条文释义
     * 注意：此方法通过LangChain4j的@Tool注解供AI Agent调用，IDE可能显示未使用但实际会被AI调用
     * 
     * @param lawName 法律名称，如"民法典"、"合同法"等
     * @param articleNumber 条文编号，如"第585条"
     * @return 条文内容和释义
     */
    @SuppressWarnings("unused") // 通过AI Agent的工具调用机制使用
    @Tool("查询特定法律条文的详细内容和释义。输入法律名称和条文编号，返回条文原文和专业解释。")
    public String queryLegalArticle(String lawName, String articleNumber) {
        log.info("Agent调用法律条文查询工具，法律: {}, 条文: {}", lawName, articleNumber);
        
        try {
            // 构建查询
            String query = lawName + " " + articleNumber + " 条文 内容";
            
            List<Document> documents = aiService.searchSimilarDocuments(query, 2);
            
            if (documents.isEmpty()) {
                return String.format("未找到%s %s的相关条文。请检查法律名称和条文编号是否正确。", lawName, articleNumber);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("%s %s 查询结果：\n\n", lawName, articleNumber));
            
            for (Document doc : documents) {
                String content = doc.getText();
                String source = doc.getMetadata().getOrDefault("original_filename", "未知来源").toString();
                
                result.append("来源: ").append(source).append("\n");
                result.append("内容: ").append(content).append("\n\n");
            }
            
            result.append("注意: 以上内容仅供参考，具体适用请咨询专业法律人士。");
            
            log.info("法律条文查询完成");
            return result.toString();
            
        } catch (Exception e) {
            log.error("法律条文查询失败", e);
            return String.format("查询%s %s时出现错误，请稍后重试。", lawName, articleNumber);
        }
    }

    /**
     * 分析合同条款的合理性
     * 
     * @param clauseText 合同条款文本
     * @param clauseType 条款类型，如"违约责任"、"付款条件"等
     * @return 条款分析结果和改进建议
     */
    @Tool("分析特定合同条款的合法性和合理性。输入条款文本和类型，返回专业分析和改进建议。")
    public String analyzeContractClause(String clauseText, String clauseType) {
        log.info("Agent调用合同条款分析工具，条款类型: {}", clauseType);
        
        try {
            if (clauseText == null || clauseText.trim().length() < 10) {
                return "条款文本过短，请提供完整的条款内容进行分析。";
            }
            
            // 搜索相关法律依据
            String query = clauseType + " 合同条款 法律规定 标准条款";
            List<Document> documents = aiService.searchSimilarDocuments(query, 3);
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("'%s'条款分析：\n\n", clauseType));
            result.append("条款内容: ").append(clauseText).append("\n\n");
            
            if (!documents.isEmpty()) {
                result.append("相关法律依据：\n");
                for (Document doc : documents) {
                    String content = doc.getText();
                    String source = doc.getMetadata().getOrDefault("original_filename", "未知").toString();

                    if (content != null && content.length() > 150) {
                        content = content.substring(0, 150) + "...";
                    }

                    result.append("- 来源: ").append(source).append("\n");
                    result.append("  内容: ").append(content).append("\n\n");
                }
            }
            
            // 基于常见风险点提供分析建议
            result.append("分析建议：\n");
            
            if (clauseType.contains("违约") || clauseType.contains("责任")) {
                result.append("- 检查违约责任是否对等，避免责任约定过重或过轻\n");
                result.append("- 确认违约金数额是否合理，建议控制在合同价值的20%-30%以内\n");
            } else if (clauseType.contains("付款") || clauseType.contains("支付")) {
                result.append("- 确认付款期限是否明确，避免模糊表述\n");
                result.append("- 检查逾期付款利息或违约金的约定是否合理\n");
            } else if (clauseType.contains("解除") || clauseType.contains("终止")) {
                result.append("- 确认合同解除条件是否明确具体\n");
                result.append("- 检查解除后的责任承担和财产处理是否清晰\n");
            } else {
                result.append("- 确认条款表述是否明确，避免歧义\n");
                result.append("- 检查是否符合相关法律法规的强制性规定\n");
            }
            
            result.append("\n重要提醒: 以上分析仅供参考，具体的合同条款设计建议咨询专业律师。");
            
            log.info("合同条款分析完成，条款类型: {}", clauseType);
            return result.toString();
            
        } catch (Exception e) {
            log.error("合同条款分析失败", e);
            return "条款分析过程中出现错误，请稍后重试。";
        }
    }
}
