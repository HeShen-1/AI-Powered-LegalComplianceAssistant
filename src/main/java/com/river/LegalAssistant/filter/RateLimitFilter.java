package com.river.LegalAssistant.filter;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API速率限制过滤器
 * 使用Guava RateLimiter实现基于IP的速率限制
 */
@Component
@Order(2)
@Slf4j
public class RateLimitFilter implements Filter {
    
    /**
     * 每个IP的速率限制器缓存
     */
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    
    /**
     * 默认速率：每秒10个请求
     */
    private static final double DEFAULT_RATE = 10.0;
    
    /**
     * API接口的速率：每秒50个请求（开发环境放宽限制）
     */
    private static final double API_RATE = 50.0;
    
    /**
     * 聊天接口的速率：每秒100个请求（支持流式传输和频繁的历史记录查询）
     */
    private static final double CHAT_RATE = 100.0;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String uri = httpRequest.getRequestURI();
        
        // 跳过健康检查、静态资源和认证端点
        if (shouldSkipRateLimit(uri)) {
            chain.doFilter(request, response);
            return;
        }
        
        String clientIp = getClientIp(httpRequest);
        // 根据端点类型选择不同的速率限制（使用final变量）
        final double rate = getRateLimit(uri);
        
        // 获取或创建速率限制器
        RateLimiter limiter = limiters.computeIfAbsent(
                clientIp + "_" + rate, 
                k -> RateLimiter.create(rate)
        );
        
        // 尝试获取令牌
        if (!limiter.tryAcquire()) {
            log.warn("[RATE_LIMIT] 请求速率超限: ip={}, uri={}", clientIp, uri);
            
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write(
                    "{\"errorCode\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"请求过于频繁，请稍后重试\"}"
            );
            return;
        }
        
        chain.doFilter(request, response);
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        // 优先从X-Forwarded-For获取（经过代理的情况）
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 如果是多级代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
    
    /**
     * 获取指定URI的速率限制
     */
    private double getRateLimit(String uri) {
        if (isChatEndpoint(uri)) {
            return CHAT_RATE;
        } else if (isApiEndpoint(uri)) {
            return API_RATE;
        }
        return DEFAULT_RATE;
    }
    
    /**
     * 判断是否应该跳过速率限制
     */
    private boolean shouldSkipRateLimit(String uri) {
        return uri.startsWith("/actuator/health") ||
               uri.startsWith("/actuator/") ||
               uri.startsWith("/static/") ||
               uri.startsWith("/css/") ||
               uri.startsWith("/js/") ||
               uri.startsWith("/images/") ||
               uri.startsWith("/webjars/") ||        // Knife4j 静态资源
               uri.startsWith("/v3/api-docs") ||     // OpenAPI 文档
               uri.startsWith("/doc.html") ||        // Knife4j 文档页面
               uri.startsWith("/swagger-resources") || // Swagger 资源
               uri.contains("/webjars/") ||          // Knife4j 静态资源 (含/api前缀)
               uri.contains("/doc.html") ||          // Knife4j 文档页面 (含/api前缀)
               uri.contains("/v3/api-docs") ||       // OpenAPI 文档 (含/api前缀)
               uri.contains("/swagger-resources") || // Swagger 资源 (含/api前缀)
               uri.equals("/login") ||
               uri.equals("/api/v1/login") ||
               uri.startsWith("/api/v1/auth") ||     // 认证相关端点
               uri.startsWith("/api/auth") ||        // 认证相关端点（不含版本前缀）
               uri.equals("/");
    }
    
    /**
     * 判断是否为聊天端点
     */
    private boolean isChatEndpoint(String uri) {
        return uri.contains("/chat");
    }
    
    /**
     * 判断是否为API端点
     */
    private boolean isApiEndpoint(String uri) {
        return uri.startsWith("/api/");
    }
}

