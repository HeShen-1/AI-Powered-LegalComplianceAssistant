package com.river.LegalAssistant.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪过滤器
 * 为每个请求生成唯一的追踪ID,便于日志追踪和问题排查
 */
@Component
@Order(1)
@Slf4j
public class RequestTrackingFilter implements Filter {
    
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_KEY = "traceId";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            // 生成或获取追踪ID
            String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isEmpty()) {
                traceId = generateTraceId();
            }
            
            // 设置到MDC和响应头
            MDC.put(TRACE_ID_KEY, traceId);
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            
            long startTime = System.currentTimeMillis();
            String uri = httpRequest.getRequestURI();
            String method = httpRequest.getMethod();
            
            log.debug("[REQUEST_START] traceId={}, method={}, uri={}", traceId, method, uri);
            
            try {
                chain.doFilter(request, response);
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                int status = httpResponse.getStatus();
                
                if (status >= 500) {
                    log.error("[REQUEST_END] traceId={}, method={}, uri={}, status={}, duration={}ms", 
                            traceId, method, uri, status, duration);
                } else if (status >= 400) {
                    log.warn("[REQUEST_END] traceId={}, method={}, uri={}, status={}, duration={}ms", 
                            traceId, method, uri, status, duration);
                } else {
                    log.info("[REQUEST_END] traceId={}, method={}, uri={}, status={}, duration={}ms", 
                            traceId, method, uri, status, duration);
                }
            }
        } finally {
            // 清理MDC,防止内存泄漏
            MDC.clear();
        }
    }
    
    /**
     * 生成追踪ID
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

