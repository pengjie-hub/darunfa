package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 权限实体。
 * <p>
 * 权限是系统中可被鉴权控制的最小单元，采用「资源:操作」两级编码（冒号分隔），例如：
 * <ul>
 *   <li>{@code product:view} —— 商品查看</li>
 *   <li>{@code product:create} —— 商品新增</li>
 *   <li>{@code order:ship} —— 订单发货</li>
 *   <li>{@code review:reply} —— 评论回复</li>
 * </ul>
 * 资源维度支持树形（{@code parentId}），便于按模块分组展示。{@code type} 区分权限粒度：
 * <ul>
 *   <li>{@code MENU} —— 菜单级权限（控制后台左侧菜单可见性）</li>
 *   <li>{@code BUTTON} —— 按钮/操作级权限（控制页内按钮可用性）</li>
 *   <li>{@code API} —— 接口级权限（控制后台 REST 路由的拦截）</li>
 * </ul>
 * 角色与权限的多对多关系通过 {@link RolePermission} 中间表实现。
 */
@Entity
@Table(name = "permissions")
public class Permission {

    /** 权限类型：MENU 菜单 / BUTTON 按钮 / API 接口。 */
    public enum Type { MENU, BUTTON, API }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 权限编码（系统唯一），如 product:view。 */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /** 权限名称（中文），如「查看商品」。 */
    @Column(nullable = false, length = 64)
    private String name;

    /** 权限类型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type = Type.MENU;

    /** 父权限 id（0 表示顶级，便于按资源分组展示）。 */
    @Column(name = "parent_id", nullable = false)
    private Long parentId = 0L;

    /** 所属资源模块（如「商品」「订单」），便于按模块聚合。 */
    @Column(length = 32)
    private String module;

    /** 展示顺序（同 parentId 下越小越靠前）。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 1=启用，0=禁用。 */
    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
