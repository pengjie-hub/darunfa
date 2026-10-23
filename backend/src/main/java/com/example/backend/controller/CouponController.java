package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.dto.ClaimCouponRequest;
import com.example.backend.dto.CouponDTO;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券接口（需登录）。
 * <p>
 * 路径不在拦截器公开名单里 —— 券是个人资产，一律按 token 里的 userId 隔离。
 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 领取优惠券（同一活动每人限领一张）。 */
    @PostMapping("/claim")
    public ApiResponse<CouponDTO> claim(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @Valid @RequestBody ClaimCouponRequest req) {
        return ApiResponse.ok(CouponDTO.from(couponService.claim(userId, req.getPromotionId())));
    }

    /** 我的全部券（新领的在前，含已使用/已过期）。 */
    @GetMapping("/mine")
    public ApiResponse<List<CouponDTO>> mine(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return ApiResponse.ok(couponService.mine(userId).stream().map(CouponDTO::from).toList());
    }

    /**
     * 我在给定金额下可用的券（未使用 + 未过期 + 已过门槛，按优惠额从高到低）。
     * 收银台用它渲染可选券列表。
     */
    @GetMapping("/available")
    public ApiResponse<List<CouponDTO>> available(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(required = false, defaultValue = "0") BigDecimal amount) {
        return ApiResponse.ok(couponService.availableFor(userId, amount).stream()
                .map(CouponDTO::from).toList());
    }

    /** 我手上还能用的券张数（「我的」页优惠券数字用）。 */
    @GetMapping("/count")
    public ApiResponse<Long> count(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return ApiResponse.ok(couponService.countUsable(userId));
    }
}
