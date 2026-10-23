package com.example.backend.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「需要某项权限」的方法或类（细粒度 RBAC）。
 * <p>
 * 配合 {@link AuthInterceptor} 使用：
 * <ul>
 *   <li>标注在类上：该类所有方法都受此权限约束。</li>
 *   <li>标注在方法上：仅该方法受约束，覆盖类级声明。</li>
 * </ul>
 * 校验规则：列出多个 {@link #value()} 时，<b>任一满足即放行</b>（OR 语义）。
 * 例：{@code @RequirePermission({"product:view", "product:edit"})} 表示拥有任一权限即可调用。
 * <p>
 * 仍可与 {@link RequireAdmin} 共存；若两者都存在，<b>任一未满足即拒绝</b>（AND 语义）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /** 权限码（对应 permissions.code 字段），任一满足即放行。 */
    String[] value();
}
