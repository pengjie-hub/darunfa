package com.example.myapplication.remote.dto;

/**
 * 登录请求。
 */
public class LoginRequest {
    public String username;
    public String password;

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
