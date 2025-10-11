package com.river.LegalAssistant.repository;

import com.river.LegalAssistant.entity.ContractReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 合同审查记录数据访问层
 */
@Repository
public interface ContractReviewRepository extends JpaRepository<ContractReview, Long> {

    /**
     * 根据用户ID查询合同审查记录
     */
    Page<ContractReview> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 根据审查状态查询记录
     */
    List<ContractReview> findByReviewStatus(ContractReview.ReviewStatus status);

    /**
     * 根据文件哈希查找记录（用于去重）
     */
    Optional<ContractReview> findByFileHash(String fileHash);

    /**
     * 查询指定时间范围内的审查记录
     */
    @Query("SELECT cr FROM ContractReview cr WHERE cr.createdAt BETWEEN :startDate AND :endDate")
    List<ContractReview> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate);

    /**
     * 统计用户的审查记录数量
     */
    long countByUserId(Long userId);

    /**
     * 统计各风险等级的记录数量
     */
    @Query("SELECT cr.riskLevel, COUNT(cr) FROM ContractReview cr WHERE cr.riskLevel IS NOT NULL GROUP BY cr.riskLevel")
    List<Object[]> countByRiskLevel();

    /**
     * 根据ID查找审查记录，同时加载关联的风险条款和用户信息
     * 用于避免懒加载异常
     */
    @Query("SELECT cr FROM ContractReview cr " +
           "LEFT JOIN FETCH cr.riskClauses " +
           "LEFT JOIN FETCH cr.user " +
           "WHERE cr.id = :id")
    Optional<ContractReview> findByIdWithRiskClauses(@Param("id") Long id);
}
