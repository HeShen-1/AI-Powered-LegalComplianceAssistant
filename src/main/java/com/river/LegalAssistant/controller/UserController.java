package com.river.LegalAssistant.controller;

import com.river.LegalAssistant.entity.User;
import com.river.LegalAssistant.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "用户管理", description = "用户注册、信息管理等接口")
public class UserController {

    private final UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建一个新的用户账户。用户名和邮箱地址必须是唯一的。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "用户注册成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"用户注册成功\",\"userId\":1,\"username\":\"newuser\"}"))),
        @ApiResponse(responseCode = "400", description = "注册失败，例如用户名或邮箱已存在", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":false,\"message\":\"用户名已存在\"}")))
    })
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        try {
            User user = userService.createUser(
                request.getUsername(),
                request.getEmail(), 
                request.getPassword(),
                request.getFullName()
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "用户注册成功",
                "userId", user.getId(),
                "username", user.getUsername()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取用户信息", description = "根据用户ID获取用户的详细信息。管理员可以获取任何用户的信息，普通用户只能获取自己的信息。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "无权访问该用户信息"),
        @ApiResponse(responseCode = "404", description = "指定ID的用户不存在")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or authentication.principal.id == #id")
    public ResponseEntity<Map<String, Object>> getUserInfo(@Parameter(description = "要查询的用户的唯一ID", required = true, example = "1") @PathVariable Long id) {
        Optional<User> userOpt = userService.findById(id);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = userOpt.get();
        return ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "fullName", user.getFullName(),
            "role", user.getRole(),
            "enabled", user.isEnabled(),
            "createdAt", user.getCreatedAt()
        ));
    }

    /**
     * 根据用户名查找用户
     */
    @GetMapping("/username/{username}")
    @Operation(summary = "根据用户名查找用户（管理员）", description = "通过用户名精确查找用户信息。仅限管理员访问。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查找成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "指定用户名的用户不存在")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserByUsername(@Parameter(description = "要查询的用户名", required = true, example = "admin") @PathVariable String username) {
        Optional<User> userOpt = userService.findByUsername(username);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = userOpt.get();
        return ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "fullName", user.getFullName(),
            "role", user.getRole(),
            "enabled", user.isEnabled()
        ));
    }

    /**
     * 根据邮箱查找用户
     */
    @GetMapping("/email/{email}")
    @Operation(summary = "根据邮箱查找用户（管理员）", description = "通过邮箱地址精确查找用户信息。仅限管理员访问。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查找成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "指定邮箱的用户不存在")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserByEmail(@Parameter(description = "要查询的邮箱地址", required = true, example = "admin@example.com") @PathVariable String email) {
        Optional<User> userOpt = userService.findByEmail(email);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = userOpt.get();
        return ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "fullName", user.getFullName(),
            "role", user.getRole(),
            "enabled", user.isEnabled()
        ));
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户信息", description = "更新用户的基本信息，如全名和邮箱。管理员可以更新任何用户，普通用户只能更新自己的信息。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"用户信息更新成功\", \"user\":{...}}"))),
        @ApiResponse(responseCode = "400", description = "更新失败，例如邮箱格式错误或已被占用"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "无权修改该用户信息"),
        @ApiResponse(responseCode = "404", description = "指定ID的用户不存在")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or authentication.principal.id == #id")
    public ResponseEntity<Map<String, Object>> updateUser(
            @Parameter(description = "待更新用户的唯一ID", required = true, example = "1")
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request) {
        try {
            User updatedUser = userService.updateUser(id, request.getFullName(), request.getEmail());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "用户信息更新成功",
                "user", Map.of(
                    "id", updatedUser.getId(),
                    "username", updatedUser.getUsername(),
                    "email", updatedUser.getEmail(),
                    "fullName", updatedUser.getFullName()
                )
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 修改密码
     */
    @PostMapping("/{id}/change-password")
    @Operation(summary = "修改密码", description = "修改指定用户的登录密码。需要提供旧密码进行验证。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "密码修改成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"密码修改成功\"}"))),
        @ApiResponse(responseCode = "400", description = "修改失败，例如旧密码错误"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "无权修改该用户密码"),
        @ApiResponse(responseCode = "404", description = "指定ID的用户不存在")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or authentication.principal.id == #id")
    public ResponseEntity<Map<String, Object>> changePassword(
            @Parameter(description = "待修改密码用户的唯一ID", required = true, example = "1")
            @PathVariable Long id,
            @RequestBody ChangePasswordRequest request) {
        try {
            userService.changePassword(id, request.getOldPassword(), request.getNewPassword());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "密码修改成功"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * 启用/禁用用户
     */
    @PostMapping("/{id}/toggle-status")
    @Operation(summary = "启用/禁用用户（管理员）", description = "管理员启用或禁用指定用户的账户。被禁用的用户将无法登录。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "状态切换成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"用户已启用\"}"))),
        @ApiResponse(responseCode = "400", description = "请求失败"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "指定ID的用户不存在")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> toggleUserStatus(
            @Parameter(description = "待操作用户的唯一ID", required = true, example = "1")
            @PathVariable Long id,
            @Parameter(description = "设置用户状态，`true`为启用，`false`为禁用", required = true, schema = @Schema(example = "{\"enabled\": true}"))
            @RequestBody Map<String, Boolean> request) {
        try {
            boolean enabled = request.getOrDefault("enabled", true);
            userService.setUserEnabled(id, enabled);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", enabled ? "用户已启用" : "用户已禁用"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    // 请求DTO类 - 这些setter方法被Spring框架在JSON反序列化时自动调用
    @Schema(description = "用户注册请求体")
    public static class RegisterRequest {
        @Schema(description = "用户名，必须唯一", example = "newuser123", requiredMode = Schema.RequiredMode.REQUIRED)
        private String username;
        @Schema(description = "邮箱地址，必须唯一", example = "newuser@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        private String email;
        @Schema(description = "登录密码", example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
        private String password;
        @Schema(description = "用户全名或昵称", example = "张三")
        private String fullName;

        // 默认构造函数（Spring反序列化需要）
        public RegisterRequest() {}

        // Getters - 用于序列化和业务逻辑访问
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public String getFullName() { return fullName; }

        // Setters - Spring在JSON反序列化时自动调用
        public void setUsername(String username) { this.username = username; }
        public void setEmail(String email) { this.email = email; }
        public void setPassword(String password) { this.password = password; }
        public void setFullName(String fullName) { this.fullName = fullName; }
    }

    @Schema(description = "用户信息更新请求体")
    public static class UpdateUserRequest {
        @Schema(description = "新的用户全名或昵称", example = "李四")
        private String fullName;
        @Schema(description = "新的邮箱地址，必须唯一", example = "newemail@example.com")
        private String email;

        // 默认构造函数（Spring反序列化需要）
        public UpdateUserRequest() {}

        // Getters - 用于业务逻辑访问
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }

        // Setters - Spring在JSON反序列化时自动调用
        public void setFullName(String fullName) { this.fullName = fullName; }
        public void setEmail(String email) { this.email = email; }
    }

    @Schema(description = "修改密码请求体")
    public static class ChangePasswordRequest {
        @Schema(description = "当前使用的旧密码", example = "OldPassword123", requiredMode = Schema.RequiredMode.REQUIRED)
        private String oldPassword;
        @Schema(description = "设置的新密码", example = "NewPassword456!", requiredMode = Schema.RequiredMode.REQUIRED)
        private String newPassword;

        // 默认构造函数（Spring反序列化需要）
        public ChangePasswordRequest() {}

        // Getters - 用于业务逻辑访问
        public String getOldPassword() { return oldPassword; }
        public String getNewPassword() { return newPassword; }

        // Setters - Spring在JSON反序列化时自动调用
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
