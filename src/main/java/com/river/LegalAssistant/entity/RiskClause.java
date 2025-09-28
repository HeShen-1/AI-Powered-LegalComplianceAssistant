package com.river.LegalAssistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 风险条款实体类
 */
@Entity
@Table(name = "risk_clauses")
@Data
@EqualsAndHashCode(callSuper = false)
public class RiskClause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_review_id", nullable = false)
    private ContractReview contractReview;

    @Column(name = "clause_text", nullable = false, columnDefinition = "TEXT")
    private String clauseText;

    @Column(name = "risk_type", nullable = false, length = 50)
    private String riskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private ContractReview.RiskLevel riskLevel;

    @Column(name = "risk_description", nullable = false, columnDefinition = "TEXT")
    private String riskDescription;

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "legal_basis", columnDefinition = "TEXT")
    private String legalBasis;

    @Column(name = "position_start")
    private Integer positionStart;

    @Column(name = "position_end")
    private Integer positionEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
