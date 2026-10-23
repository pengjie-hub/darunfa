package com.example.backend.repository;

import com.example.backend.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findAllByRoleId(Long roleId);


    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    void deleteAllByRoleId(Long roleId);

    void deleteAllByPermissionId(Long permissionId);

    @Modifying
    @Query("delete from RolePermission rp where rp.roleId = :roleId and rp.permissionId = :permissionId")
    void deleteByRoleIdAndPermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
