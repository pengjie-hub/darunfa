package com.example.backend.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「需要管理员权限」的注解，配合 {@link AuthInterceptor} 使用（AND 语义，与方法/类上的
 * {@link RequirePermission} 同时生效；ADMIN 角色对 {@code @RequirePermission} 短路放行）。
 *
 * <p><b>⚠️ 必须带 {@code @Retention(RetentionPolicy.RUNTIME)}</b>：
 * {@link AuthInterceptor} 是通过反射 {@code getMethodAnnotation/getAnnotation} 读取本注解的，
 * 而 Java 注解的默认保留策略是 {@code CLASS}（编译进 class 文件但不进入运行时，
 * 反射读出来恒为 {@code null}）。早期版本漏写了保留策略，导致所有 {@code @RequireAdmin}
 * 接口的「仅管理员」校验**静默失效** —— 任何已登录用户都能调用，包括
 * {@code /api/admin/rbac/**}（可自我提权）、后台退款/支付退款、封号改角色等资金与权限接口。
 * 这类注解一旦漏掉 RUNTIME，编译期不报错、启动不报错、日志无异常，只能靠越权用例才能发现。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
