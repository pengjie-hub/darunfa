package com.example.myapplication.entity;

public class UserInfo {
    private int id;
    private String name;
    private String password;
    private String phone;   // 手机号(可选)
    private String avatar;  // 头像资源名(可选, 为空则使用默认头像)

    public UserInfo() {
    }

    public UserInfo(int id, String name, String password) {
        this.id = id;
        this.name = name;
        this.password = password;
    }

    public UserInfo(int id, String name, String password, String phone, String avatar) {
        this.id = id;
        this.name = name;
        this.password = password;
        this.phone = phone;
        this.avatar = avatar;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
