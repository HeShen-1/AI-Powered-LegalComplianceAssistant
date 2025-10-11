package com.river.LegalAssistant.repository;

import com.river.LegalAssistant.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 聊天消息Repository
 * 提供消息相关的数据访问方法
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 根据会话ID查询所有消息，按创建时间升序排列
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 统计会话的消息数量
     * @param sessionId 会话ID
     * @return 消息数量
     */
    long countBySessionId(String sessionId);

    /**
     * 删除会话的所有消息
     * @param sessionId 会话ID
     */
    void deleteBySessionId(String sessionId);

    /**
     * 查询会话中第一条用户消息（用于生成会话标题）
     * @param sessionId 会话ID
     * @param role 角色
     * @return 第一条消息
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId = :sessionId AND m.role = :role ORDER BY m.createdAt ASC LIMIT 1")
    ChatMessage findFirstBySessionIdAndRole(@Param("sessionId") String sessionId, @Param("role") String role);
}

