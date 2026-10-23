package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券（「领券中心」领取后落在用户名下的一张券）。
 * <p>
 * <b>为什么要把活动规则快照过来</b>（name / type / threshold / discount / expireTime）：
 * 运营随时可能改动或停用促销活动，如果券直接引用 {@link Promotion} 去取规则，
 * 用户昨天领的「满100减10」今天就可能变成「满200减5」，甚至因为活动停用而失效。
 * 券一旦领取就是用户资产，规则必须冻结。
 * <p>
 * 因此 {@code promotionId} 只作为「来源标记」（用于限领一张与溯源展示），
 * 结算一律按快照字段计算。
 */
@Entity
@Table(name = "user_coupons",
        // 业务铁律「同一活动每人限领一张」的数据库级兜底：Service 里的判重是「先查再插」，
        // 两个请求同时进来会双双查空、双双插入。唯一约束把这条规则钉在存储层，
        // 并发下后到的那条必然写失败，不会留下重复券。
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_coupon_user_promo",
                columnNames = {"user_id", "promotion_id"}),
        indexes = {
                @Index(name = "idx_user_coupon_user", columnList = "user_id"),
                @Index(name = "idx_user_coupon_user_promo", columnList = "user_id,promotion_id")
        })
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 来源促销活动 ID（仅溯源与「同一活动限领一张」判重）。 */
    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    /** 券名快照，如「满 100 减 10（全场通用）」。 */
    @Column(nullable = false, length = 64)
    private String name;

    /** 规则类型快照：FULL_REDUCTION / DISCOUNT。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PromotionType type = PromotionType.FULL_REDUCTION;

    /** 门槛金额快照（0 = 无门槛）。 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal threshold = BigDecimal.ZERO;

    /** 满减=减免额；折扣=折扣率(0~1)。 */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal discount = BigDecimal.ZERO;

    /** 失效时间快照（取领取时活动的 endTime；为 null 表示长期有效）。 */
    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CouponStatus status = CouponStatus.UNUSED;

    @Column(name = "claimed_at", updatable = false)
    private LocalDateTime claimedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** 核销订单 ID（已使用时写回，便于溯源与退款回补）。 */
    @Column(name = "order_id")
    private Long orderId;

    @PrePersist
    void onCreate() {
        this.claimedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PromotionType getType() { return type; }
    public void setType(PromotionType type) { this.type = type; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public CouponStatus getStatus() { return status; }
    public void setStatus(CouponStatus status) { this.status = status; }

    public LocalDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(LocalDateTime claimedAt) { this.claimedAt = claimedAt; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    /** 是否已过失效时间（派生状态，不入库）。 */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }
}
