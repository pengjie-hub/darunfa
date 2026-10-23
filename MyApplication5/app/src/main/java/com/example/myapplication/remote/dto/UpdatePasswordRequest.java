package com.example.myapplication.remote.dto;

/**
 * 修改密码请求。
 * <p>
 * 对应后端 {@code PUT /api/user/password}；
 * 后端要求必须提供原密码用于校验（见 UserService.updatePassword）。
 */
public class UpdatePasswordRequest {

    public String oldPassword;
    public String newPassword;

    public UpdatePasswordRequest(String oldPassword, String newPassword) {
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
    }
}
