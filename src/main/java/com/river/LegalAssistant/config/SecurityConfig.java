package com.river.LegalAssistant.config;

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
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;

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
                // 禁用 CSRF（开发阶段，生产环境需要启用）
                .csrf(AbstractHttpConfigurer::disable)
                
                // 配置用户详情服务 - 直接使用 UserService Bean
                .userDetailsService(userService)
                
                // 配置授权规则
                .authorizeHttpRequests(authz -> authz
                        // 公开访问的端点
                        .requestMatchers("/health/**", "/actuator/**").permitAll()
                        .requestMatchers("/doc.html", "/webjars/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/favicon.ico").permitAll()
                        .requestMatchers("/login", "/css/**", "/js/**", "/images/**").permitAll()
                        
                        // API 端点 - 注意顺序很重要，更具体的规则要放在前面
                        .requestMatchers("/api/health/**").permitAll()  // 健康检查无需认证
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        
                        // 其他所有请求需要认证
                        .anyRequest().authenticated()
                )
                
                // 配置表单登录
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .defaultSuccessUrl("/health", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                
                // 配置登出
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                )
                
                // 保留 HTTP Basic 认证作为 API 访问的备用方案
                .httpBasic(httpBasic -> httpBasic.realmName("Legal Assistant"));

        return http.build();
    }
}
