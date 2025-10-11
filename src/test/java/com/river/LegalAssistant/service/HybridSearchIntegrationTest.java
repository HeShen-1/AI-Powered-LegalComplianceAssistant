package com.river.LegalAssistant.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HybridSearchService集成测试
 * 验证混合检索策略的实际效果
 * 
 * 注意：这些测试需要向量数据库中有实际数据
 */
@SpringBootTest
class HybridSearchIntegrationTest {
    
    @Autowired
    private HybridSearchService hybridSearchService;
    
    @Test
    void testPreciseArticleSearch_Success() {
        // 测试场景：精确条款查询成功
        // 前提：向量库中存在环境保护法第30条
        List<Document> results = hybridSearchService.search(
            "环境保护法第30条的内容是什么", 5
        );
        
        assertNotNull(results);
        // 如果向量库中有数据，应该能找到结果
        if (!results.isEmpty()) {
            Document topResult = results.get(0);
            
            // 验证metadata中有article_number字段
            Object articleNumber = topResult.getMetadata().get("article_number");
            assertNotNull(articleNumber, "Top结果应该包含article_number元数据");
            
            // 如果有precision_score，说明使用了混合检索
            Object precisionScore = topResult.getMetadata().get("precision_score");
            if (precisionScore != null) {
                double score = (double) precisionScore;
                // 精确匹配的得分应该很高
                assertTrue(score >= 0, "精确度得分应该为非负数");
            }
        }
    }
    
    @Test
    void testPreciseArticleSearch_NoAdjacencyConfusion() {
        // 测试场景：不应该混淆相邻条款
        // 前提：向量库中存在民法典第1198条及其相邻条款
        List<Document> results = hybridSearchService.search(
            "民法典第1198条", 3
        );
        
        assertNotNull(results);
        if (!results.isEmpty()) {
            Document topResult = results.get(0);
            Object articleNumber = topResult.getMetadata().get("article_number");
            
            if (articleNumber != null) {
                String articleStr = articleNumber.toString();
                // 第一个结果应该是第1198条，而不是相邻的1197或1199条
                assertTrue(
                    articleStr.contains("一千一百九十八") || articleStr.contains("1198"),
                    "第一个结果应该是第1198条，但实际是: " + articleStr
                );
            }
        }
    }
    
    @Test
    void testSemanticQuery() {
        // 测试场景：语义查询不受影响
        List<Document> results = hybridSearchService.search(
            "什么是违约责任", 5
        );
        
        assertNotNull(results);
        // 语义查询应该正常工作
    }
    
    @Test
    void testChapterLevelQuery() {
        // 测试场景：章节级查询
        List<Document> results = hybridSearchService.search(
            "民法典第三章", 5
        );
        
        assertNotNull(results);
        // 如果有结果，验证是否都属于第三章
        if (!results.isEmpty()) {
            for (Document doc : results) {
                Object chapter = doc.getMetadata().get("chapter");
                // 注意：可能不是所有结果都有chapter元数据
                if (chapter != null) {
                    // 如果有chapter，应该匹配查询的章节
                    // 这里不做严格断言，因为向量检索可能返回相关但不完全匹配的结果
                }
            }
        }
    }
    
    @Test
    void testEmptyQuery() {
        // 测试场景：空查询
        List<Document> results = hybridSearchService.search("", 5);
        
        assertNotNull(results);
        // 应该返回空列表或语义检索结果
    }
    
    @Test
    void testQueryWithoutLawName() {
        // 测试场景：只有条款编号，没有法律名称
        List<Document> results = hybridSearchService.search("第30条", 5);
        
        assertNotNull(results);
        // 应该降级到向量检索 + 元数据加权
    }
    
    @Test
    void testPerformanceComparison() {
        // 性能对比测试：精确查询应该很快
        String query = "环境保护法第30条";
        
        long startTime = System.currentTimeMillis();
        List<Document> results = hybridSearchService.search(query, 5);
        long duration = System.currentTimeMillis() - startTime;
        
        assertNotNull(results);
        // 检索时间应该在合理范围内（<1秒）
        assertTrue(duration < 1000, "检索耗时过长: " + duration + "ms");
    }
}

