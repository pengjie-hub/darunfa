package com.example.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新个人信息请求。
 */
public class UpdateProfileRequest {

    @Size(max = 32, message = "昵称过长")
    private String nickname;

    @Size(max = 20, message = "手机号过长")
    private String phone;

    @Size(max = 64, message = "邮箱过长")
    private String email;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}
