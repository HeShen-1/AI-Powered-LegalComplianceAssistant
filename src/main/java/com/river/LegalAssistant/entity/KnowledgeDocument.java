package com.river.LegalAssistant.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识库文档实体类
 * 对应数据库中的knowledge_documents表
 */
@Entity
@Table(name = "knowledge_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 文档标题
     */
    @Column(nullable = false)
    private String title;

    /**
     * 文档内容
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 文档类型
     * LAW - 法律
     * REGULATION - 法规
     * CASE - 案例
     * CONTRACT_TEMPLATE - 合同模板
     */
    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    /**
     * 源文件路径
     */
    @Column(name = "source_file")
    private String sourceFile;

    /**
     * 文件哈希值，用于去重
     */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    /**
     * 文档元数据，存储额外信息
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 自动设置创建时间
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * 自动设置更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 文档类型枚举
     */
    public static class DocumentType {
        public static final String LAW = "LAW";
        public static final String REGULATION = "REGULATION"; 
        public static final String CASE = "CASE";
        public static final String CONTRACT_TEMPLATE = "CONTRACT_TEMPLATE";
    }
}
