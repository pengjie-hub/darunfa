package com.example.backend.entity;

/**
 * 评论回复类型。
 * <p>
 * {@code review_replies} 表同时承载两种语义的回复，靠本字段区分：
 * <ul>
 *   <li>{@link #USER} —— 评论作者本人补充的「追评」（前台商品详情页可见「追评」按钮）；</li>
 *   <li>{@link #MERCHANT} —— 商家/客服在后台评论管理里对该评论的正式回复。</li>
 * </ul>
 * AI 评论总结只应统计用户发表的评论，因此凡是要把评论文本喂给模型的场景，
 * 都必须先按本字段过滤掉 {@link #MERCHANT}。
 */
public enum ReplyType {

    /** 用户追评（评论作者本人）。 */
    USER("用户追评"),

    /** 商家回复（后台客服/运营）。 */
    MERCHANT("商家回复");

    private final String label;

    ReplyType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
