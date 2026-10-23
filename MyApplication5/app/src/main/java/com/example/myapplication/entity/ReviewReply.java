package com.example.myapplication.entity;

/**
 * 追评实体类
 * 关联初始评论(review_id), 同一用户可对自己的初始评论发表不限次数追评
 */
public class ReviewReply {
    private int id;
    private int reviewId;    // 关联初始评论的 _id
    private int userId;      // 追评者(必须等于初始评论的发布者)
    private String userName;
    private String content;  // 追评内容
    private String createdAt;

    /**
     * 回复类型：{@code "MERCHANT"} 表示商家/客服回复，其余（含 null）视为用户追评。
     * <p>
     * UI 靠这个字段决定标签文案与配色，避免把商家的话当成买家自己写的。
     */
    private String replyType;

    public boolean isMerchant() {
        return "MERCHANT".equals(replyType);
    }

    public String getReplyType() {
        return replyType;
    }

    public void setReplyType(String replyType) {
        this.replyType = replyType;
    }

    public ReviewReply() {
    }

    public ReviewReply(int reviewId, int userId, String userName, String content, String createdAt) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.userName = userName;
        this.content = content;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getReviewId() {
        return reviewId;
    }

    public void setReviewId(int reviewId) {
        this.reviewId = reviewId;
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
}
