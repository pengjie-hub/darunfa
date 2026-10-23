package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.dto.AddCartRequest;
import com.example.backend.dto.UpdateCartRequest;
import com.example.backend.entity.CartItem;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车接口。
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<List<CartItem>> list(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return ApiResponse.ok(cartService.list(userId));
    }

    @PostMapping
    public ApiResponse<CartItem> add(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                     @Valid @RequestBody AddCartRequest req) {
        return ApiResponse.ok(cartService.add(userId, req));
    }

    @PutMapping("/{productId}")
    public ApiResponse<CartItem> update(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                        @PathVariable Long productId,
                                        @RequestBody UpdateCartRequest req) {
        return ApiResponse.ok(cartService.update(userId, productId, req));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> remove(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                    @PathVariable Long productId) {
        cartService.remove(userId, productId);
        return ApiResponse.ok();
    }

    @PostMapping("/remove-batch")
    public ApiResponse<Void> removeBatch(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                         @RequestBody List<Long> productIds) {
        cartService.removeBatch(userId, productIds);
        return ApiResponse.ok();
    }

    @PutMapping("/select-all")
    public ApiResponse<Void> selectAll(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                       @RequestParam boolean selected) {
        cartService.selectAll(userId, selected);
        return ApiResponse.ok();
    }

    @DeleteMapping
    public ApiResponse<Void> clear(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        cartService.clear(userId);
        return ApiResponse.ok();
    }
}
