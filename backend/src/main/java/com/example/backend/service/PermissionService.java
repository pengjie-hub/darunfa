package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.entity.Permission;
import com.example.backend.repository.PermissionRepository;
import com.example.backend.repository.RolePermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 权限管理服务。
 * <p>
 * 权限字典本身是相对稳定的（资源:操作粒度），由开发维护，运营一般只做状态调整。
 * 删除权限时需先解绑所有「角色-权限」关联，避免悬空引用。
 */
@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public PermissionService(PermissionRepository permissionRepository,
                             RolePermissionRepository rolePermissionRepository) {
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public List<Permission> listAll() {
        return permissionRepository.findAllByOrderByParentIdAscSortOrderAscIdAsc();
    }

    public Permission get(Long id) {
        return permissionRepository.findById(id).orElseThrow(() -> new BusinessException(404, "权限不存在"));
    }

    @Transactional
    public Permission create(Permission p) {
        if (p.getCode() == null || p.getCode().isBlank()) {
            throw new BusinessException("权限编码不能为空");
        }
        if (permissionRepository.existsByCode(p.getCode())) {
            throw new BusinessException("权限编码已存在: " + p.getCode());
        }
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getSortOrder() == null) p.setSortOrder(0);
        if (p.getParentId() == null) p.setParentId(0L);
        if (p.getType() == null) p.setType(Permission.Type.MENU);
        return permissionRepository.save(p);
    }

    @Transactional
    public Permission update(Long id, Permission src) {
        Permission p = get(id);
        if (src.getName() != null) p.setName(src.getName());
        if (src.getType() != null) p.setType(src.getType());
        if (src.getParentId() != null) p.setParentId(src.getParentId());
        if (src.getModule() != null) p.setModule(src.getModule());
        if (src.getSortOrder() != null) p.setSortOrder(src.getSortOrder());
        if (src.getStatus() != null) p.setStatus(src.getStatus());
        return permissionRepository.save(p);
    }

    @Transactional
    public void delete(Long id) {
        // 先解除所有角色的关联，避免悬空
        rolePermissionRepository.deleteAllByPermissionId(id);
        permissionRepository.deleteById(id);
    }
}
