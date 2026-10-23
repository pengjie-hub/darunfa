package com.example.myapplication.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户评论实体类
 * 用于商品详情页中展示用户评论
 * 每条初始评论可关联多条追评(replies)
 */
public class ReviewInfo {
    private int id;
    private int productId;
    private int userId;
    private String userName;
    private double rating;       // 评分 0-5
    private String content;     // 评论内容
    private String createdAt;   // 评论时间
    private List<ReviewReply> replies = new ArrayList<>(); // 该评论的追评列表

    public ReviewInfo() {
    }

    public ReviewInfo(int productId, int userId, String userName, double rating, String content, String createdAt) {
        this.productId = productId;
        this.userId = userId;
        this.userName = userName;
        this.rating = rating;
        this.content = content;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<ReviewReply> getReplies() {
        return replies;
    }

    public void setReplies(List<ReviewReply> replies) {
        this.replies = replies;
    }
}
