package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.dto.AddressDTO;
import com.example.backend.dto.AddressRequest;
import com.example.backend.entity.Address;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口（需登录）。
 * <p>
 * <b>为什么强制登录</b>：地址是强个人数据，必须按 token 里的 userId 隔离。
 * 这些路径**故意不放进** {@code AuthInterceptor} 的公开名单，
 * 拦截器解析 token 后把 userId 塞进 {@code ATTR_USER_ID}，这里直接用，
 * 不接受任何由前端传入的 userId（否则改个参数就能读写别人的地址）。
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /** 我的地址列表（默认地址在最前）。 */
    @GetMapping
    public ApiResponse<List<AddressDTO>> list(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return ApiResponse.ok(addressService.list(userId).stream().map(AddressDTO::from).toList());
    }

    /** 我的默认地址；一条都没有时返回 null（前端据此提示「去设置默认地址」）。 */
    @GetMapping("/default")
    public ApiResponse<AddressDTO> defaultAddress(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return ApiResponse.ok(addressService.getDefault(userId).map(AddressDTO::from).orElse(null));
    }

    /** 新增地址（用户的第一条会自动成为默认）。 */
    @PostMapping
    public ApiResponse<AddressDTO> create(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @Valid @RequestBody AddressRequest req) {
        Address a = addressService.create(userId, req);
        return ApiResponse.ok(AddressDTO.from(a));
    }

    /** 编辑地址。 */
    @PutMapping("/{id}")
    public ApiResponse<AddressDTO> update(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest req) {
        return ApiResponse.ok(AddressDTO.from(addressService.update(userId, id, req)));
    }

    /** 删除地址（若删的是默认地址，会自动把剩余第一条设为默认）。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long id) {
        addressService.delete(userId, id);
        return ApiResponse.ok();
    }

    /** 设为默认地址（全局唯一）。 */
    @PutMapping("/{id}/default")
    public ApiResponse<AddressDTO> setDefault(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(AddressDTO.from(addressService.setDefault(userId, id)));
    }
}
