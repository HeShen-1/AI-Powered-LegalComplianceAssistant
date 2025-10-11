package com.river.LegalAssistant.util;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReportContentValidator 测试类
 * 测试重复内容检测和清理功能
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportContentValidatorTest {

    private final ReportContentValidator validator = new ReportContentValidator();

    @Test
    void testRemoveDuplicateContent_NoDuplicates() {
        String content = "这是一个正常的合同条款。这个条款规定了双方的权利义务。";
        String result = validator.removeDuplicateContent(content);
        assertEquals(content, result);
    }

    @Test
    void testRemoveDuplicateContent_WithDuplicates() {
        String content = "农村土地经营权租赁合同农村土地经营权租赁合同农村土地经营权租赁合同农村土地经营权租赁合同农村土地经营权租赁合同农村土地经营权租赁合同农村土地经营权租赁合同农村土地经营权租赁合同。";
        String result = validator.removeDuplicateContentImproved(content);
        
        // 应该移除重复内容，但保留至少一次
        assertTrue(result.length() < content.length());
        assertTrue(result.contains("农村土地经营权租赁合同"));
        // 检查是否还有大量重复
        assertFalse(result.contains("农村土地经营权租赁合同农村土地经营权租赁合同农村土地经营权租赁合同农村土地经营权租赁合同"));
    }

    @Test
    void testRemoveDuplicateContent_EmptyContent() {
        String result1 = validator.removeDuplicateContent(null);
        assertNull(result1);
        
        String result2 = validator.removeDuplicateContent("");
        assertEquals("", result2);
        
        String result3 = validator.removeDuplicateContent("   ");
        assertEquals("   ", result3);
    }

    @Test
    void testRemoveDuplicateContent_SingleSentence() {
        String content = "这是一个单独的句子。";
        String result = validator.removeDuplicateContent(content);
        assertEquals(content, result);
    }

    @Test
    void testRemoveDuplicateContent_ComplexDuplicates() {
        String content = "关键商业条款待约定关键商业条款待约定关键商业条款待约定关键商业条款待约定关键商业条款待约定关键商业条款待约定关键商业条款待约定关键商业条款待约定。风险等级为中。";
        String result = validator.removeDuplicateContentImproved(content);
        
        // 应该移除重复的"关键商业条款待约定"部分
        assertTrue(result.length() < content.length());
        assertTrue(result.contains("关键商业条款待约定"));
        assertTrue(result.contains("风险等级为中"));
    }
}