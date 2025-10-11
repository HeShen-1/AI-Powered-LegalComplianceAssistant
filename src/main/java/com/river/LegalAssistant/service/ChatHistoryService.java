package com.river.LegalAssistant.service;

import com.river.LegalAssistant.dto.ChatMessageDto;
import com.river.LegalAssistant.dto.ChatSessionDto;
import com.river.LegalAssistant.entity.ChatMessage;
import com.river.LegalAssistant.entity.ChatSession;
import com.river.LegalAssistant.entity.User;
import com.river.LegalAssistant.repository.ChatMessageRepository;
import com.river.LegalAssistant.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聊天历史服务类
 * 负责管理聊天会话和消息的持久化
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /**
     * 获取当前登录用户的所有会话
     * @return 会话列表
     */
    @Transactional(readOnly = true)
    public List<ChatSessionDto> getSessionsForCurrentUser() {
        Long userId = getCurrentUserId();
        log.info("获取用户 {} 的所有会话", userId);

        List<ChatSession> sessions = sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        
        return sessions.stream()
                .map(this::convertToSessionDto)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定会话的所有消息
     * @param sessionId 会话ID
     * @return 消息列表
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessagesForSession(String sessionId) {
        Long userId = getCurrentUserId();
        log.info("用户 {} 获取会话 {} 的消息", userId, sessionId);

        // 验证会话是否属于当前用户
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AccessDeniedException("会话不存在或您没有访问权限"));

        List<ChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        
        return messages.stream()
                .map(this::convertToMessageDto)
                .collect(Collectors.toList());
    }

    /**
     * 删除指定会话
     * @param sessionId 会话ID
     */
    @Transactional
    public void deleteSession(String sessionId) {
        Long userId = getCurrentUserId();
        log.info("用户 {} 删除会话 {}", userId, sessionId);

        // 验证会话是否属于当前用户
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AccessDeniedException("会话不存在或您没有访问权限"));

        // 由于设置了级联删除，删除会话会自动删除相关消息
        sessionRepository.delete(session);
        log.info("会话 {} 已删除", sessionId);
    }

    /**
     * 保存用户消息
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param content 消息内容
     * @return 消息ID
     */
    @Transactional
    public Long saveUserMessage(String sessionId, Long userId, String content) {
        log.debug("保存用户消息: sessionId={}, userId={}", sessionId, userId);

        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(ChatMessage.Role.USER.getValue())
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();

        message = messageRepository.save(message);
        
        // 更新会话的更新时间
        updateSessionTimestamp(sessionId);

        return message.getId();
    }

    /**
     * 保存AI助手消息
     * @param sessionId 会话ID
     * @param content 消息内容
     * @param metadata 元数据（如RAG来源等）
     * @return 消息ID
     */
    @Transactional
    public Long saveAssistantMessage(String sessionId, String content, Map<String, Object> metadata) {
        log.debug("保存助手消息: sessionId={}", sessionId);

        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(ChatMessage.Role.ASSISTANT.getValue())
                .content(content)
                .metadata(metadata)
                .createdAt(LocalDateTime.now())
                .build();

        message = messageRepository.save(message);
        
        // 更新会话的更新时间
        updateSessionTimestamp(sessionId);

        return message.getId();
    }

    /**
     * 确保会话存在，如果不存在则创建
     * @param sessionId 会话ID（如果为null则生成新的UUID）
     * @param userId 用户ID
     * @param firstMessage 第一条消息内容（用于生成标题）
     * @return 会话ID
     */
    @Transactional
    public String ensureSessionExists(String sessionId, Long userId, String firstMessage) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            // 生成新的会话ID
            sessionId = UUID.randomUUID().toString();
            log.info("生成新会话ID: {}", sessionId);
        }

        // 检查会话是否已存在
        if (!sessionRepository.existsById(sessionId)) {
            // 创建新会话
            String title = generateSessionTitle(firstMessage);
            
            ChatSession session = ChatSession.builder()
                    .id(sessionId)
                    .userId(userId)
                    .title(title)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            sessionRepository.save(session);
            log.info("创建新会话: sessionId={}, userId={}, title={}", sessionId, userId, title);
        }

        return sessionId;
    }

    /**
     * 根据会话ID和用户ID确保会话存在（带权限验证）
     * @param sessionId 会话ID
     * @param firstMessage 第一条消息内容
     * @return 会话ID
     */
    @Transactional
    public String ensureSessionExistsForCurrentUser(String sessionId, String firstMessage) {
        Long userId = getCurrentUserId();
        return ensureSessionExists(sessionId, userId, firstMessage);
    }

    /**
     * 更新会话标题
     * @param sessionId 会话ID
     * @param title 新标题
     */
    @Transactional
    public void updateSessionTitle(String sessionId, String title) {
        Long userId = getCurrentUserId();
        log.info("用户 {} 更新会话 {} 的标题: {}", userId, sessionId, title);

        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AccessDeniedException("会话不存在或您没有访问权限"));

        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    /**
     * 获取用户的会话总数
     * @return 会话数量
     */
    @Transactional(readOnly = true)
    public long getSessionCountForCurrentUser() {
        Long userId = getCurrentUserId();
        return sessionRepository.countByUserId(userId);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取当前登录用户的ID
     */
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            // 这里假设username可以转换为Long类型的ID
            // 如果您的系统使用不同的方式，请相应调整
            log.debug("从SecurityContext获取用户: {}", username);
            
            // 从UserDetails中获取用户ID
            if (principal instanceof User) {
                return ((User) principal).getId();
            }
            
            // 如果UserDetails不是User类型，可能需要额外的查询
            throw new IllegalStateException("无法从UserDetails获取用户ID");
        }
        
        throw new IllegalStateException("当前用户未认证");
    }

    /**
     * 生成会话标题（基于第一条消息）
     */
    private String generateSessionTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) {
            return "新对话";
        }
        
        // 限制标题长度
        int maxLength = 50;
        if (firstMessage.length() <= maxLength) {
            return firstMessage.trim();
        }
        
        return firstMessage.substring(0, maxLength).trim() + "...";
    }

    /**
     * 更新会话的时间戳
     */
    private void updateSessionTimestamp(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    /**
     * 转换会话实体为DTO
     */
    private ChatSessionDto convertToSessionDto(ChatSession session) {
        long messageCount = messageRepository.countBySessionId(session.getId());
        
        return ChatSessionDto.builder()
                .id(session.getId())
                .title(session.getTitle())
                .updatedAt(session.getUpdatedAt())
                .messageCount((int) messageCount)
                .build();
    }

    /**
     * 转换消息实体为DTO
     */
    private ChatMessageDto convertToMessageDto(ChatMessage message) {
        return ChatMessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .metadata(message.getMetadata())
                .createdAt(message.getCreatedAt())
                .build();
    }
}

