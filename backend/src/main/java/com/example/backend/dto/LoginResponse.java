package com.example.backend.dto;

/**
 * 登录/注册成功后的响应：JWT 令牌 + 用户信息（不含密码）。
 */
public class LoginResponse {

    private String token;
    private UserDTO user;

    public LoginResponse() {
    }

    public LoginResponse(String token, UserDTO user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public UserDTO getUser() { return user; }
    public void setUser(UserDTO user) { this.user = user; }
}
