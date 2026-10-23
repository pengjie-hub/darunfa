package com.example.backend.dto;

import com.example.backend.entity.RiskLevel;
import com.example.backend.entity.RiskScore;
import com.example.backend.service.RiskDetectionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 风险评分响应 DTO（给前端表格 / 详情弹窗用）。
 * reasons JSON 在后端反序列化成可读结构，避免前端再 JSON.parse。
 */
public class RiskScoreDTO {

    private Long id;
    private Long orderId;
    /** 关联订单的订单号（来自 orders 表，后端批量补全）。 */
    private String orderNo;
    private Integer score;
    private RiskLevel level;
    private String suggestedAction;
    private RiskScore.Source source;
    private Boolean isRead;
    private LocalDateTime createdAt;

    /** 反序列化后的 reasons 可读视图 */
    private Integer ruleScore;
    private List<Map<String, Object>> signals;
    private String llmSummary;
    private String llmAction;

    public static RiskScoreDTO from(RiskScore rs) {
        RiskScoreDTO dto = new RiskScoreDTO();
        dto.id = rs.getId();
        dto.orderId = rs.getOrderId();
        dto.score = rs.getScore();
        dto.level = rs.getLevel();
        dto.suggestedAction = rs.getSuggestedAction();
        dto.source = rs.getSource();
        dto.isRead = rs.getIsRead();
        dto.createdAt = rs.getCreatedAt();
        RiskDetectionService.ReasonView v = RiskDetectionService.parseReasons(rs.getReasons());
        dto.ruleScore = v.ruleScore;
        dto.signals = v.signals;
        dto.llmSummary = v.llmSummary;
        dto.llmAction = v.llmAction;
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public RiskLevel getLevel() { return level; }
    public void setLevel(RiskLevel level) { this.level = level; }
    public String getSuggestedAction() { return suggestedAction; }
    public void setSuggestedAction(String suggestedAction) { this.suggestedAction = suggestedAction; }
    public RiskScore.Source getSource() { return source; }
    public void setSource(RiskScore.Source source) { this.source = source; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Integer getRuleScore() { return ruleScore; }
    public void setRuleScore(Integer ruleScore) { this.ruleScore = ruleScore; }
    public List<Map<String, Object>> getSignals() { return signals; }
    public void setSignals(List<Map<String, Object>> signals) { this.signals = signals; }
    public String getLlmSummary() { return llmSummary; }
    public void setLlmSummary(String llmSummary) { this.llmSummary = llmSummary; }
    public String getLlmAction() { return llmAction; }
    public void setLlmAction(String llmAction) { this.llmAction = llmAction; }
}