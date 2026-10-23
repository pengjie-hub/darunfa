package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.entity.CouponStatus;
import com.example.backend.entity.Promotion;
import com.example.backend.entity.UserCoupon;
import com.example.backend.repository.UserCouponRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券服务：领券中心领取、我的券列表、可用券筛选。
 * <p>
 * 与 {@link PromotionService} 的分工：
 * <ul>
 *   <li>PromotionService 管的是「活动本身」（能不能领、什么时候有效）；</li>
 *   <li>CouponService 管的是「用户手里的券」（领没领过、用没用掉、够不够门槛）。</li>
 * </ul>
 * 优惠金额怎么算交给 {@link DiscountService}，本类不碰钱。
 */
@Service
public class CouponService {

    private final UserCouponRepository userCouponRepository;
    private final PromotionService promotionService;

    public CouponService(UserCouponRepository userCouponRepository,
                         PromotionService promotionService) {
        this.userCouponRepository = userCouponRepository;
        this.promotionService = promotionService;
    }

    /**
     * 领取优惠券。
     * <p>
     * 规则：同一活动每人限领一张 —— 否则用户可以连点几次把「满100减10」领成 10 张，
     * 叠加后直接白嫖。
     */
    @Transactional
    public UserCoupon claim(Long userId, Long promotionId) {
        Promotion p = promotionService.getClaimable(promotionId);

        userCouponRepository.findByUserIdAndPromotionId(userId, promotionId).ifPresent(c -> {
            throw new BusinessException(400, "该活动每人限领一张，你已领取过了");
        });

        try {
            return userCouponRepository.saveAndFlush(buildCoupon(userId, p));
        } catch (DataIntegrityViolationException e) {
            // 上面那道判重是「先查再插」，挡不住并发：两个请求可能同时查到空。
            // 真正兜底的是 user_coupons 上的唯一约束 uk_user_coupon_user_promo，
            // 后到的写入会在这里失败 —— 翻译成人话，而不是甩一个 500 给用户。
            throw new BusinessException(400, "该活动每人限领一张，你已领取过了");
        }
    }

    /** 按活动规则快照出一张券（不落库）。 */
    private UserCoupon buildCoupon(Long userId, Promotion p) {
        UserCoupon c = new UserCoupon();
        c.setUserId(userId);
        c.setPromotionId(p.getId());
        // 规则快照：领到手后即使活动被改/被停用，这张券仍然按领取时的规则用
        c.setName(p.getName());
        c.setType(p.getType());
        c.setThreshold(p.getThreshold() == null ? BigDecimal.ZERO : p.getThreshold());
        c.setDiscount(p.getDiscount() == null ? BigDecimal.ZERO : p.getDiscount());
        c.setExpireTime(p.getEndTime());
        c.setStatus(CouponStatus.UNUSED);
        return c;
    }

    /** 我的全部券（新领的在前）。 */
    public List<UserCoupon> mine(Long userId) {
        return userCouponRepository.findByUserIdOrderByClaimedAtDesc(userId);
    }

    /**
     * 我当前可用于某笔金额的券（未使用 + 未过期 + 已过门槛）。
     * <p>
     * 按「减免金额从高到低」排序，这样收银台的券列表第一个就是最划算的，
     * 用户不选也有默认最优解。
     */
    public List<UserCoupon> availableFor(Long userId, BigDecimal amount) {
        BigDecimal base = amount == null ? BigDecimal.ZERO : amount;
        return userCouponRepository
                .findByUserIdAndStatusOrderByClaimedAtDesc(userId, CouponStatus.UNUSED)
                .stream()
                .filter(c -> !c.isExpired())
                .filter(c -> {
                    BigDecimal th = c.getThreshold() == null ? BigDecimal.ZERO : c.getThreshold();
                    return base.compareTo(th) >= 0;
                })
                .sorted((a, b) -> reduceOf(b, base).compareTo(reduceOf(a, base)))
                .toList();
    }

    /** 我手上还能用的券张数（「我的」页角标 / 优惠券入口用）。 */
    public long countUsable(Long userId) {
        return userCouponRepository
                .findByUserIdAndStatusOrderByClaimedAtDesc(userId, CouponStatus.UNUSED)
                .stream()
                .filter(c -> !c.isExpired())
                .count();
    }

    /**
     * 取出一张属于该用户的券，并校验它眼下确实可用。
     * <p>
     * 任何一条不满足都抛业务异常 —— 注意这是在下单事务里调的，
     * 抛异常会把整个下单回滚，绝不会出现「券没用上但订单已生成」。
     */
    public UserCoupon requireUsable(Long userId, Long couponId, BigDecimal amount) {
        UserCoupon c = userCouponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(() -> new BusinessException(404, "优惠券不存在或不属于你"));
        if (c.getStatus() == CouponStatus.USED) {
            throw new BusinessException(400, "该优惠券已使用");
        }
        if (c.isExpired()) {
            throw new BusinessException(400, "该优惠券已过期");
        }
        BigDecimal th = c.getThreshold() == null ? BigDecimal.ZERO : c.getThreshold();
        if (amount.compareTo(th) < 0) {
            throw new BusinessException(400, "订单金额未达到该券的使用门槛");
        }
        return c;
    }

    /**
     * 核销：标记已使用并记下订单号。
     * <p>
     * 这里**不再校验门槛**：门槛在 {@link #requireUsable} 已经校验过一次，
     * 且本方法与下单在同一个事务里；此处只做「券还是未使用状态吗」的二次确认，
     * 防止两笔订单并发用掉同一张券（后到的那笔在这里失败 → 整单回滚）。
     */
    @Transactional
    public void consume(Long userId, Long couponId, Long orderId) {
        if (couponId == null) return;
        UserCoupon c = userCouponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(() -> new BusinessException(404, "优惠券不存在或不属于你"));
        if (c.getStatus() == CouponStatus.USED) {
            throw new BusinessException(400, "该优惠券已被使用，请重新下单");
        }
        c.setStatus(CouponStatus.USED);
        c.setUsedAt(java.time.LocalDateTime.now());
        c.setOrderId(orderId);
        userCouponRepository.save(c);
    }

    /**
     * 回补券：订单取消时把券退回「未使用」。
     * <p>
     * 只有「券确实被这张订单用掉」时才回补，避免把已用于其它订单的券误解锁。
     * 过期券不回补 —— 直接置为未使用会让用户在过期后还能用，等于凭空续期。
     */
    @Transactional
    public void release(Long userId, Long couponId, Long orderId) {
        if (couponId == null || orderId == null) return;
        userCouponRepository.findByIdAndUserId(couponId, userId).ifPresent(c -> {
            boolean usedByThisOrder = c.getStatus() == CouponStatus.USED
                    && orderId.equals(c.getOrderId());
            if (!usedByThisOrder || c.isExpired()) return;
            c.setStatus(CouponStatus.UNUSED);
            c.setUsedAt(null);
            c.setOrderId(null);
            userCouponRepository.save(c);
        });
    }

    /** 一张券在给定金额下能减多少（与 DiscountService 保持同一套算法）。 */
    public static BigDecimal reduceOf(UserCoupon c, BigDecimal amount) {
        if (c.getType() != null
                && c.getType() == com.example.backend.entity.PromotionType.DISCOUNT) {
            BigDecimal rate = c.getDiscount() == null ? BigDecimal.ONE : c.getDiscount();
            BigDecimal off = amount.multiply(BigDecimal.ONE.subtract(rate))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            return off.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : off;
        }
        BigDecimal d = c.getDiscount() == null ? BigDecimal.ZERO : c.getDiscount();
        return d.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : d;
    }
}
