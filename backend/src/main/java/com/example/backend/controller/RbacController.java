package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.entity.Menu;
import com.example.backend.entity.Permission;
import com.example.backend.entity.Role;
import com.example.backend.security.RequireAdmin;
import com.example.backend.service.MenuService;
import com.example.backend.service.PermissionService;
import com.example.backend.service.RoleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台 RBAC（角色-权限-菜单）管理接口。
 * <p>
 * 三个子模块统一挂在 {@code /api/admin/rbac} 下，路径清晰：
 * <ul>
 *   <li>角色：{@code /rbac/roles...}</li>
 *   <li>权限：{@code /rbac/permissions...}</li>
 *   <li>菜单：{@code /rbac/menus...}</li>
 * </ul>
 * 仅管理员可访问。
 */
@RestController
@RequestMapping("/api/admin/rbac")
@RequireAdmin
public class RbacController {

    private final RoleService roleService;
    private final PermissionService permissionService;
    private final MenuService menuService;

    public RbacController(RoleService roleService,
                          PermissionService permissionService,
                          MenuService menuService) {
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.menuService = menuService;
    }

    // ==================== 角色 ====================

    @GetMapping("/roles")
    public ApiResponse<List<Role>> listRoles() {
        return ApiResponse.ok(roleService.listAll());
    }

    @GetMapping("/roles/{id}")
    public ApiResponse<Role> getRole(@PathVariable Long id) {
        return ApiResponse.ok(roleService.get(id));
    }

    @PostMapping("/roles")
    public ApiResponse<Role> createRole(@RequestBody Role r) {
        return ApiResponse.ok(roleService.create(r));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<Role> updateRole(@PathVariable Long id, @RequestBody Role r) {
        return ApiResponse.ok(roleService.update(id, r));
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok();
    }

    /** 查询角色已分配的权限 id 集合（用于角色管理页的「分配权限」勾选回显）。 */
    @GetMapping("/roles/{id}/permissions")
    public ApiResponse<Set<Long>> getRolePermissions(@PathVariable Long id) {
        return ApiResponse.ok(roleService.getAssignedPermissionIds(id));
    }

    /** 批量替换角色的权限集合（传完整 id 列表）。 */
    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<Void> assignRolePermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        roleService.assignPermissions(id, body.get("permissionIds"));
        return ApiResponse.ok();
    }

    // ==================== 权限 ====================

    @GetMapping("/permissions")
    public ApiResponse<List<Permission>> listPermissions() {
        return ApiResponse.ok(permissionService.listAll());
    }

    @GetMapping("/permissions/{id}")
    public ApiResponse<Permission> getPermission(@PathVariable Long id) {
        return ApiResponse.ok(permissionService.get(id));
    }

    @PostMapping("/permissions")
    public ApiResponse<Permission> createPermission(@RequestBody Permission p) {
        return ApiResponse.ok(permissionService.create(p));
    }

    @PutMapping("/permissions/{id}")
    public ApiResponse<Permission> updatePermission(@PathVariable Long id, @RequestBody Permission p) {
        return ApiResponse.ok(permissionService.update(id, p));
    }

    @DeleteMapping("/permissions/{id}")
    public ApiResponse<Void> deletePermission(@PathVariable Long id) {
        permissionService.delete(id);
        return ApiResponse.ok();
    }

    // ==================== 菜单 ====================

    @GetMapping("/menus")
    public ApiResponse<List<Menu>> listMenus() {
        return ApiResponse.ok(menuService.listAll());
    }

    @GetMapping("/menus/{id}")
    public ApiResponse<Menu> getMenu(@PathVariable Long id) {
        return ApiResponse.ok(menuService.get(id));
    }

    @PostMapping("/menus")
    public ApiResponse<Menu> createMenu(@RequestBody Menu m) {
        return ApiResponse.ok(menuService.create(m));
    }

    @PutMapping("/menus/{id}")
    public ApiResponse<Menu> updateMenu(@PathVariable Long id, @RequestBody Menu m) {
        return ApiResponse.ok(menuService.update(id, m));
    }

    @DeleteMapping("/menus/{id}")
    public ApiResponse<Void> deleteMenu(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResponse.ok();
    }
}
