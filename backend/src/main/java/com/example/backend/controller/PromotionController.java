package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.dto.CheckoutPreviewDTO;
import com.example.backend.dto.PromotionDTO;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.service.DiscountService;
import com.example.backend.service.PromotionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 前台促销活动查询接口（只读）。
 * <p>
 * 与后台 {@code /api/admin/promotions} 的区别：
 * <ul>
 *   <li>路径对外开放（见 {@code AuthInterceptor#browsePublic}），未登录访客也能浏览；</li>
 *   <li>只返回「启用中 且 处于有效期内」的活动，不暴露停用/过期数据；</li>
 *   <li>返回展示型 DTO，已把优惠规则转成可读文案。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    private final PromotionService promotionService;
    private final DiscountService discountService;

    public PromotionController(PromotionService promotionService,
                               DiscountService discountService) {
        this.promotionService = promotionService;
        this.discountService = discountService;
    }

    /** 当前生效中的促销活动列表（启用 + 在有效期内）。 */
    @GetMapping
    public ApiResponse<List<PromotionDTO>> active() {
        List<PromotionDTO> list = promotionService.listActive().stream()
                .map(PromotionDTO::from)
                .toList();
        return ApiResponse.ok(list);
    }

    /**
     * 优惠试算：给定商品总额（和可选的券），返回优惠明细与应付金额。
     * <p>
     * 购物车与收银台都调这个接口，才能在**下单之前**把「满 100 减 10 生效了」显示出来。
     * <p>
     * 注意：本路径**不在**拦截器公开名单（公开的只有 {@code GET /api/promotions} 本身），
     * 因为要按 token 里的 userId 校验券归属，必须登录。
     *
     * @param amount   商品总额
     * @param couponId 选用的优惠券，可空
     */
    @GetMapping("/calc")
    public ApiResponse<CheckoutPreviewDTO> calc(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) Long couponId) {
        return ApiResponse.ok(discountService.preview(userId, amount, couponId));
    }
}

