package com.example.backend.repository;

import com.example.backend.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findAllByOrderByParentIdAscSortOrderAscIdAsc();


    List<Menu> findAllByParentIdOrderBySortOrderAscIdAsc(Long parentId);

    /** 按组件名判重（seed 幂等用；新增菜单无需清表）。 */
    Optional<Menu> findByComponent(String component);
}
