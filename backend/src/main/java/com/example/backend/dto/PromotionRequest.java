package com.example.backend.dto;

import com.example.backend.entity.PromotionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 促销活动创建/更新请求。
 */
public class PromotionRequest {

    @NotBlank(message = "活动名称不能为空")
    private String name;

    @NotNull(message = "促销类型不能为空")
    private PromotionType type;

    private BigDecimal threshold = BigDecimal.ZERO;

    @NotNull(message = "优惠值不能为空")
    @DecimalMin(value = "0.0", message = "优惠值不能为负")
    private BigDecimal discount;

    private Long productId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;

    /** 活动配图 URL（前台展示）。 */
    @Size(max = 512, message = "图片地址过长")
    private String imageUrl;

    /** 活动简短描述（前台卡片文案）。 */
    @Size(max = 512, message = "描述过长")
    private String description;

    /** 点击跳转链接（http(s) 外链 / 纯数字商品ID / 空）。 */
    @Size(max = 512, message = "跳转链接过长")
    private String link;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PromotionType getType() { return type; }
    public void setType(PromotionType type) { this.type = type; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
}
