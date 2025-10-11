package com.river.LegalAssistant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptTemplateService 单元测试
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("提示词模板服务测试")
class PromptTemplateServiceTest {
    
    @Autowired
    private PromptTemplateService promptTemplateService;
    
    @Test
    @DisplayName("应该正确加载并渲染提示词模板")
    void shouldLoadAndRenderTemplate() {
        // Given
        String templateName = "legal-qa";
        Map<String, Object> variables = Map.of(
            "context", "合同法第XX条规定...",
            "question", "如何处理违约责任?"
        );
        
        // When
        String result = promptTemplateService.render(templateName, variables);
        
        // Then
        assertThat(result).isNotBlank();
        assertThat(result).contains("合同法第XX条规定...");
        assertThat(result).contains("如何处理违约责任?");
    }
    
    @Test
    @DisplayName("模板不存在时应该返回空字符串并记录日志")
    void shouldReturnEmptyWhenTemplateNotFound() {
        // When
        String result = promptTemplateService.render("non-existent-template", Map.of());
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("应该能获取法律问答系统提示词")
    void shouldGetLegalSystemPrompt() {
        // When
        String result = promptTemplateService.getBasicLegalSystemPrompt();
        
        // Then
        assertThat(result).isNotBlank();
        assertThat(result).contains("法律");
    }
}

