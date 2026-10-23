package com.example.backend.repository;

import com.example.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByParentIdOrderBySortOrderAsc(Long parentId);

    List<Category> findByStatusOrderBySortOrderAsc(Integer status);

    /** 按名精确查找，供种子数据做「按名幂等」判断。 */
    Optional<Category> findByName(String name);
}
