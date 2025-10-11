package com.river.LegalAssistant.service.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 法律文档分割器单元测试
 * 
 * 测试三个阶段的功能:
 * 1. 基础的按条分割
 * 2. 长条文的二次分割与元数据传播
 * 3. 层级结构的上下文感知
 */
@DisplayName("法律文档分割器测试")
class LegalDocumentSplitterTest {

    private LegalDocumentSplitter splitter;
    
    @BeforeEach
    void setUp() {
        // 使用默认配置初始化分割器
        splitter = new LegalDocumentSplitter(512, true, 50);
    }
    
    @Test
    @DisplayName("第一阶段: 基础按条分割测试")
    void testBasicArticleSplitting() {
        // 准备测试数据
        String content = """
            第一条 中华人民共和国公民有劳动的权利和义务。
            第二条 在中华人民共和国境内的企业、个体经济组织和与之形成劳动关系的劳动者，适用本法。
            第三条 劳动者享有平等就业和选择职业的权利、取得劳动报酬的权利、休息休假的权利、获得劳动安全卫生保护的权利、接受职业技能培训的权利。
            """;
        
        Document document = Document.from(content, Metadata.from("source", "test.pdf"));
        
        // 执行分割
        List<TextSegment> segments = splitter.split(document);
        
        // 验证结果
        assertNotNull(segments, "分割结果不应为null");
        assertEquals(3, segments.size(), "应该分割出3个条文");
        
        // 验证第一条
        TextSegment firstArticle = segments.get(0);
        assertTrue(firstArticle.text().contains("第一条"), "第一条应包含条文编号");
        assertTrue(firstArticle.text().contains("劳动的权利和义务"), "第一条应包含完整内容");
        assertEquals("第一条", firstArticle.metadata().toMap().get("article_number"), "元数据应包含条文编号");
        
        // 验证第二条
        TextSegment secondArticle = segments.get(1);
        assertTrue(secondArticle.text().contains("第二条"), "第二条应包含条文编号");
        assertTrue(secondArticle.text().contains("适用本法"), "第二条应包含完整内容");
        
        // 验证第三条
        TextSegment thirdArticle = segments.get(2);
        assertTrue(thirdArticle.text().contains("第三条"), "第三条应包含条文编号");
        assertTrue(thirdArticle.text().contains("职业技能培训"), "第三条应包含完整内容");
    }
    
    @Test
    @DisplayName("第一阶段: 测试中文数字编号识别")
    void testChineseNumberRecognition() {
        String content = """
            第十二条 劳动合同可以有固定期限、无固定期限或者以完成一定的工作为期限。
            第二十条 劳动合同期限届满，双方当事人可以续订劳动合同。
            第一百条 用人单位违反本法规定，侵害劳动者合法权益的，依法承担民事责任。
            """;
        
        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);
        
