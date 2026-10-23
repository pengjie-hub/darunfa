package com.example.backend.entity;

/**
 * 用户角色枚举。
 * <p>
 * 角色编码与 {@link Role#getCode()} 保持一致：用户当前角色由本字段记录；
 * 未来需要支持「一个用户多角色」时，扩展为 {@code user_roles} 中间表即可，
 * 本枚举可作为「默认角色」或「主角色」继续使用，向后兼容。
 */
public enum UserRole {
    USER,   // 普通用户（前台消费者）
    ADMIN,  // 超级管理员
    OPS,    // 运营专员（商品/订单/促销等日常运营）
    CS      // 客服（评论处理、退款审核）
}
