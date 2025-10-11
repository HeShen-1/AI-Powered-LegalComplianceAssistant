package com.river.LegalAssistant.service.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试LegalDocumentSplitter的修复
 * 验证对于各种格式的法律文档都能正确分割
 */
class LegalDocumentSplitterFixTest {

    private LegalDocumentSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new LegalDocumentSplitter(512, true, 50);
    }

    @Test
    void testSplitWithLeadingSpaces() {
        // 测试条文前有空格的情况
        String content = """
                中华人民共和国环境保护法
                
                  第一条 为保护和改善环境，防治污染和其他公害，保障公众健康，推进生态文明建设，促进经济社会可持续发展，制定本法。
                
                  第二条 本法所称环境，是指影响人类生存和发展的各种天然的和经过人工改造的自然因素的总体，包括大气、水、海洋、土地、矿藏、森林、草原、湿地、野生生物、自然遗迹、人文遗迹、自然保护区、风景名胜区、城市和乡村等。
                
                  第三条 本法适用于中华人民共和国领域和中华人民共和国管辖的其他海域。
                """;

        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);

        // 应该能够识别并分割出3个条文
        assertNotNull(segments);
        assertTrue(segments.size() >= 3, "应该至少分割出3个条文，实际: " + segments.size());
        
        // 验证第一个片段包含第一条
        assertTrue(segments.get(0).text().contains("第一条"), "第一个片段应该包含第一条");
        assertTrue(segments.get(0).text().contains("保护和改善环境"), "第一个片段应该包含第一条的内容");
    }

    @Test
    void testSplitWithoutArticleStructure() {
        // 测试完全没有条文结构的情况（应该降级到段落分割）
        String content = """
                这是一个法律文档的序言部分。
                
                它包含了一些重要的背景信息。
                
                但是没有任何条文编号。
                
                分割器应该能够处理这种情况。
                """;

        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);

        // 应该降级到段落分割，至少能分割出一些片段
        assertNotNull(segments);
        assertTrue(segments.size() > 0, "即使没有条文结构，也应该能分割出片段");
    }

    @Test
    void testSplitWithChapterStructure() {
        // 测试带有章节结构的法律文档
        String content = """
                第一章 总则
                
                第一条 为了规范某某行为，制定本法。
                
                第二条 本法适用于全国范围。
                
                第二章 具体规定
                
                第三条 具体规定内容。
                """;

        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);

        // 应该能够识别章节和条文
        assertNotNull(segments);
        assertTrue(segments.size() >= 3, "应该至少分割出3个条文");
        
        // 检查是否包含章节信息
        boolean hasChapterMetadata = segments.stream()
                .anyMatch(seg -> seg.metadata().toMap().containsKey("chapter"));
        assertTrue(hasChapterMetadata, "应该包含章节元数据");
    }

    @Test
    void testLongArticle() {
        // 测试超长条文（需要二次分割）
        StringBuilder longContent = new StringBuilder("第一条 ");
        for (int i = 0; i < 2000; i++) {
            longContent.append("这是一个很长的条文内容。");
        }

        Document document = Document.from(longContent.toString());
        List<TextSegment> segments = splitter.split(document);

        // 超长条文应该被分割成多个部分
        assertNotNull(segments);
        assertTrue(segments.size() > 1, "超长条文应该被分割成多个部分");
        
        // 验证每个片段都不超过token限制
        for (TextSegment segment : segments) {
            int estimatedTokens = segment.text().length() / 3;
            assertTrue(estimatedTokens <= 512 * 1.2, 
                    "每个片段的token数应该接近限制，实际: " + estimatedTokens);
        }
    }

    @Test
    void testMixedFormat() {
        // 测试混合格式：有空格、有章节、有长短不一的条文
        String content = """
                中华人民共和国某某法
                
                第一章 总则
                
                  第一条 短条文。
                
                第二条 这是一个中等长度的条文，包含了一些具体的规定和说明。
                
                  第三条 """ + "很长的条文内容。".repeat(500) + """
                
                第二章 具体规定
                
                第四条 另一个条文。
                """;

        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);

        // 应该能正确处理混合格式
        assertNotNull(segments);
        assertTrue(segments.size() >= 4, "应该至少识别出4个条文，实际: " + segments.size());
    }
}

