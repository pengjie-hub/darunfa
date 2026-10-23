package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 追评请求。
 */
public class AddReplyRequest {

    @NotBlank(message = "追评内容不能为空")
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
