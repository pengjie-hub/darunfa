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
 * 菜单实体。
 * <p>
 * 菜单是后台左侧导航的展示项，与权限（Permission）解耦但有关联：
 * <ul>
 *   <li>一个菜单通常对应一个 {@code MENU} 类型的权限；</li>
 *   <li>通过 {@code permissionId}（逻辑外键）把菜单与权限关联起来，
 *       运营可在「权限管理」里勾选哪些角色可以看到该菜单。</li>
 * </ul>
 * 菜单支持树形结构（{@code parentId}），便于多级导航。
 * 字段说明：
 * <ul>
 *   <li>{@code path} —— 前端路由路径，如 {@code /admin/products}；</li>
 *   <li>{@code icon} —— emoji 或 icon 字符（这里用 emoji 简化展示）；</li>
 *   <li>{@code type} —— DIR 目录 / MENU 菜单 / BUTTON 按钮占位；</li>
 *   <li>{@code component} —— 前端模块标识（与 admin.html 的 MODULES key 对应，如 {@code products}）。</li>
 * </ul>
 */
@Entity
@Table(name = "menus")
public class Menu {

    /** 菜单类型：DIR 目录（仅分组，无页面）/ MENU 菜单（实际页面）/ BUTTON 按钮占位。 */
    public enum Type { DIR, MENU, BUTTON }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 父菜单 id（0 表示顶级）。 */
    @Column(name = "parent_id", nullable = false)
    private Long parentId = 0L;

    /** 菜单名称（中文展示）。 */
    @Column(nullable = false, length = 32)
    private String name;

    /** 路由路径（前端使用），如 /admin/products。 */
    @Column(length = 128)
    private String path;

    /** 图标（emoji 或图标类名）。 */
    @Column(length = 32)
    private String icon;

    /** 前端模块标识（对应 admin.html 的 MODULES key，如 products/orders）。 */
    @Column(length = 32)
    private String component;

    /** 菜单类型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type = Type.MENU;

    /** 关联的权限 id（用于权限校验，可为空）。 */
    @Column(name = "permission_id")
    private Long permissionId;

    /** 显示顺序（同 parentId 下越小越靠前）。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 1=显示，0=隐藏。 */
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
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
