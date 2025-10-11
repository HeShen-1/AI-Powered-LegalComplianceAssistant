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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/users")
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
    public ResponseEntity<Map<String, Object>> register(@jakarta.validation.Valid @RequestBody RegisterRequest request) {
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
     * 获取所有用户列表（管理员）
     */
    @GetMapping
    @Operation(summary = "获取所有用户列表（管理员）", description = "获取系统中所有用户的列表。仅限管理员访问。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<Map<String, Object>>> getAllUsers() {
        java.util.List<User> users = userService.findAll();
        
        java.util.List<Map<String, Object>> userList = users.stream()
            .map(user -> {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("email", user.getEmail());
                userMap.put("fullName", user.getFullName() != null ? user.getFullName() : "");
                userMap.put("role", user.getRole().toString());
                userMap.put("enabled", user.isEnabled());
                userMap.put("createdAt", user.getCreatedAt().toString());
                return userMap;
            })
            .collect(java.util.stream.Collectors.toList());
        
        return ResponseEntity.ok(userList);
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
            @jakarta.validation.Valid @RequestBody UpdateUserRequest request) {
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
            @jakarta.validation.Valid @RequestBody ChangePasswordRequest request) {
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
     * 获取用户统计数据
     */
    @GetMapping("/{id}/stats")
    @Operation(summary = "获取用户统计数据", description = "获取用户的使用统计数据，包括合同审查数量、AI问答次数等信息。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "无权访问该用户统计数据"),
        @ApiResponse(responseCode = "404", description = "指定ID的用户不存在")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or authentication.principal.id == #id")
    public ResponseEntity<com.river.LegalAssistant.dto.UserStatsDto> getUserStats(
            @Parameter(description = "用户的唯一ID", required = true, example = "1")
            @PathVariable Long id) {
        try {
            com.river.LegalAssistant.dto.UserStatsDto stats = userService.getUserStats(id);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
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

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户（管理员）", description = "管理员删除指定用户。此操作不可恢复，会删除用户的所有相关数据。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(mediaType = "application/json", schema = @Schema(example = "{\"success\":true,\"message\":\"用户已删除\"}"))),
        @ApiResponse(responseCode = "400", description = "删除失败，例如不能删除自己或最后一个管理员"),
        @ApiResponse(responseCode = "401", description = "用户未认证"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "指定ID的用户不存在")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @Parameter(description = "待删除用户的唯一ID", required = true, example = "2")
            @PathVariable Long id,
            org.springframework.security.core.Authentication authentication) {
        try {
            // 获取当前登录的管理员
            org.springframework.security.core.userdetails.UserDetails principal = 
                (org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal();
            
            // 不允许删除自己
            Optional<User> currentUserOpt = userService.findByUsername(principal.getUsername());
            if (currentUserOpt.isPresent() && currentUserOpt.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "不能删除自己的账户"
                ));
            }
            
            // 删除用户
            userService.deleteUser(id);
            
            log.info("管理员 {} 删除了用户 {}", principal.getUsername(), id);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "用户已删除"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("删除用户失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "删除用户失败: " + e.getMessage()
            ));
        }
    }

    // 请求DTO类 - 这些setter方法被Spring框架在JSON反序列化时自动调用
    @Schema(description = "用户注册请求体")
    public static class RegisterRequest {
        @jakarta.validation.constraints.NotBlank(message = "用户名不能为空")
        @jakarta.validation.constraints.Size(min = 3, max = 50, message = "用户名长度必须在3到50个字符之间")
        @Schema(description = "用户名，必须唯一", example = "newuser123", requiredMode = Schema.RequiredMode.REQUIRED)
        private String username;
        
        @jakarta.validation.constraints.NotBlank(message = "邮箱不能为空")
        @jakarta.validation.constraints.Email(message = "邮箱格式不正确")
        @Schema(description = "邮箱地址，必须唯一", example = "newuser@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        private String email;
        
        @jakarta.validation.constraints.NotBlank(message = "密码不能为空")
        @jakarta.validation.constraints.Size(min = 6, max = 100, message = "密码长度必须在6到100个字符之间")
        @Schema(description = "登录密码", example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
        private String password;
        
        @jakarta.validation.constraints.Size(max = 100, message = "用户全名不能超过100个字符")
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
        @jakarta.validation.constraints.Size(max = 100, message = "用户全名不能超过100个字符")
        @Schema(description = "新的用户全名或昵称", example = "李四")
        private String fullName;
        
        @jakarta.validation.constraints.Email(message = "邮箱格式不正确")
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
        @jakarta.validation.constraints.NotBlank(message = "旧密码不能为空")
        @Schema(description = "当前使用的旧密码", example = "OldPassword123", requiredMode = Schema.RequiredMode.REQUIRED)
        private String oldPassword;
        
        @jakarta.validation.constraints.NotBlank(message = "新密码不能为空")
        @jakarta.validation.constraints.Size(min = 6, max = 100, message = "密码长度必须在6到100个字符之间")
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
