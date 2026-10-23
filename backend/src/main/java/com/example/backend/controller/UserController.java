package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.dto.UpdatePasswordRequest;
import com.example.backend.dto.UpdateProfileRequest;
import com.example.backend.dto.UserDTO;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户个人信息接口。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ApiResponse<UserDTO> profile(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        return ApiResponse.ok(UserDTO.from(userService.getById(userId)));
    }

    @PutMapping("/profile")
    public ApiResponse<UserDTO> updateProfile(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                              @Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(UserDTO.from(userService.updateProfile(userId, req)));
    }

    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                                            @Valid @RequestBody UpdatePasswordRequest req) {
        userService.updatePassword(userId, req);
        return ApiResponse.ok();
    }
}
