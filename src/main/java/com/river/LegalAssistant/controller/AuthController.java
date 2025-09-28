package com.river.LegalAssistant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 认证控制器
 */
@Controller
@Slf4j
@Tag(name = "认证", description = "用户认证相关接口")
public class AuthController {

    /**
     * 登录页面
     */
    @GetMapping("/login")
    @Operation(summary = "登录页面", description = "提供用户登录的HTML页面。此接口不返回JSON，而是渲染一个Thymeleaf模板。")
    @ApiResponse(responseCode = "200", description = "成功渲染登录页面")
    public String loginPage(
            @Parameter(description = "当登录失败时，此参数为true，用于在页面上显示错误信息")
            @RequestParam(value = "error", required = false) Boolean error,
            @Parameter(description = "当用户登出时，此参数为true，用于在页面上显示登出成功信息")
            @RequestParam(value = "logout", required = false) Boolean logout,
            Model model) {
        
        if (error != null && error) {
            model.addAttribute("errorMessage", "用户名或密码错误");
            log.debug("登录失败 - 用户名或密码错误");
        }
        
        if (logout != null && logout) {
            model.addAttribute("logoutMessage", "您已成功登出");
            log.debug("用户已登出");
        }
        
        return "login";
    }

    /**
     * 首页重定向
     */
    @GetMapping("/")
    @Operation(summary = "首页重定向", description = "根路径 (/) 将被重定向到 `/health` 基础健康检查端点。")
    @ApiResponse(responseCode = "302", description = "成功重定向到健康检查页面")
    public String index() {
        return "redirect:/health";
    }
}
