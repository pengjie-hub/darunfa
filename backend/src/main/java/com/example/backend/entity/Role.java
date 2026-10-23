package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 角色实体。
 * <p>
 * 角色是「权限」的集合，是用户与权限之间的中间层。设计要点：
 * <ul>
 *   <li>{@code code}（如 {@code ADMIN / OPS / CS}）是面向系统的唯一键；</li>
 *   <li>{@code name} 是面向运营的中文展示名（如「超级管理员」）；</li>
 *   <li>{@code status} 1=启用，0=禁用，禁用后关联用户无法登录后台；</li>
 *   <li>{@code sortOrder} 控制列表/下拉的显示顺序，越小越靠前；</li>
 *   <li>{@code dataScope} 预留数据权限扩展（ALL / DEPT / SELF），当前版本固定为 ALL。</li>
 * </ul>
 * 关联设计：用户通过 {@code User.role} 字段关联本表的 {@code code}（1对多复用）。
 * 角色与权限的多对多通过 {@link RolePermission} 中间表实现。
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色编码（系统唯一），如 ADMIN / OPS / CS。 */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    /** 角色名称（中文展示），如「超级管理员」。 */
    @Column(nullable = false, length = 32)
    private String name;

    /** 角色描述（运营人员可读）。 */
    @Column(length = 200)
    private String description;

    /** 1=启用，0=禁用。 */
    @Column(nullable = false)
    private Integer status = 1;

    /** 列表/下拉的显示顺序，越小越靠前。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 数据权限范围（预留）：ALL / DEPT / SELF。 */
    @Column(name = "data_scope", length = 16)
    private String dataScope = "ALL";

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
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
