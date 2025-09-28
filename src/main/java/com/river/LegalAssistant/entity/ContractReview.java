package com.river.LegalAssistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 合同审查记录实体类
 */
@Entity
@Table(name = "contract_reviews")
@Data
@EqualsAndHashCode(callSuper = false)
public class ContractReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Column(name = "total_risks")
    private Integer totalRisks = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_result", columnDefinition = "jsonb")
    private Map<String, Object> reviewResult;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "contractReview", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RiskClause> riskClauses = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * 审查状态枚举
     */
    public enum ReviewStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        HIGH, MEDIUM, LOW
    }
}
