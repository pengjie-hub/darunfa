package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.dto.RechargeRequest;
import com.example.backend.dto.WalletDTO;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钱包接口：查询余额/流水、充值。
 * <p>
 * 需登录（未加入 {@code AuthInterceptor.isPublic()} 白名单）。金额校验统一在 {@code WalletService}。
 */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ApiResponse<WalletDTO> wallet(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                         @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(walletService.getWallet(userId, limit));
    }

    @PostMapping("/recharge")
    public ApiResponse<WalletDTO> recharge(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                           @RequestBody RechargeRequest req) {
        return ApiResponse.ok(walletService.recharge(userId, req.getAmount(), req.getRequestId()));
    }
}
