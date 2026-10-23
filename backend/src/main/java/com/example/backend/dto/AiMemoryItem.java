package com.example.backend.dto;

/**
 * AI 长期记忆条目（供前端展示）。
 * <p>
 * {@code updatedAt} 以字符串形式返回，避免前端额外处理时间格式。
 * </p>
 */
public class AiMemoryItem {

    private String memKey;
    private String memValue;
    private String updatedAt;

    public AiMemoryItem() {}

    public AiMemoryItem(String memKey, String memValue, String updatedAt) {
        this.memKey = memKey;
        this.memValue = memValue;
        this.updatedAt = updatedAt;
    }

    public String getMemKey() { return memKey; }
    public void setMemKey(String memKey) { this.memKey = memKey; }

    public String getMemValue() { return memValue; }
    public void setMemValue(String memValue) { this.memValue = memValue; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
