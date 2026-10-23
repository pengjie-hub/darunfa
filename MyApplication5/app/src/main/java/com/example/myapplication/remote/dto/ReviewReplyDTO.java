package com.example.myapplication.remote.dto;

/**
 * 评论回复（后端 ReviewReply 实体对应）。
 * <p>
 * 同一条评论下会有两种回复：{@code replyType == "USER"} 是评论作者本人的追评，
 * {@code "MERCHANT"} 是商家/客服的回复。前端据此决定标签显示「追评」还是「商家回复」。
 */
public class ReviewReplyDTO {
    public long id;
    public long reviewId;
    public long userId;
    public String userName;
    public String content;
    public String createdAt;
    /** USER / MERCHANT（旧数据可能为 null，按 USER 处理）。 */
    public String replyType;
    /** 中文类型名：用户追评 / 商家回复（后端直接下发）。 */
    public String replyTypeLabel;
}
