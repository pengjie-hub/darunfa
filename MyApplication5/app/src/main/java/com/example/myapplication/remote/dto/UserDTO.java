package com.example.myapplication.remote.dto;

/**
 * 用户信息（后端 UserDTO 对应，不含密码）。
 */
public class UserDTO {
    public long id;
    public String username;
    public String nickname;
    public String phone;
    public String email;
    public String avatar;
    public String role;
    public int status;
    public String createdAt;
    public String updatedAt;
    /**
     * 账户余额（元）。后端 UserDTO 新增字段（与 {@code GET /api/wallet} 同一账户）。
     * 「我的」页头部的余额即读此字段，不再使用本地 SharedPreferences 的假余额。
     */
    public double balance;
}
