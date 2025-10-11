package com.river.LegalAssistant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextProcessingService 单元测试
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("文本处理服务测试")
class TextProcessingServiceTest {
    
    @Autowired
    private TextProcessingService textProcessingService;
    
    @Test
    @DisplayName("应该正确清理和规范化文本")
    void shouldCleanAndNormalizeText() {
        // Given
        String dirtyText = "测试文本\u0000包含特殊字符\n\n\n多个换行";
        
        // When
        String cleaned = textProcessingService.cleanAndNormalizeText(dirtyText);
        
        // Then
        assertThat(cleaned).doesNotContain("\u0000");
        assertThat(cleaned).contains("测试文本");
        assertThat(cleaned).contains("包含特殊字符");
        assertThat(cleaned).doesNotContain("\n\n\n");
    }
    
    @Test
    @DisplayName("应该正确分割长文本")
    void shouldSplitIntoChunks() {
        // Given
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longText.append("这是第").append(i).append("段文本。");
        }
        
        // When
        List<String> chunks = textProcessingService.splitIntoChunks(longText.toString());
        
        // Then
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThan(1);
    }
    
    @Test
    @DisplayName("应该正确清理文件名")
    void shouldCleanFilename() {
        // Given
        String filenameWithHash = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234567890_测试文档.pdf";
        String filenameWithPath = "uploads/contracts/测试文档.pdf";
        
        // When
        String cleaned1 = textProcessingService.cleanFilename(filenameWithHash);
        String cleaned2 = textProcessingService.cleanFilename(filenameWithPath);
        
        // Then
        assertThat(cleaned1).isEqualTo("测试文档.pdf");
        assertThat(cleaned2).contains("测试文档.pdf");
    }
    
    @Test
    @DisplayName("应该正确估算Token数量")
    void shouldEstimateTokenCount() {
        // Given
        String text = "这是一段测试文本";
        
        // When
        int tokenCount = textProcessingService.estimateTokenCount(text);
        
        // Then
        assertThat(tokenCount).isGreaterThan(0);
        assertThat(tokenCount).isLessThanOrEqualTo(text.length());
    }
    
    @Test
    @DisplayName("应该正确截断超长文本")
    void shouldTruncateToTokenLimit() {
        // Given
        String longText = "测试文本".repeat(100);
        int maxTokens = 50;
        
        // When
        String truncated = textProcessingService.truncateToTokenLimit(longText, maxTokens);
        
        // Then
        assertThat(truncated.length()).isLessThan(longText.length());
        assertThat(textProcessingService.estimateTokenCount(truncated))
                .isLessThanOrEqualTo(maxTokens + 10); // 允许一些误差
    }
}

