package com.river.LegalAssistant.service.advanced;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class LegalQueryTransformer implements QueryTransformer {

    @Qualifier("langchain4jChatModel")
    @SuppressWarnings("unused")
    private final ChatModel chatModel;

    private static final Map<String, String> LEGAL_TERM_MAPPINGS = Map.of(
            "赔偿", "损害赔偿",
            "合同", "合同协议",
            "违约", "违约责任",
            "责任", "法律责任",
            "权利", "合法权益",
            "义务", "法定义务",
            "纠纷", "法律争议",
            "起诉", "提起诉讼"
    );

    private static final Pattern LEGAL_KEYWORDS_PATTERN = Pattern.compile(
            "法律|条款|合同|违约|责任|权利|义务|损失|赔偿|诉讼|仲裁|法规|条例|民法典|劳动|数据|出境|广告|隐私|个人信息|消费者"
    );

    private static final List<String> IMPORTANT_WORDS = List.of(
            "风险", "问题", "处理", "分析", "建议", "注意", "避免",
            "程序", "补偿", "告知", "披露", "争议", "救济", "赔付"
    );

    private static final Map<String, List<String>> DOMAIN_SIGNALS = Map.of(
            "civil_breach", List.of("违约", "违约金", "继续履行", "迟延履行", "损害赔偿", "补救措施"),
            "ecommerce", List.of("电商", "直播", "退货", "促销", "价格", "商品质量", "先行赔付", "消费者"),
            "labor_termination", List.of("劳动合同", "解除", "试用期", "裁员", "经济补偿", "代通知金", "用人单位"),
            "data_cross_border", List.of("境外", "跨境", "出境", "海外", "接收方", "客服系统", "标准合同"),
            "advertising", List.of("广告", "文案", "绝对化", "短视频", "功效", "KOL", "促销文案"),
            "personal_info", List.of("个人信息", "隐私政策", "最小必要", "默认勾选", "注销", "访问", "删除")
    );

    private static final Map<String, List<String>> DOMAIN_RETRIEVAL_TERMS = Map.of(
            "civil_breach", List.of("合同违约责任", "继续履行", "违约金", "损害赔偿", "迟延履行", "补救措施"),
            "ecommerce", List.of("电商消费者权益", "七天无理由退货", "促销规则", "价格欺诈", "商品质量争议", "先行赔付"),
            "labor_termination", List.of("劳动合同解除", "法定条件", "试用期", "裁员", "经济补偿", "代通知金"),
            "data_cross_border", List.of("数据出境", "跨境传输", "个人信息出境", "影响评估", "标准合同", "接收方"),
            "advertising", List.of("广告合规", "绝对化用语", "广告标识", "虚假宣传", "价格促销", "商业合作"),
            "personal_info", List.of("个人信息处理", "最小必要", "隐私政策", "用户权利", "访问删除", "留痕机制")
    );

    private static final Map<String, List<String>> DOMAIN_SUB_QUESTIONS = Map.of(
            "civil_breach", List.of(
                    "违约后通常可以主张哪些责任承担方式？",
                    "违约金与损害赔偿如何衔接适用？",
                    "迟延履行场景下如何控制损失扩大责任？"
            ),
            "ecommerce", List.of(
                    "平台对消费者投诉和商品质量争议有哪些处理义务？",
                    "促销与退货限制条款应如何真实显著披露？",
                    "消费者在该场景下通常有哪些救济路径？"
            ),
            "labor_termination", List.of(
                    "解除劳动合同前需要准备哪些证据和程序材料？",
                    "是否需要提前通知或支付经济补偿、代通知金？",
                    "劳动争议发生后常见的仲裁诉讼风险有哪些？"
            ),
            "data_cross_border", List.of(
                    "数据出境前需要完成哪些合法性基础与影响评估？",
                    "接收方合同、安全措施和权限控制应如何约定？",
                    "哪些业务变化会触发重新评估既有出境安排？"
            ),
            "advertising", List.of(
                    "广告文案和宣传用语有哪些禁止性要求？",
                    "商业合作和推广内容应如何做显著广告标识？",
                    "违规宣传后品牌方、平台方会面临哪些责任？"
            ),
            "personal_info", List.of(
                    "处理目的、范围和保存期限如何满足最小必要原则？",
                    "隐私政策和告知同意环节应披露哪些核心信息？",
                    "访问、更正、删除和注销响应机制应如何设置？"
            )
    );

    @Override
    public Collection<Query> transform(Query originalQuery) {
        if (originalQuery == null || originalQuery.text() == null || originalQuery.text().isBlank()) {
            return List.of(originalQuery);
        }

        log.info("转换法律查询: {}", originalQuery.text());

        List<Query> transformedQueries = new ArrayList<>();
        String originalText = originalQuery.text().trim();
        transformedQueries.add(originalQuery);

        try {
            String standardizedQuery = standardizeLegalTerms(originalText);
            if (!standardizedQuery.equals(originalText)) {
                transformedQueries.add(createQuery(standardizedQuery, originalQuery.metadata()));
            }

            String professionalQuery = generateProfessionalQuery(originalText);
            if (professionalQuery != null && !containsSimilarQuery(transformedQueries, professionalQuery)) {
                transformedQueries.add(createQuery(professionalQuery, originalQuery.metadata()));
            }

            for (String subQuestion : generateSubQuestions(originalText)) {
                if (!containsSimilarQuery(transformedQueries, subQuestion)) {
                    transformedQueries.add(createQuery(subQuestion, originalQuery.metadata()));
                }
            }

            String keywordQuery = extractKeywordQuery(originalText);
            if (keywordQuery != null && !containsSimilarQuery(transformedQueries, keywordQuery)) {
                transformedQueries.add(createQuery(keywordQuery, originalQuery.metadata()));
            }
        } catch (Exception exception) {
            log.error("查询转换失败，回退为原始查询", exception);
            return List.of(originalQuery);
        }

        log.info("查询转换完成，原始1个查询扩展为{}个查询", transformedQueries.size());
        return transformedQueries;
    }

    private String standardizeLegalTerms(String query) {
        String result = query;
        for (Map.Entry<String, String> entry : LEGAL_TERM_MAPPINGS.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result
                .replace("怎么办", "如何处理")
                .replace("能不能", "是否可以")
                .replace("要是", "如果")
                .replace("咋样", "如何")
                .replace("怎么", "如何")
                .trim();
    }

    private String generateProfessionalQuery(String query) {
        String domain = detectDomain(query);
        if (domain == null) {
            return null;
        }

        LinkedHashSet<String> retrievalTerms = new LinkedHashSet<>(extractMatchedKeywords(query));
        retrievalTerms.addAll(DOMAIN_RETRIEVAL_TERMS.getOrDefault(domain, Collections.emptyList()));

        if (retrievalTerms.isEmpty()) {
            return null;
        }

        String expandedQuery = String.join(" ", retrievalTerms);
        return expandedQuery.equals(query) ? null : expandedQuery;
    }

    private List<String> generateSubQuestions(String query) {
        String domain = detectDomain(query);
        if (domain == null) {
            return Collections.emptyList();
        }

        List<String> suggestions = new ArrayList<>();
        for (String candidate : DOMAIN_SUB_QUESTIONS.getOrDefault(domain, Collections.emptyList())) {
            if (!query.contains(candidate)) {
                suggestions.add(candidate);
            }
        }

        if ((query.contains("风险") || query.contains("分析")) && suggestions.size() < 3) {
            suggestions.add(getRiskControlQuestion(domain));
        }

        return suggestions.stream()
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .distinct()
                .limit(3)
                .toList();
    }

    private String extractKeywordQuery(String query) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();

        Matcher matcher = LEGAL_KEYWORDS_PATTERN.matcher(query);
        while (matcher.find()) {
            keywords.add(matcher.group());
        }

        for (String word : IMPORTANT_WORDS) {
            if (query.contains(word)) {
                keywords.add(word);
            }
        }

        String domain = detectDomain(query);
        if (domain != null) {
            DOMAIN_RETRIEVAL_TERMS.getOrDefault(domain, Collections.emptyList())
                    .stream()
                    .limit(3)
                    .forEach(keywords::add);
        }

        if (keywords.size() < 2) {
            return null;
        }

        return String.join(" ", keywords);
    }

    private List<String> extractMatchedKeywords(String query) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();

        Matcher matcher = LEGAL_KEYWORDS_PATTERN.matcher(query);
        while (matcher.find()) {
            keywords.add(matcher.group());
        }

        for (Map.Entry<String, List<String>> entry : DOMAIN_SIGNALS.entrySet()) {
            for (String signal : entry.getValue()) {
                if (query.contains(signal)) {
                    keywords.add(signal);
                }
            }
        }

        return new ArrayList<>(keywords);
    }

    private String detectDomain(String query) {
        String bestDomain = null;
        int bestScore = 0;

        for (Map.Entry<String, List<String>> entry : DOMAIN_SIGNALS.entrySet()) {
            int score = scoreDomain(query, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestDomain = entry.getKey();
            }
        }

        return bestScore > 0 ? bestDomain : null;
    }

    private int scoreDomain(String query, List<String> signals) {
        int score = 0;
        for (String signal : signals) {
            if (query.contains(signal)) {
                score++;
            }
        }
        return score;
    }

    private String getRiskControlQuestion(String domain) {
        return switch (domain) {
            case "civil_breach" -> "该违约场景下的责任边界、证据要求和救济路径分别是什么？";
            case "ecommerce" -> "该电商场景下的价格、退货和平台责任风险控制点分别是什么？";
            case "labor_termination" -> "该解除劳动合同场景下的证据、程序和补偿风险控制点分别是什么？";
            case "data_cross_border" -> "该数据出境场景下的合法性基础、安全保障和持续评估控制点分别是什么？";
            case "advertising" -> "该广告投放场景下的宣传真实性、标识义务和处罚风险分别是什么？";
            case "personal_info" -> "该个人信息处理场景下的同意有效性、最小必要和权利保障控制点分别是什么？";
            default -> "该法律场景下的主要风险、义务和救济路径分别是什么？";
        };
    }

    private boolean containsSimilarQuery(List<Query> existingQueries, String newQuery) {
        for (Query existingQuery : existingQueries) {
            String existing = existingQuery.text().toLowerCase();
            String candidate = newQuery.toLowerCase();

            if (existing.equals(candidate) || existing.contains(candidate) || candidate.contains(existing)) {
                return true;
            }

            double similarity = calculateSimilarity(existing, candidate);
            if (similarity > 0.8) {
                return true;
            }
        }

        return false;
    }

    private double calculateSimilarity(String first, String second) {
        int maxLength = Math.max(first.length(), second.length());
        if (maxLength == 0) {
            return 1.0;
        }

        int distance = levenshteinDistance(first, second);
        return 1.0 - (double) distance / maxLength;
    }

    private int levenshteinDistance(String first, String second) {
        int[][] dp = new int[first.length() + 1][second.length() + 1];

        for (int row = 0; row <= first.length(); row++) {
            dp[row][0] = row;
        }
        for (int column = 0; column <= second.length(); column++) {
            dp[0][column] = column;
        }

        for (int row = 1; row <= first.length(); row++) {
            for (int column = 1; column <= second.length(); column++) {
                int cost = first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1;
                dp[row][column] = Math.min(
                        Math.min(dp[row - 1][column] + 1, dp[row][column - 1] + 1),
                        dp[row - 1][column - 1] + cost
                );
            }
        }

        return dp[first.length()][second.length()];
    }

    private Query createQuery(String text, Object metadata) {
        return Query.from(text);
    }
}
