package com.river.LegalAssistant.repository;

import com.river.LegalAssistant.entity.KnowledgeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库文档数据访问层
 */
@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    /**
     * 根据文件哈希查找文档
     * @param fileHash 文件哈希值
     * @return 文档记录
     */
    Optional<KnowledgeDocument> findByFileHash(String fileHash);

    /**
     * 根据源文件路径查找文档
     * @param sourceFile 源文件路径
     * @return 文档记录
     */
    Optional<KnowledgeDocument> findBySourceFile(String sourceFile);

    /**
     * 根据文档类型查找文档
     * @param documentType 文档类型
     * @param pageable 分页参数
     * @return 分页文档列表
     */
    Page<KnowledgeDocument> findByDocumentType(String documentType, Pageable pageable);

    /**
     * 根据标题模糊查找文档
     * @param title 标题关键词
     * @param pageable 分页参数
     * @return 分页文档列表
     */
    Page<KnowledgeDocument> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    /**
     * 根据文档类型和标题模糊查找文档
     * @param documentType 文档类型
     * @param title 标题关键词
     * @param pageable 分页参数
     * @return 分页文档列表
     */
    Page<KnowledgeDocument> findByDocumentTypeAndTitleContainingIgnoreCase(
            String documentType, String title, Pageable pageable);

    /**
     * 统计各文档类型的数量
     * @return 文档类型统计
     */
    @Query("SELECT kd.documentType, COUNT(kd) FROM KnowledgeDocument kd GROUP BY kd.documentType")
    List<Object[]> countByDocumentType();

    /**
     * 获取总文档数量
     * @return 总数量
     */
    @Query("SELECT COUNT(kd) FROM KnowledgeDocument kd")
    Long getTotalDocumentCount();

    /**
     * 检查指定目录下是否已有文档记录
     * @param directoryPath 目录路径
     * @return 该目录下的文档数量
     */
    @Query("SELECT COUNT(kd) FROM KnowledgeDocument kd WHERE kd.sourceFile LIKE CONCAT(:directoryPath, '%')")
    Long countByDirectoryPath(@Param("directoryPath") String directoryPath);

    /**
     * 获取指定目录下的所有文档
     * @param directoryPath 目录路径
     * @return 文档列表
     */
    @Query("SELECT kd FROM KnowledgeDocument kd WHERE kd.sourceFile LIKE CONCAT(:directoryPath, '%')")
    List<KnowledgeDocument> findByDirectoryPath(@Param("directoryPath") String directoryPath);

    /**
     * 检查是否存在指定哈希值的文档
     * @param fileHash 文件哈希值
     * @return 是否存在
     */
    boolean existsByFileHash(String fileHash);

    /**
     * 检查是否存在指定源文件的文档
     * @param sourceFile 源文件路径
     * @return 是否存在
     */
    boolean existsBySourceFile(String sourceFile);

    /**
     * 根据更新时间倒序获取最新的文档
     * @return 最新更新的文档
     */
    Optional<KnowledgeDocument> findTopByOrderByUpdatedAtDesc();
}
