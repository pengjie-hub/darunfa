package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.common.BusinessException;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.dto.MeResponse;
import com.example.backend.dto.RegisterRequest;
import com.example.backend.security.JwtUtil;
import com.example.backend.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    /**
     * 查询当前登录用户信息（含 role + 权限码列表）。
     * 路径在 /api/auth/** 下，拦截器不强制鉴权，但本接口要求必须携带 Bearer token。
     */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BusinessException(401, "缺少登录凭证");
        }
        String token = auth.substring(7).trim();
        try {
            Claims claims = jwtUtil.parse(token);
            Long userId = jwtUtil.getUserId(claims);
            return ApiResponse.ok(authService.me(userId));
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(401, "登录凭证无效或已过期");
        }
    }
}
