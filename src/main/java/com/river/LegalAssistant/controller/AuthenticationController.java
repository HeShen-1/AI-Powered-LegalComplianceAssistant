package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.dto.ApiResponse;
import com.river.LegalAssistant.entity.User;
import com.river.LegalAssistant.service.UserService;
import com.river.LegalAssistant.util.JwtTokenUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 提供登录、注册、Token刷新等API
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "认证管理", description = "用户认证相关接口")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserService userService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回JWT Token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginRequest request) {
        try {
            log.info("用户登录请求: {}", request.getUsername());
            
            // 认证用户
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // 生成Token
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtTokenUtil.generateToken(userDetails);

            // 获取用户信息（UserDetails实际上是User对象）
            User user = (User) userDetails;

            // 构建响应
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("type", "Bearer");
            data.put("user", convertToUserInfo(user));

            log.info("用户 {} 登录成功", request.getUsername());
            return ResponseEntity.ok(ApiResponse.success(data, "登录成功"));

        } catch (BadCredentialsException e) {
            log.warn("用户 {} 登录失败: 用户名或密码错误", request.getUsername());
            return ResponseEntity.status(401)
                    .body(ApiResponse.errorWithCode("用户名或密码错误", "AUTH_FAILED"));
        } catch (Exception e) {
            log.error("登录异常: ", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.errorWithCode("登录失败: " + e.getMessage(), "SERVER_ERROR"));
        }
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "使用旧Token刷新获取新Token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshToken(
            @RequestHeader("Authorization") String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.errorGeneric("Token格式不正确"));
            }

            String oldToken = authorization.substring(7);
            
            if (!jwtTokenUtil.canTokenBeRefreshed(oldToken)) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.errorGeneric("Token无法刷新"));
            }

            String newToken = jwtTokenUtil.refreshToken(oldToken);
            String username = jwtTokenUtil.getUsernameFromToken(newToken);
            User user = (User) userService.loadUserByUsername(username);

            Map<String, Object> data = new HashMap<>();
            data.put("token", newToken);
            data.put("type", "Bearer");
            data.put("user", convertToUserInfo(user));

            log.info("用户 {} Token刷新成功", username);
            return ResponseEntity.ok(ApiResponse.success(data, "Token刷新成功"));

        } catch (Exception e) {
            log.error("Token刷新失败: ", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.errorGeneric("Token刷新失败: " + e.getMessage()));
        }
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出（客户端需清除Token）")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // JWT是无状态的，登出由客户端删除Token即可
        // 这里只是提供一个标准的登出端点
        log.info("用户登出");
        return ResponseEntity.ok(ApiResponse.success(null, "登出成功"));
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "根据Token获取当前登录用户的信息")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(
            @RequestHeader("Authorization") String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.errorGeneric("Token格式不正确"));
            }

            String token = authorization.substring(7);
            String username = jwtTokenUtil.getUsernameFromToken(token);
            User user = (User) userService.loadUserByUsername(username);

            return ResponseEntity.ok(
                    ApiResponse.success(convertToUserInfo(user), "获取用户信息成功")
            );

        } catch (Exception e) {
            log.error("获取用户信息失败: ", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.errorGeneric("获取用户信息失败: " + e.getMessage()));
        }
    }

    /**
     * 转换用户信息（过滤敏感字段）
     */
    private Map<String, Object> convertToUserInfo(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("fullName", user.getFullName());
        userInfo.put("role", user.getRole());
        userInfo.put("enabled", user.getEnabled());
        userInfo.put("createdAt", user.getCreatedAt());
        return userInfo;
    }

    /**
     * 登录请求DTO
     */
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}

