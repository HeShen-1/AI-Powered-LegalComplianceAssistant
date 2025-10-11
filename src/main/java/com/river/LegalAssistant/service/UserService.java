package com.river.LegalAssistant.service;

import com.river.LegalAssistant.dto.UserStatsDto;
import com.river.LegalAssistant.entity.ContractReview;
import com.river.LegalAssistant.entity.User;
import com.river.LegalAssistant.repository.ChatMessageRepository;
import com.river.LegalAssistant.repository.ChatSessionRepository;
import com.river.LegalAssistant.repository.ContractReviewRepository;
import com.river.LegalAssistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 用户服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContractReviewRepository contractReviewRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 根据用户名加载用户详情（Spring Security 需要）
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
    }

    /**
     * 根据用户名查找用户
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 根据邮箱查找用户
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * 根据ID查找用户
     */
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * 获取所有用户
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * 创建新用户
     */
    @Transactional
    public User createUser(String username, String email, String password, String fullName) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已存在: " + email);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setRole(User.UserRole.USER);
        user.setEnabled(true);

        User savedUser = userRepository.save(user);
        log.info("创建新用户: {}", username);
        return savedUser;
    }

    /**
     * 更新用户信息
     */
    @Transactional
    public User updateUser(Long id, String fullName, String email) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));

        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("邮箱已存在: " + email);
            }
            user.setEmail(email);
        }

        if (fullName != null) {
            user.setFullName(fullName);
        }

        return userRepository.save(user);
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("原密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("用户 {} 修改密码成功", user.getUsername());
    }

    /**
     * 启用/禁用用户
     */
    @Transactional
    public void setUserEnabled(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        user.setEnabled(enabled);
        userRepository.save(user);
        log.info("用户 {} 状态更新为: {}", user.getUsername(), enabled ? "启用" : "禁用");
    }

    /**
     * 删除用户
     * 注意：删除用户时会同时删除其聊天会话和消息，但保留合同审查记录
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        // 检查是否是最后一个管理员
        if (user.getRole() == User.UserRole.ADMIN) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == User.UserRole.ADMIN)
                    .count();
            if (adminCount <= 1) {
                throw new IllegalArgumentException("不能删除最后一个管理员账户");
            }
        }

        String username = user.getUsername();

        // 删除用户的聊天会话和消息
        List<String> userSessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(session -> session.getId())
                .toList();
        
        for (String sessionId : userSessions) {
            chatMessageRepository.deleteBySessionId(sessionId);
            chatSessionRepository.deleteById(sessionId);
        }
        log.info("删除用户 {} 的 {} 个聊天会话", username, userSessions.size());

        // 将用户的合同审查记录的用户引用设置为null（保留审查数据）
        List<ContractReview> userReviews = contractReviewRepository.findAll().stream()
                .filter(review -> review.getUser() != null && review.getUser().getId().equals(userId))
                .toList();
        
        for (ContractReview review : userReviews) {
            review.setUser(null);
            contractReviewRepository.save(review);
        }
        log.info("保留用户 {} 的 {} 个合同审查记录（用户引用已移除）", username, userReviews.size());

        // 最后删除用户
        userRepository.deleteById(userId);
        log.info("已删除用户: {}", username);
    }

    /**
     * 获取用户统计数据
     */
    public UserStatsDto getUserStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        
        // 获取合同审查统计
        List<ContractReview> allReviews = contractReviewRepository.findAll().stream()
                .filter(review -> review.getUser() != null && review.getUser().getId().equals(userId))
                .toList();
        
        int totalReviews = allReviews.size();
        int completedReviews = (int) allReviews.stream()
                .filter(review -> review.getReviewStatus() == ContractReview.ReviewStatus.COMPLETED)
                .count();
        int processingReviews = (int) allReviews.stream()
                .filter(review -> review.getReviewStatus() == ContractReview.ReviewStatus.PROCESSING)
                .count();
        int highRiskCount = (int) allReviews.stream()
                .filter(review -> "HIGH".equals(review.getRiskLevel()))
                .count();
        
        // 获取AI问答统计
        long totalSessions = chatSessionRepository.countByUserId(userId);
        
        // 计算本月会话数
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        long monthlySessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(session -> session.getCreatedAt() != null && 
                        session.getCreatedAt().isAfter(monthStart))
                .count();
        
        // 统计总消息数（用户消息数量代表提问数）
        long totalQuestions = chatMessageRepository.findAll().stream()
                .filter(msg -> {
                    var session = chatSessionRepository.findById(msg.getSessionId());
                    return session.isPresent() && 
                           session.get().getUserId() != null && 
                           session.get().getUserId().equals(userId) &&
                           "user".equals(msg.getRole());
                })
                .count();
        
        long monthlyQuestions = chatMessageRepository.findAll().stream()
                .filter(msg -> {
                    var session = chatSessionRepository.findById(msg.getSessionId());
                    return session.isPresent() && 
                           session.get().getUserId() != null && 
                           session.get().getUserId().equals(userId) &&
                           "user".equals(msg.getRole()) &&
                           msg.getCreatedAt() != null &&
                           msg.getCreatedAt().isAfter(monthStart);
                })
                .count();
        
        // 计算平均响应时间（模拟值，实际应从日志或监控系统获取）
        double avgResponseTime = 2.3;
        
        // 满意度（模拟值）
        int satisfaction = 95;
        
        // 计算加入天数
        int joinDays = 0;
        if (user.getCreatedAt() != null) {
            joinDays = (int) ChronoUnit.DAYS.between(
                user.getCreatedAt().toLocalDate(), 
                LocalDateTime.now().toLocalDate()
            );
        }
        
        return UserStatsDto.builder()
                .totalReviews(totalReviews)
                .completedReviews(completedReviews)
                .processingReviews(processingReviews)
                .highRiskCount(highRiskCount)
                .totalQuestions((int) totalQuestions)
                .monthlyQuestions((int) monthlyQuestions)
                .avgResponseTime(avgResponseTime)
                .satisfaction(satisfaction)
                .joinDays(joinDays)
                .build();
    }
}
