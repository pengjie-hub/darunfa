package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 促销活动实体。
 * <p>
 * FULL_REDUCTION(满减)：订单金额 >= threshold 时减免 discount 元；
 * DISCOUNT(折扣)：按折扣率 discount（0~1）打折，threshold 为可选门槛。
 * 可指定 productId 针对单个商品，为空表示全店通用。
 */
@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PromotionType type = PromotionType.FULL_REDUCTION;

    /** 门槛金额（满减=满多少，折扣=满多少才享折扣；0 表示无门槛）。 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal threshold = BigDecimal.ZERO;

    /** 满减：减免金额；折扣：折扣率(0~1)。 */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal discount = BigDecimal.ZERO;

    /** 指定商品（为空=全店通用）。 */
    @Column(name = "product_id")
    private Long productId;

    /**
     * 活动配图 URL（前台展示用；为空时前台降级为内置素材/渐变占位）。
     */
    @Column(name = "image_url", length = 512)
    private String imageUrl;

    /**
     * 活动简短描述（前台卡片文案；为空时前台按满减/折扣规则自动生成）。
     */
    @Column(name = "description", length = 512)
    private String description;

    /**
     * 点击跳转链接。
     * <p>
     * 约定：
     * <ul>
     *   <li>{@code http(s)://} 开头 → 前台用浏览器/内置 WebView 打开；</li>
     *   <li>纯数字 → 视为商品 ID，前台跳转商品详情；</li>
     *   <li>为空 → 前台按 productId 兜底（有则跳商品详情，无则跳促销列表页）。</li>
     * </ul>
     */
    @Column(name = "link", length = 512)
    private String link;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 1 启用 0 停用。 */
    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
