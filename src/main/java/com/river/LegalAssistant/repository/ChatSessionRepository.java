package com.river.LegalAssistant.repository;

import com.river.LegalAssistant.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 聊天会话Repository
 * 提供会话相关的数据访问方法
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    /**
     * 根据用户ID查询所有会话，按更新时间倒序排列
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * 根据会话ID和用户ID查询会话（用于权限验证）
     * @param id 会话ID
     * @param userId 用户ID
     * @return 会话（如果存在且属于该用户）
     */
    Optional<ChatSession> findByIdAndUserId(String id, Long userId);

    /**
     * 统计用户的会话数量
     * @param userId 用户ID
     * @return 会话数量
     */
    long countByUserId(Long userId);

    /**
     * 删除用户的所有会话
     * @param userId 用户ID
     */
    void deleteByUserId(Long userId);
}

