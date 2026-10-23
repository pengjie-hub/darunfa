package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.entity.Role;
import com.example.backend.entity.RolePermission;
import com.example.backend.repository.RolePermissionRepository;
import com.example.backend.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色管理服务。
 * <p>
 * 职责：
 * <ul>
 *   <li>CRUD 角色字典（增删改查）</li>
 *   <li>维护角色-权限多对多关联（{@code assignPermissions / removePermission}）</li>
 *   <li>对外暴露当前角色已分配的权限 id 列表，便于权限管理页面的勾选回显</li>
 * </ul>
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public RoleService(RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public List<Role> listAll() {
        return roleRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    public Role get(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> new BusinessException(404, "角色不存在"));
    }

    @Transactional
    public Role create(Role r) {
        if (r.getCode() == null || r.getCode().isBlank()) {
            throw new BusinessException("角色编码不能为空");
        }
        if (roleRepository.existsByCode(r.getCode())) {
            throw new BusinessException("角色编码已存在: " + r.getCode());
        }
        if (r.getStatus() == null) r.setStatus(1);
        if (r.getSortOrder() == null) r.setSortOrder(0);
        if (r.getDataScope() == null) r.setDataScope("ALL");
        return roleRepository.save(r);
    }

    @Transactional
    public Role update(Long id, Role src) {
        Role r = get(id);
        // 编码是主键性字段，禁用修改，避免引用错位
        if (src.getName() != null) r.setName(src.getName());
        if (src.getDescription() != null) r.setDescription(src.getDescription());
        if (src.getStatus() != null) r.setStatus(src.getStatus());
        if (src.getSortOrder() != null) r.setSortOrder(src.getSortOrder());
        if (src.getDataScope() != null) r.setDataScope(src.getDataScope());
        return roleRepository.save(r);
    }

    @Transactional
    public void delete(Long id) {
        // 解除该角色与所有权限的关联
        rolePermissionRepository.deleteAllByRoleId(id);
        roleRepository.deleteById(id);
    }

    /** 获取角色当前已分配的权限 id 集合（去重）。 */
    public Set<Long> getAssignedPermissionIds(Long roleId) {
        Set<Long> ids = new HashSet<>();
        for (var rp : rolePermissionRepository.findAllByRoleId(roleId)) {
            ids.add(rp.getPermissionId());
        }
        return ids;
    }

    /** 分配权限：传入完整权限 id 列表，先清空再批量写入。 */
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        get(roleId); // 校验存在
        rolePermissionRepository.deleteAllByRoleId(roleId);
        if (permissionIds == null || permissionIds.isEmpty()) return;
        for (Long pid : permissionIds) {
            rolePermissionRepository.save(new RolePermission(roleId, pid));
        }
    }

    /** 切换单个权限（用于「打勾/取消」细粒度操作）。 */
    @Transactional
    public boolean togglePermission(Long roleId, Long permissionId, boolean checked) {
        get(roleId);
        if (checked) {
            if (!rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
                rolePermissionRepository.save(new RolePermission(roleId, permissionId));
            }
            return true;
        } else {
            rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);
            return false;
        }
    }
}
