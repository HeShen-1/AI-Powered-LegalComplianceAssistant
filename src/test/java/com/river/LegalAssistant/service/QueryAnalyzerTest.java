package com.river.LegalAssistant.service;

import com.river.LegalAssistant.dto.QueryIntent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryAnalyzer单元测试
 * 验证查询意图识别的准确性
 */
@SpringBootTest
class QueryAnalyzerTest {
    
    @Autowired
    private QueryAnalyzer queryAnalyzer;
    
    @Test
    void testPreciseArticleQuery_WithNumber() {
        // 测试：数字格式的条款查询
        QueryIntent intent = queryAnalyzer.analyze("环境保护法第30条的内容是什么");
        
        assertEquals("环境保护法", intent.getLawName());
        assertEquals("第三十条", intent.getArticleNumber());
        assertEquals(QueryIntent.QueryType.PRECISE_ARTICLE, intent.getQueryType());
        assertTrue(intent.isPreciseQuery());
        assertTrue(intent.hasExactMatchInfo());
    }
    
    @Test
    void testPreciseArticleQuery_WithChinese() {
        // 测试：中文格式的条款查询
        QueryIntent intent = queryAnalyzer.analyze("民法典第一千一百九十八条");
        
        assertEquals("民法典", intent.getLawName());
        assertEquals("第一千一百九十八条", intent.getArticleNumber());
        assertEquals(QueryIntent.QueryType.PRECISE_ARTICLE, intent.getQueryType());
    }
    
    @Test
    void testPreciseArticleQuery_WithoutLawName() {
        // 测试：只有条款编号，没有法律名称
        QueryIntent intent = queryAnalyzer.analyze("第30条的内容");
        
        assertNull(intent.getLawName());
        assertEquals("第三十条", intent.getArticleNumber());
        assertEquals(QueryIntent.QueryType.PRECISE_ARTICLE, intent.getQueryType());
        assertFalse(intent.hasExactMatchInfo());  // 缺少法律名称
    }
    
    @Test
    void testChapterLevelQuery() {
        // 测试：章节级查询
        QueryIntent intent = queryAnalyzer.analyze("民法典第三章的主要内容");
        
        assertEquals("民法典", intent.getLawName());
        assertEquals("第三章", intent.getChapter());
        assertEquals(QueryIntent.QueryType.CHAPTER_LEVEL, intent.getQueryType());
    }
    
    @Test
    void testSemanticQuery() {
        // 测试：语义查询
        QueryIntent intent = queryAnalyzer.analyze("什么是违约责任");
        
        assertNull(intent.getArticleNumber());
        assertNull(intent.getChapter());
        assertEquals(QueryIntent.QueryType.SEMANTIC, intent.getQueryType());
        assertFalse(intent.isPreciseQuery());
    }
    
    @Test
    void testComplexQuery() {
        // 测试：复杂查询
        QueryIntent intent = queryAnalyzer.analyze("民法典第1198条和环境保护法第30条");
        
        // 注意：当前版本会识别第一个条款
        assertEquals(QueryIntent.QueryType.PRECISE_ARTICLE, intent.getQueryType());
        // TODO: 未来版本应该识别为COMPLEX
    }
    
    @Test
    void testLawNameExtraction_WithBookmarks() {
        // 测试：带书名号的法律名称
        QueryIntent intent = queryAnalyzer.analyze("《中华人民共和国环境保护法》第30条");
        
        assertEquals("环境保护法", intent.getLawName());  // 应该去除"中华人民共和国"前缀
        assertEquals("第三十条", intent.getArticleNumber());
    }
    
    @Test
    void testChineseNumberConversion() {
        // 测试：各种数字格式的转换
        String[][] testCases = {
            {"第1条", "第一条"},
            {"第10条", "第十条"},
            {"第11条", "第十一条"},
            {"第20条", "第二十条"},
            {"第30条", "第三十条"},
            {"第100条", "第一百条"},
            {"第1198条", "第一千一百九十八条"}
        };
        
        for (String[] testCase : testCases) {
            QueryIntent intent = queryAnalyzer.analyze(testCase[0]);
            assertEquals(testCase[1], intent.getArticleNumber(),
                "Failed for input: " + testCase[0]);
        }
    }
    
    @Test
    void testEmptyQuery() {
        // 测试：空查询
        QueryIntent intent = queryAnalyzer.analyze("");
        
        assertEquals(QueryIntent.QueryType.SEMANTIC, intent.getQueryType());
    }
    
    @Test
    void testNullQuery() {
        // 测试：null查询
        QueryIntent intent = queryAnalyzer.analyze(null);
        
        assertEquals(QueryIntent.QueryType.SEMANTIC, intent.getQueryType());
    }
}

