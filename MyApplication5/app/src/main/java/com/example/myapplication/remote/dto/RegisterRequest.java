package com.example.myapplication.remote.dto;

/**
 * 注册请求。
 */
public class RegisterRequest {
    public String username;
    public String password;
    public String nickname;
    public String phone;

    public RegisterRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
