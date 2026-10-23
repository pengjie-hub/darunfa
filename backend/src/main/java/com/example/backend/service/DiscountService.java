package com.example.backend.service;

import com.example.backend.dto.CheckoutPreviewDTO;
import com.example.backend.entity.UserCoupon;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 结算优惠计算：把「自动满减/折扣」与「用户选用的券」合成一个结果。
 * <p>
 * <b>为什么单独拆一个服务</b>：试算（下单前，只看不改）和下单（真正落库）必须走
 * <b>同一段算法</b>。以前只有 {@code OrderService} 内部算一次，
 * 前端拿不到，就出现了「页面上显示原价、订单里却减了 10 块」的怪现象。
 * 抽出来之后，接口试算和下单各调一次 {@link #preview}，口径天然一致。
 * <p>
 * 叠加规则（两条，缺一不可）：
 * <ol>
 *   <li><b>跨活动可以叠加</b>：自动选中的活动与「用户手里那张券的来源活动」不是同一个，
 *       两者金额相加，应付金额下限 0（不做倒找）。</li>
 *   <li><b>同活动不得重复扣减</b>：券自带 {@code promotionId} 来源标记，若它恰好就是本次
 *       自动选中的那个活动，则只能算一次，取两者中更优的那一个。</li>
 * </ol>
 * 总优惠不会超过商品总额 —— 否则订单会出现「优惠 130、应付 -10」这种脏数据。
 */
@Service
public class DiscountService {

    private final PromotionService promotionService;
    private final CouponService couponService;

    public DiscountService(PromotionService promotionService, CouponService couponService) {
        this.promotionService = promotionService;
        this.couponService = couponService;
    }

    /**
     * 试算优惠（只读，不改任何数据）。
     *
     * @param userId      当前用户（用于校验券归属；无券时可传 null）
     * @param totalAmount 商品总额
     * @param couponId    选用的券，可空
     */
    public CheckoutPreviewDTO preview(Long userId, BigDecimal totalAmount, Long couponId) {
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;

        CheckoutPreviewDTO dto = new CheckoutPreviewDTO();
        dto.setTotalAmount(total);

        // 1) 自动促销：取减免最多的那一个
        Optional<PromotionService.PromotionHit> hit = promotionService.computeDiscount(total);
        Long promoId = null;
        BigDecimal promoOff = BigDecimal.ZERO;
        if (hit.isPresent()) {
            promoId = hit.get().promotion().getId();
            dto.setPromotionId(promoId);
            dto.setPromotionName(hit.get().promotion().getName());
            promoOff = hit.get().reduceAmount() == null
                    ? BigDecimal.ZERO : hit.get().reduceAmount();
        }

        // 2) 券：门槛/归属/是否已用都在这里一并校验，不合格直接抛异常
        UserCoupon coupon = null;
        BigDecimal couponOff = BigDecimal.ZERO;
        if (couponId != null && userId != null) {
            coupon = couponService.requireUsable(userId, couponId, total);
            BigDecimal raw = CouponService.reduceOf(coupon, total);
            // 单张券减免不超过商品总额，避免叠加后算出负数
            couponOff = raw.compareTo(total) > 0 ? total : raw;
        }

        // 3) 去重：券与自动活动**同源**时只能算一次。
        //    券的 promotionId 就是它的来源活动；若该活动恰好也被自动选中，
        //    再相加就会「同一活动扣两遍」（先按券减一次、再按活动减一次）。
        //    处理：券只有**严格更优**才参与结算。
        //    - 券 > 活动：券顶掉活动（活动侧清零），用户多拿到差额，核销这张券；
        //    - 券 <= 活动：活动本来就减完了，用不用券到手金额都一样。
        //      此时绝不能核销 —— 否则用户只是白白烧掉一张券，一分钱没多省。
        if (coupon != null && promoId != null && promoId.equals(coupon.getPromotionId())) {
            if (couponOff.compareTo(promoOff) > 0) {
                promoOff = BigDecimal.ZERO;
            } else {
                coupon = null;
                couponOff = BigDecimal.ZERO;
            }
        }

        dto.setPromotionDiscount(promoOff);
        if (coupon != null) {
            dto.setCouponId(coupon.getId());
            dto.setCouponName(coupon.getName());
        }
        dto.setCouponDiscount(couponOff);

        // 4) 汇总：总优惠上限 = 商品总额
        BigDecimal totalOff = promoOff.add(couponOff);
        if (totalOff.compareTo(total) > 0) totalOff = total;
        dto.setDiscountAmount(totalOff);
        dto.setPayAmount(total.subtract(totalOff));
        dto.setCouponApplied(couponOff.compareTo(BigDecimal.ZERO) > 0);

        // 5) 未达门槛时的「再买多少」提示（没有未达标活动时为 null）
        dto.setHint(promotionService.nextTierHint(total));
        return dto;
    }

    /** 核销券（下单事务内调用；失败会连订单一起回滚）。 */
    public void consumeCoupon(Long userId, Long couponId, Long orderId) {
        couponService.consume(userId, couponId, orderId);
    }

    /**
     * 回补券：订单取消/退货时把券还给用户。
     * <p>
     * 不还券是很多电商系统的经典槽点 —— 用户券没了、货也没买成。
     * 这里只在券确实被这张订单用掉的前提下回补，避免误把已用于其它订单的券解锁。
     */
    public void releaseCoupon(Long userId, Long couponId, Long orderId) {
        couponService.release(userId, couponId, orderId);
    }
}
