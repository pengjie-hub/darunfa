package com.example.backend.dto;

import java.util.List;

/**
 * 当前登录用户信息（含权限码列表）。
 * <p>
 * 用于 {@code GET /api/auth/me}：前端登录后调用一次，得到当前用户的 role 与权限码集合，
 * 据此过滤侧边栏菜单与操作按钮。
 */
public class MeResponse {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private String role;
    /** 权限码集合（例：["product:view","product:edit"]）。 */
    private List<String> permissions;

    public MeResponse() {
    }

    public MeResponse(Long id, String username, String nickname, String phone, String email,
                      String role, List<String> permissions) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.phone = phone;
        this.email = email;
        this.role = role;
        this.permissions = permissions;
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

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}
