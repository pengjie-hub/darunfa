package com.example.backend.dto;

import java.util.List;

/**
 * 后台「AI 营销文案生成」响应体（作为 {@code ApiResponse.data}）。
 *
 * <ul>
 *   <li>{@code scene} / {@code channel} — 归一后的最终场景与渠道（非法入参会被落到默认值）</li>
 *   <li>{@code title} — 标题（≤30 字，缺失时以名称兜底）</li>
 *   <li>{@code subtitle} — 副标题</li>
 *   <li>{@code sellingPoints} — 卖点列表（去空、去重、截断，保留 3~6 条，不足不硬凑）</li>
 *   <li>{@code body} — 正文（已按渠道夹取长度）</li>
 *   <li>{@code cta} — 行动号召</li>
 *   <li>{@code tips} — 处理提示（如忽略原价、归一渠道、过滤绝对化用语）</li>
 *   <li>{@code elapsedMs} — 本次生成耗时（毫秒）</li>
 * </ul>
 */
public class MarketingCopyResponse {

    private String scene;
    private String channel;
    private String title;
    private String subtitle;
    private List<String> sellingPoints;
    private String body;
    private String cta;
    private List<String> tips;
    private long elapsedMs;

    public MarketingCopyResponse() {}

    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public List<String> getSellingPoints() { return sellingPoints; }
    public void setSellingPoints(List<String> sellingPoints) { this.sellingPoints = sellingPoints; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getCta() { return cta; }
    public void setCta(String cta) { this.cta = cta; }

    public List<String> getTips() { return tips; }
    public void setTips(List<String> tips) { this.tips = tips; }

    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }
}
