package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.entity.Menu;
import com.example.backend.repository.MenuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 菜单管理服务。
 * <p>
 * 菜单用于控制后台左侧导航的展示项；通过 {@code permissionId} 关联到权限字典，
 * 后续可基于「用户→角色→权限」链路动态过滤可见菜单。
 * 当前版本仅做 CRUD，动态过滤留给下一阶段。
 */
@Service
public class MenuService {

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    public List<Menu> listAll() {
        return menuRepository.findAllByOrderByParentIdAscSortOrderAscIdAsc();
    }

    public Menu get(Long id) {
        return menuRepository.findById(id).orElseThrow(() -> new BusinessException(404, "菜单不存在"));
    }

    @Transactional
    public Menu create(Menu m) {
        if (m.getName() == null || m.getName().isBlank()) {
            throw new BusinessException("菜单名称不能为空");
        }
        if (m.getStatus() == null) m.setStatus(1);
        if (m.getSortOrder() == null) m.setSortOrder(0);
        if (m.getParentId() == null) m.setParentId(0L);
        if (m.getType() == null) m.setType(Menu.Type.MENU);
        return menuRepository.save(m);
    }

    @Transactional
    public Menu update(Long id, Menu src) {
        Menu m = get(id);
        if (src.getParentId() != null) m.setParentId(src.getParentId());
        if (src.getName() != null) m.setName(src.getName());
        if (src.getPath() != null) m.setPath(src.getPath());
        if (src.getIcon() != null) m.setIcon(src.getIcon());
        if (src.getComponent() != null) m.setComponent(src.getComponent());
        if (src.getType() != null) m.setType(src.getType());
        if (src.getPermissionId() != null) m.setPermissionId(src.getPermissionId());
        if (src.getSortOrder() != null) m.setSortOrder(src.getSortOrder());
        if (src.getStatus() != null) m.setStatus(src.getStatus());
        return menuRepository.save(m);
    }

    @Transactional
    public void delete(Long id) {
        // 若有子菜单，禁止删除，避免悬空
        List<Menu> children = menuRepository.findAllByParentIdOrderBySortOrderAscIdAsc(id);
        if (!children.isEmpty()) {
            throw new BusinessException("该菜单下还有 " + children.size() + " 个子菜单，请先删除子菜单");
        }
        menuRepository.deleteById(id);
    }
}
