package com.river.LegalAssistant.config;

import com.river.LegalAssistant.filter.JwtAuthenticationFilter;
import com.river.LegalAssistant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import jakarta.annotation.PostConstruct;

/**
 * Spring Security 配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;
    private final CorsConfigurationSource corsConfigurationSource;
    private final AuthenticationEntryPoint unauthorizedEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 初始化SecurityContext策略，支持异步请求（如SSE）
     * 设置为MODE_INHERITABLETHREADLOCAL，使子线程可以继承父线程的SecurityContext
     */
    @PostConstruct
    public void init() {
        // 允许异步线程继承SecurityContext，解决SSE等异步请求的认证问题
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    /**
     * 认证管理器 - 使用Spring Security 6.5.5的AuthenticationConfiguration
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 安全过滤器链配置 - Spring Security 6.5.5方式
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF（对于无状态API是安全的）
                .csrf(AbstractHttpConfigurer::disable)
                
                // 启用 CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // 配置异常处理，特别是对于未授权的请求
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorizedEntryPoint)
                )
                
                // 配置会话管理 - 使用无状态会话（JWT认证）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                
                // 配置用户详情服务
                .userDetailsService(userService)
                
                // 配置授权规则
                .authorizeHttpRequests(authz -> authz
                        // 公开访问的端点
                        .requestMatchers("/health/**", "/actuator/**").permitAll()
                        
                        // Knife4j和OpenAPI文档端点 - 完整配置
                        .requestMatchers("/doc.html", "/doc.html/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers("/swagger-resources/**").permitAll()
                        .requestMatchers("/swagger-config/**").permitAll()
                        .requestMatchers("/favicon.ico").permitAll()
                        
                        // 认证相关端点公开（登录、注册不需要认证）
                        .requestMatchers("/auth/login", "/auth/register").permitAll()

                        // SSE流式端点 - 允许访问但在Controller中手动验证JWT
                        .requestMatchers("/chat/stream").permitAll()
                        .requestMatchers("/contracts/*/analyze-async").permitAll()
                        
                        // API 端点 - 注意顺序很重要，更具体的规则要放在前面
                        .requestMatchers("/admin/**").hasRole("ADMIN")  // 管理员接口
                        
                        // 其他所有请求需要认证
                        .anyRequest().authenticated()
                )
                
                // 添加JWT认证过滤器（在UsernamePasswordAuthenticationFilter之前）
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
