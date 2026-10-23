package com.example.myapplication.remote.dto;


/**
 * 登录/注册成功响应：token + 用户信息。
 */
public class LoginResponse {
    public String token;
    public UserDTO user;
}
