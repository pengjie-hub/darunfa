package com.example.myapplication.data;

import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.dto.CheckoutPreviewDTO;
import com.example.myapplication.remote.dto.CouponDTO;

import java.util.List;

/**
 * 优惠券与「结算优惠试算」的数据访问接口（异步回调）。
 * <p>
 * 为什么把试算也放这里：试算的意义就是「这笔订单用券/不用券各减多少」，
 * 与券是同一件事的两面，分开反而要两个仓储来回传参。
 */
public interface CouponRepository {

    /** 领取优惠券（同一活动每人限领一张，重复领取会回调业务错误）。 */
    void claim(long promotionId, ApiCallback<CouponDTO> cb);

    /** 我的全部券（含已使用、已过期）。 */
    void mine(ApiCallback<List<CouponDTO>> cb);

    /** 给定金额下可用的券（按优惠额从高到低）。 */
    void available(double amount, ApiCallback<List<CouponDTO>> cb);

    /** 我手上还能用的券张数。 */
    void count(ApiCallback<Long> cb);

    /**
     * 优惠试算：返回「商品总额 / 活动优惠 / 券优惠 / 应付」明细。
     * <p>
     * 这是让用户在下单前就能看到满减生效的唯一途径 ——
     * 下单接口虽然也会算，但那时金额已经定了，看不到过程。
     *
     * @param couponId 选用的券，可传 null
     */
    void preview(double amount, Long couponId, ApiCallback<CheckoutPreviewDTO> cb);
}