        assertEquals(3, segments.size(), "应该识别出3个条文");
        assertEquals("第十二条", segments.get(0).metadata().toMap().get("article_number"));
        assertEquals("第二十条", segments.get(1).metadata().toMap().get("article_number"));
        assertEquals("第一百条", segments.get(2).metadata().toMap().get("article_number"));
    }
    
    @Test
    @DisplayName("第二阶段: 超长条文分割测试")
    void testLongArticleSplitting() {
        // 创建一个超长的条文(超过512 tokens估算值,约1536字符)
        StringBuilder longArticle = new StringBuilder("第一条 ");
        for (int i = 0; i < 200; i++) {
            longArticle.append("劳动者享有平等就业的权利、选择职业的权利、取得劳动报酬的权利、");
        }
        
        Document document = Document.from(longArticle.toString());
        List<TextSegment> segments = splitter.split(document);
        
        // 验证结果
        assertTrue(segments.size() > 1, "超长条文应该被分割成多个片段");
        
        // 验证所有片段都有条文编号
        for (TextSegment segment : segments) {
            assertEquals("第一条", segment.metadata().toMap().get("article_number"), 
                    "所有片段都应该保留原始条文编号");
        }
        
        // 验证分片标记
        TextSegment firstPart = segments.get(0);
        assertEquals(1, firstPart.metadata().toMap().get("part"), "第一个分片的part应该为1");
        assertTrue(firstPart.metadata().toMap().containsKey("total_parts"), "应该包含total_parts元数据");
    }
    
    @Test
    @DisplayName("第三阶段: 层级结构识别 - 编章节条")
    void testHierarchicalStructure() {
        String content = """
            第一编 总则
            第一章 基本原则
            第一条 为了保护民事主体的合法权益，调整民事关系，维护社会和经济秩序，适应中国特色社会主义发展要求，弘扬社会主义核心价值观，根据宪法，制定本法。
            第二条 民法调整平等主体的自然人、法人和非法人组织之间的人身关系和财产关系。
            第二章 自然人
            第一节 民事权利能力和民事行为能力
            第十条 处理民事纠纷，应当依照法律。
            """;
        
        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);
        
        // 验证分割结果
        assertNotNull(segments, "分割结果不应为null");
        assertTrue(segments.size() >= 3, "应该至少分割出3个条文");
        
        // 验证第一条的层级信息
        TextSegment firstArticle = segments.get(0);
        assertEquals("第一条", firstArticle.metadata().toMap().get("article_number"));
        assertEquals("第一编 总则", firstArticle.metadata().toMap().get("book"));
        assertEquals("第一章 基本原则", firstArticle.metadata().toMap().get("chapter"));
        assertNull(firstArticle.metadata().toMap().get("section"), "第一条不属于任何节");
        
        // 验证第二条的层级信息
        TextSegment secondArticle = segments.get(1);
        assertEquals("第二条", secondArticle.metadata().toMap().get("article_number"));
        assertEquals("第一编 总则", secondArticle.metadata().toMap().get("book"));
        assertEquals("第一章 基本原则", secondArticle.metadata().toMap().get("chapter"));
        
        // 验证第十条的层级信息
        TextSegment thirdArticle = segments.get(2);
        assertEquals("第十条", thirdArticle.metadata().toMap().get("article_number"));
        assertEquals("第一编 总则", thirdArticle.metadata().toMap().get("book"));
        assertEquals("第二章 自然人", thirdArticle.metadata().toMap().get("chapter"));
        assertEquals("第一节 民事权利能力和民事行为能力", thirdArticle.metadata().toMap().get("section"));
    }
    
    @Test
    @DisplayName("第三阶段: 层级结构识别 - 层级变化测试")
    void testHierarchyTransition() {
        String content = """
            第一编 总则
            第一章 基本规定
            第一条 为了规范合同行为，保护合同当事人的合法权益，制定本法。
            第二编 典型合同
            第十章 买卖合同
            第一节 一般规定
            第五百九十五条 买卖合同是出卖人转移标的物的所有权于买受人。
            """;
        
        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);
        
        assertEquals(2, segments.size(), "应该分割出2个条文");
        
        // 验证第一条
        TextSegment firstArticle = segments.get(0);
        assertEquals("第一编 总则", firstArticle.metadata().toMap().get("book"));
        assertEquals("第一章 基本规定", firstArticle.metadata().toMap().get("chapter"));
        assertNull(firstArticle.metadata().toMap().get("section"));
        
        // 验证第五百九十五条(层级发生了变化)
        TextSegment secondArticle = segments.get(1);
        assertEquals("第二编 典型合同", secondArticle.metadata().toMap().get("book"));
        assertEquals("第十章 买卖合同", secondArticle.metadata().toMap().get("chapter"));
        assertEquals("第一节 一般规定", secondArticle.metadata().toMap().get("section"));
    }
    
    @Test
    @DisplayName("测试空文档处理")
    void testEmptyDocument() {
        // 测试没有条文标记的文档
        Document noArticleDocument = Document.from("这是一些没有条文标记的文本内容，没有任何第X条的标记。");
        List<TextSegment> segments = splitter.split(noArticleDocument);
        
        assertTrue(segments.isEmpty(), "没有条文标记的文档应该返回空列表");
        
        // 测试包含其他内容但无法律条文的文档
        Document nonLegalDocument = Document.from("本文档包含一般性文字描述\n\n但不包含任何法律条文");
        List<TextSegment> segments2 = splitter.split(nonLegalDocument);
        
        assertTrue(segments2.isEmpty(), "非法律条文文档应该返回空列表");
    }
    
    @Test
    @DisplayName("测试无条文标记的文档")
    void testDocumentWithoutArticles() {
        String content = "这是一段没有任何法律条文标记的普通文本内容。";
        
        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);
        
        assertTrue(segments.isEmpty(), "没有条文标记的文档应该返回空列表");
    }
    
    @Test
    @DisplayName("测试元数据传播")
    void testMetadataPropagation() {
        String content = """
            第一条 测试条文内容。
            """;
        
        Metadata originalMetadata = Metadata.from("source", "test.pdf");
        originalMetadata.put("test_key", "test_value");
        
        Document document = Document.from(content, originalMetadata);
        List<TextSegment> segments = splitter.split(document);
        
        assertEquals(1, segments.size());
        
        TextSegment segment = segments.get(0);
        assertEquals("test.pdf", segment.metadata().toMap().get("source"), "应该保留原始元数据");
        assertEquals("test_value", segment.metadata().toMap().get("test_key"), "应该保留自定义元数据");
        assertEquals("第一条", segment.metadata().toMap().get("article_number"), "应该添加条文编号元数据");
    }
    
    @Test
    @DisplayName("测试禁用层级解析模式")
    void testDisableHierarchicalParsing() {
        // 创建禁用层级解析的分割器
        LegalDocumentSplitter basicSplitter = new LegalDocumentSplitter(512, false, 50);
        
        // 测试数据中不包含"编"和"章",避免被识别为条文
        String content = """
            第一条 测试内容一。
            第二条 测试内容二。
            """;
        
        Document document = Document.from(content);
        List<TextSegment> segments = basicSplitter.split(document);
        
        assertEquals(2, segments.size(), "应该分割出2个条文");
        
        // 验证不包含层级信息
        TextSegment firstArticle = segments.get(0);
        assertNull(firstArticle.metadata().toMap().get("book"), "禁用层级解析时不应包含编信息");
        assertNull(firstArticle.metadata().toMap().get("chapter"), "禁用层级解析时不应包含章信息");
        assertEquals("article", firstArticle.metadata().toMap().get("split_type"));
    }
    
    @Test
    @DisplayName("综合测试: 实际法律文档片段")
    void testRealLegalDocumentFragment() {
        String content = """
            第一编 总则
            第一章 基本规定
            第一条 为了保护民事主体的合法权益，调整民事关系，维护社会和经济秩序，适应中国特色社会主义发展要求，弘扬社会主义核心价值观，根据宪法，制定本法。
            第二条 民法调整平等主体的自然人、法人和非法人组织之间的人身关系和财产关系。
            第三条 民事主体的人身权利、财产权利以及其他合法权益受法律保护，任何组织或者个人不得侵犯。
            第二章 自然人
            第一节 民事权利能力和民事行为能力
            第十三条 自然人从出生时起到死亡时止，具有民事权利能力，依法享有民事权利，承担民事义务。
            第十四条 自然人的民事权利能力一律平等。
            第二节 监护
            第二十六条 父母对未成年子女负有抚养、教育和保护的义务。
            """;
        
        Document document = Document.from(content, Metadata.from("source", "民法典.pdf"));
        List<TextSegment> segments = splitter.split(document);
        
        // 验证基本分割
        assertEquals(6, segments.size(), "应该分割出6个条文");
        
        // 验证条文编号正确性
        assertEquals("第一条", segments.get(0).metadata().toMap().get("article_number"));
        assertEquals("第二条", segments.get(1).metadata().toMap().get("article_number"));
        assertEquals("第三条", segments.get(2).metadata().toMap().get("article_number"));
        assertEquals("第十三条", segments.get(3).metadata().toMap().get("article_number"));
        assertEquals("第十四条", segments.get(4).metadata().toMap().get("article_number"));
        assertEquals("第二十六条", segments.get(5).metadata().toMap().get("article_number"));
        
        // 验证层级信息正确性
        assertEquals("第一编 总则", segments.get(0).metadata().toMap().get("book"));
        assertEquals("第一章 基本规定", segments.get(0).metadata().toMap().get("chapter"));
        
        assertEquals("第二章 自然人", segments.get(3).metadata().toMap().get("chapter"));
        assertEquals("第一节 民事权利能力和民事行为能力", segments.get(3).metadata().toMap().get("section"));
        
        assertEquals("第二节 监护", segments.get(5).metadata().toMap().get("section"));
        
        // 验证所有片段都包含源文件信息
        for (TextSegment segment : segments) {
            assertEquals("民法典.pdf", segment.metadata().toMap().get("source"));
            assertNotNull(segment.metadata().toMap().get("article_number"));
            assertTrue(segment.text().length() > 0);
        }
    }
    
    @Test
    @DisplayName("性能测试: 大文档分割")
    void testPerformanceWithLargeDocument() {
        // 创建一个包含30个条文的文档(足够测试性能)
        StringBuilder largeDocument = new StringBuilder();
        largeDocument.append("第一编 总则\n");
        largeDocument.append("第一章 基本规定\n");
        
        // 使用简单的中文数字
        String[] numbers = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
                           "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
                           "二十一", "二十二", "二十三", "二十四", "二十五", "二十六", "二十七", "二十八", "二十九", "三十"};
        
        for (int i = 0; i < 30; i++) {
            largeDocument.append("第").append(numbers[i]).append("条 ");
            largeDocument.append("这是第").append(i + 1).append("条的内容。");
            largeDocument.append("本条包含了重要的法律规定，涉及多个方面的内容。\n");
        }
        
        Document document = Document.from(largeDocument.toString());
        
        long startTime = System.currentTimeMillis();
        List<TextSegment> segments = splitter.split(document);
        long duration = System.currentTimeMillis() - startTime;
        
        assertEquals(30, segments.size(), "应该分割出30个条文");
        assertTrue(duration < 1000, "处理30个条文应该在1秒内完成，实际耗时: " + duration + "ms");
        
        System.out.println("大文档分割性能: 处理30个条文耗时 " + duration + "ms");
    }
}

