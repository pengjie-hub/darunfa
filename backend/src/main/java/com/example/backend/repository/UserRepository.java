package com.example.backend.repository;

import com.example.backend.entity.User;
import com.example.backend.entity.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 统计指定角色的用户数量（用于"至少保留 1 个 ADMIN"等保护性校验）。 */
    long countByRole(UserRole role);

    /** 悲观写锁：资金操作锁定用户行，防止并发扣款超扣/余额为负。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
