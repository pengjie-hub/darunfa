package com.example.backend.dto;

import com.example.backend.entity.User;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * 用户响应 DTO：不含密码字段，避免密码散列泄漏到前端。
 */
public class UserDTO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private BigDecimal balance;
    private String role;
    private Integer status;
    private String createdAt;
    private String updatedAt;

    public static UserDTO from(User u) {
        UserDTO d = new UserDTO();
        d.id = u.getId();
        d.username = u.getUsername();
        d.nickname = u.getNickname();
        d.phone = u.getPhone();
        d.email = u.getEmail();
        d.avatar = u.getAvatar();
        d.balance = u.getBalance();
        d.role = u.getRole() == null ? null : u.getRole().name();
        d.status = u.getStatus();
        d.createdAt = u.getCreatedAt() == null ? null : u.getCreatedAt().format(FMT);
        d.updatedAt = u.getUpdatedAt() == null ? null : u.getUpdatedAt().format(FMT);
        return d;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
