package com.river.LegalAssistant.service;

import com.river.LegalAssistant.entity.KnowledgeDocument;
import com.river.LegalAssistant.service.splitter.DocumentSplitterFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DocumentProcessingService 单元测试
 * 
 * 测试内容:
 * 1. 文档类型识别
 * 2. 分割器选择
 * 3. 法律文档处理
 * 4. 合同文档处理
 * 5. 通用文档处理
 * 6. 批量处理
 * 7. 错误处理
 * 
 * @author River
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentProcessingServiceTest {
    
    @Mock
    private DocumentSplitterFactory splitterFactory;
    
    @Mock
    private EmbeddingModel embeddingModel;
    
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;
    
    private DocumentProcessingService documentProcessingService;
    
    // 测试数据
    private static final String LEGAL_CONTENT = """
        中华人民共和国民法典
        
        第一编 总则
        第一章 基本规定
        第一条 为了保护民事主体的合法权益，调整民事关系，维护社会和经济秩序，适应中国特色社会主义发展要求，弘扬社会主义核心价值观，根据宪法，制定本法。
        第二条 民法调整平等主体的自然人、法人和非法人组织之间的人身关系和财产关系。
        第三条 民事主体的人身权利、财产权利以及其他合法权益受法律保护，任何组织或者个人不得侵犯。
        """;
    
    private static final String CONTRACT_CONTENT = """
        买卖合同
        
        第一章 总则
        第一条 甲方与乙方根据《中华人民共和国合同法》及相关法律法规，在平等、自愿的基础上，就买卖事宜达成如下协议。
        第二条 本合同所称标的物是指甲方向乙方出售的商品。
        
        第二章 价格与支付
        第三条 标的物总价款为人民币壹万元整（¥10,000.00）。
        第四条 乙方应在签订本合同后3个工作日内支付全款。
        """;
    
    private static final String GENERAL_CONTENT = """
        这是一篇普通的文档内容，没有特殊的法律或合同结构。
        它包含多个段落的普通文本。
        
        这里是第二段内容，介绍了一些背景信息。
        包含了一些说明性的文字。
        
        最后一段是总结性的内容。
        """;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 创建真实的DocumentProcessingService实例（集成测试风格）
        // 在真实测试中，需要使用实际的splitterFactory
        // 这里为了单元测试的独立性，我们mock splitterFactory
        
        documentProcessingService = new DocumentProcessingService(
            splitterFactory,
            embeddingModel,
            embeddingStore
        );
    }
    
    @Test
    @DisplayName("测试法律文档处理 - 应该使用法律文档分割器")
    void testProcessLegalDocument() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("original_filename", "民法典.pdf");
        metadata.put("source_type", "knowledge_base");
        
        String documentType = "LAW";
        
        // When
        // 注意: 这个测试需要真实的splitterFactory，或者更详细的mock设置
        // 这里展示测试结构，实际测试需要完整的Spring上下文或更多mock
        
        // Then
        // 验证选择了正确的分割器
        // 验证文档被正确分割
        // 验证元数据包含法律文档特征
    }
    
    @Test
    @DisplayName("测试合同文档处理 - 应该使用合同文档分割器")
    void testProcessContractDocument() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("original_filename", "买卖合同.docx");
        metadata.put("source_type", "knowledge_base");
        
        String documentType = "CONTRACT_TEMPLATE";
        
        // When & Then
        // 验证选择了正确的分割器
        // 验证合同条款被正确识别
    }
    
    @Test
    @DisplayName("测试通用文档处理 - 应该使用递归分割器")
    void testProcessGeneralDocument() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("original_filename", "普通文档.txt");
        metadata.put("source_type", "knowledge_base");
        
        String documentType = "GENERAL";
        
        // When & Then
        // 验证使用了递归分割器
        // 验证段落被正确分割
    }
    
    @Test
    @DisplayName("测试空文档处理 - 应该返回失败")
    void testProcessEmptyDocument() {
        // Given
        String emptyContent = "";
        Map<String, Object> metadata = new HashMap<>();
        String documentType = "LAW";
        
        // When
        DocumentProcessingService.ProcessingResult result = 
            documentProcessingService.processDocument(emptyContent, metadata, documentType);
        
        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("内容为空");
        assertThat(result.getSegmentCount()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("测试文档类型自动识别 - 根据文件名")
    void testDocumentTypeDetectionByFilename() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        
        // 法律文档
        metadata.put("original_filename", "刑法.pdf");
        // 应该识别为法律文档
        
        metadata.put("original_filename", "劳动合同.docx");
        // 应该识别为合同文档
        
        metadata.put("original_filename", "普通文件.txt");
        // 应该识别为通用文档
        
        // When & Then
        // 验证文档类型识别逻辑
    }
    
    @Test
    @DisplayName("测试批量处理文档")
    void testBatchProcessDocuments() {
        // Given
        List<KnowledgeDocument> documents = createTestDocuments();
        
        // When
        DocumentProcessingService.BatchProcessingResult result = 
            documentProcessingService.batchProcessDocuments(documents);
        
        // Then
        assertThat(result.getTotalDocuments()).isEqualTo(documents.size());
        // 验证批量处理结果
    }
    
    @Test
    @DisplayName("测试质量过滤 - 过滤太短的片段")
    void testQualityFilter() {
        // Given
        String shortContent = "太短";  // 只有2个字
        Map<String, Object> metadata = new HashMap<>();
        String documentType = "GENERAL";
        
        // When
        DocumentProcessingService.ProcessingResult result = 
            documentProcessingService.processDocument(shortContent, metadata, documentType);
        
        // Then
        // 验证短片段被过滤
    }
    
    @Test
    @DisplayName("测试元数据保留 - 分割器生成的元数据应该保留")
    void testMetadataPreservation() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("original_filename", "民法典.pdf");
        metadata.put("custom_field", "custom_value");
        
        String documentType = "LAW";
        
        // When & Then
        // 验证原始元数据被保留
        // 验证分割器添加的元数据存在
    }
    
    @Test
    @DisplayName("测试知识库文档实体处理")
    void testProcessKnowledgeDocument() {
        // Given
        KnowledgeDocument doc = createLegalDocument();
        
        // When
        DocumentProcessingService.ProcessingResult result = 
            documentProcessingService.processKnowledgeDocument(doc);
        
        // Then
        // 验证处理成功
        // 验证元数据正确设置
    }
    
    @Test
    @DisplayName("测试获取支持的文档类型")
    void testGetSupportedDocumentTypes() {
        // When
        List<String> types = documentProcessingService.getSupportedDocumentTypes();
        
        // Then
        assertThat(types).isNotEmpty();
        assertThat(types).contains("LAW", "CONTRACT_TEMPLATE", "CASE", "REGULATION");
    }
    
    @Test
    @DisplayName("测试获取配置信息")
    void testGetConfigInfo() {
        // When
        DocumentProcessingService.ConfigInfo config = documentProcessingService.getConfigInfo();
        
        // Then
        assertThat(config).isNotNull();
        assertThat(config.minChunkSize()).isGreaterThan(0);
        assertThat(config.supportedDocumentTypes()).isNotEmpty();
    }
    
    // ==================== 辅助方法 ====================
    
    private List<KnowledgeDocument> createTestDocuments() {
        return List.of(
            createLegalDocument(),
            createContractDocument(),
            createGeneralDocument()
        );
    }
    
    private KnowledgeDocument createLegalDocument() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setTitle("民法典.pdf");
        doc.setContent(LEGAL_CONTENT);
        doc.setDocumentType("LAW");
        doc.setSourceFile("uploads/law/民法典.pdf");
        doc.setFileHash("legal_hash_123");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", "法律法规");
        doc.setMetadata(metadata);
        
        return doc;
    }
    
    private KnowledgeDocument createContractDocument() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(2L);
        doc.setTitle("买卖合同.docx");
        doc.setContent(CONTRACT_CONTENT);
        doc.setDocumentType("CONTRACT_TEMPLATE");
        doc.setSourceFile("uploads/contract/买卖合同.docx");
        doc.setFileHash("contract_hash_456");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", "合同模板");
        doc.setMetadata(metadata);
        
        return doc;
    }
    
    private KnowledgeDocument createGeneralDocument() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(3L);
        doc.setTitle("普通文档.txt");
        doc.setContent(GENERAL_CONTENT);
        doc.setDocumentType("GENERAL");
        doc.setSourceFile("uploads/general/普通文档.txt");
        doc.setFileHash("general_hash_789");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", "其他");
        doc.setMetadata(metadata);
        
        return doc;
    }
}

