package com.example.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 根路径入口：访问 {@code /} 时自动重定向到 Web 登录页。
 * <p>
 * 本后端为纯 REST 服务，登录页为 {@code static/login.html} 静态资源。
 * 浏览器访问根路径时跳转过去，便于直接体验/演示登录流程。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/login.html";
    }
}
